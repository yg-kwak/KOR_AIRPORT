package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.CarSearchParam;
import AirPort.model.MenuPermission;
import AirPort.model.TbCar;
import AirPort.model.TbLoginUser;
import AirPort.service.CarService;
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
 * 차량등록관리 CRUD — 골든 샘플(LoginUserController) 구조를 따른다.
 *
 * <p>차종(car_type)은 공통 코드팝업(cmm_id='CT')으로 선택하므로 별도 refs 엔드포인트가 없다.
 */
@Controller
@RequestMapping("/carInfo/car")
public class CarController {

  private final CarService carService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public CarController(
      CarService carService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.carService = carService;
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
    return "web/carInfo/car";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbCar>> list(CarSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok(carService.list(param, actor(session), menuId()));
  }

  /** 엑셀 다운로드 — 현재 검색/정렬 조건의 전체 데이터. 목적(purpose)은 감사 remark 로 기록. */
  @GetMapping("/excel")
  public void excel(
      CarSearchParam param,
      @RequestParam String purpose,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    List<TbCar> rows = carService.listAllForExcel(param, actor(session), menuId(), purpose);
    String[] headers = {"차량번호", "차량명칭", "차종", "등록일"};
    List<String[]> data =
        rows.stream()
            .map(
                r ->
                    new String[] {
                      r.getCarNo(),
                      r.getCarName(),
                      r.getCarTypeName(),
                      r.getRegDt() == null ? "" : r.getRegDt().toString()
                    })
            .toList();
    String filename =
        "차량_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
    ExcelUtil.download(response, filename, headers, data);
  }

  /** 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbCar row, HttpSession session) {
    carService.create(row, actor(session), menuId());
    return ApiResponse.okMessage("등록되었습니다.");
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbCar row, HttpSession session) {
    carService.update(row, actor(session), menuId());
    return ApiResponse.okMessage("수정되었습니다.");
  }

  /** 삭제 (AJAX) — 소프트 삭제 */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int carId, HttpSession session) {
    carService.delete(carId, actor(session), menuId());
    return ApiResponse.okMessage("삭제되었습니다.");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
