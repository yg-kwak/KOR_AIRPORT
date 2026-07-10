package AirPort.model;

import java.util.ArrayList;
import java.util.List;

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
