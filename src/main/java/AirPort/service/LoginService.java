package AirPort.service;

import AirPort.mapper.TbLoginUserMapper;
import AirPort.model.LoginResult;
import AirPort.model.TbLoginUser;
import AirPort.security.ARIAUtil;
import org.springframework.stereotype.Service;

/**
 * 로그인 인증. 비밀번호는 ARIA 암호문(hex)으로 비교한다. (docs/security.md)
 *
 * <p><b>계정 잠금</b>: 비밀번호를 {@value #MAX_FAIL} 회 연속 틀리면 {@code login_fail_cnt} 가 한도에 도달해 잠긴다. 잠긴 뒤에는
 * <b>비밀번호가 맞아도 로그인되지 않는다</b>(잠금이 우회되면 의미가 없다). 해제는 사용자관리 화면의 [잠금해제] 또는 관리자의 비밀번호 재설정으로 한다. 로그인에
 * 성공하면 실패 횟수는 0 으로 되돌린다.
 */
@Service
public class LoginService {

  /** 연속 실패 허용 횟수 — 이 횟수에 도달하면 잠긴다. */
  public static final int MAX_FAIL = 5;

  private static final String MSG_INVALID = "아이디 또는 비밀번호가 올바르지 않습니다.";
  private static final String MSG_LOCKED =
      "비밀번호를 " + MAX_FAIL + "회 연속 틀려 계정이 잠겼습니다. 관리자에게 잠금 해제를 요청하세요.";

  private final TbLoginUserMapper loginUserMapper;
  private final AuditService auditService;

  public LoginService(TbLoginUserMapper loginUserMapper, AuditService auditService) {
    this.loginUserMapper = loginUserMapper;
    this.auditService = auditService;
  }

  /** 계정이 잠긴 상태인지 — 실패 횟수가 한도 이상. */
  public static boolean isLocked(TbLoginUser user) {
    return user != null && user.getLoginFailCnt() != null && user.getLoginFailCnt() >= MAX_FAIL;
  }

  /**
   * 로그인 시도. 성공 시 세션용 사용자(비밀번호 제거, 성명 복호화), 실패 시 사유 문구를 담아 돌려준다.
   *
   * <p>계정이 없거나 미사용이면 <b>존재 여부를 흘리지 않도록</b> 비밀번호 불일치와 같은 문구를 쓴다.
   */
  public LoginResult authenticate(String userId, String rawPassword) {
    TbLoginUser user = loginUserMapper.selectById(userId);
    if (user == null || !"Y".equals(user.getUseYn())) {
      return LoginResult.fail(MSG_INVALID);
    }
    if (isLocked(user)) {
      // 잠긴 계정은 비밀번호를 확인하지 않는다 — 맞더라도 통과시키면 잠금이 무의미하다
      return LoginResult.fail(MSG_LOCKED);
    }
    if (!ARIAUtil.ariaEncrypt(rawPassword).equals(user.getPassword())) {
      return LoginResult.fail(countFailure(user));
    }
    if (user.getLoginFailCnt() != null && user.getLoginFailCnt() > 0) {
      loginUserMapper.updateLoginFailCnt(userId, 0); // 성공했으므로 누적 실패를 지운다
    }
    user.setPassword(null); // 세션에 비밀번호를 담지 않는다
    user.setUserName(ARIAUtil.ariaDecrypt(user.getUserName())); // 성명 복호화(표시용)
    return LoginResult.ok(user);
  }

  /** 실패 1회 누적 후 안내 문구 생성 — 한도에 도달하면 잠금 사실을 감사에 남긴다. */
  private String countFailure(TbLoginUser user) {
    int cnt = (user.getLoginFailCnt() == null ? 0 : user.getLoginFailCnt()) + 1;
    loginUserMapper.updateLoginFailCnt(user.getUserId(), cnt);
    if (cnt >= MAX_FAIL) {
      auditService.log(
          null, AuditService.LOGIN, null, "계정 잠금: " + user.getUserId() + " (연속 실패 " + cnt + "회)");
      return MSG_LOCKED;
    }
    // 남은 횟수를 알려준다 — 예고 없이 잠기면 현장에서 즉시 대응이 불가능하다
    return MSG_INVALID + " (" + cnt + "/" + MAX_FAIL + " 회 실패 — " + MAX_FAIL + "회 시 잠금)";
  }
}
