package AirPort.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 세션(bs-session-id) 관리자 — API 호출마다 로그인하지 않도록 세션을 캐시한다(외부 연동 격리, AGENTS §4).
 *
 * <p>동작:
 *
 * <ul>
 *   <li>세션이 없으면 로그인해 {@code bs-session-id} 를 발급·캐시한다.
 *   <li>인증 API 응답이 만료(HTTP 401 + {@code Response.code == "10"}, "Login required")면 재로그인 후 1회 재시도.
 *   <li>IP/로그인ID 조합이 바뀌면 캐시를 폐기하고 새로 로그인한다.
 * </ul>
 *
 * <p>세션ID/비밀번호는 로그에 남기지 않는다(security.md). BiostarX 는 내부망 self-signed 인증서를 쓰므로 TLS 신뢰를 완화한다.
 */
@Component
public class BiostarSession {

  private static final Logger log = LoggerFactory.getLogger(BiostarSession.class);
  private static final String SESSION_HEADER = "bs-session-id";
  private static final String EXPIRED_CODE = "10"; // BiostarX "Login required."

  private final ObjectMapper objectMapper;
  private final Object lock = new Object();

  private HttpClient client; // trust-all, 스레드-세이프 재사용
  private String sessionId; // 캐시된 bs-session-id
  private String sessionKey; // "base\0loginId" — 자격 변경 감지

