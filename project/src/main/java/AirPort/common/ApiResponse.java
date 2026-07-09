package AirPort.common;

/**
 * AJAX 표준 응답 포맷. (docs/frontend.md, docs/backend.md)
 *
 * <pre>{ "success": true, "code": "OK", "message": null, "data": ... }</pre>
 */
public class ApiResponse<T> {

  private boolean success;
  private String code;
  private String message;
  private T data;

  public ApiResponse() {}

  public ApiResponse(boolean success, String code, String message, T data) {
    this.success = success;
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, "OK", null, data);
  }

  public static <T> ApiResponse<T> ok() {
    return new ApiResponse<>(true, "OK", null, null);
  }

  public static <T> ApiResponse<T> fail(String code, String message) {
    return new ApiResponse<>(false, code, message, null);
  }

  public boolean isSuccess() {
    return success;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public T getData() {
    return data;
  }
}
