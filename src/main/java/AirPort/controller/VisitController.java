package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.MenuPermission;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import AirPort.model.VisitForm;
import AirPort.model.VisitSearchParam;
import AirPort.service.CardService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.VisitService;
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
 * 임시인원등록(방문) — 그룹/인솔자/방문객/차량 탭 CRUD. (docs/backend.md)
 *
 * <p>출입그룹 트리·미할당 카드는 정규 화면과 같은 조회를 재사용한다. 인솔자 후보는 정규인원(PT01) 검색.
 */
@Controller
@RequestMapping("/visitor/visitor")
public class VisitController {

  private final VisitService visitService;
  private final AirPort.service.VisitPermitService permitService;
  private final CardService cardService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu;

  public VisitController(
      VisitService visitService,
      AirPort.service.VisitPermitService permitService,
      CardService cardService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.visitService = visitService;
    this.permitService = permitService;
    this.cardService = cardService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    model.addAttribute("screenTitle", "임시인원등록");
    model.addAttribute("base", "/visitor/visitor");
    model.addAttribute("codeTag", "PTD02");
    model.addAttribute("fixedVisitType", "PT02"); // 임시 고정
    model.addAttribute("fixedVisitTypeName", "임시");
    model.addAttribute("visitTypes", java.util.List.of());
    return "web/visitor/visitor";
  }

  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbVisit>> list(VisitSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    param.setCodeTag("PTD02"); // 임시(PTD02) 계열만
    return ApiResponse.ok(visitService.list(param, actor(session), menuId()));
  }

  /** 출입허가 신청서 (AJAX) — 인쇄용 값. 출력은 화면이 한다. */
  @GetMapping("/permit")
  @ResponseBody
  public ApiResponse<AirPort.model.PermitForm> permit(
      @RequestParam int visitNo, HttpSession session) {
    return ApiResponse.ok(permitService.permit(visitNo, actor(session), menuId()));
  }

  /** 상세 (AJAX) — 수정 모달용 그룹 + 인솔자/방문객/차량/출입그룹 */
  @GetMapping("/detail")
  @ResponseBody
  public ApiResponse<VisitService.VisitDetail> detail(
      @RequestParam int visitNo, HttpSession session) {
    return ApiResponse.ok(visitService.detail(visitNo, actor(session), menuId()));
  }

  /** 출입권한 선택 트리 (AJAX) — 방문유형 구역범위(code_remark)에 따라 최상위/세부 노출 */
  @GetMapping("/acGroups")
  @ResponseBody
  public ApiResponse<List<AirPort.model.TbAcGroup>> acGroups(HttpSession session) {
    return ApiResponse.ok(visitService.acGroupTree("PT02", actor(session), menuId()));
  }

  /** 미할당 방문객(인원) 카드 (AJAX) — 검색 지원 */
  @GetMapping("/cards/unassigned")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCards(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(
        cardService.listUnassigned(
            keyword, CardService.CARD_TYPE_PERSON, actor(session), menuId()));
  }

  /** 미할당 차량 카드 (AJAX) — 검색 지원(스캔 없음) */
  @GetMapping("/cards/unassigned/car")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCarCards(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(
        cardService.listUnassigned(keyword, CardService.CARD_TYPE_CAR, actor(session), menuId()));
  }

  /** 카드 스캔 (AJAX) — 방문객 카드용(리더로 카드번호 읽기). 차량 카드는 스캔 미지원. */
  @PostMapping("/card/scan")
  @ResponseBody
  public ApiResponse<AirPort.adapter.BiostarCard> scanCard(HttpSession session) {
    return ApiResponse.ok(cardService.scan(actor(session), menuId()));
  }

  /** 인솔자 후보 (AJAX) — 정규인원(PT01) 검색 */
  @GetMapping("/managers")
  @ResponseBody
  public ApiResponse<List<TbPerson>> managers(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(visitService.searchManagers(keyword, actor(session), menuId()));
  }

  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody VisitForm form, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("등록되었습니다.", visitService.create(form, actor(session), menuId())));
  }

  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody VisitForm form, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("수정되었습니다.", visitService.update(form, actor(session), menuId())));
  }

  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int visitNo, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("삭제되었습니다.", visitService.delete(visitNo, actor(session), menuId())));
  }

  /** 퇴실 처리 — 입실 중 방문을 퇴실완료로. BiostarX 사용자 비활성화 + 카드 회수. */
  @PostMapping("/checkout")
  @ResponseBody
  public ApiResponse<Void> checkout(@RequestParam int visitNo, HttpSession session) {
    return ApiResponse.okMessage(
        withWarning("퇴실 처리되었습니다.", visitService.checkout(visitNo, actor(session), menuId())));
  }

  /** 방문객 개별 퇴실 (AJAX) — 카드 발급된 방문객은 제거 대신 이 방식으로 내보낸다. */
  @PostMapping("/visitor/checkout")
  @ResponseBody
  public ApiResponse<Void> checkoutVisitor(
      @RequestParam int visitNo, @RequestParam String personId, HttpSession session) {
    visitService.checkoutVisitor(visitNo, personId, actor(session), menuId());
    return ApiResponse.okMessage("퇴실 처리되었습니다.");
  }

  private static String withWarning(String message, String warn) {
    return warn == null ? message : message + " (BiostarX 동기화 실패: " + warn + ")";
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
