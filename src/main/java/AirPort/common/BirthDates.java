package AirPort.common;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 생년월일 형식 — 저장 형태는 <b>{@code YYYY-MM-DD} 하나</b>다.
 *
 * <p>생년월일을 받는 화면이 셋이다(정규인원·임시/장기 방문객·키오스크 방문객). 화면마다 규칙이 갈리면 같은 사람이 어디서 등록됐느냐에 따라 {@code
 * 1990-01-01} 과 {@code 19900101} 로 갈려 저장된다. 신청서에 나란히 찍히면 바로 눈에 띄고, 무엇보다 <b>암호화 컬럼이라 나중에 SQL 로 정리할 수
 * 없다</b> — 들어올 때 맞춰야 한다.
 *
 * <p>그래서 흔한 입력 변형({@code 19900101}, {@code 1990.01.01}, {@code 1990/01/01})은 <b>받아서 고쳐 넣는다</b>. 사람이
 * 손으로 치는 값이라 구분자 하나로 거절하면 현장에서 성가시기만 하고, 저장되는 모양은 어차피 하나로 정해져 있다.
 *
 * <p>달력에 없는 날짜(2월 30일 등)는 거절한다 — 형식만 맞고 존재하지 않는 날은 오타다.
 */
public final class BirthDates {

  private BirthDates() {}

  /**
   * 정규화 — 비었으면 {@code null}, 알아볼 수 있으면 {@code YYYY-MM-DD}, 아니면 400.
   *
   * @param label 오류 문구에 쓸 대상 이름(예: "생년월일", "방문객 생년월일")
   */
  public static String normalize(String value, String label) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim();
    // 구분자만 다른 경우를 먼저 맞춘다 — 1990.01.01 · 1990/01/01 → 1990-01-01
    v = v.replace('.', '-').replace('/', '-');
    // 구분자가 아예 없는 경우 — 19900101 → 1990-01-01
    if (v.length() == 8 && v.chars().allMatch(Character::isDigit)) {
      v = v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6);
    }
    try {
      // 달력에 실제로 있는 날짜인지까지 본다. 형식만 보는 정규식으로는 2월 30일이 통과한다.
      return LocalDate.parse(v).toString();
    } catch (DateTimeParseException e) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, label + "은(는) YYYY-MM-DD 형식으로 입력하세요. 예: 1990-01-01");
    }
  }

  /**
   * 신청서 표기 — {@code 1993-04-07} → {@code 930407}.
   *
   * <p>양식 칸이 좁아 여덟 자리로는 줄이 접힌다. 저장 형태는 그대로 두고 <b>찍을 때만</b> 줄인다 — 화면에서 정규식으로 자르면 같은 규칙이 또 한 벌이 되고,
   * 형식이 바뀔 때 한쪽만 고쳐진다.
   *
   * <p>알아볼 수 없는 값은 있는 그대로 돌려준다. 신청서는 사람이 눈으로 확인하는 종이라, 예전에 다른 형태로 들어간 값이 빈칸으로 사라지는 것보다 그대로 보이는 편이
   * 낫다.
   */
  public static String yymmdd(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    String v = value.trim();
    try {
      LocalDate d = LocalDate.parse(v);
      return String.format("%02d%02d%02d", d.getYear() % 100, d.getMonthValue(), d.getDayOfMonth());
    } catch (DateTimeParseException e) {
      return v; // 규칙 밖의 옛 값 — 손대지 않는다
    }
  }

  /** 정규화 + 필수 검사. 비어 있으면 400. */
  public static String require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, label + "은(는) 필수입니다.");
    }
    return normalize(value, label);
  }
}
