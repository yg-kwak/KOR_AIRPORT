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
 * 장기출입등록(방문) — 임시인원등록과 동일 구성이나 방문유형을 PTD03 계열(장기·상주)에서 선택한다. (docs/backend.md)
 *
 * <p>화면·CRUD 는 {@link VisitService} 와 임시인원등록 화면(web/visitor/visitor)을 공유하고, 방문유형 계열(codeTag)만 PTD03
 * 로 달리한다. 목록은 PTD03 계열만 노출한다.
 */
@Controller
@RequestMapping("/visitor/longterm")
public class LongTermController {

  private static final String CODE_TAG = "PTD03"; // 장기·상주 발급구분
  private static final String AREA_TYPE = "PT03"; // 구역범위 대표(장기, code_remark='Y' → 세부트리)

  private final VisitService visitService;
  private final CardService cardService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu;

  public LongTermController(
      VisitService visitService,
      CardService cardService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.visitService = visitService;
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
    model.addAttribute("screenTitle", "장기출입등록");
    model.addAttribute("base", "/visitor/longterm");
    model.addAttribute("codeTag", CODE_TAG);
    model.addAttribute("fixedVisitType", null); // 방문유형은 select 로 선택
    model.addAttribute("fixedVisitTypeName", null);
    model.addAttribute("visitTypes", visitService.visitTypes(CODE_TAG)); // PTD03 계열(장기·상주)
    return "web/visitor/visitor";
  }

  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbVisit>> list(VisitSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    param.setCodeTag(CODE_TAG); // PTD03 계열만
    return ApiResponse.ok(visitService.list(param, actor(session), menuId()));
  }

  @GetMapping("/detail")
  @ResponseBody
  public ApiResponse<VisitService.VisitDetail> detail(
      @RequestParam int visitNo, HttpSession session) {
    return ApiResponse.ok(visitService.detail(visitNo, actor(session), menuId()));
  }

  @GetMapping("/acGroups")
  @ResponseBody
  public ApiResponse<List<AirPort.model.TbAcGroup>> acGroups(HttpSession session) {
    return ApiResponse.ok(visitService.acGroupTree(AREA_TYPE, actor(session), menuId()));
  }

  @GetMapping("/cards/unassigned")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCards(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(
        cardService.listUnassigned(
            keyword, CardService.CARD_TYPE_PERSON, actor(session), menuId()));
  }

  @GetMapping("/cards/unassigned/car")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCarCards(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(
        cardService.listUnassigned(keyword, CardService.CARD_TYPE_CAR, actor(session), menuId()));
  }

  @PostMapping("/card/scan")
  @ResponseBody
  public ApiResponse<AirPort.adapter.biostar.BiostarCard> scanCard(HttpSession session) {
    return ApiResponse.ok(cardService.scan(actor(session), menuId()));
  }

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

  @PostMapping("/checkout")
  @ResponseBody
  public ApiResponse<Void> checkout(@RequestParam int visitNo, HttpSession session) {
    return ApiResponse.okMessage(
        withSyncFailure("퇴실 처리되었습니다.", visitService.checkout(visitNo, actor(session), menuId())));
  }

  /** 방문객 개별 퇴실 (AJAX) — 카드 발급된 방문객은 제거 대신 이 방식으로 내보낸다. */
  @PostMapping("/visitor/checkout")
  @ResponseBody
  public ApiResponse<Void> checkoutVisitor(
      @RequestParam int visitNo, @RequestParam String personId, HttpSession session) {
    visitService.checkoutVisitor(visitNo, personId, actor(session), menuId());
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
