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

/** 실시간 이벤트 모니터링 — 조회 전용(입력/수정/삭제 없음). */
@Controller
@RequestMapping("/monitor/event")
public class MonitorController {

  private final MonitorService monitorService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public MonitorController(
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
    return "web/monitor/event";
  }

  /**
   * 로그인 세션 유지 (AJAX) — 화면이 주기적으로 부른다.
   *
   * <p>SSE 는 <b>요청 하나</b>다. 연결이 유지되는 동안 세션의 최종접근시각은 갱신되지 않으므로, 이 화면을 켜 두기만 하면 한 시간 뒤 세션이 만료된다. 그 뒤
   * 망이 한 번만 출렁여도 재연결이 로그인 화면으로 튕기고, 브라우저는 그것을 3초마다 영원히 반복한다 — 상황실에 사람이 없으면 몇 시간을 그렇게 있는다.
   *
   * <p>이 요청은 그 자체로 세션을 갱신한다. 세션이 이미 끊겼으면 인증 인터셉터가 막으므로 화면이 그때 사용자에게 알린다.
   */
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

  /**
   * 인증 이벤트 스트림 (SSE) — 고른 단말기의 인증 성공만 흘려 보낸다.
   *
   * <p>브라우저가 BiostarX 소켓을 직접 열 수 없어(self-signed 인증서 + 세션은 서버 보유) 서버가 중계한다.
   */
  @GetMapping("/stream")
  public SseEmitter stream(@RequestParam String deviceId, HttpSession session) {
    return monitorService.subscribe(deviceId, actor(session), menuId());
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
