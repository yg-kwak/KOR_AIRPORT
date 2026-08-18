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

  /**
   * 요청 파라미터 타입 불일치 → 400. {@code ?size=xyz} 처럼 숫자 자리에 글자가 오면 여기로 온다.
   *
   * <p>{@code BindException} 은 검색 조건 객체(PageParam 등)에 바인딩할 때, {@code
   * MethodArgumentTypeMismatchException} 은 {@code @RequestParam} 단건에서 난다. 둘 다 <b>보낸 쪽 잘못</b>이라 500
   * 이면 안 된다.
   */
  @ExceptionHandler({
    org.springframework.validation.BindException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBindFailure(Exception e) {
    log.warn("parameter bind failure: {}", e.getMessage());
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.code(), "요청 값의 형식이 올바르지 않습니다."));
  }

  /**
   * 컬럼 길이를 넘겨 잘릴 때(MSSQL 8152/2628) → 400.
   *
   * <p>서비스마다 주요 항목은 길이를 미리 검사하지만, 컬럼은 수십 개고 사람이 다 적어 두면 반드시 빠진다. 빠진 자리에서 500 이 나지 않도록 받아 낸다 — 값이
   * 길어서 못 넣은 것은 <b>보낸 쪽 잘못</b>이다.
   */
  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataAccess(
      org.springframework.dao.DataAccessException e) {
    if (isTruncation(e)) {
      log.warn("컬럼 길이 초과로 저장하지 못했습니다: {}", rootMessage(e));
      return ResponseEntity.badRequest()
          .body(ApiResponse.fail(ErrorCode.INVALID_INPUT.code(), "입력한 값이 너무 깁니다. 길이를 줄여 주세요."));
    }
    log.error("data access error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(ErrorCode.INTERNAL.code(), ErrorCode.INTERNAL.defaultMessage()));
  }

  /** MSSQL 절단 오류인가 — 8152(구버전 메시지)·2628(어느 컬럼인지 알려주는 신버전). */
  private static boolean isTruncation(Throwable e) {
    for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
      if (t instanceof java.sql.SQLException sql
          && (sql.getErrorCode() == 8152 || sql.getErrorCode() == 2628)) {
        return true;
      }
    }
    return false;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleEtc(
      Exception e, jakarta.servlet.http.HttpServletResponse response) {
    // 브라우저가 먼저 끊은 것 — 서버 잘못이 아니다. 실시간 이벤트(SSE)처럼 오래 열어 두는
    // 응답에서는 늘 일어난다(화면을 닫거나 새로고침만 해도 난다). 사유 한 줄만 남긴다.
    if (isClientGone(e)) {
      log.info("클라이언트가 연결을 끊었습니다 — 응답을 쓰지 않는다: {}", rootMessage(e));
      return null;
    }
    // 여기부터는 진짜 오류다. 응답이 이미 나갔더라도 **스택은 반드시 남긴다** —
    // 엑셀 다운로드처럼 응답에 직접 쓰는 경로는 실패해도 헤더가 이미 커밋된 상태라,
    // 이것을 끊김과 한 갈래로 묶으면 장애가 INFO 한 줄로 조용해진다.
    log.error("unexpected error", e);
    if (response.isCommitted()) {
      // 본문만 생략한다. 헤더가 나간 뒤에는 상태코드도 Content-Type 도 바꿀 수 없어,
      // 여기서 JSON 을 쓰려 하면 두 번째 실패가 난다(null = 처리 완료).
      log.warn("응답이 이미 전송되어 오류 본문을 실을 수 없습니다 — 로그로만 남깁니다.");
      return null;
    }
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
