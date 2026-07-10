package AirPort.controller;

import AirPort.service.MenuService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 메인 화면. 사이드바 메뉴를 함께 내려준다. */
@Controller
public class MainController {

  private final MenuService menuService;

  public MainController(MenuService menuService) {
    this.menuService = menuService;
  }

  @GetMapping("/")
  public String main(Model model) {
    model.addAttribute("menus", menuService.tree());
    return "web/main";
  }
}
