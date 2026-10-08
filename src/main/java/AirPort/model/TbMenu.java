package AirPort.model;

import lombok.Data;

/** 메뉴 (tb_menu). 트리 구조. docs/database.md */
@Data
public class TbMenu {
  private Integer menuId;
  private String menuName;
  private Integer parentMenuId;
  private String menuUrl;
  private Integer menuLevel;
  private Integer menuOrder;
  private String newWindowYn; // 'Y' 면 새 창으로 연다(예: 그래픽맵 — 늘 켜 두는 상황판)
  private String menuIcon; // level 1 그룹 아이콘 키 (예: settings)
  private String useYn;
}
