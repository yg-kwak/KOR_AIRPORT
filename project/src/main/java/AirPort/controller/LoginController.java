package AirPort.controller;

import AirPort.common.SessionKeys;
import AirPort.model.TbLoginUser;
import AirPort.service.AuditService;
import AirPort.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 로그인/로그아웃. (docs/security.md — 세션 기반 인증) */
@Controller
public class LoginController {

  private final LoginService loginService;
  private final AuditService auditService;

  public LoginController(LoginService loginService, AuditService auditService) {
    this.loginService = loginService;
    this.auditService = auditService;
  }

  @GetMapping("/login")
  public String loginForm(HttpSession session) {
    if (session.getAttribute(SessionKeys.LOGIN_USER) != null) {
      return "redirect:/";
    }
    return "login";
  }

  @PostMapping("/login")
  public String login(
      @RequestParam String userId,
      @RequestParam String password,
      HttpServletRequest request,
      Model model) {
    TbLoginUser user = loginService.authenticate(userId, password);
    if (user == null) {
      model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않습니다.");
      return "login";
    }
    HttpSession session = request.getSession(true);
    session.setAttribute(SessionKeys.LOGIN_USER, user);
    auditService.log(user, AuditService.MENU, null, "로그인");
    return "redirect:/";
  }

  @GetMapping("/logout")
  public String logout(HttpSession session) {
    session.invalidate();
    return "redirect:/login";
  }
}
