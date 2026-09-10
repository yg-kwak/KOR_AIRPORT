package AirPort.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 실물 카드 명칭 규칙 — 구역 카드({@code 임시234-0001})와 공용 카드({@code 대여-0001}).
 *
 * <p>구역이 맞지 않는 카드를 쥐어 주면 <b>문 앞에서야</b> 안 열리는 것을 알게 된다. 후보를 좁히는 이 판정이 조용히 헐거워지면 안 된다.
 */
class CardNamesTest {

  // ── 구역 카드 ─────────────────────────────────────────────────────────────

  @Test
  void 구역_카드는_구역이_정확히_같아야_쓴다() {
    assertTrue(CardNames.matchesArea("임시234-0001", "234"));
    assertTrue(CardNames.matchesArea("상주234-0007", "234"), "유형이 달라도 구역이 같으면 쓴다");
    assertFalse(CardNames.matchesArea("임시23-0001", "234"), "구역이 덜 포함된다");
    assertFalse(CardNames.matchesArea("임시2345-0001", "234"), "구역이 더 포함된다");
    assertFalse(CardNames.matchesArea("임시345-0001", "234"), "다른 구역이다");
  }

  @Test
  void 이름이_말하는_구역을_읽는다() {
    assertEquals("234", CardNames.areaOf("임시234-0001"));
    assertEquals("2", CardNames.areaOf("상주2-0001"));
    assertNull(CardNames.areaOf("대여-0001"), "공용 카드는 구역이 없다");
    assertNull(CardNames.areaOf("임시11111112"), "규칙 밖이다");
  }

  // ── 공용 카드(대여) ───────────────────────────────────────────────────────

  @Test
  void 구역이_없는_카드는_어느_구역에나_쓴다() {
    // 대여 카드는 이름에 구역이 없다 — 출입권한은 사용자 출입그룹으로 붙으므로 카드는 매개체일 뿐이다
    assertTrue(CardNames.isPlain("대여-0001"));
    assertTrue(CardNames.matchesArea("대여-0001", "234"));
    assertTrue(CardNames.matchesArea("대여-0002", "1"));
    assertFalse(CardNames.isPlain("임시2-0001"), "구역이 있는 이름이다");
  }

  @Test
  void 구역이_정해지기_전에는_어떤_카드도_맞지_않는다() {
    // '거르지 않는다'가 아니라 '아직 고를 수 없다'
    assertFalse(CardNames.matchesArea("임시234-0001", ""));
    assertFalse(CardNames.matchesArea("대여-0001", null));
  }

  // ── 규칙 밖 ───────────────────────────────────────────────────────────────

  @Test
  void 두_모양_어디에도_맞지_않으면_규칙_밖이다() {
    // 예전에 하이픈 없이 지은 카드와 정규인원 카드다. 구역을 알 수 없어 판정할 수 없다
    for (String name : new String[] {"임시11111112", "400001", "빛가람관제부대", "임시-", "-0001", null}) {
      assertFalse(CardNames.isWellFormed(name), String.valueOf(name));
      assertFalse(CardNames.matchesArea(name, "234"), String.valueOf(name));
    }
  }

  // ── 유형(패스구분 대조용) ─────────────────────────────────────────────────

  @Test
  void 이름이_말하는_유형을_읽는다() {
    // 이름은 상주인데 패스구분이 임시로 등록된 카드를 걸러내는 데 쓴다
    assertEquals("임시", CardNames.typeOf("임시234-0001"));
    assertEquals("상주", CardNames.typeOf("상주2-0001"));
    assertEquals("대여", CardNames.typeOf("대여-0001"));
    assertNull(CardNames.typeOf("임시11111112"));
  }

  @Test
  void 규칙_안의_이름은_통과한다() {
    assertTrue(CardNames.isWellFormed("임시234-0001"));
    assertTrue(CardNames.isWellFormed("상주2345-0001"));
    assertTrue(CardNames.isWellFormed("대여-0001"));
  }
}
