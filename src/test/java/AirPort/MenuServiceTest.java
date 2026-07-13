package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.mapper.TbMenuMapper;
import AirPort.model.TbMenu;
import AirPort.service.MenuService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** menu_url → menu_id 역조회(경로 경계 최장 접두사) 순수 로직 단위 테스트. DB/Spring 불필요. */
class MenuServiceTest {

  private static TbMenu menu(int id, String url) {
    TbMenu m = new TbMenu();
    m.setMenuId(id);
    m.setMenuUrl(url);
    return m;
  }

  private static MenuService service(List<TbMenu> menus) {
    // resolveMenuId/isMenuUrl 는 menuMapper 만 사용(권한 서비스 미사용) → null 주입 가능
    TbMenuMapper mapper = () -> menus;
    return new MenuService(mapper, null);
  }

  @Test
  void resolveMenuId_경로경계_최장접두사() {
    MenuService s =
        service(
            List.of(
                menu(300, null), // 그룹(url 없음)
                menu(301, "/system/common"),
                menu(302, "/system/system"),
                menu(305, "/system/systemLog")));

    assertEquals(301, s.resolveMenuId("/system/common"));
    assertEquals(301, s.resolveMenuId("/system/common/list"));
    assertEquals(302, s.resolveMenuId("/system/system/test"));
    // /system/systemLog 가 /system/system 의 '문자열' 접두사여도 경계 검사로 305 로 해석
    assertEquals(305, s.resolveMenuId("/system/systemLog/menus"));
    assertNull(s.resolveMenuId("/login"));
    assertNull(s.resolveMenuId("/"));
  }

  @Test
  void isMenuUrl_정확일치만() {
    MenuService s = service(List.of(menu(301, "/system/common")));
    assertTrue(s.isMenuUrl("/system/common"));
    assertFalse(s.isMenuUrl("/system/common/list")); // 하위(AJAX)는 메뉴 URL 아님
  }
}
