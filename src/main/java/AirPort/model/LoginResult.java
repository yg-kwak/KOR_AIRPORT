package AirPort.model;

/**
 * 로그인 시도 결과 — 성공 사용자 또는 실패 사유. (docs/security.md)
 *
 * <p>실패를 단순히 {@code null} 로 돌려주면 "비밀번호 불일치"와 "계정 잠김"을 구분해 안내할 수 없어, 사용자가 몇 번을 더 틀릴 수 있는지도 모른 채 갑자기
 * 잠기게 된다. 사유 문구는 서비스가 만들고 화면은 그대로 보여준다.
 */
public class LoginResult {

  private final TbLoginUser user; // 성공일 때만 채워진다
  private final String message; // 실패 사유(성공이면 null)

  private LoginResult(TbLoginUser user, String message) {
    this.user = user;
    this.message = message;
  }

  public static LoginResult ok(TbLoginUser user) {
    return new LoginResult(user, null);
  }

  public static LoginResult fail(String message) {
    return new LoginResult(null, message);
  }

  public boolean isSuccess() {
    return user != null;
  }

  public TbLoginUser getUser() {
    return user;
  }

  public String getMessage() {
    return message;
  }
}
