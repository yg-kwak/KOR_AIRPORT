package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Suprema BiostarX 연동 어댑터 — 외부 연동은 이 계층으로만 격리한다(AGENTS §4). (docs/integration.md)
 *
 * <p>BiostarX 는 내부망 self-signed 인증서를 쓰므로 TLS 신뢰를 완화한다(내부 어플라이언스 전제).
 */
@Component
public class BiostarAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarAdapter.class);

  private final ObjectMapper objectMapper;

  public BiostarAdapter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** BiostarX 로그인(POST /api/login) 시도. 성공 판정: 200 + bs-session-id 헤더 또는 Response.code=="0". */
  public BiostarResult testLogin(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 비어 있습니다.");
    }
    try {
      String base = (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
      String url = base + "/api/login";
      String body =
          objectMapper.writeValueAsString(
              Map.of(
                  "User",
                  Map.of(
                      "login_id", loginId == null ? "" : loginId,
                      "password", password == null ? "" : password)));

      HttpClient client =
          HttpClient.newBuilder()
              .sslContext(trustAllSsl())
              .connectTimeout(Duration.ofSeconds(5))
              .build();
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(7))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      boolean ok =
          resp.statusCode() == 200
              && (resp.headers().firstValue("bs-session-id").isPresent()
                  || (resp.body() != null && resp.body().contains("\"code\":\"0\"")));
      return ok
          ? BiostarResult.ok()
          : BiostarResult.fail("인증 실패 (HTTP " + resp.statusCode() + ", 세션 미발급)");
    } catch (java.net.ConnectException e) {
      return BiostarResult.fail("서버에 연결할 수 없습니다. IP/포트를 확인하세요.");
    } catch (java.net.http.HttpConnectTimeoutException e) {
      return BiostarResult.fail("연결 시간이 초과되었습니다.");
    } catch (Exception e) {
      log.warn("BiostarX 연결 테스트 오류: {}", e.toString());
      return BiostarResult.fail(e.getClass().getSimpleName());
    }
  }

  /**
   * BiostarX 출입그룹 목록 조회 — 로그인(세션 발급) 후 {@code POST /api/v2/access_groups/search}. 응답 {@code
   * AccessGroupCollection.rows[].{id,name}} 를 파싱한다. 세션ID/비밀번호는 로그에 남기지 않는다.
   */
  public BiostarGroups searchAccessGroups(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarGroups.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      String base = (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
      HttpClient client =
          HttpClient.newBuilder()
              .sslContext(trustAllSsl())
              .connectTimeout(Duration.ofSeconds(5))
              .build();

      // 1) 로그인 → bs-session-id
      String loginBody =
          objectMapper.writeValueAsString(
              Map.of(
                  "User",
                  Map.of(
                      "login_id", loginId == null ? "" : loginId,
                      "password", password == null ? "" : password)));
      HttpResponse<String> login =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/api/login"))
                  .timeout(Duration.ofSeconds(7))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(loginBody, StandardCharsets.UTF_8))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      String session = login.headers().firstValue("bs-session-id").orElse(null);
      if (login.statusCode() != 200 || session == null) {
        return BiostarGroups.fail("BiostarX 인증 실패 (HTTP " + login.statusCode() + ")");
      }

      // 2) 출입그룹 검색
      HttpResponse<String> resp =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/api/v2/access_groups/search"))
                  .timeout(Duration.ofSeconds(10))
                  .header("Content-Type", "application/json")
                  .header("bs-session-id", session)
                  .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        return BiostarGroups.fail("출입그룹 조회 실패 (HTTP " + resp.statusCode() + ")");
      }

      JsonNode rows = objectMapper.readTree(resp.body()).path("AccessGroupCollection").path("rows");
      List<BiostarGroup> groups = new ArrayList<>();
      if (rows.isArray()) {
        for (JsonNode n : rows) {
          Integer id = n.path("id").isMissingNode() ? null : n.path("id").asInt();
          String name = n.path("name").asText(null);
          if (id != null) {
            groups.add(new BiostarGroup(id, name));
          }
        }
      }
      return BiostarGroups.ok(groups);
    } catch (java.net.ConnectException e) {
      return BiostarGroups.fail("BiostarX 서버에 연결할 수 없습니다. IP/포트를 확인하세요.");
    } catch (java.net.http.HttpConnectTimeoutException e) {
      return BiostarGroups.fail("연결 시간이 초과되었습니다.");
    } catch (Exception e) {
      log.warn("BiostarX 출입그룹 조회 오류: {}", e.toString());
      return BiostarGroups.fail(e.getClass().getSimpleName());
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
