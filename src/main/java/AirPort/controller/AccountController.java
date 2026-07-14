package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.SessionKeys;
import AirPort.model.MenuNode;
import AirPort.model.PasswordChangeForm;
import AirPort.model.StartMenuForm;
import AirPort.model.TbLoginUser;
import AirPort.service.AccountService;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 자가서비스(헤더 사용자 메뉴) — 시작메뉴/비밀번호 변경.
 *
 * <p>본인(세션 사용자) 대상이라 메뉴 권한 게이트 없이 로그인만 요구한다(tb_menu 미등록 URL). 인증은 AuthInterceptor 가 보장.
 */
@RestController
@RequestMapping("/account")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  /** 시작메뉴 후보 트리(본인 read 권한 메뉴) + 현재 시작메뉴. */
  @GetMapping("/menus")
  public ApiResponse<Map<String, Object>> menus(HttpSession session) {
    TbLoginUser actor = actor(session);
    List<MenuNode> tree = accountService.myMenuTree(actor);
    Map<String, Object> data = new HashMap<>();
    data.put("items", tree);
    data.put("current", actor.getStartMenuId());
    return ApiResponse.ok(data);
  }

  @PostMapping("/startMenu")
  public ApiResponse<Void> startMenu(@RequestBody StartMenuForm form, HttpSession session) {
    accountService.changeStartMenu(actor(session), form.getStartMenuId());
    return ApiResponse.okMessage("시작메뉴가 변경되었습니다.");
  }

  @PostMapping("/password")
  public ApiResponse<Void> password(@RequestBody PasswordChangeForm form, HttpSession session) {
    accountService.changePassword(actor(session), form);
    return ApiResponse.okMessage("비밀번호가 변경되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
