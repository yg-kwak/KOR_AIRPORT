package AirPort.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import AirPort.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * 생년월일은 <b>한 가지 모양</b>으로만 저장된다.
 *
 * <p>받는 화면이 셋이라 규칙이 갈리기 쉽고, 암호화 컬럼이라 <b>나중에 SQL 로 정리할 수 없다</b> — 들어올 때 맞추지 못하면 영영 섞인 채 남는다.
 */
class BirthDatesTest {

  @Test
  void 구분자가_달라도_같은_모양으로_저장한다() {
    // 사람이 손으로 치는 값이다. 구분자 하나로 되돌려 보내면 현장에서 성가시기만 하고,
    // 저장되는 모양은 어차피 하나로 정해져 있다
    for (String input : new String[] {"1990-01-01", "1990.01.01", "1990/01/01", "19900101"}) {
      assertEquals("1990-01-01", BirthDates.normalize(input, "생년월일"), input);
    }
  }

  @Test
  void 앞뒤_공백은_털어낸다() {
    assertEquals("1990-01-01", BirthDates.normalize("  1990-01-01  ", "생년월일"));
  }

  @Test
  void 달력에_없는_날짜는_거절한다() {
    // 형식만 보는 정규식으로는 2월 30일이 통과한다 — 오타를 그대로 저장하면 되돌릴 수 없다
    assertThrows(BusinessException.class, () -> BirthDates.normalize("1990-02-30", "생년월일"));
    assertThrows(BusinessException.class, () -> BirthDates.normalize("1990-13-01", "생년월일"));
  }

  @Test
  void 알아볼_수_없는_값은_거절한다() {
    assertThrows(BusinessException.class, () -> BirthDates.normalize("90-01-01", "생년월일"));
    assertThrows(BusinessException.class, () -> BirthDates.normalize("어제", "생년월일"));
  }

  @Test
  void 비어_있으면_normalize_는_통과하고_require_는_막는다() {
    // 두 쓰임이 다르다 — 선택 항목의 정리와 필수 항목의 검사
    assertNull(BirthDates.normalize("  ", "생년월일"));
    assertThrows(BusinessException.class, () -> BirthDates.require("  ", "생년월일"));
    assertThrows(BusinessException.class, () -> BirthDates.require(null, "생년월일"));
  }

  @Test
  void 오류_문구에_대상_이름이_들어간다() {
    // 방문객 표에서는 어느 칸이 잘못됐는지 문구로만 알 수 있다
    BusinessException e =
        assertThrows(BusinessException.class, () -> BirthDates.require(null, "방문객 생년월일"));
    assertEquals("방문객 생년월일은(는) 필수입니다.", e.getMessage());
  }
}
