package AirPort.controller;

import AirPort.common.ApiResponse;
import AirPort.common.PageParam;
import AirPort.common.PageResult;
import AirPort.common.SessionKeys;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.service.CommonService;
import AirPort.service.MenuService;
import jakarta.servlet.http.HttpSession;
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
 * 공통 코드 관리 — 골든 샘플 CRUD. 신규 CRUD 화면은 이 컨트롤러 구조를 그대로 따른다.
 *
 * <p>화면(GET) 과 데이터(AJAX, @ResponseBody) 를 한 컨트롤러에서 구분. 라우팅은 클래스 상단 프리픽스로 정의(docs/architecture.md).
 */
@Controller
@RequestMapping("/system/commonCode")
public class CommonController {

  private static final int MENU_ID = 301; // tb_menu 의 공통코드관리 menu_id (seed)

  private final CommonService commonService;
  private final MenuService menuService;

  public CommonController(CommonService commonService, MenuService menuService) {
    this.commonService = commonService;
    this.menuService = menuService;
  }

  /** 화면 */
  @GetMapping
  public String page(Model model) {
    model.addAttribute("menus", menuService.useList());
    return "web/system/commonCode";
  }

  /** 목록 (AJAX) */
  @GetMapping("/list")
  @ResponseBody
  public ApiResponse<PageResult<TbCommon>> list(PageParam param) {
    return ApiResponse.ok(commonService.list(param));
  }

  /** 등록 (AJAX) */
  @PostMapping
  @ResponseBody
  public ApiResponse<Void> create(@RequestBody TbCommon row, HttpSession session) {
    commonService.create(row, actor(session), MENU_ID);
    return ApiResponse.ok();
  }

  /** 수정 (AJAX) */
  @PutMapping
  @ResponseBody
  public ApiResponse<Void> update(@RequestBody TbCommon row, HttpSession session) {
    commonService.update(row, actor(session), MENU_ID);
    return ApiResponse.ok();
  }

  /** 삭제 (AJAX) */
  @DeleteMapping
  @ResponseBody
  public ApiResponse<Void> delete(
      @RequestParam String cmmId, @RequestParam String codeId, HttpSession session) {
    commonService.delete(cmmId, codeId, actor(session), MENU_ID);
    return ApiResponse.ok();
  }

  private TbLoginUser actor(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
