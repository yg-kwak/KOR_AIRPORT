package AirPort.service;

import AirPort.mapper.TbMenuMapper;
import AirPort.model.MenuNode;
import AirPort.model.TbLoginUser;
import AirPort.model.TbMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 메뉴 조회(사이드바 등). 사이드바는 로그인 사용자의 read 권한 메뉴만 노출한다(root 는 전체). */
@Service
public class MenuService {

  private final TbMenuMapper menuMapper;
  private final MenuAuthService menuAuthService;

  public MenuService(TbMenuMapper menuMapper, MenuAuthService menuAuthService) {
    this.menuMapper = menuMapper;
    this.menuAuthService = menuAuthService;
  }

  public List<TbMenu> useList() {
    return menuMapper.selectUseList();
  }

  /**
   * 사이드바용 메뉴 트리 — level 1(parent 없음)을 루트로, 하위를 parent_menu_id 로 연결.
   *
   * <p>로그인 사용자의 read 권한이 있는 메뉴만 남긴다(root 는 전권이라 전체 노출). 하위가 모두 걸러진 상위 그룹은 숨긴다.
   */
  public List<MenuNode> tree(TbLoginUser actor) {
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
    return filterByPermission(roots, actor);
  }

  /**
   * 권한 필터 — 화면(menu_url 있는) 메뉴는 read 권한이 있을 때만, 상위 그룹은 노출할 하위가 하나라도 있을 때만 남긴다. root 는 {@link
   * MenuAuthService#permissionFor}가 전권을 돌려줘 전부 통과한다.
   */
  private List<MenuNode> filterByPermission(List<MenuNode> nodes, TbLoginUser actor) {
    List<MenuNode> kept = new ArrayList<>();
    for (MenuNode n : nodes) {
      List<MenuNode> keptChildren = filterByPermission(n.getChildren(), actor);
      n.getChildren().clear();
      n.getChildren().addAll(keptChildren);
      boolean selfVisible =
          n.getMenuUrl() != null && menuAuthService.permissionFor(actor, n.getMenuId()).isCanRead();
      if (selfVisible || !keptChildren.isEmpty()) {
        kept.add(n);
      }
    }
    return kept;
  }
}
