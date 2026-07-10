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
  private String menuIcon; // level 1 그룹 아이콘 키 (예: settings)
  private String useYn;
}
