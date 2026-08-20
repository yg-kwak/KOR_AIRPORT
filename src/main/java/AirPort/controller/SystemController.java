package AirPort.controller;

import AirPort.adapter.biostar.BiostarResult;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.SessionKeys;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.SystemService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** 설정관리(tb_system) — BiostarX 연동정보. 단일 행 폼 + 저장/연결테스트. */
@Controller
@RequestMapping("/system/system")
public class SystemController {

  private final SystemService systemService;
  private final AirPort.service.PersonImportBiostarService importService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public SystemController(
      SystemService systemService,
      AirPort.service.PersonImportBiostarService importService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.systemService = systemService;
    this.importService = importService;
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
      return "error/forbidden"; // 무권한 URL 직접 접근 → 권한 없음 페이지
    }
    model.addAttribute("menus", menuService.tree(actor(session)));
    model.addAttribute("perm", perm);
    model.addAttribute("system", systemService.getForView(actor(session), menuId()));
    return "web/system/system";
  }

  /** 저장 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> save(@RequestBody TbSystem input, HttpSession session) {
    systemService.save(input, actor(session), menuId());
    return ApiResponse.okMessage("저장되었습니다.");
  }

  /** 가져오기 대상 목록 (AJAX) — 화면에서 고를 사용자. 장비 상세는 읽지 않는다(1명당 1회 왕복). */
  @GetMapping("/import/candidates")
  @ResponseBody
  public ApiResponse<java.util.List<AirPort.model.ImportCandidateResult>> importCandidates(
      @RequestParam(required = false) Long groupId, HttpSession session) {
    return ApiResponse.ok(importService.candidates(groupId, actor(session), menuId()));
  }

  /** 가져올 수 있는 사용자그룹 (AJAX) — 수천 명일 때 그룹으로 끊어 부르기 위한 목록. */
  @GetMapping("/import/groups")
  @ResponseBody
  public ApiResponse<java.util.List<AirPort.model.ImportGroupResult>> importGroups(
      HttpSession session) {
    return ApiResponse.ok(importService.groups(actor(session), menuId()));
  }

  /** BiostarX 가져오기 미리보기 (AJAX) — 고른 사람에게 무엇이 일어나는지만 센다. DB 를 건드리지 않는다. */
  @PostMapping("/import/preview")
  @ResponseBody
  public ApiResponse<AirPort.model.ImportResult> importPreview(
      @RequestBody AirPort.model.ImportForm form, HttpSession session) {
    return ApiResponse.ok(importService.preview(form, actor(session), menuId()));
  }

  /**
   * BiostarX 가져오기 실행 (AJAX) — 고른 사람을 장비 기준으로 맞춘다. 장비에는 쓰지 않는다.
   *
   * <p>카드·출입권한은 <b>덮어쓰기</b>다(우리에만 있던 것은 사라진다). 그래서 대상은 폼으로 받은 선택 목록뿐이다.
   */
  @PostMapping("/import")
  @ResponseBody
  public ApiResponse<AirPort.model.ImportResult> importRun(
      @RequestBody AirPort.model.ImportForm form, HttpSession session) {
    return ApiResponse.ok(importService.importUsers(form, actor(session), menuId()));
  }

  /** BiostarX 연결 테스트 (AJAX) — 성공/실패 메시지 반환(자동 토스트). */
  @PostMapping("/test")
  @ResponseBody
  public ApiResponse<Void> test(@RequestBody TbSystem input, HttpSession session) {
    BiostarResult result = systemService.testConnection(input, actor(session), menuId());
    return result.success()
        ? ApiResponse.okMessage("BiostarX 연결에 성공했습니다.")
        : ApiResponse.fail("BIOSTAR_TEST", "BiostarX 연결 실패: " + result.message());
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
