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
import AirPort.service.VisitCheckoutService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 임시인원등록(방문) — 그룹/인솔자/방문객/차량 탭 CRUD. (docs/backend.md)
 *
 * <p>출입그룹 트리·미할당 카드는 정규 화면과 같은 조회를 재사용한다. 인솔자 후보는 정규인원(PT01) 검색.
 */
@Controller
@RequestMapping("/visitor/visitor")
public class VisitController {

  /** 이 화면은 임시 방문만 다룬다(tb_common PT) — 방문유형은 화면 값이 아니라 서버가 정한다. */
  private static final String VISIT_TYPE = "PT02";

  private final VisitService visitService;
  private final VisitCheckoutService checkoutService;
  private final AirPort.service.VisitPermitService permitService;
  private final CardService cardService;
  private final AirPort.service.VisitCardService visitCardService;
  private final AirPort.service.CardTagService cardTagService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu;

  public VisitController(
      VisitService visitService,
      VisitCheckoutService checkoutService,
      AirPort.service.VisitPermitService permitService,
      CardService cardService,
      AirPort.service.VisitCardService visitCardService,
      AirPort.service.CardTagService cardTagService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.visitService = visitService;
    this.checkoutService = checkoutService;
    this.permitService = permitService;
    this.cardService = cardService;
    this.visitCardService = visitCardService;
    this.cardTagService = cardTagService;
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
    model.addAttribute("fixedVisitType", VISIT_TYPE); // 임시 고정
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
    return ApiResponse.ok(visitService.acGroupTree(VISIT_TYPE, actor(session), menuId()));
  }

  /**
   * 미할당 방문객(인원) 카드 (AJAX) — 고른 <b>출입그룹에 맞는 카드만</b>({@code 임시234-0001}·{@code 대여-0001}).
   *
   * <p>출입그룹을 아직 고르지 않았으면 빈 목록이다. 전체를 보여 주면 구역이 맞지 않는 카드를 고르게 되고, 그 사실은 문 앞에서야 드러난다.
   *
   * <p>방문유형은 받지 않는다 — 실물 카드는 임시·상주·대여 3종뿐이고 어느 방문에서나 같은 카드를 쓴다.
   */
  @GetMapping("/cards/unassigned")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCards(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) List<Integer> acGroupIds,
      HttpSession session) {
    return ApiResponse.ok(
        visitCardService.candidates(acGroupIds, keyword, actor(session), menuId()));
  }

  /** 미할당 차량 카드 (AJAX) — 검색 지원(스캔 없음) */
  @GetMapping("/cards/unassigned/car")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCarCards(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(
        cardService.listUnassigned(keyword, CardService.CARD_TYPE_CAR, actor(session), menuId()));
  }

  /** 카드 스캔 (AJAX) — 방문객 카드용. 읽은 카드가 이 방문의 구역에 맞지 않으면 어떤 카드여야 하는지 알린다. */
  @PostMapping("/card/scan")
  @ResponseBody
  public ApiResponse<AirPort.adapter.biostar.BiostarCard> scanCard(
      @RequestParam(required = false) List<Integer> acGroupIds, HttpSession session) {
    return ApiResponse.ok(visitCardService.scan(acGroupIds, actor(session), menuId()));
  }

  /**
   * 방문객 카드 태깅 스트림 (SSE) — 내 리더에 카드를 대면 카드번호가 내려온다.
   *
   * <p>[SCAN] 은 한 장에 버튼 한 번이지만, 이 스트림을 켜 두면 <b>대는 대로</b> 방문객이 위에서부터 채워진다. 어느 방문객에게 넣을지와 구역이 맞는지는
   * 화면이 판정한다 — 지금 고른 출입그룹 기준이어야 한다.
   */
  @GetMapping("/card/tag/stream")
  public SseEmitter cardTagStream(HttpSession session) {
    return cardTagService.subscribe(actor(session), menuId());
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
        withSyncFailure("삭제되었습니다.", visitService.delete(visitNo, actor(session), menuId())));
  }

  /** 퇴실 처리 — 입실 중 방문을 퇴실완료로. BiostarX 사용자 비활성화 + 카드 회수. */
  @PostMapping("/checkout")
  @ResponseBody
  public ApiResponse<Void> checkout(@RequestParam int visitNo, HttpSession session) {
    return ApiResponse.okMessage(
        withSyncFailure(
            "퇴실 처리되었습니다.", checkoutService.checkout(visitNo, actor(session), menuId())));
  }

  /** 방문객 개별 퇴실 (AJAX) — 카드 발급된 방문객은 제거 대신 이 방식으로 내보낸다. */
  @PostMapping("/visitor/checkout")
  @ResponseBody
  public ApiResponse<Void> checkoutVisitor(
      @RequestParam int visitNo, @RequestParam String personId, HttpSession session) {
    checkoutService.checkoutVisitor(visitNo, personId, actor(session), menuId());
    return ApiResponse.okMessage("퇴실 처리되었습니다.");
  }

  /**
   * 저장(등록·수정) 경고 — 사유가 담긴 문장 그대로 붙인다.
   *
   * <p>이름표를 씌우지 않는다. 이 자리로 오는 문구는 BiostarX 동기화 실패만이 아니라 "카드를 받지 않은 방문객이 N명", "주차 차단기 등록 실패" 도 있어서,
   * 한 가지 이름을 붙이면 나머지가 사실과 다르게 표시된다.
   */
  private static String withWarning(String message, String warn) {
    return warn == null ? message : message + " " + warn;
  }

  /** 삭제·퇴실 경고 — BiostarX 가 돌려준 사유(예: {@code P2(HTTP 500)})라 이름표가 있어야 읽힌다. */
  private static String withSyncFailure(String message, String warn) {
    return warn == null ? message : message + " (BiostarX 동기화 실패: " + warn + ")";
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
