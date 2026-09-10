package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 개인 인증 모드(private_operation_modes) payload 검증.
 *
 * <p>정규인원은 얼굴을 등록해 얼굴+카드로 인증하지만 방문객(임시·장기·상주·순찰·대여)은 얼굴이 없다. 카드 전용 모드를 안 붙이면 카드를 대도 문이 열리지 않고, 반대로
 * 정규인원에 붙이면 얼굴 인증이 막힌다 — 어느 쪽이든 현장에서 사람이 못 들어간다.
 */
class BiostarOperationModeTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final BiostarUserAdapter adapter =
      new BiostarUserAdapter(mapper, mock(BiostarSession.class));

  private static BiostarUserRequest request(String userId, Integer operationMode) {
    return request(userId, operationMode, null);
  }

  private static BiostarUserRequest request(
      String userId, Integer operationMode, String department) {
    return new BiostarUserRequest(
        userId,
        "홍길동",
        null,
        null,
        14236,
        null,
        "2026-08-11T00:00:00.00Z",
        "2026-08-12T23:59:00.00Z",
        null,
        List.of(1),
        null,
        null,
        null,
        List.of(),
        operationMode,
        department);
  }

  @Test
  void 방문객은_카드_전용_인증_모드가_실린다() throws Exception {
    JsonNode user = mapper.readTree(adapter.userPayload(request("IS000001", 21))).path("User");

    JsonNode modes = user.path("private_operation_modes");
    assertTrue(modes.isArray() && modes.size() == 1, user.toString());
    JsonNode m = modes.get(0);
    assertEquals(0, m.path("index").asInt(-1));
    assertEquals("IS000001", m.path("user_id").asText()); // 자기 자신에게 붙는 설정이다
    assertEquals(1, m.path("operation_method").asInt(-1));
    assertEquals(21, m.path("operation_mode").asInt(-1));
  }

  @Test
  void 정규인원은_인증_모드를_보내지_않는다() throws Exception {
    // 붙이는 순간 장비/사용자그룹에 설정된 얼굴+카드 모드를 덮어쓴다
    JsonNode user = mapper.readTree(adapter.userPayload(request("400001", null))).path("User");

    assertFalse(user.has("private_operation_modes"), user.toString());
  }

  // ── 부서(허가구역) ────────────────────────────────────────────────────────

  @Test
  void 부서에_허가구역_번호가_실린다() throws Exception {
    // 장비 화면·이벤트 목록에서 그 사람이 어디를 다니는 사람인지 한눈에 보이라고 넣는다
    JsonNode user =
        mapper.readTree(adapter.userPayload(request("400001", null, "124"))).path("User");

    assertEquals("124", user.path("department").asText(), user.toString());
  }

  @Test
  void 구역이_없으면_부서를_보내지_않는다() throws Exception {
    // 등록 payload 는 없는 값을 넣지 않는다 — 빈 문자열을 보내면 장비에 빈 부서가 생긴다
    JsonNode user = mapper.readTree(adapter.userPayload(request("400001", null))).path("User");

    assertFalse(user.has("department"), user.toString());
  }

  @Test
  void 출입그룹을_바꾸면_부서도_따라_바뀐다() throws Exception {
    // 구역만 고치고 부서를 그대로 두면 장비 화면이 옛 구역을 계속 보여준다 — 둘 중 뭐가 맞는지 알 수 없다
    JsonNode user = updatePayload(request("400001", null, "12"), request("400001", null, "124"));

    assertEquals("124", user.path("department").asText(), user.toString());
  }

  @Test
  void 구역을_전부_빼면_부서를_지운다() throws Exception {
    // 있다가 없어진 값은 공란으로 보낸다(BiostarUserAdapter 규칙). 안 보내면 지워지지 않는다
    JsonNode user = updatePayload(request("400001", null, "124"), request("400001", null, null));

    assertTrue(user.has("department"), user.toString());
    assertEquals("", user.path("department").asText());
  }

  @Test
  void 부서가_그대로면_보내지_않는다() throws Exception {
    JsonNode user = updatePayload(request("400001", null, "124"), request("400001", null, "124"));

    assertFalse(user.has("department"), user.toString());
  }

  /** PUT payload 의 User 노드 — updateUser 는 통신을 하므로 델타 구성만 같은 방식으로 확인한다. */
  private JsonNode updatePayload(BiostarUserRequest before, BiostarUserRequest after)
      throws Exception {
    return mapper.readTree(adapter.updatePayload(before, after)).path("User");
  }

  @Test
  void 상수는_카드_전용_값이다() {
    // 현장 장비와 맞춘 값 — 바뀌면 방문객이 문을 못 연다
    assertEquals(21, BiostarUserAdapter.OPERATION_MODE_CARD_ONLY);
  }
}
