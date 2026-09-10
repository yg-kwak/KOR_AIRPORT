package AirPort.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 허가구역 표기 — 같은 값을 네 곳이 쓴다(실시간 이벤트, 신청서, 카드명칭, BiostarX 부서).
 *
 * <p>표기가 자리마다 달라지면 <b>어느 쪽이 맞는지 화면으로는 알 수 없다.</b> 그래서 규칙을 여기서 한 번에 못 박는다.
 */
class AccessAreasTest {

  @Test
  void 고른_구역_번호를_이어_붙인다() {
    assertEquals("123", AccessAreas.key(List.of("인원구역1", "인원구역2", "인원구역3")));
    assertEquals("1", AccessAreas.key(List.of("인원구역1")));
    assertEquals("", AccessAreas.key(List.of()));
    assertEquals("", AccessAreas.key(null));
  }

  @Test
  void 번호_오름차순으로_붙인다() {
    // 고른 순서나 그룹ID 순서로 붙이면 같은 조합인데 자리마다 다른 값이 나온다
    assertEquals("123", AccessAreas.key(List.of("인원구역3", "인원구역1", "인원구역2")));
    // 이름순으로 붙이면 "인원구역10" 이 "인원구역2" 앞에 서서 "102" 가 된다
    assertEquals("2910", AccessAreas.key(List.of("인원구역10", "인원구역9", "인원구역2")));
  }

  @Test
  void 같은_번호는_한_번만_붙인다() {
    // 구역 트리는 상위를 고르면 하위까지 함께 선택된다(인원구역2 → '인원구역2 안쪽' → '2-1-2').
    // 그 하위들도 결국 2번 구역이다 — 그대로 이으면 '222' 가 되어 어떤 카드와도 맞지 않는다
    assertEquals("2", AccessAreas.key(List.of("인원구역2", "인원구역2 안쪽", "2-1-2")));
    assertEquals("12", AccessAreas.key(List.of("인원구역1", "인원구역2", "인원구역2 안쪽")));
  }

  @Test
  void 구역_번호가_여러_자리여도_그대로_쓴다() {
    // "123" 은 1·2·3 구역일 수도, 123번 구역 하나일 수도 있다 — 이어 붙인 결과는 같다
    assertEquals("123", AccessAreas.key(List.of("인원구역123")));
    assertEquals("127", AccessAreas.key(List.of("인원구역127")));
  }

  @Test
  void 번호가_없는_이름은_그대로_뒤에_붙인다() {
    // 조용히 버리면 어느 구역이 빠졌는지 알 수 없다. 카드 후보를 고를 때는 더 나쁘다 —
    // 그 구역을 '못 여는' 카드를 맞다고 판정해 쥐어 주게 된다
    assertEquals("1게이트", AccessAreas.key(List.of("인원구역1", "게이트")));
    assertEquals("게이트", AccessAreas.key(List.of("게이트")));
    assertEquals("12게이트", AccessAreas.key(List.of("게이트", "인원구역2", "인원구역1")));
  }

  @Test
  void 빈_이름은_건너뛴다() {
    assertEquals("12", AccessAreas.key(Arrays.asList("인원구역1", null, "  ", "인원구역2")));
  }

  @Test
  void 신청서는_콤마로_벌려_적는다() {
    // 사람이 한 칸씩 읽는 자리다 — "125" 는 125번 구역 하나로 읽힌다
    assertEquals("1,2,5", AccessAreas.csv(List.of("인원구역1", "인원구역2", "인원구역5")));
    assertEquals("1,2", AccessAreas.csv(List.of("차량구역2", "차량구역1")));
    assertEquals("", AccessAreas.csv(List.of()));
  }
}
