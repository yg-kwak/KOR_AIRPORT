package AirPort.controller;

import AirPort.adapter.biostar.BiostarDoor;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.SessionKeys;
import AirPort.model.GraphicMapForm;
import AirPort.model.MenuPermission;
import AirPort.model.TbGraphicMap;
import AirPort.model.TbGraphicMapDoor;
import AirPort.model.TbLoginUser;
import AirPort.service.GraphicMapService;
import AirPort.service.MenuAuthService;
import AirPort.service.MonitorService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 그래픽맵(모니터링 903) — 평면도 위에 출입문(단말기)을 놓고, 그 문들의 인증을 평면도·로그·사진으로 본다. 새 창으로 여는 상황판이다(사이드바·헤더 없음).
 *
 * <p>인증 이벤트는 이벤트 로그(902)와 같은 {@link MonitorService} 스트림이다 — 이 화면은 지금 보는 맵에 놓인 단말기들을 구독할 뿐이다.
 */
@Controller
@RequestMapping("/monitor/graphicMap")
public class GraphicMapController {

  private final GraphicMapService graphicMapService;
  private final MonitorService monitorService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public GraphicMapController(
      GraphicMapService graphicMapService,
      MonitorService monitorService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.graphicMapService = graphicMapService;
    this.monitorService = monitorService;
    this.menuAuthService = menuAuthService;
    this.currentMenu = currentMenu;
  }

  private Integer menuId() {
    return currentMenu.getMenuId();
  }

  /** 화면 — 새 창용이라 사이드바 없이 그린다. */
  @GetMapping
  public String page(Model model, HttpSession session, HttpServletResponse response) {
    MenuPermission perm = menuAuthService.permissionFor(actor(session), menuId());
    if (!perm.isCanRead()) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return "error/forbidden";
    }
    model.addAttribute("perm", perm);
    return "web/monitor/graphicMap";
  }

  /** 로그인 세션 유지 (AJAX) — SSE 는 요청 하나라 연결만으로는 세션이 갱신되지 않는다. */
  @GetMapping("/alive")
  @ResponseBody
  public ApiResponse<Void> alive(HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok();
  }

  @GetMapping("/maps")
  @ResponseBody
  public ApiResponse<List<TbGraphicMap>> maps(HttpSession session) {
    return ApiResponse.ok(graphicMapService.maps(actor(session), menuId()));
  }

  /** 평면도 이미지 — 저장할 때 판정한 형식으로만 내보낸다(업로드한 쪽이 붙인 MIME 은 쓰지 않는다). */
  @GetMapping("/image")
  public ResponseEntity<byte[]> image(@RequestParam int mapId, HttpSession session) {
    TbGraphicMap m = graphicMapService.image(mapId, actor(session), menuId());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(m.getImageType()))
        .cacheControl(CacheControl.noCache()) // 교체하면 바로 보이게 — 화면은 주소에 수정 표식을 붙인다
        .header("X-Content-Type-Options", "nosniff")
        .body(m.getImageData());
  }

  /** 평면도 추가 (multipart: mapName, file). */
  @PostMapping("/maps")
  @ResponseBody
  public ApiResponse<Integer> create(
      @RequestParam String mapName, @RequestParam("file") MultipartFile file, HttpSession session)
      throws IOException {
    // 새 맵 번호를 돌려준다 — 화면이 추가한 맵을 바로 연다(토스트는 화면이 띄운다)
    return ApiResponse.ok(
        graphicMapService.create(mapName, file.getBytes(), actor(session), menuId()));
  }

  /** 평면도 이미지 교체 (multipart: mapId, file). */
  @PostMapping("/maps/image")
  @ResponseBody
  public ApiResponse<Void> replaceImage(
      @RequestParam int mapId, @RequestParam("file") MultipartFile file, HttpSession session)
      throws IOException {
    graphicMapService.replaceImage(mapId, file.getBytes(), actor(session), menuId());
    return ApiResponse.okMessage("평면도를 바꿨습니다.");
  }

  /** 이름 변경 / 출입문 배치 저장. */
  @PutMapping("/maps")
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody GraphicMapForm form, HttpSession session) {
    graphicMapService.update(form, actor(session), menuId());
    return ApiResponse.okMessage("저장했습니다.");
  }

  @DeleteMapping("/maps")
  @ResponseBody
  public ApiResponse<Void> delete(@RequestParam int mapId, HttpSession session) {
    graphicMapService.delete(mapId, actor(session), menuId());
    return ApiResponse.okMessage("맵을 삭제했습니다.");
  }

  @GetMapping("/doors")
  @ResponseBody
  public ApiResponse<List<TbGraphicMapDoor>> doors(@RequestParam int mapId, HttpSession session) {
    return ApiResponse.ok(graphicMapService.doors(mapId, actor(session), menuId()));
  }

  /** 놓을 수 있는 출입문 — BiostarX 출입문 목록(전체 출입문 그룹). */
  @GetMapping("/biostarDoors")
  @ResponseBody
  public ApiResponse<List<BiostarDoor>> biostarDoors(HttpSession session) {
    return ApiResponse.ok(graphicMapService.biostarDoors(actor(session), menuId()));
  }

  /** 출입문 원격 제어 — action: unlock(개방) · lock(잠금) · release(해제). 이 맵에 놓인 문만. */
  @PostMapping("/doors/control")
  @ResponseBody
  public ApiResponse<Void> control(
      @RequestParam int mapId,
      @RequestParam long doorId,
      @RequestParam String action,
      HttpSession session) {
    return ApiResponse.okMessage(
        graphicMapService.controlDoor(mapId, doorId, action, actor(session), menuId()));
  }

  /** 인증 이벤트 스트림 (SSE) — 지금 맵에 놓인 출입문들의 입구 단말기. */
  @GetMapping("/stream")
  public SseEmitter stream(
      @RequestParam(required = false) List<String> deviceId, HttpSession session) {
    // 아래 이벤트 표는 모든 이벤트 — 놓인 문이 없는 맵에서도 열린다(사진·문 반짝임은 놓인 문의 단말기만)
    return monitorService.subscribe(deviceId, true, actor(session), menuId());
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
