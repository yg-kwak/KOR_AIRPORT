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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleEtc(Exception e) {
    log.error("unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(ErrorCode.INTERNAL.code(), ErrorCode.INTERNAL.defaultMessage()));
  }
}
