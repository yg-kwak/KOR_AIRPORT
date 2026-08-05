package AirPort;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.mapper.TbLoginUserMapper;
import AirPort.model.LoginResult;
import AirPort.model.TbLoginUser;
import AirPort.security.ARIAUtil;
import AirPort.service.AuditService;
import AirPort.service.LoginService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 계정 잠금 단위 테스트 — 비밀번호를 {@link LoginService#MAX_FAIL} 회 연속 틀리면 잠기고, 잠긴 뒤에는 <b>비밀번호가 맞아도</b> 통과하지 않아야
 * 한다(그렇지 않으면 잠금이 무의미하다). 성공하면 누적 실패는 지워진다. DB/Spring 없이 mock.
 */
class LoginLockTest {

  private final TbLoginUserMapper mapper = mock(TbLoginUserMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final LoginService service = new LoginService(mapper, auditService);

  /** ARIAUtil 은 프로퍼티 주입 기반이라 테스트에서 직접 키를 넣어 준다. */
  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private TbLoginUser user(String rawPassword, Integer failCnt) {
    TbLoginUser u = new TbLoginUser();
    u.setUserId("tester");
    u.setUserName(ARIAUtil.ariaEncrypt("테스터"));
    u.setPassword(ARIAUtil.ariaEncrypt(rawPassword));
    u.setUseYn("Y");
    u.setLoginFailCnt(failCnt);
    return u;
  }

  @Test
  void 비밀번호를_틀리면_실패횟수가_1_올라가고_남은_횟수를_알려준다() {
    when(mapper.selectById("tester")).thenReturn(user("right", 2));

    LoginResult r = service.authenticate("tester", "wrong");

    assertFalse(r.isSuccess());
    verify(mapper).updateLoginFailCnt("tester", 3);
    assertTrue(r.getMessage().contains("3/5"), r.getMessage());
  }

  @Test
  void 한도에_도달하면_잠기고_감사에_남는다() {
    when(mapper.selectById("tester")).thenReturn(user("right", LoginService.MAX_FAIL - 1));

    LoginResult r = service.authenticate("tester", "wrong");

    assertFalse(r.isSuccess());
    verify(mapper).updateLoginFailCnt("tester", LoginService.MAX_FAIL);
    assertTrue(r.getMessage().contains("잠겼습니다"), r.getMessage());
    verify(auditService).log(any(), anyString(), any(), anyString());
  }

  @Test
  void 잠긴_계정은_비밀번호가_맞아도_로그인되지_않는다() {
    when(mapper.selectById("tester")).thenReturn(user("right", LoginService.MAX_FAIL));

    LoginResult r = service.authenticate("tester", "right"); // 올바른 비밀번호

    assertFalse(r.isSuccess());
    assertNull(r.getUser());
    assertTrue(r.getMessage().contains("잠겼습니다"), r.getMessage());
    // 잠긴 상태에서 더 시도해도 횟수를 올리지 않는다(무의미한 증가 방지)
    verify(mapper, never()).updateLoginFailCnt(anyString(), anyInt());
  }

  @Test
  void 로그인에_성공하면_누적_실패가_초기화된다() {
    when(mapper.selectById("tester")).thenReturn(user("right", 3));

    LoginResult r = service.authenticate("tester", "right");

    assertTrue(r.isSuccess());
    verify(mapper).updateLoginFailCnt("tester", 0);
    assertNull(r.getUser().getPassword()); // 세션에 비밀번호를 담지 않는다
  }

  @Test
  void 실패가_없던_계정은_성공해도_불필요한_갱신을_하지_않는다() {
    when(mapper.selectById("tester")).thenReturn(user("right", 0));

    assertTrue(service.authenticate("tester", "right").isSuccess());

    verify(mapper, never()).updateLoginFailCnt(anyString(), eq(0));
  }

  @Test
  void 없는_계정과_미사용_계정은_존재를_흘리지_않는다() {
    when(mapper.selectById("nobody")).thenReturn(null);
    TbLoginUser off = user("right", 0);
    off.setUseYn("N");
    when(mapper.selectById("off")).thenReturn(off);

    String a = service.authenticate("nobody", "x").getMessage();
    String b = service.authenticate("off", "right").getMessage();

    assertTrue(a.equals(b), a + " vs " + b); // 같은 문구여야 계정 존재 여부가 드러나지 않는다
  }
}
