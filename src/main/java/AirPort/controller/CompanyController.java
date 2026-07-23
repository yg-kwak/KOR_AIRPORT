package AirPort.controller;

import AirPort.adapter.BiostarUserGroups;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.CompanySearchParam;
import AirPort.model.ExcelImportResult;
import AirPort.model.MenuPermission;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
import AirPort.service.CompanyService;
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
 * 기관등록관리 CRUD — 골든 샘플(LoginUserController) 구조를 따른다.
 *
 * <p>기관구분(company_type)은 공통 코드팝업(cmm_id='CO')으로 선택한다.
 */
@Controller
@RequestMapping("/company/company")
public class CompanyController {

  private final CompanyService companyService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public CompanyController(
      CompanyService companyService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.companyService = companyService;
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
    return "web/company/company";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbCompany>> list(CompanySearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok(companyService.list(param, actor(session), menuId()));
  }

  /** 엑셀 다운로드 — 현재 검색/정렬 조건의 전체 데이터. 목적(purpose)은 감사 remark 로 기록. */
  @GetMapping("/excel")
  public void excel(
      CompanySearchParam param,
      @RequestParam String purpose,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    List<TbCompany> rows = companyService.listAllForExcel(param, actor(session), menuId(), purpose);
    String[] headers = {"기관코드", "기관구분", "기관명", "대표자", "연락처", "사용유무"};
    List<String[]> data =
        rows.stream()
            .map(
                r ->
                    new String[] {
                      r.getCompanyCode(),
                      r.getCompanyTypeName(),
                      r.getCompanyName(),
                      r.getCeoName(),
                      r.getTel(),
                      "Y".equals(r.getUseYn()) ? "사용" : "미사용"
                    })
            .toList();
    String filename =
        "기관_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
    ExcelUtil.download(response, filename, headers, data);
  }

  /** 엑셀 일괄등록 양식 다운로드 — 헤더만 있는 빈 양식(기관코드*·기관명* 필수). */
  @GetMapping("/excel/template")
  public void excelTemplate(HttpServletResponse response) throws IOException {
    ExcelUtil.download(response, "기관등록양식.xlsx", CompanyService.IMPORT_HEADERS, List.of());
  }

  /** 엑셀 일괄등록 (AJAX, multipart) — 행별 성공/실패를 요약해 돌려준다. */
  @PostMapping("/excel/import")
  @ResponseBody
  public ApiResponse<ExcelImportResult> excelImport(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
      HttpSession session)
      throws IOException {
    if (file == null || file.isEmpty()) {
      return ApiResponse.fail(
          AirPort.common.exception.ErrorCode.INVALID_INPUT.code(), "업로드할 파일을 선택하세요.");
    }
    return ApiResponse.ok(companyService.importExcel(file.getInputStream(), actor(session), menuId()));
  }

  /** 기관 선택 팝업용 조회 — 로그인 사용자 공용(특정 메뉴 권한 불요). 코드팝업과 동일한 선례. (AJAX) */
  @GetMapping("/picker")
  @ResponseBody
  public ApiResponse<List<TbCompany>> picker() {
    return ApiResponse.ok(companyService.pickerCompanies());
  }

  /** BiostarX 사용자그룹 목록 (AJAX) — PTD01 하위만. 기관 등록 모달의 선택 팝업용 */
  @GetMapping("/biostarGroups")
  @ResponseBody
  public ApiResponse<BiostarUserGroups> biostarGroups(HttpSession session) {
    return ApiResponse.ok(companyService.biostarUserGroups(actor(session), menuId()));
  }

  /** 등록 (AJAX) — BiostarX 연동 실패 시에도 기관은 저장하고 경고를 메시지에 덧붙인다. */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbCompany row, HttpSession session) {
    String warn = companyService.create(row, actor(session), menuId());
    return ApiResponse.okMessage(withWarning("등록되었습니다.", warn));
  }

  /** 수정 (AJAX) — 기관명 변경 시 BiostarX 그룹명도 수정(실패는 경고). */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbCompany row, HttpSession session) {
    String warn = companyService.update(row, actor(session), menuId());
    return ApiResponse.okMessage(withWarning("수정되었습니다.", warn));
  }

  private static String withWarning(String message, String warn) {
    return warn == null ? message : message + " (BiostarX 사용자그룹 연동 실패: " + warn + ")";
  }

  /** 삭제 (AJAX) — 소프트 삭제 */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam String companyCode, HttpSession session) {
    companyService.delete(companyCode, actor(session), menuId());
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
