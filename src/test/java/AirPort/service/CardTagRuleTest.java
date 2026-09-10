package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.adapter.biostar.BiostarAuthEvent;
import org.junit.jupiter.api.Test;

/**
 * 방문객 카드 태깅 — <b>내 리더에서 읽은 미등록 카드</b>만 화면으로 올린다.
 *
 * <p>거르는 두 조건이 다 위험하다. 이벤트 코드를 넓히면 <b>인증 성공</b>의 인원ID 를 카드번호로 읽어 엉뚱한 카드를 찾고, 리더를 안 보면 다른 사람이 지나가며
 * 찍은 카드가 내 화면의 방문객에게 배정된다. 둘 다 화면에는 그럴듯하게 보인다.
 *
 * <p>실제 소켓 프레임에서 이 값들이 어떻게 나오는지는 {@code BiostarEventSocketTest} 가 지킨다.
 */
class CardTagRuleTest {

  private static final String MY_READER = "543737030";

  private static BiostarAuthEvent event(String code, String deviceId, String userId) {
    return new BiostarAuthEvent(
        code, "VERIFY_FAIL_CARD", "2026-09-10T01:58:40.00Z", deviceId, "리더", userId, null);
  }

  @Test
  void 내_리더에서_읽은_미등록_카드만_올린다() {
    assertTrue(CardTagService.forReader(event("4354", MY_READER, "113732383"), MY_READER));
  }

  @Test
  void 다른_리더에서_읽힌_카드는_보내지_않는다() {
    // 공항에는 리더가 여럿이다 — 지나가며 찍은 카드가 내 화면의 방문객에게 배정되면 안 된다
    assertFalse(CardTagService.forReader(event("4354", "999999999", "113732383"), MY_READER));
    assertFalse(CardTagService.forReader(event("4354", MY_READER, "113732383"), null));
    assertFalse(CardTagService.forReader(event("4354", null, "113732383"), MY_READER));
  }

  @Test
  void 인증_성공은_카드_읽기가_아니다() {
    // 4102/4106 의 user_id 는 카드번호가 아니라 그 사람의 인원ID 다 — 카드번호로 읽으면 엉뚱한 카드를 찾는다
    assertFalse(CardTagService.isCardRead(event("4106", MY_READER, "400001")));
    assertFalse(CardTagService.isCardRead(event("4102", MY_READER, "400001")));
    assertFalse(CardTagService.forReader(event("4106", MY_READER, "400001"), MY_READER));
  }

  @Test
  void 카드번호가_없으면_보내지_않는다() {
    assertFalse(CardTagService.isCardRead(event("4354", MY_READER, null)));
    assertFalse(CardTagService.isCardRead(event("4354", MY_READER, "  ")));
    assertFalse(CardTagService.isCardRead(null));
  }
}
