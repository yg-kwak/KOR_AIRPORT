package AirPort.config;

import AirPort.common.SessionKeys;
import AirPort.model.TbLoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 구조화 로깅 — 요청마다 requestId(UUID 8자리)와 userId 를 MDC 에 주입한다. (docs/backend.md 로깅 컨벤션)
 *
 * <p>로그 패턴의 %X{requestId}/%X{userId} 로 출력되어 한 요청의 로그를 상관관계 추적할 수 있다.
 */
@Component
public class MdcFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
      HttpSession session = request.getSession(false);
      Object u = (session == null) ? null : session.getAttribute(SessionKeys.LOGIN_USER);
      MDC.put("userId", (u instanceof TbLoginUser user) ? user.getUserId() : "-");
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
