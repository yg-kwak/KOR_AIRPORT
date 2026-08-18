package AirPort.common;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 목록 검색의 기간 조건(yyyy-MM-dd) 검사.
 *
 * <p>화면 달력은 늘 올바른 형식을 보내지만, 주소창을 직접 고치면 아무 값이나 온다. 검사 없이 두면 {@code CAST(? AS datetime2)} 까지 내려가 SQL
 * 오류(241)로 <b>500</b> 이 난다 — 보낸 쪽 잘못이므로 400 으로 돌려줘야 한다.
 *
 * <p>기간을 쓰는 화면이 둘 이상(감사추적·주차 조회)이라 한곳에 모은다.
 */
public final class SearchDates {

  private SearchDates() {}

  /** 비어 있으면(=조건 없음) 통과, 값이 있으면 yyyy-MM-dd 여야 한다. */
  public static void require(String startDate, String endDate) {
    check(startDate, "시작일");
    check(endDate, "종료일");
  }

  private static void check(String value, String label) {
    if (value == null || value.isBlank()) {
      return;
    }
    try {
      LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, label + " 형식이 올바르지 않습니다(yyyy-MM-dd): " + value);
    }
  }
}
