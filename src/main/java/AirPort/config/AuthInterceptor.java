package AirPort.config;

import AirPort.common.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 세션 기반 인증 인터셉터. 세션에 로그인 사용자가 없으면 로그인 페이지로 보낸다. 예외 경로(로그인/정적 등)는 WebConfig 에서 excludePathPatterns 로
 * 지정한다. (docs/security.md)
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    HttpSession session = request.getSession(false);
    Object user = (session == null) ? null : session.getAttribute(SessionKeys.LOGIN_USER);
    if (user != null) {
      return true;
    }
    // AJAX 요청은 401, 화면 요청은 로그인 페이지로 리다이렉트
    String requestedWith = request.getHeader("X-Requested-With");
    if ("XMLHttpRequest".equals(requestedWith)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    } else {
      response.sendRedirect(request.getContextPath() + "/login");
    }
    return false;
  }
}
