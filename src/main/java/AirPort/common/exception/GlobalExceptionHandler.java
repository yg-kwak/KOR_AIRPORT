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
  public ResponseEntity<ApiResponse<Void>> handleEtc(Exception e) {
    log.error("unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(ErrorCode.INTERNAL.code(), ErrorCode.INTERNAL.defaultMessage()));
  }
}
