package AirPort.common;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 현재 요청이 속한 메뉴(menu_id) 홀더 — 요청 스코프.
 *
 * <p>MenuAccessInterceptor 가 요청 URI(=menu_url)를 역조회해 채운다. 컨트롤러는 하드코딩 상수 대신 이 값을 권한 판정·감사에 쓴다(메뉴 번호가
 * 순수 데이터가 되어 tb_menu 만 바꾸면 됨). (docs/security.md)
 */
@Component
@RequestScope
public class CurrentMenu {

  private Integer menuId;

  public Integer getMenuId() {
    return menuId;
  }

  public void setMenuId(Integer menuId) {
    this.menuId = menuId;
  }
}
