package AirPort.controller;

import AirPort.common.SessionKeys;
import AirPort.model.TbLoginUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 뷰 렌더링 시 공통 모델(현재 로그인 사용자)을 주입. @Controller 대상만 적용. */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

  @ModelAttribute("currentUser")
  public TbLoginUser currentUser(HttpSession session) {
    Object u = session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
