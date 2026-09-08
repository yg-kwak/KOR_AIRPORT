package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.BlacklistSearchParam;
import AirPort.model.MenuPermission;
import AirPort.model.TbBlacklist;
import AirPort.model.TbLoginUser;
import AirPort.service.BlacklistService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
 * 제재인원관리 CRUD (보안관리 → 제재인원관리, menu_id 503). 골든 샘플(CompanyController) 구조를 따른다.
 *
 * <p>여기에 오른 사람은 성명+생년월일 대조로 <b>임시·장기·정규 등록이 막힌다</b>({@link BlacklistService#requireNotBanned}).
 */
@Controller
@RequestMapping("/security/blacklist")
public class BlacklistController {

  private final BlacklistService blacklistService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public BlacklistController(
      BlacklistService blacklistService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.blacklistService = blacklistService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  /** 화면 — 메뉴 권한(perm)을 내려 버튼 노출을 제어한다(1차 방어). */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden"; // 무권한 URL 직접 접근 → 권한 없음 페이지
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/security/blacklist";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbBlacklist>> list(
      BlacklistSearchParam param, HttpSession session) {
    return ApiResponse.ok(blacklistService.list(param, actor(session), menuId()));
  }

  /** 단건 상세 (AJAX) — 수정 모달용 */
  @GetMapping("/detail")
  @ResponseBody
  public ApiResponse<TbBlacklist> detail(@RequestParam int blacklistId, HttpSession session) {
    return ApiResponse.ok(blacklistService.detail(blacklistId, actor(session), menuId()));
  }

  /** 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbBlacklist row, HttpSession session) {
    blacklistService.create(row, actor(session), menuId());
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbBlacklist row, HttpSession session) {
    blacklistService.update(row, actor(session), menuId());
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 해제 (AJAX) — 소프트 삭제. 제재 이력은 남는다. */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int blacklistId, HttpSession session) {
    blacklistService.delete(blacklistId, actor(session), menuId());
    return ApiResponse.okMessage("해제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
