package AirPort.common;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;

/**
 * 문자 항목 공통 검사.
 *
 * <p>길이를 미리 보지 않으면 값이 그대로 DB 까지 가서 절단 오류(MSSQL 8152)로 <b>500</b> 이 난다. 사용자에게는 "서버 오류"로 보이지만 실제로는
 * <b>몇 자를 줄이면 되는 일</b>이다. 그 사실을 여기서 알려 준다.
 *
 * <p>{@code GlobalExceptionHandler} 에도 절단 → 400 그물이 있지만, 그쪽은 <b>어느 항목인지 말해 주지 못한다</b>. 화면에서 자주 쓰는
 * 항목은 여기서 먼저 잡는다.
 */
public final class Texts {

  private Texts() {}

  /** {@code max} 자를 넘으면 400. 한도는 해당 컬럼의 DDL 길이와 같아야 한다(sql/ddl 참고). */
  public static void maxLen(String value, int max, String label) {
    if (value != null && value.length() > max) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          label + "은(는) " + max + "자를 넘을 수 없습니다. (현재 " + value.length() + "자)");
    }
  }
}
