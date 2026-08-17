package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.MenuPermission;
import AirPort.model.ParkingEventSearchParam;
import AirPort.model.TbLoginUser;
import AirPort.model.TbParkingEvent;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.ParkingEventService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 주차 조회(tb_parking_event) — 주차서버가 보내온 입·출차 이력. 조회 전용(입력/수정/삭제 없음).
 *
 * <p>이력을 <b>받는</b> 쪽은 {@link ParkingEventApiController} 다(무인증 외부 수신).
 */
@Controller
@RequestMapping("/carInfo/parkingEvent")
public class ParkingEventController {

  private final ParkingEventService parkingEventService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public ParkingEventController(
      ParkingEventService parkingEventService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.parkingEventService = parkingEventService;
    this.menuService = menuService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  /** 화면 */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    return "web/carInfo/parkingEvent";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbParkingEvent>> list(
      ParkingEventSearchParam param, HttpSession session) {
    return ApiResponse.ok(parkingEventService.list(param, actor(session), menuId()));
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
