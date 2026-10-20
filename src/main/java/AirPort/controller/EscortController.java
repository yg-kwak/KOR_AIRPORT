package AirPort.controller;

import AirPort.adapter.biostar.BiostarDoor;
import AirPort.adapter.biostar.BiostarDoorAuth;
import AirPort.adapter.biostar.BiostarDoorUserPage;
import AirPort.adapter.biostar.BiostarUserGroup;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.SessionKeys;
import AirPort.model.EscortForm;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.service.EscortService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** 에스코트 인원 관리 — BiostarX 출입문의 에스코트 사용자를 조회·추가·제외한다. 이 시스템에는 저장하지 않는다({@link EscortService}). */
@Controller
@RequestMapping("/security/escort")
public class EscortController {

  private final EscortService escortService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu;

  public EscortController(
      EscortService escortService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.escortService = escortService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  /** 화면 */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/security/escort";
  }

  /** 출입문 목록 (AJAX) */
  @GetMapping("/doors")
  @ResponseBody
  public ApiResponse<List<BiostarDoor>> doors(HttpSession session) {
    return ApiResponse.ok(escortService.doors(actor(session), menuId()));
  }

  /** 출입문 하나의 에스코트 설정 + 등록된 사용자 (AJAX) */
  @GetMapping("/door")
  @ResponseBody
  public ApiResponse<BiostarDoorAuth> door(@RequestParam long doorId, HttpSession session) {
    return ApiResponse.ok(escortService.door(doorId, actor(session), menuId()));
  }

  /** 그 문에 추가할 수 있는 사용자 (AJAX) */
  @GetMapping("/users")
  @ResponseBody
  public ApiResponse<BiostarDoorUserPage> users(
      @RequestParam long doorId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "200") int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long userGroupId,
      HttpSession session) {
    return ApiResponse.ok(
        escortService.accessibleUsers(
            doorId, page, size, keyword, userGroupId, actor(session), menuId()));
  }

  /** 사용자 그룹 목록 (AJAX) — 추가할 사용자 검색 조건 */
  @GetMapping("/userGroups")
  @ResponseBody
  public ApiResponse<List<BiostarUserGroup>> userGroups(HttpSession session) {
    return ApiResponse.ok(escortService.userGroups(actor(session), menuId()));
  }

  /** 사용자 반영 (AJAX) — 더하고 빼서 BiostarX 에 바로 반영한다 */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody EscortForm form, HttpSession session) {
    return ApiResponse.okMessage(escortService.update(form, actor(session), menuId()));
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
