package AirPort.common.exception;

import AirPort.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** AJAX/데이터 응답 전역 예외 처리. 화면(뷰) 오류는 별도 에러 페이지로 처리한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    log.warn("business error: {} - {}", e.getErrorCode().code(), e.getMessage());
    HttpStatus status =
        switch (e.getErrorCode()) {
          case FORBIDDEN -> HttpStatus.FORBIDDEN;
          case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          default -> HttpStatus.BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(ApiResponse.fail(e.getErrorCode().code(), e.getMessage()));
  }

  /** 필수 요청 파라미터 누락 → 400 (예: 엑셀 다운로드 purpose 미입력) */
  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParam(
      org.springframework.web.bind.MissingServletRequestParameterException e) {
    log.warn("missing parameter: {}", e.getParameterName());
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.fail(
                ErrorCode.INVALID_INPUT.code(), "필수 값이 누락되었습니다: " + e.getParameterName()));
  }

  /** 요청 본문을 읽을 수 없음(깨진 JSON·인코딩·타입 불일치) → 400. 서버 잘못이 아니므로 500 으로 내보내지 않는다. */
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
      org.springframework.http.converter.HttpMessageNotReadableException e) {
    log.warn("unreadable request body: {}", e.getMostSpecificCause().getMessage());
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.code(), "요청 형식이 올바르지 않습니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleEtc(
      Exception e, jakarta.servlet.http.HttpServletResponse response) {
    if (isClientGone(e) || response.isCommitted()) {
      // 브라우저가 먼저 끊은 것뿐이다 — 서버 잘못이 아니다.
      // 게다가 여기서 본문을 쓰려 하면 두 번째 실패가 난다: 응답은 이미 나가 버렸고
      // Content-Type 도 정해져 있어(실시간 이벤트는 text/event-stream) JSON 을 실을 수 없다.
      // 그래서 사유 한 줄만 남기고 본문 없이 끝낸다(null = 처리 완료).
      log.info("클라이언트가 연결을 끊었습니다 — 응답을 쓰지 않는다: {}", rootMessage(e));
      return null;
    }
    log.error("unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(ErrorCode.INTERNAL.code(), ErrorCode.INTERNAL.defaultMessage()));
  }

  /**
   * 브라우저가 먼저 끊어서 난 오류인가.
   *
   * <p>실시간 이벤트(SSE)처럼 오래 열어 두는 응답에서는 <b>정상적으로 늘 일어난다</b> — 화면을 닫거나 새로고침만 해도 난다. 클래스 이름으로 판별한다:
   * 컨테이너마다 예외 타입이 달라(Tomcat 은 {@code ClientAbortException}·{@code CloseNowException}) 특정 타입에 의존하면
   * 놓친다.
   */
  private static boolean isClientGone(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      String name = t.getClass().getSimpleName();
      if (name.equals("ClientAbortException")
          || name.equals("CloseNowException")
          || name.equals("AsyncRequestNotUsableException")
          || name.equals("EofException")) {
        return true;
      }
      if (t.getCause() == t) {
        break; // 자기 자신을 원인으로 갖는 예외 방어
      }
    }
    return false;
  }

  private static String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    return t.getClass().getSimpleName() + ": " + t.getMessage();
  }
}
