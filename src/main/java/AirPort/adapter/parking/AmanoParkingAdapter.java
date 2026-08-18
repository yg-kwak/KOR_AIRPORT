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
   * 정기권 등록 — <b>삭제 후 등록</b>한다.
   *
   * <p>아마노는 같은 (주차장, 차량번호)가 이미 있으면 {@code "[정기차량 등록] 이미 등록된 차량 (…)"} 으로 거부한다(2026-08-13 시험서버 실증).
   * 재저장 때마다 등록을 다시 날리는 우리 정책을 그대로 지키려면 먼저 지워야 한다. 삭제는 없는 차량이어도 성공으로 돌아오므로 신규 등록에도 안전하다.
   */
  public ParkingResult register(ParkingPassRequest req) {
    if (!enabled()) {
      return ParkingResult.fail("주차 연동이 꺼져 있습니다.");
    }
    ParkingResult cleared = delete(req.carNo());
    if (!cleared.success()) {
      return cleared; // 지우지 못하면 등록도 거부당한다 — 사유를 그대로 올린다
    }
    try {
      return call(REGISTER_PATH, registerBody(req), "정기권 등록", req.carNo());
    } catch (Exception e) {
      return ParkingResult.fail(friendly(e, "정기권 등록"));
    }
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

  /** POST 후 {@code data.success} 로 성공을 판정한다. 실패 사유는 {@code data.errorMessage}. */
  private ParkingResult call(String path, String body, String what, String carNo) throws Exception {
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
