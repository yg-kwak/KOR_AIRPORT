package AirPort.common.exception;

/**
 * 에러 코드 단일 출처. 문구는 messages.properties 로 분리 가능(docs/backend.md). 코드 문자열을 화면/응답에 하드코딩하지 말고 이 enum 을
 * 참조한다.
 */
public enum ErrorCode {
  INVALID_INPUT("E400", "입력값이 올바르지 않습니다."),
  UNAUTHORIZED("E401", "로그인이 필요합니다."),
  FORBIDDEN("E403", "권한이 없습니다."),
  NOT_FOUND("E404", "대상을 찾을 수 없습니다."),
  DUPLICATE("E409", "이미 존재하는 데이터입니다."),
  INTERNAL("E500", "처리 중 오류가 발생했습니다.");

  private final String code;
  private final String defaultMessage;

  ErrorCode(String code, String defaultMessage) {
    this.code = code;
    this.defaultMessage = defaultMessage;
  }

  public String code() {
    return code;
  }

  public String defaultMessage() {
    return defaultMessage;
  }
}
