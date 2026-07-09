package AirPort.mapper;

import AirPort.model.TbMenu;
import java.util.List;

/** 메뉴 매퍼. SQL 은 mapper/TbMenuMapper.xml. */
public interface TbMenuMapper {

  List<TbMenu> selectUseList();
}