  public BiostarSession(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 인증된 POST 호출. 캐시 세션이 없으면 로그인, 세션 만료(401+code 10)면 재로그인 후 1회 재시도한다.
   *
   * @throws BiostarSessionException 로그인 실패(세션 미발급)
   */
  public HttpResponse<String> post(
      String base, String loginId, String password, String path, String jsonBody) throws Exception {
    return exchange("POST", base, loginId, password, path, jsonBody);
  }

  /** 인증된 PUT 호출(수정). post 와 동일하게 세션 캐시·만료 재시도를 적용한다. */
  public HttpResponse<String> put(
      String base, String loginId, String password, String path, String jsonBody) throws Exception {
    return exchange("PUT", base, loginId, password, path, jsonBody);
  }

  /** 인증된 GET 호출(조회). 본문 없이 전송한다. */
  public HttpResponse<String> get(String base, String loginId, String password, String path)
      throws Exception {
    return exchange("GET", base, loginId, password, path, null);
  }

  /** 인증된 DELETE 호출(삭제). 본문 없이 전송한다(대상은 쿼리 파라미터). */
  public HttpResponse<String> delete(String base, String loginId, String password, String path)
      throws Exception {
    return exchange("DELETE", base, loginId, password, path, null);
  }

  private HttpResponse<String> exchange(
      String method, String base, String loginId, String password, String path, String jsonBody)
      throws Exception {
    HttpClient c = client();
    String key = sessionKey(base, loginId);
    String sid = acquire(c, base, loginId, password, key);

    HttpResponse<String> resp = send(c, method, base, path, sid, jsonBody);
    if (isSessionExpired(resp)) {
      log.info("BiostarX 세션 만료 감지 — 재로그인 후 재시도");
      sid = refresh(c, base, loginId, password, key);
      resp = send(c, method, base, path, sid, jsonBody);
    }
    return resp;
  }

  /** 로그인 세션 발급(연결 테스트/자격 검증용). 성공 시 세션을 캐시한다. */
  public void login(String base, String loginId, String password) throws Exception {
    refresh(client(), base, loginId, password, sessionKey(base, loginId));
  }

  /*
   * BiostarX 인증서는 설치 시 만들어진 self-signed 라 SAN 에 실제 서버 IP 가 없다.
   * 그러면 trust-all 을 써도 "No subject alternative names matching IP address ..." 로 핸드셰이크가 깨진다
   * — 인증서 신뢰(TrustManager)와 호스트명 검증은 별개이고, HttpClient 는 후자를 항상 켠다.
   * SSLParameters.setEndpointIdentificationAlgorithm(null) 은 HttpClient 가 다시 덮어써서 듣지 않는다.
   * 이 속성이 유일하게 동작하며, HttpClient 가 처음 만들어지기 전에 정해져야 해서 클래스 로딩 시점에 건다.
   * 앱에서 HttpClient 를 쓰는 곳은 BiostarX 연동뿐이다(내부망 전용).
   */
  static {
    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
  }

  /** trust-all HttpClient(스레드-세이프, 재사용). */
  public HttpClient client() throws Exception {
    synchronized (lock) {
      if (client == null) {
        client =
            HttpClient.newBuilder()
                .sslContext(trustAllSsl())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
      }
      return client;
    }
  }

  private static String sessionKey(String base, String loginId) {
    return base + "\u0000" + (loginId == null ? "" : loginId);
  }

  /** 캐시 세션 반환 — 없거나 자격 키가 바뀌었으면 로그인. */
  private String acquire(HttpClient c, String base, String loginId, String password, String key)
      throws Exception {
    synchronized (lock) {
      if (sessionId != null && key.equals(sessionKey)) {
        return sessionId;
      }
    }
    return refresh(c, base, loginId, password, key);
  }

  /** 강제 재로그인 + 캐시 갱신. */
  private String refresh(HttpClient c, String base, String loginId, String password, String key)
      throws Exception {
    String sid = doLogin(c, base, loginId, password);
    synchronized (lock) {
      sessionId = sid;
      sessionKey = key;
    }
    return sid;
  }

  private String doLogin(HttpClient c, String base, String loginId, String password)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "User",
                Map.of(
                    "login_id", loginId == null ? "" : loginId,
                    "password", password == null ? "" : password)));
    HttpResponse<String> resp =
        c.send(
            HttpRequest.newBuilder(URI.create(base + "/api/login"))
                .timeout(Duration.ofSeconds(7))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    String sid = resp.headers().firstValue(SESSION_HEADER).orElse(null);
    if (resp.statusCode() != 200 || sid == null) {
      // 무엇을 고쳐야 하는지 화면에서 바로 알 수 있어야 한다(현장 점검용). 본문은 원인 파악에만 쓰고 짧게 자른다.
      log.warn(
          "BiostarX 로그인 실패 — {} HTTP {} 세션헤더={} 응답={}",
          base + "/api/login",
          resp.statusCode(),
          sid == null ? "없음" : "있음",
          snippet(resp.body()));
      throw new BiostarSessionException(loginFailMessage(resp.statusCode(), sid));
    }
    return sid;
  }

  /** 실패 원인을 사람이 읽고 바로 조치할 수 있는 문구로 바꾼다. */
  private static String loginFailMessage(int status, String sid) {
    return switch (status) {
      case 200 -> "로그인은 되었으나 세션이 발급되지 않았습니다. BiostarX 의 API 사용 설정을 확인하세요.";
      case 401, 403 -> "로그인 ID 또는 비밀번호가 맞지 않습니다. (HTTP " + status + ")";
      case 404 -> "로그인 API(/api/login)를 찾을 수 없습니다. 포트가 BiostarX 웹 포트인지, API 사용이 켜져 있는지 확인하세요.";
      default ->
          status >= 500
              ? "BiostarX 서버 내부 오류입니다. (HTTP " + status + ")"
              : "BiostarX 인증 실패 (HTTP " + status + ")";
    };
  }

  /** 응답 본문 앞부분만 — 로그가 길어지지 않게. */
  private static String snippet(String body) {
    if (body == null || body.isBlank()) {
      return "(없음)";
    }
    String one = body.replaceAll("\\s+", " ").trim();
    return one.length() <= 200 ? one : one.substring(0, 200) + "…";
  }

  /**
   * 실패한 요청의 본문을 남긴다 — 응답만 보면 "not defined" 처럼 <b>무엇이</b> 잘못됐는지 알 수 없다. 어느 그룹·출입그룹·카드를 보냈는지가 있어야 원인을
   * 찾는다.
   *
   * <p>성공은 남기지 않는다(양이 많고 개인정보다). 401 도 제외한다 — 세션 만료는 곧바로 재로그인해 다시 보내므로 실패가 아니다.
   *
   * <p>성명·사진·연락처는 개인정보라 가린다(security.md). 진단에 필요한 건 <b>구조</b>다 — user_group_id, access_groups,
   * cards, 유효기간. 로그인 본문은 이 경로를 타지 않아 비밀번호가 실릴 일이 없다.
   */
  private static final java.util.regex.Pattern MASK =
      java.util.regex.Pattern.compile(
          "\"(password|name|photo|user_photo|phone|email|birthday)\"\s*:\s*\"[^\"]*\"",
          java.util.regex.Pattern.CASE_INSENSITIVE);

  static String maskBody(String json) { // 테스트에서 직접 확인한다
    if (json == null || json.isBlank()) {
      return "(본문 없음)";
    }
    String masked = MASK.matcher(json).replaceAll("\"$1\":\"***\"");
    return masked.length() <= 1500 ? masked : masked.substring(0, 1500) + "…";
  }

  private HttpResponse<String> send(
      HttpClient c, String method, String base, String path, String sid, String jsonBody)
      throws Exception {
    HttpResponse<String> resp = doSend(c, method, base, path, sid, jsonBody);
    if (resp.statusCode() >= 400 && resp.statusCode() != 401) {
      log.warn("BiostarX 요청 실패 — {} {} 본문: {}", method, path, maskBody(jsonBody));
    }
    return resp;
  }

  private HttpResponse<String> doSend(
      HttpClient c, String method, String base, String path, String sid, String jsonBody)
      throws Exception {
    return c.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header(SESSION_HEADER, sid)
            .method(
                method,
                jsonBody == null
                    ? HttpRequest.BodyPublishers.noBody() // GET 등 본문 없는 호출
                    : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** 세션 만료 판정: HTTP 401 + {@code Response.code == "10"}. 401 인데 파싱 불가하면 만료로 간주(1회 재시도). */
  private boolean isSessionExpired(HttpResponse<String> resp) {
    if (resp.statusCode() != 401) {
      return false;
    }
    try {
      String code = objectMapper.readTree(resp.body()).path("Response").path("code").asText("");
      return code.isEmpty() || EXPIRED_CODE.equals(code);
    } catch (Exception e) {
      return true;
    }
  }

  private static SSLContext trustAllSsl() throws Exception {
    SSLContext ctx = SSLContext.getInstance("TLS");
    TrustManager[] trustAll = {
      new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}

        public void checkServerTrusted(X509Certificate[] c, String a) {}

        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }
    };
    ctx.init(null, trustAll, new SecureRandom());
    return ctx;
  }
}
