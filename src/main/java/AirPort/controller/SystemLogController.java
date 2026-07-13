package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.MenuNode;
import AirPort.model.MenuPermission;
import AirPort.model.SystemLogSearchParam;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystemLog;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.SystemLogService;
import AirPort.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** 감사추적(tb_system_log) — 조회 전용(입력/수정/삭제 없음). */
@Controller
@RequestMapping("/system/systemLog")
public class SystemLogController {

  private static final int MENU_ID = 305; // tb_menu 의 감사추적 menu_id (seed)
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SystemLogService systemLogService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;

  public SystemLogController(
      SystemLogService systemLogService, MenuService menuService, MenuAuthService menuAuthService) {
    this.systemLogService = systemLogService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
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
    return "web/system/systemLog";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbSystemLog>> list(
      SystemLogSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), MENU_ID);
    return ApiResponse.ok(systemLogService.list(param, actor(session), MENU_ID));
  }

  /** 유형 필터 옵션 (AJAX) */
  @GetMapping("/types")
  @ResponseBody
  public ApiResponse<List<TbCommon>> types(HttpSession session) {
    return ApiResponse.ok(systemLogService.actionTypes(actor(session), MENU_ID));
  }

  /** 메뉴 필터 옵션 — 본인 권한 메뉴만 (AJAX) */
  @GetMapping("/menus")
  @ResponseBody
  public ApiResponse<List<MenuNode>> menus(HttpSession session) {
    return ApiResponse.ok(systemLogService.menuOptions(actor(session), MENU_ID));
  }

  /** 엑셀 다운로드 — 현재 검색조건 전체. 목적(purpose)은 감사 remark. */
  @GetMapping("/excel")
  public void excel(
      SystemLogSearchParam param,
      @RequestParam String purpose,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    List<TbSystemLog> rows =
        systemLogService.listAllForExcel(param, actor(session), MENU_ID, purpose);
    String[] headers = {"일시", "사용자ID", "사용자명", "유형", "메뉴", "내용", "비고"};
    List<String[]> data =
        rows.stream()
            .map(
                r ->
                    new String[] {
                      r.getRegDt() == null ? "" : r.getRegDt().format(TS),
                      r.getUserId(),
                      r.getUserName(),
                      r.getActionTypeName() != null ? r.getActionTypeName() : r.getActionType(),
                      r.getMenuName(),
                      r.getActionDetail(),
                      r.getRemark()
                    })
            .toList();
    String filename =
        "감사추적_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            + ".xlsx";
    ExcelUtil.download(response, filename, headers, data);
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
