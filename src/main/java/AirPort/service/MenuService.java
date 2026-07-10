package AirPort.service;

import AirPort.mapper.TbMenuMapper;
import AirPort.model.MenuNode;
import AirPort.model.TbMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 메뉴 조회(사이드바 등). */
@Service
public class MenuService {

  private final TbMenuMapper menuMapper;

  public MenuService(TbMenuMapper menuMapper) {
    this.menuMapper = menuMapper;
  }

  public List<TbMenu> useList() {
    return menuMapper.selectUseList();
  }

  /** 사이드바용 메뉴 트리 — level 1(parent 없음)을 루트로, 하위를 parent_menu_id 로 연결. */
  public List<MenuNode> tree() {
    List<TbMenu> all = menuMapper.selectUseList(); // level/order 정렬됨
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
}
