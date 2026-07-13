package AirPort.config;

import AirPort.common.CurrentMenu;
import AirPort.common.SessionKeys;
import AirPort.model.TbLoginUser;
import AirPort.service.AuditService;
import AirPort.service.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 요청 URI(=menu_url)를 menu_id 로 역조회해 {@link CurrentMenu} 에 넣고, 페이지 접속(정상 GET)이면 "메뉴 접속" 감사를 남긴다.
 *
 * <p>컨트롤러가 MENU_ID 를 하드코딩하지 않고 이 값을 쓰게 해, 메뉴 번호를 순수 데이터로 만든다(권한 판정 menu_id 는 서버가 결정 = 안전).
 * (docs/security.md)
 */
@Component
public class MenuAccessInterceptor implements HandlerInterceptor {

  private final MenuService menuService;
  private final AuditService auditService;
  private final CurrentMenu currentMenu;

  public MenuAccessInterceptor(
      MenuService menuService, AuditService auditService, CurrentMenu currentMenu) {
    this.menuService = menuService;
    this.auditService = auditService;
    this.currentMenu = currentMenu;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    currentMenu.setMenuId(menuService.resolveMenuId(request.getRequestURI()));
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView) {
    // 메뉴 접속 감사: 정상(200) 페이지 GET(= 메뉴 URL 자체) 일 때만. 하위 AJAX(/list 등)는 서비스가 READ 로 기록.
    Integer menuId = currentMenu.getMenuId();
    if (menuId == null
        || !"GET".equals(request.getMethod())
        || response.getStatus() != HttpServletResponse.SC_OK
        || !menuService.isMenuUrl(request.getRequestURI())) {
      return;
    }
    auditService.log(actor(request), AuditService.MENU, menuId, "메뉴 접속");
  }

  private TbLoginUser actor(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    Object u = (session == null) ? null : session.getAttribute(SessionKeys.LOGIN_USER);
    return (u instanceof TbLoginUser) ? (TbLoginUser) u : null;
  }
}
