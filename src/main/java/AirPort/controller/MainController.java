package AirPort.controller;

import AirPort.common.SessionKeys;
import AirPort.model.TbLoginUser;
import AirPort.service.MenuService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 메인 화면. 사이드바 메뉴(로그인 사용자 권한 기준)를 함께 내려준다. */
@Controller
public class MainController {

  private final MenuService menuService;

  public MainController(MenuService menuService) {
    this.menuService = menuService;
  }

  @GetMapping("/")
  public String main(Model model, HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    TbLoginUser actor = (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
    model.addAttribute("menus", menuService.tree(actor));
    return "web/main";
  }
}
