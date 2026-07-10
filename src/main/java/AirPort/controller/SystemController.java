package AirPort.controller;

import AirPort.adapter.BiostarResult;
import AirPort.common.ApiResponse;
import AirPort.common.SessionKeys;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.SystemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 설정관리(tb_system) — BiostarX 연동정보. 단일 행 폼 + 저장/연결테스트. */
@Controller
@RequestMapping("/system/system")
public class SystemController {

  private static final int MENU_ID = 302; // tb_menu 의 설정관리 menu_id (seed)

  private final SystemService systemService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;

  public SystemController(
      SystemService systemService, MenuService menuService, MenuAuthService menuAuthService) {
    this.systemService = systemService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
  }

  /** 화면 */
  @GetMapping
  public String page(Model model, HttpSession session) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), MENU_ID);
    if (!perm.isCanRead()) {
      return "redirect:/";
    }
    model.addAttribute("menus", menuService.tree());
    model.addAttribute("perm", perm);
    model.addAttribute("system", systemService.getForView(actor(session), MENU_ID));
    return "web/system/system";
  }

  /** 저장 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> save(@RequestBody TbSystem input, HttpSession session) {
    systemService.save(input, actor(session), MENU_ID);
    return ApiResponse.okMessage("저장되었습니다.");
  }

  /** BiostarX 연결 테스트 (AJAX) — 성공/실패 메시지 반환(자동 토스트). */
  @PostMapping("/test")
  @ResponseBody
  public ApiResponse<Void> test(@RequestBody TbSystem input, HttpSession session) {
    BiostarResult result = systemService.testConnection(input, actor(session), MENU_ID);
    return result.success()
        ? ApiResponse.okMessage("BiostarX 연결에 성공했습니다.")
        : ApiResponse.fail("BIOSTAR_TEST", "BiostarX 연결 실패: " + result.message());
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
