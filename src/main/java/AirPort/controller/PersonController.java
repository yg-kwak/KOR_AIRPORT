package AirPort.controller;

import AirPort.adapter.BiostarFace;
import AirPort.common.ApiResponse;
import AirPort.common.CurrentMenu;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.MenuPermission;
import AirPort.model.PersonForm;
import AirPort.model.PersonSearchParam;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.service.AcGroupService;
import AirPort.service.MenuAuthService;
import AirPort.service.MenuService;
import AirPort.service.PersonService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 정규인원등록 (tb_person, person_type='PT01') — 목록/등록 + 얼굴(업로드·촬영) 프록시.
 *
 * <p>얼굴 API 는 브라우저가 BiostarX 를 직접 부를 수 없으므로 서버가 중계한다(외부 연동은 adapter 로만, AGENTS §4).
 */
@Controller
@RequestMapping("/person/person")
public class PersonController {

  private final PersonService personService;
  private final AcGroupService acGroupService;
  private final MenuService menuService;
  private final MenuAuthService menuAuthService;
  private final CurrentMenu currentMenu; // 요청 URL 로 해석된 menu_id (하드코딩 대체)

  public PersonController(
      PersonService personService,
      AcGroupService acGroupService,
      MenuService menuService,
      MenuAuthService menuAuthService,
      CurrentMenu currentMenu) {
    this.personService = personService;
    this.acGroupService = acGroupService;
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
    return "web/person/person";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbPerson>> list(PersonSearchParam param, HttpSession session) {
    menuAuthService.requireRead(actor(session), menuId());
    return ApiResponse.ok(personService.list(param, actor(session), menuId()));
  }

  /** 등록 참조 데이터(기관 옵션) (AJAX) */
  @GetMapping("/refs")
  @ResponseBody
  public ApiResponse<Map<String, Object>> refs(HttpSession session) {
    return ApiResponse.ok(personService.refs(actor(session), menuId()));
  }

  /** 출입권한 선택 트리 (AJAX) — tb_ac_group 재사용 */
  @GetMapping("/acGroups")
  @ResponseBody
  public ApiResponse<List<AirPort.model.TbAcGroup>> acGroups(HttpSession session) {
    return ApiResponse.ok(acGroupService.tree(actor(session), menuId()));
  }

  /** 인원의 출입권한 ID 목록 (AJAX) */
  @GetMapping("/personAcGroups")
  @ResponseBody
  public ApiResponse<List<Integer>> personAcGroups(
      @RequestParam String personId, HttpSession session) {
    return ApiResponse.ok(personService.acGroupIds(personId, actor(session), menuId()));
  }

  /** 사진 파일 업로드 → BiostarX 정규화 얼굴 (AJAX) */
  @PostMapping("/face/upload")
  @ResponseBody
  public ApiResponse<BiostarFace> faceUpload(
      @RequestBody Map<String, String> body, HttpSession session) {
    return ApiResponse.ok(
        personService.uploadPicture(body.get("image"), actor(session), menuId()));
  }

  /** 로그인 계정의 장치로 얼굴 촬영 (AJAX) */
  @GetMapping("/face/capture")
  @ResponseBody
  public ApiResponse<BiostarFace> faceCapture(HttpSession session) {
    return ApiResponse.ok(personService.captureFace(actor(session), menuId()));
  }

  /** 등록 (AJAX) — BiostarX 사용자 생성 실패 시에도 인원은 저장하고 경고를 덧붙인다. */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody PersonForm form, HttpSession session) {
    String warn = personService.create(form, actor(session), menuId());
    return ApiResponse.okMessage(
        warn == null ? "등록되었습니다." : "등록되었습니다. (BiostarX 사용자 동기화 실패: " + warn + ")");
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
