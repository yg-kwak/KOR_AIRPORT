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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 구조화 로깅 — 요청마다 requestId(UUID 8자리)와 userId 를 MDC 에 주입하고, 처리 결과를 한 줄로 남긴다. (docs/backend.md 로깅 컨벤션)
 *
 * <p>로그 패턴의 %X{requestId}/%X{userId} 로 출력되어 한 요청의 로그를 상관관계 추적할 수 있다.
 *
 * <p><b>요청 로그를 여기서 남기는 이유</b> — Tomcat 접근 로그는 별도 파일로 빠져 로그가 두 곳으로 갈린다. 같은 요청의 업무 이력·오류와 나란히 보려면 한
 * 파일이어야 하고, 요청ID 로 이어 볼 수 있어야 한다. 필터가 요청 전체를 감싸므로 처리 시간도 여기서 잰다.
 */
@Component
public class MdcFilter extends OncePerRequestFilter {

  /** 요청 로그 전용 로거 — 끄려면 {@code logging.level.REQUEST=OFF}. */
  private static final Logger request = LoggerFactory.getLogger("REQUEST");

  /** 화면 리소스는 업무 흐름과 무관하고 양만 많아 남기지 않는다. */
  private static boolean skip(String uri) {
    return uri.startsWith("/css/")
        || uri.startsWith("/js/")
        || uri.startsWith("/images/")
        || uri.startsWith("/fonts/")
        || uri.startsWith("/webjars/")
        || uri.endsWith(".ico")
        || uri.endsWith(".map");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long started = System.currentTimeMillis();
    try {
      MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
      HttpSession session = req.getSession(false);
      Object u = (session == null) ? null : session.getAttribute(SessionKeys.LOGIN_USER);
      MDC.put("userId", (u instanceof TbLoginUser user) ? user.getUserId() : "-");
      chain.doFilter(req, res);
    } finally {
      String uri = req.getRequestURI();
      if (!skip(uri)) {
        String query = req.getQueryString();
        // 예) REQUEST - GET /person/person/list?page=1&size=5 200 282ms (172.30.1.5)
        request.info(
            "{} {}{} {} {}ms ({})",
            req.getMethod(),
            uri,
            (query == null || query.isBlank()) ? "" : "?" + query,
            res.getStatus(),
            System.currentTimeMillis() - started,
            req.getRemoteAddr());
      }
      MDC.clear();
    }
  }
}
