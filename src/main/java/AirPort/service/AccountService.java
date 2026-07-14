package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbLoginUserMapper;
import AirPort.model.MenuNode;
import AirPort.model.PasswordChangeForm;
import AirPort.model.TbLoginUser;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 자가서비스(헤더 사용자 메뉴) — 시작메뉴 변경 / 비밀번호 변경.
 *
 * <p>본인(세션 사용자) 대상이라 메뉴 권한 판정 없이 로그인만 요구한다. 비밀번호는 ARIA 암호문으로 비교·저장(security.md). 수정은 감사 기록.
 */
@Service
public class AccountService {

  private final TbLoginUserMapper loginUserMapper;
  private final MenuService menuService;
  private final AuditService auditService;

  public AccountService(
      TbLoginUserMapper loginUserMapper, MenuService menuService, AuditService auditService) {
    this.loginUserMapper = loginUserMapper;
    this.menuService = menuService;
    this.auditService = auditService;
  }

  /** 본인이 read 권한을 가진 메뉴 트리(시작메뉴 후보) — 그룹 → 하위 메뉴. 선택은 화면(URL) 있는 하위만. */
  public List<MenuNode> myMenuTree(TbLoginUser actor) {
    return menuService.tree(actor);
  }

  /** 시작메뉴 변경 — 본인 접근 가능한 화면(URL 있는 read 권한) 메뉴만 허용. 세션 사용자 객체도 갱신(다음 로그인 진입 반영). */
  @Transactional
  public void changeStartMenu(TbLoginUser actor, Integer startMenuId) {
    if (startMenuId == null
        || menuService.readableLeaves(actor).stream()
            .noneMatch(n -> startMenuId.equals(n.getMenuId()))) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "선택할 수 없는 메뉴입니다.");
    }
    loginUserMapper.updateStartMenu(actor.getUserId(), startMenuId);
    actor.setStartMenuId(startMenuId);
    auditService.log(actor, AuditService.UPDATE, null, "시작메뉴 변경: " + startMenuId);
  }

  /** 비밀번호 변경 — 이전 비밀번호(암호문) 확인 후 신규 저장(ARIA). */
  @Transactional
  public void changePassword(TbLoginUser actor, PasswordChangeForm form) {
    String oldPw = form.getOldPassword();
    String newPw = form.getNewPassword();
    if (oldPw == null || oldPw.isBlank() || newPw == null || newPw.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호를 입력하세요.");
    }
    if (!newPw.equals(form.getConfirmPassword())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "변경 비밀번호가 일치하지 않습니다.");
    }
    TbLoginUser stored = loginUserMapper.selectById(actor.getUserId());
    if (stored == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (!ARIAUtil.ariaEncrypt(oldPw).equals(stored.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이전 비밀번호가 올바르지 않습니다.");
    }
    if (ARIAUtil.ariaEncrypt(newPw).equals(stored.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이전과 다른 비밀번호를 사용하세요.");
    }
    loginUserMapper.updatePassword(actor.getUserId(), ARIAUtil.ariaEncrypt(newPw));
    auditService.log(actor, AuditService.UPDATE, null, "비밀번호 변경");
  }
}
