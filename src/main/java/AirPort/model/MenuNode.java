package AirPort.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 사이드바용 메뉴 트리 노드. level 1(그룹) → 하위 메뉴 재귀. (docs/frontend.md) */
public class MenuNode {

  private Integer menuId;
  private String menuName;
  private String menuUrl;
  private Integer menuLevel;
  private String menuIcon; // level 1 그룹 아이콘 키 (프론트 ICONS 매핑)
  private final List<MenuNode> children = new ArrayList<>();

  public MenuNode(TbMenu m) {
    this.menuId = m.getMenuId();
    this.menuName = m.getMenuName();
    this.menuUrl = m.getMenuUrl();
    this.menuLevel = m.getMenuLevel();
    this.menuIcon = m.getMenuIcon();
  }

  /** 평면 메뉴 목록(level/order 정렬됨)을 parent_menu_id 로 연결해 트리(roots)로 만든다. */
  public static List<MenuNode> buildTree(List<TbMenu> all) {
    Map<Integer, MenuNode> byId = new LinkedHashMap<>();
    for (TbMenu m : all) {
      byId.put(m.getMenuId(), new MenuNode(m));
    }
    List<MenuNode> roots = new ArrayList<>();
    for (TbMenu m : all) {
      MenuNode node = byId.get(m.getMenuId());
      MenuNode parent = (m.getParentMenuId() == null) ? null : byId.get(m.getParentMenuId());
      if (parent == null) {
        roots.add(node);
      } else {
        parent.getChildren().add(node);
      }
    }
    return roots;
  }

  public Integer getMenuId() {
    return menuId;
  }

  public String getMenuName() {
    return menuName;
  }

  public String getMenuUrl() {
    return menuUrl;
  }

  public Integer getMenuLevel() {
    return menuLevel;
  }

  public String getMenuIcon() {
    return menuIcon;
  }

  public List<MenuNode> getChildren() {
    return children;
  }
}
