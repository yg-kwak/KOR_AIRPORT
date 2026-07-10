package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.CommonSearchParam;
import AirPort.model.MenuPermission;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.service.CommonService;
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

/**
 * 공통 코드 관리 — 골든 샘플 CRUD. 신규 CRUD 화면은 이 컨트롤러 구조를 그대로 따른다.
 *
 * <p>화면(GET) 과 데이터(AJAX, @ResponseBody) 를 한 컨트롤러에서 구분. 라우팅은 클래스 상단 프리픽스로 정의(docs/architecture.md).
 */
@Controller
@RequestMapping("/system/commonCode")
public class CommonController {

  private static final int MENU_ID = 301; // tb_menu 의 공통코드관리 menu_id (seed)

  private final CommonService commonService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;

  public CommonController(
      CommonService commonService, MenuService menuService, MenuAuthService menuAuthService) {
    this.commonService = commonService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
  }

  /** 화면 — 메뉴 권한(perm)을 내려 버튼 노출을 제어한다(1차 방어). */
  @GetMapping
  public String page(Model model, HttpSession session) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), MENU_ID);
    if (!perm.isCanRead()) {
      return "redirect:/"; // 조회 권한 없음 → 메인으로
    }
    model.addAttribute("menus", menuService.tree());
    model.addAttribute("perm", perm);
    return "web/system/commonCode";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbCommon>> list(CommonSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), MENU_ID);
    return ApiResponse.ok(commonService.list(param, actor(session), MENU_ID));
  }

  /** 코드구분 select 목록 — 사용자 추가 허용 구분(user_input='Y')만. (AJAX) */
  @GetMapping("/groups")
  @ResponseBody
  public ApiResponse<java.util.List<TbCommon>> groups(HttpSession session) {
    return ApiResponse.ok(commonService.addableGroups(actor(session), MENU_ID));
  }

  /** 엑셀 다운로드 — 현재 검색/정렬 조건의 전체(모든 페이지) 데이터. 목적(purpose)은 감사 remark 로 기록. */
  @GetMapping("/excel")
  public void excel(
      CommonSearchParam param,
      @RequestParam String purpose,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    List<TbCommon> rows = commonService.listAllForExcel(param, actor(session), MENU_ID, purpose);
    String[] headers = {"코드구분ID", "코드구분명", "코드ID", "코드명", "사용여부"};
    List<String[]> data =
        rows.stream()
            .map(
                r ->
                    new String[] {
                      r.getCmmId(),
                      r.getCmmName(),
                      r.getCodeId(),
                      r.getCodeName(),
                      "Y".equals(r.getUseYn()) ? "사용" : "미사용"
                    })
            .toList();
    String filename =
        "공통코드_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            + ".xlsx";
    ExcelUtil.download(response, filename, headers, data);
  }

  /** 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbCommon row, HttpSession session) {
    commonService.create(row, actor(session), MENU_ID);
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbCommon row, HttpSession session) {
    commonService.update(row, actor(session), MENU_ID);
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 삭제 (AJAX) */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(
      @RequestParam String cmmId, @RequestParam String codeId, HttpSession session) {
    commonService.delete(cmmId, codeId, actor(session), MENU_ID);
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
