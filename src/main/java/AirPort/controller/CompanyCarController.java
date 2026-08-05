package AirPort.controller;

import AirPort.adapter.BiostarCard;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.CarCardForm;
import AirPort.model.CarForm;
import AirPort.model.CompanySearchParam;
import AirPort.model.MenuPermission;
import AirPort.model.TbCar;
import AirPort.model.TbCard;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.service.CardService;
import AirPort.service.CompanyCarService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
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
 * 기관차량등록 — 기관 소속 차량(tb_car) + 차량용 카드(tb_card) 발급.
 *
 * <p>차량등록관리(/carInfo/car)는 차량 마스터 전용이고, 이 화면은 <b>기관에 묶인 차량</b>과 그 카드를 다룬다.
 */
@Controller
@RequestMapping("/company/companyCar")
public class CompanyCarController {

  private final CompanyCarService companyCarService;
  private final CardService cardService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public CompanyCarController(
      CompanyCarService companyCarService,
      CardService cardService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.companyCarService = companyCarService;
    this.cardService = cardService;
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
      return "error/forbidden";
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/company/companyCar";
  }

  /** 목록 (AJAX) — 기관 목록(삭제되지 않은 기관) + 등록차량 수 */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbCompany>> list(CompanySearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok(companyCarService.list(param, actor(session), menuId()));
  }

  /** 기관의 차량 목록 (AJAX) — 기관 모달 */
  @GetMapping("/cars")
  @ResponseBody
  public ApiResponse<List<TbCar>> cars(@RequestParam String companyCode, HttpSession session) {
    return ApiResponse.ok(companyCarService.carsOf(companyCode, actor(session), menuId()));
  }

  /** 기관의 정규인원 (AJAX) — 차량관리자 선택 팝업 */
  @GetMapping("/managers")
  @ResponseBody
  public ApiResponse<List<TbPerson>> managers(
      @RequestParam String companyCode, HttpSession session) {
    return ApiResponse.ok(companyCarService.managersOf(companyCode, actor(session), menuId()));
  }

  /** 미할당 차량 (AJAX) — 차량 불러오기 팝업 */
  @GetMapping("/unassigned")
  @ResponseBody
  public ApiResponse<List<TbCar>> unassigned(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(companyCarService.unassignedCars(keyword, actor(session), menuId()));
  }

  /** 차량의 출입구역 코드 (AJAX) — tb_common(CAR) */
  @GetMapping("/acCodes")
  @ResponseBody
  public ApiResponse<List<String>> acCodes(@RequestParam int carId, HttpSession session) {
    return ApiResponse.ok(companyCarService.acCodesOf(carId, actor(session), menuId()));
  }

  /** 차량의 발급 카드 목록 (AJAX) */
  @GetMapping("/cards")
  @ResponseBody
  public ApiResponse<List<TbCard>> cards(@RequestParam int carId, HttpSession session) {
    return ApiResponse.ok(companyCarService.cards(carId, actor(session), menuId()));
  }

  /** 미할당 차량 카드 목록 (AJAX) — 카드 발급 시 이미 등록된(회수된) 카드를 불러온다. */
  @GetMapping("/cards/unassigned")
  @ResponseBody
  public ApiResponse<List<TbCard>> unassignedCards(
      @RequestParam(required = false) String keyword, HttpSession session) {
    return ApiResponse.ok(
        cardService.listUnassigned(keyword, CardService.CARD_TYPE_CAR, actor(session), menuId()));
  }

  /** 장치 리더로 카드번호 읽기 (AJAX) */
  @PostMapping("/card/scan")
  @ResponseBody
  public ApiResponse<BiostarCard> cardScan(HttpSession session) {
    return ApiResponse.ok(cardService.scan(actor(session), menuId()));
  }

  /** 차량용 카드 발급 (AJAX) — BiostarX 등록까지 성공해야 저장된다. */
  @PostMapping("/card")
  @ResponseBody
  public ApiResponse<Void> issueCard(@RequestBody CarCardForm form, HttpSession session) {
    companyCarService.issueCard(form, actor(session), menuId());
    return ApiResponse.okMessage("카드를 발급했습니다.");
  }

  /** 차량용 카드 회수 (AJAX) — 삭제가 아니라 미배정으로 되돌린다. */
  @DeleteMapping("/card")
  @ResponseBody
  public ApiResponse<Void> releaseCard(@RequestParam int cardId, HttpSession session) {
    companyCarService.releaseCard(cardId, actor(session), menuId());
    return ApiResponse.okMessage("카드를 회수했습니다.");
  }

  /** 차량 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody CarForm form, HttpSession session) {
    companyCarService.create(form, actor(session), menuId());
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 차량 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody CarForm form, HttpSession session) {
    companyCarService.update(form, actor(session), menuId());
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 차량 삭제 (AJAX) — 소프트 삭제. 발급된 카드가 있으면 거부 */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int carId, HttpSession session) {
    companyCarService.delete(carId, actor(session), menuId());
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
