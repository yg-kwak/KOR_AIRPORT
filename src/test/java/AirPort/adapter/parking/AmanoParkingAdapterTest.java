package AirPort.adapter.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 아마노 정기권 어댑터 단위 테스트 — 실제 주차관제 대신 로컬 HTTP 스텁을 띄운다.
 *
 * <p>지키려는 것: (1) 인증은 HTTP Basic, (2) 등록은 <b>삭제 후 등록</b>(같은 차량 재등록을 아마노가 거부한다), (3) 성공 판정은 HTTP 상태가
 * 아니라 {@code data.success}, (4) 한글 사유가 깨지지 않는다(UTF-8).
 */
class AmanoParkingAdapterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private final List<String> paths = new ArrayList<>();
  private final List<String> bodies = new ArrayList<>();
  private final List<String> auths = new ArrayList<>();

  /** 다음 응답 본문 — 테스트가 갈아 끼운다. */
  private volatile String nextBody = success();

  private static String success() {
    return "{\"status\":\"200\",\"statusMsg\":\"success\",\"data\":"
        + "{\"success\":true,\"errorMessage\":\"\"}}";
  }

  @BeforeEach
  void startStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/interop/", this::handle);
    server.start();
  }

  private void handle(HttpExchange ex) throws IOException {
    paths.add(ex.getRequestURI().getPath());
    auths.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
    bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    byte[] out = nextBody.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json"); // charset 없이 — 현장과 같다
    ex.sendResponseHeaders(200, out.length);
    ex.getResponseBody().write(out);
    ex.close();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  private AmanoParkingAdapter adapter(boolean enabled) {
    return new AmanoParkingAdapter(
        MAPPER, "http://127.0.0.1:" + server.getAddress().getPort(), "ezcare", "pw!", 20, enabled);
  }

  private static ParkingPassRequest pass() {
    return new ParkingPassRequest("99테9901", "연동시험", "passType2", "20260813", "20260820");
  }

  @Test
  void 등록은_삭제를_먼저_보낸다() {
    // 아마노는 이미 있는 차량의 등록을 거부한다("이미 등록된 차량"). 재저장마다 등록을 다시 날리려면 먼저 지워야 한다.
    assertTrue(adapter(true).register(pass()).success());

    assertEquals(List.of("/interop/deleteCustdefInfo.do", "/interop/setCustdefInfo.do"), paths);
  }

  @Test
  void 인증은_HTTP_Basic_이다() {
    adapter(true).delete("99테9901");

    String expected =
        "Basic "
            + Base64.getEncoder().encodeToString("ezcare:pw!".getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, auths.get(0));
  }

  @Test
  void 등록_본문은_규격대로_채운다() throws Exception {
    adapter(true).register(pass());

    JsonNode body = MAPPER.readTree(bodies.get(1)); // 0=삭제, 1=등록
    assertEquals(20, body.path("lotAreaNo").asInt());
    assertEquals("99테9901", body.path("carNo").asText());
    assertEquals("연동시험", body.path("userName").asText());
    assertEquals("passType2", body.path("passType").asText());
    assertEquals("20260813", body.path("startDate").asText());
    assertEquals("20260820", body.path("endDate").asText());
    // 공동주택용 필드는 공항에서 쓰지 않는다 — 공란/기본값으로 고정
    assertEquals("", body.path("dongCode").asText());
    assertEquals("", body.path("hoCode").asText());
    assertEquals("", body.path("remark").asText());
    assertEquals(0, body.path("groupNo").asInt());
    assertFalse(body.path("noAlarm").asBoolean());
    assertFalse(body.path("isVIP").asBoolean());
    assertEquals(0, body.path("iTendatedOverlapped").asInt());
  }

  @Test
  void 삭제_본문은_주차장번호와_차량번호_뿐이다() throws Exception {
    adapter(true).delete("99테9901");

    JsonNode body = MAPPER.readTree(bodies.get(0));
    assertEquals(20, body.path("lotAreaNo").asInt());
    assertEquals("99테9901", body.path("carNo").asText());
    assertEquals(2, body.size(), body.toString());
  }

  @Test
  void HTTP_200_이어도_success_가_false_면_실패다() {
    // 아마노는 거부도 HTTP 200 으로 돌려준다 — 상태코드만 보면 실패를 성공으로 읽는다
    nextBody =
        "{\"status\":\"200\",\"statusMsg\":\"success\",\"data\":"
            + "{\"success\":false,\"errorMessage\":\"[정기차량 등록] 이미 등록된 차량 (99테9901)\"}}";

    ParkingResult r = adapter(true).delete("99테9901");

    assertFalse(r.success());
    assertTrue(r.message().contains("이미 등록된 차량"), r.message()); // 한글이 깨지지 않는다
  }

  @Test
  void 삭제가_실패하면_등록으로_넘어가지_않는다() {
    // 지우지 못한 채 등록하면 "이미 등록된 차량" 으로 또 거부된다 — 사유를 그대로 올린다
    nextBody = "{\"status\":\"200\",\"data\":{\"success\":false,\"errorMessage\":\"주차장 번호 오류\"}}";

    ParkingResult r = adapter(true).register(pass());

    assertFalse(r.success());
    assertEquals(List.of("/interop/deleteCustdefInfo.do"), paths);
  }

  @Test
  void 연동이_꺼져_있으면_아무것도_보내지_않는다() {
    // 개발·시험 환경이 현장 주차장을 건드리지 않게 하는 기본값
    assertFalse(adapter(false).register(pass()).success());
    assertFalse(adapter(false).delete("99테9901").success());
    assertTrue(paths.isEmpty());
  }
}
