package AirPort.controller;

import AirPort.adapter.BiostarGroups;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.SessionKeys;
import AirPort.model.AcGroupAddForm;
import AirPort.model.MenuPermission;
import AirPort.model.TbAcGroup;
import AirPort.model.TbLoginUser;
import AirPort.service.AcGroupService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 출입권한관리(tb_ac_group) — tb_common(AR) 최상위 동기화 + 트리 + BiostarX 출입그룹 매핑.
 *
 * <p>화면 진입(page)에서 tb_common 기준 동기화를 수행한다.
 */
@Controller
@RequestMapping("/system/acGroup")
public class AcGroupController {

  private final AcGroupService acGroupService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu;

  public AcGroupController(
      AcGroupService acGroupService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.acGroupService = acGroupService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  /** 화면 — 진입 시 tb_common(AR) 기준 동기화 후 렌더. */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    acGroupService.sync(actor(session), menuId()); // 열릴 때 동기화
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/system/acGroup";
  }

  /** 트리 (AJAX) */
  @GetMapping("/tree")
  @ResponseBody
  public ApiResponse<List<TbAcGroup>> tree(HttpSession session) {
    return ApiResponse.ok(acGroupService.tree(actor(session), menuId()));
  }

  /** BiostarX 출입그룹 목록 (AJAX) — 하위 추가 팝업용 */
  @GetMapping("/biostarGroups")
  @ResponseBody
  public ApiResponse<BiostarGroups> biostarGroups(HttpSession session) {
    return ApiResponse.ok(acGroupService.biostarGroups(actor(session), menuId()));
  }

  /** 하위 그룹 추가 (AJAX) */
  @PostMapping("/children")
  @ResponseBody
  public ApiResponse<Void> addChildren(@RequestBody AcGroupAddForm form, HttpSession session) {
    acGroupService.addChildren(form, actor(session), menuId());
    return ApiResponse.okMessage("추가되었습니다.");
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbAcGroup row, HttpSession session) {
    acGroupService.update(row, actor(session), menuId());
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 삭제 (AJAX) */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int acGroupId, HttpSession session) {
    acGroupService.delete(acGroupId, actor(session), menuId());
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
