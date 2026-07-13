package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.model.MenuNode;
import AirPort.model.TbMenu;
import java.util.List;
import org.junit.jupiter.api.Test;

/** parent_menu_id 로 평면 목록 → 트리(roots) 조립 순수 로직 단위 테스트. */
class MenuNodeTest {

  private static TbMenu menu(int id, Integer parent, String name) {
    TbMenu m = new TbMenu();
    m.setMenuId(id);
    m.setParentMenuId(parent);
    m.setMenuName(name);
    return m;
  }

  @Test
  void buildTree_부모_자식_손자_연결() {
    List<MenuNode> roots =
        MenuNode.buildTree(
            List.of(
                menu(300, null, "시스템"),
                menu(301, 300, "공통"),
                menu(302, 300, "설정"),
                menu(3021, 302, "하위설정"))); // 3단

    assertEquals(1, roots.size(), "루트는 1개(시스템)");
    MenuNode sys = roots.get(0);
    assertEquals(300, sys.getMenuId());
    assertEquals(2, sys.getChildren().size(), "공통·설정");

    MenuNode setting =
        sys.getChildren().stream().filter(n -> n.getMenuId() == 302).findFirst().orElseThrow();
    assertEquals(1, setting.getChildren().size(), "설정 하위 1개");
    assertEquals(3021, setting.getChildren().get(0).getMenuId());
  }

  @Test
  void buildTree_다중루트() {
    List<MenuNode> roots =
        MenuNode.buildTree(
            List.of(menu(300, null, "A"), menu(400, null, "B"), menu(301, 300, "a")));
    assertEquals(2, roots.size());
    assertTrue(roots.stream().anyMatch(n -> n.getMenuId() == 400 && n.getChildren().isEmpty()));
  }
}
