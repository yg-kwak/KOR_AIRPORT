package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.MenuAuthForm;
import AirPort.model.MenuAuthSearchParam;
import AirPort.model.MenuNode;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.model.TbMenuAuth;
import AirPort.model.TbMenuAuthDetail;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

/** 권한메뉴관리(tb_menu_auth) — 권한 그룹 + 메뉴별 CRUD 권한 매트릭스. 골든 샘플(CommonController) 구조를 따른다. */
@Controller
@RequestMapping("/system/menuAuth")
public class MenuAuthController {

  private static final int MENU_ID = 304; // tb_menu 의 권한메뉴관리 menu_id (seed)

  private final MenuAuthService menuAuthService;
  private final MenuService menuService;

  public MenuAuthController(MenuAuthService menuAuthService, MenuService menuService) {
    this.menuAuthService = menuAuthService;
    this.menuService = menuService;
  }

  /** 화면 */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), MENU_ID);
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/system/menuAuth";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbMenuAuth>> list(MenuAuthSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), MENU_ID);
    return ApiResponse.ok(menuAuthService.list(param, actor(session), MENU_ID));
  }

  /** 권한 선택 트리가 될 전체 메뉴 트리 (AJAX) */
  @GetMapping("/menus")
  @ResponseBody
  public ApiResponse<List<MenuNode>> menus(HttpSession session) {
    return ApiResponse.ok(menuAuthService.menuTree(actor(session), MENU_ID));
  }

  /** 특정 권한의 메뉴권한 상세 (AJAX) */
  @GetMapping("/detail")
  @ResponseBody
  public ApiResponse<List<TbMenuAuthDetail>> detail(@RequestParam int authId, HttpSession session) {
    return ApiResponse.ok(menuAuthService.detail(authId, actor(session), MENU_ID));
  }

  /** 엑셀 다운로드 — 권한 목록. 목적(purpose)은 감사 remark. */
  @GetMapping("/excel")
  public void excel(
      MenuAuthSearchParam param,
      @RequestParam String purpose,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    List<TbMenuAuth> rows =
        menuAuthService.listAllForExcel(param, actor(session), MENU_ID, purpose);
    String[] headers = {"권한ID", "권한명", "메뉴권한수"};
    List<String[]> data =
        rows.stream()
            .map(
                r ->
                    new String[] {
                      String.valueOf(r.getAuthId()),
                      r.getAuthName(),
                      String.valueOf(r.getMenuCount() == null ? 0 : r.getMenuCount())
                    })
            .toList();
    String filename =
        "권한_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
    ExcelUtil.download(response, filename, headers, data);
  }

  /** 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody MenuAuthForm form, HttpSession session) {
    menuAuthService.create(form, actor(session), MENU_ID);
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody MenuAuthForm form, HttpSession session) {
    menuAuthService.update(form, actor(session), MENU_ID);
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 삭제 (AJAX) */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int authId, HttpSession session) {
    menuAuthService.delete(authId, actor(session), MENU_ID);
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
