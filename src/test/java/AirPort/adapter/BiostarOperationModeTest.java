package AirPort.adapter;

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
        operationMode);
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

  @Test
  void 상수는_카드_전용_값이다() {
    // 현장 장비와 맞춘 값 — 바뀌면 방문객이 문을 못 연다
    assertEquals(21, BiostarUserAdapter.OPERATION_MODE_CARD_ONLY);
  }
}
