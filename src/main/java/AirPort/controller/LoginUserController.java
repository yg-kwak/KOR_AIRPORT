package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.LoginUserSearchParam;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.service.LoginUserService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
 * 사용자관리 CRUD — 골든 샘플(CommonController) 구조를 따른다.
 *
 * <p>화면(GET)과 데이터(AJAX, @ResponseBody)를 한 컨트롤러에서 구분. 라우팅은 클래스 상단 프리픽스로 정의(docs/architecture.md).
 */
@Controller
@RequestMapping("/system/loginUser")
public class LoginUserController {

  private static final int MENU_ID = 303; // tb_menu 의 사용자관리 menu_id (seed)

  private final LoginUserService userService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;

  public LoginUserController(
      LoginUserService userService, MenuService menuService, MenuAuthService menuAuthService) {
    this.userService = userService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
  }

  /** 화면 — 메뉴 권한(perm)을 내려 버튼 노출을 제어한다(1차 방어). */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), MENU_ID);
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden"; // 무권한 URL 직접 접근 → 권한 없음 페이지
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/system/loginUser";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbLoginUser>> list(
      LoginUserSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), MENU_ID);
    return ApiResponse.ok(userService.list(param, actor(session), MENU_ID));
  }

  /** 등록/수정 참조 데이터(권한/시작메뉴/근무지역 select). (AJAX) */
  @GetMapping("/refs")
  @ResponseBody
  public ApiResponse<Map<String, Object>> refs(HttpSession session) {
    return ApiResponse.ok(userService.refs(actor(session), MENU_ID));
  }

  /** 엑셀 다운로드 — 현재 검색/정렬 조건의 전체 데이터. 목적(purpose)은 감사 remark 로 기록. */
  @GetMapping("/excel")
  public void excel(
      LoginUserSearchParam param,
      @RequestParam String purpose,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    List<TbLoginUser> rows = userService.listAllForExcel(param, actor(session), MENU_ID, purpose);
    String[] headers = {"사용자ID", "성명", "소속부서", "권한", "사용여부", "관리자여부"};
    List<String[]> data =
        rows.stream()
            .map(
                r ->
                    new String[] {
                      r.getUserId(),
                      r.getUserName(),
                      r.getDeptName(),
                      r.getAuthName(),
                      "Y".equals(r.getUseYn()) ? "사용" : "미사용",
                      "Y".equals(r.getRootYn()) ? "관리자" : "일반"
                    })
            .toList();
    String filename =
        "사용자_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
    ExcelUtil.download(response, filename, headers, data);
  }

  /** 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbLoginUser row, HttpSession session) {
    userService.create(row, actor(session), MENU_ID);
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbLoginUser row, HttpSession session) {
    userService.update(row, actor(session), MENU_ID);
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 삭제 (AJAX) */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam String userId, HttpSession session) {
    userService.delete(userId, actor(session), MENU_ID);
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
