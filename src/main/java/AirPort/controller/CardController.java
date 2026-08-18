package AirPort.controller;

import AirPort.adapter.biostar.BiostarCard;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.CardSearchParam;
import AirPort.model.ExcelImportResult;
import AirPort.model.MenuPermission;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.service.CardImportService;
import AirPort.service.CardService;
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
 * 카드등록관리 CRUD (tb_card) — 카드 마스터. 골든 샘플(CarController) 구조를 따른다.
 *
 * <p>카드구분(CDT)·패스구분(PT)·카드상태(CS)는 공통 코드팝업으로 고르므로 별도 refs 엔드포인트가 없다. 인원 부여/회수는 정규인원등록 화면이 담당한다(여기서는
 * 할당 인원을 표시만 한다).
 */
@Controller
@RequestMapping("/card/card")
public class CardController {

  private final CardService cardService;
  private final CardImportService cardImportService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public CardController(
      CardService cardService,
      CardImportService cardImportService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.cardService = cardService;
    this.cardImportService = cardImportService;
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
    return "web/card/card";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbCard>> list(CardSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok(cardService.list(param, actor(session), menuId()));
  }

  /** 장치 리더로 카드번호 읽기 (AJAX) — 로그인 계정의 장치(dev_id) */
  @PostMapping("/scan")
  @ResponseBody
  public ApiResponse<BiostarCard> scan(HttpSession session) {
    return ApiResponse.ok(cardService.scan(actor(session), menuId()));
  }

  /** 등록 (AJAX) — BiostarX 카드 생성까지 성공해야 저장된다. */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbCard row, HttpSession session) {
    cardService.createCard(row, actor(session), menuId());
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 수정 (AJAX) — 카드번호·할당 인원은 불변 */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbCard row, HttpSession session) {
    cardService.updateCard(row, actor(session), menuId());
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 엑셀 일괄등록 양식 다운로드 — 헤더만 있는 빈 양식(카드번호*·카드구분*·카드명칭* 필수). */
  @GetMapping("/excel/template")
  public void excelTemplate(HttpServletResponse response) throws java.io.IOException {
    AirPort.util.ExcelUtil.download(
        response,
        "카드등록양식.xlsx",
        CardImportService.IMPORT_HEADERS,
        java.util.List.<String[]>of(CardImportService.EXAMPLE_ROW));
  }

  /** 엑셀 일괄등록 (AJAX, multipart) — 행별 성공/실패 요약. 미발급 카드로 등록된다. */
  @PostMapping("/excel/import")
  @ResponseBody
  public ApiResponse<ExcelImportResult> excelImport(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
      HttpSession session)
      throws java.io.IOException {
    if (file == null || file.isEmpty()) {
      return ApiResponse.fail(
          AirPort.common.exception.ErrorCode.INVALID_INPUT.code(), "업로드할 파일을 선택하세요.");
    }
    return ApiResponse.ok(
        cardImportService.importExcel(file.getInputStream(), actor(session), menuId()));
  }

  /** 삭제 (AJAX) — 소프트 삭제. 인원에게 할당된 카드는 거부 */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int cardId, HttpSession session) {
    cardService.deleteCard(cardId, actor(session), menuId());
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
