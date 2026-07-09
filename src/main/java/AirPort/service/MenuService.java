package AirPort.service;

import AirPort.mapper.TbMenuMapper;
import AirPort.model.TbMenu;
import java.util.List;
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
}
