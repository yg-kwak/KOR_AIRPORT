package AirPort.adapter.parking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 아마노 주차관제 연동 어댑터 — 정기권(차단기 자동 개방) 등록·삭제. 외부 연동은 이 계층으로만 격리한다(AGENTS §4). (docs/integration.md)
 *
 * <p>규격: {@code POST http://{host}:9948/interop/{resource}.do}, {@code application/json} UTF-8,
 * POST 전용. 인증은 HTTP Basic({@code base64(userId:userPw)}). 성공 판정은 HTTP 상태가 아니라 본문 {@code
 * data.success} 다 — 잘못된 요청도 HTTP 200 으로 돌아온다.
 *
 * <p>접속정보는 설정으로 뺀다(비밀값 커밋 금지, docs/security.md). {@code app.parking.enabled=false} 면 아무 호출도 하지 않는다
 * — 개발·시험 환경이 현장 주차장을 건드리지 않게 하는 기본값이다.
 */
@Component
public class AmanoParkingAdapter {

  private static final Logger log = LoggerFactory.getLogger(AmanoParkingAdapter.class);

  private static final String REGISTER_PATH = "/interop/setCustdefInfo.do";
  private static final String DELETE_PATH = "/interop/deleteCustdefInfo.do";

  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String user;
  private final String password;
  private final int lotAreaNo;
  private final boolean enabled;

  private HttpClient client; // 스레드-세이프, 재사용
  private final Object lock = new Object();

  public AmanoParkingAdapter(
      ObjectMapper objectMapper,
      @Value("${app.parking.base-url:}") String baseUrl,
      @Value("${app.parking.user:}") String user,
      @Value("${app.parking.password:}") String password,
      @Value("${app.parking.lot-area-no:20}") int lotAreaNo,
      @Value("${app.parking.enabled:false}") boolean enabled) {
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    this.user = user == null ? "" : user;
    this.password = password == null ? "" : password;
    this.lotAreaNo = lotAreaNo;
    this.enabled = enabled;
  }

  /** 연동을 쓸 수 있는 상태인가 — 꺼져 있거나 주소가 비어 있으면 호출하지 않는다. */
  public boolean enabled() {
    return enabled && !baseUrl.isBlank();
  }

  /** 주차장 번호(설정값) — 감사 로그 문구에 쓴다. */
  public int lotAreaNo() {
    return lotAreaNo;
  }

  /**
   * 정기권 등록 — <b>등록을 먼저 시도하고, 이미 있을 때만 지우고 다시 넣는다</b>.
   *
   * <p>아마노는 같은 (주차장, 차량번호)가 이미 있으면 {@code "[정기차량 등록] 이미 등록된 차량 (…)"} 으로 거부한다(2026-08-13 시험서버 실증).
   * 그래서 한때는 <b>항상</b> 지우고 등록했는데, 그러면 <b>신규 차량에도 삭제가 한 번씩 나갔다</b> — 아마노 쪽에서 보면 없는 차를 지우는 호출이 계속 쌓인다.
   *
   * <p>조회로 미리 확인하는 방법은 쓸 수 없다. {@code getCustdefList.do} 는 <b>차량번호로 걸러지지 않아</b> 전체 목록(수천 건)이
   * 돌아온다(2026-08-19 실증). 그래서 등록을 먼저 던지고 거부 사유를 보고 판단한다 — 신규는 호출 1번으로 끝나고, 우리 기록과 아마노 실제가 어긋나 있어도
   * 스스로 맞는다.
   */
  public ParkingResult register(ParkingPassRequest req) {
    if (!enabled()) {
      return ParkingResult.fail("주차 연동이 꺼져 있습니다.");
    }
    ParkingResult first = tryRegister(req);
    if (first.success() || !alreadyRegistered(first)) {
      return first;
    }
    // 이미 있는 차량 — 종별·기간을 바꾸려면 지우고 다시 넣는 수밖에 없다(수정 API 가 없다)
    ParkingResult cleared = delete(req.carNo());
    if (!cleared.success()) {
      return cleared; // 지우지 못하면 등록도 계속 거부당한다 — 사유를 그대로 올린다
    }
    return tryRegister(req);
  }

  private ParkingResult tryRegister(ParkingPassRequest req) {
    try {
      return call(REGISTER_PATH, registerBody(req), "정기권 등록", req.carNo());
    } catch (Exception e) {
      return ParkingResult.fail(friendly(e, "정기권 등록"));
    }
  }

  /** 거부 사유가 "이미 등록된 차량" 인가 — 이때만 지우고 다시 넣는다(다른 사유는 지워선 안 된다). */
  private static boolean alreadyRegistered(ParkingResult r) {
    String m = r.message();
    return m != null && m.contains("이미 등록된");
  }

  /** 정기권 삭제 — 본문은 {@code (lotAreaNo, carNo)} 뿐이다. 등록돼 있지 않은 차량도 성공으로 돌아온다. */
  public ParkingResult delete(String carNo) {
    if (!enabled()) {
      return ParkingResult.fail("주차 연동이 꺼져 있습니다.");
    }
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("lotAreaNo", lotAreaNo);
      body.put("carNo", carNo);
      return call(DELETE_PATH, body.toString(), "정기권 삭제", carNo);
    } catch (Exception e) {
      return ParkingResult.fail(friendly(e, "정기권 삭제"));
    }
  }

  /**
   * 등록 본문 — 필드 매핑은 docs/integration.md 의 표를 따른다.
   *
   * <p>{@code dongCode}·{@code hoCode}·{@code remark}·{@code tel}·{@code mobile}·{@code carModel} 은
   * 공동주택용이라 공항에서는 쓰지 않는다(공란). {@code groupNo} 는 전광판 문구용 그룹으로 0(미사용), {@code noAlarm}/{@code isVIP}
   * 는 false, {@code iTendatedOverlapped}(부제처리)는 0=사용이다.
   */
  private String registerBody(ParkingPassRequest req) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("lotAreaNo", lotAreaNo);
    n.put("carNo", req.carNo());
    n.put("userName", req.userName() == null ? "" : req.userName());
    n.put("passType", req.passType());
    n.put("startDate", req.startDate());
    n.put("endDate", req.endDate());
    n.put("dongCode", "");
    n.put("hoCode", "");
    n.put("groupNo", 0);
    n.put("remark", "");
    n.put("tel", "");
    n.put("mobile", "");
    n.put("carModel", "");
    n.put("noAlarm", false);
    n.put("siteID", 0);
    n.put("isVIP", false);
    n.put("iTendatedOverlapped", 0);
    return n.toString();
  }

  /**
   * POST 후 {@code data.success} 로 성공을 판정한다. 실패 사유는 {@code data.errorMessage}.
   *
   * <p>주고받은 본문은 <b>DEBUG</b> 로 남긴다 — 현장에서 "왜 차단기가 안 열리나"를 따질 때 우리가 무엇을 보냈고 아마노가 무엇이라 답했는지가 있어야 한다.
   * 평소에는 꺼 두고(로그가 빠르게 쌓인다) 필요할 때만 켠다: {@code logging.level.AirPort.adapter.parking=DEBUG}.
   */
  private ParkingResult call(String path, String body, String what, String carNo) throws Exception {
    // 요청은 보내기 전에 남긴다 — 응답이 오지 않아도(타임아웃) 무엇을 보냈는지는 남아야 한다.
    // Authorization 헤더는 절대 찍지 않는다(연동 비밀번호가 그대로 드러난다).
    log.debug("주차 API 요청 — POST {}{} {}", baseUrl, path, forLog(body));
    long startedNs = System.nanoTime();
    HttpResponse<String> resp =
        client()
            .send(
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", basicAuth())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(),
                // 응답에 charset 이 없어도 본문은 UTF-8 이다. 지정하지 않으면 한글 오류 사유가 깨져 읽을 수 없다.
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    log.debug(
        "주차 API 응답 — {} HTTP {} ({}ms) {}",
        path,
        resp.statusCode(),
        (System.nanoTime() - startedNs) / 1_000_000,
        forLog(resp.body()));
    if (resp.statusCode() == 401) {
      return ParkingResult.fail("주차관제 인증에 실패했습니다. 연동 계정을 확인하세요. (HTTP 401)");
    }
    if (resp.statusCode() != 200) {
      log.warn("주차 {} 실패 — {} HTTP {}", what, carNo, resp.statusCode());
      return ParkingResult.fail(what + " 실패 (HTTP " + resp.statusCode() + ")");
    }
    JsonNode data = objectMapper.readTree(resp.body()).path("data");
    if (data.path("success").asBoolean(false)) {
      return ParkingResult.ok();
    }
    String reason = data.path("errorMessage").asText("");
    log.warn("주차 {} 거부 — {} : {}", what, carNo, reason.isBlank() ? "(사유 없음)" : reason);
    return ParkingResult.fail(reason.isBlank() ? (what + "이(가) 거부되었습니다.") : reason);
  }

  /**
   * 로그에 남길 본문 — <b>성명은 가린다</b>.
   *
   * <p>성명은 DB 에 ARIA 로 암호화해 두는 항목이다(AGENTS §4). 연동을 들여다보려고 켠 DEBUG 로그에 그것이 평문으로 쌓이면 암호화가 무의미해진다.
   * 차량번호·종별·기간은 그대로 남긴다 — 차단기 문제를 따지려면 그 값들이 필요하고, 이미 WARN 로그에도 나온다.
   */
  private String forLog(String body) {
    if (body == null || body.isBlank()) {
      return "(본문 없음)";
    }
    String out;
    try {
      JsonNode n = objectMapper.readTree(body);
      maskNames(n);
      out = n.toString();
    } catch (Exception e) {
      // 파싱이 안 되면 통째로 가린다 — 개인정보가 섞여 있을지 알 수 없다
      return "(본문 파싱 실패 — " + body.length() + "자)";
    }
    // 조회 응답은 수천 건이 온다 — 로그 파일을 삼키지 않게 자른다
    return out.length() <= 1500 ? out : out.substring(0, 1500) + "…(총 " + out.length() + "자)";
  }

  /** 중첩된 목록 안까지 훑어 성명을 가린다. */
  private static void maskNames(JsonNode node) {
    if (node.isObject()) {
      ObjectNode o = (ObjectNode) node;
      if (!o.path("userName").asText("").isBlank()) {
        o.put("userName", "***");
      }
      o.fields().forEachRemaining(e -> maskNames(e.getValue()));
    } else if (node.isArray()) {
      node.forEach(AmanoParkingAdapter::maskNames);
    }
  }

  private String basicAuth() {
    String raw = user + ":" + password;
    return "Basic "
        + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)); // 비밀번호는 로그 금지
  }

  private HttpClient client() {
    synchronized (lock) {
      if (client == null) {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      }
      return client;
    }
  }

  /** 통신 예외 → 현장에서 무엇을 고칠지 알 수 있는 문구. */
  private static String friendly(Exception e, String what) {
    if (e instanceof java.net.UnknownHostException) {
      return "주차관제 주소를 찾을 수 없습니다. 설정을 확인하세요.";
    }
    if (e instanceof java.net.ConnectException) {
      return "주차관제 서버에 연결할 수 없습니다. 주소·포트를 확인하세요.";
    }
    if (e instanceof java.net.http.HttpConnectTimeoutException
        || e instanceof java.net.SocketTimeoutException) {
      return "주차관제 연결 시간이 초과되었습니다.";
    }
    log.warn("주차 {} 오류: {}", what, e.toString());
    return what + " 중 오류가 발생했습니다. (" + e.getClass().getSimpleName() + ")";
  }
}
