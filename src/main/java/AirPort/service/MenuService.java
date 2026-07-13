package AirPort.service;

import AirPort.mapper.TbMenuMapper;
import AirPort.model.MenuNode;
import AirPort.model.TbLoginUser;
import AirPort.model.TbMenu;
import java.util.ArrayList;
import java.util.List;
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
    return filterByPermission(MenuNode.buildTree(menuMapper.selectUseList()), actor);
  }

  /** 전체 메뉴 트리(권한 필터 없음) — 권한메뉴관리 등 관리 화면의 선택 트리에 쓴다. */
  public List<MenuNode> fullTree() {
    return MenuNode.buildTree(menuMapper.selectUseList());
  }

  /** 로그인 사용자가 read 권한을 가진 화면(URL 있는) 메뉴 목록(평면) — 감사추적 메뉴 필터 등에 쓴다. */
  public List<MenuNode> readableLeaves(TbLoginUser actor) {
    List<MenuNode> out = new ArrayList<>();
    collectLeaves(tree(actor), out);
    return out;
  }

  private void collectLeaves(List<MenuNode> nodes, List<MenuNode> out) {
    for (MenuNode n : nodes) {
      if (n.getMenuUrl() != null) {
        out.add(n);
      }
      collectLeaves(n.getChildren(), out);
    }
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
