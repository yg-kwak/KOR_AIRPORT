package AirPort.controller;

import AirPort.adapter.biostar.BiostarDevice;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.SessionKeys;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.MonitorService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 이벤트 로그 모니터링 — 조회 전용. 실시간 이벤트(901)와 <b>같은 이벤트</b>를 다른 배치로 본다.
 *
 * <p>다른 점은 둘이다: <b>단말기를 여러 대</b> 한 화면에서 보고, 지난 인증을 옆으로 늘어놓는 대신 <b>세로로 쌓아 스크롤</b>한다. 서버가 하는 일은 같아
 * {@link MonitorService} 를 그대로 쓴다 — 권한은 각 화면이 자기 menu_id(요청 URL 로 해석)로 확인한다.
 */
@Controller
@RequestMapping("/monitor/eventLog")
public class MonitorLogController {

  private final MonitorService monitorService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public MonitorLogController(
      MonitorService monitorService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.monitorService = monitorService;
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
    return "web/monitor/eventLog";
  }

  /** 로그인 세션 유지 (AJAX) — SSE 는 요청 하나라 연결만으로는 세션이 갱신되지 않는다. */
  @GetMapping("/alive")
  @ResponseBody
  public ApiResponse<Void> alive(HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok();
  }

  /** 단말기 목록 (AJAX) — BiostarX 장치 */
  @GetMapping("/devices")
  @ResponseBody
  public ApiResponse<List<BiostarDevice>> devices(HttpSession session) {
    return ApiResponse.ok(monitorService.devices(actor(session), menuId()));
  }

  /** 인증 이벤트 스트림 (SSE) — 고른 단말기 <b>여러 대</b>의 인증을 한 화면으로 흘려 보낸다. */
  @GetMapping("/stream")
  public SseEmitter stream(@RequestParam List<String> deviceId, HttpSession session) {
    return monitorService.subscribe(deviceId, actor(session), menuId());
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
