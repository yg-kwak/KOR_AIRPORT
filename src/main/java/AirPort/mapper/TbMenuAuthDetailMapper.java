package AirPort.mapper;

import AirPort.model.TbMenuAuthDetail;
import org.apache.ibatis.annotations.Param;

/** 권한별 메뉴 CRUD 권한 매퍼. SQL 은 mapper/TbMenuAuthDetailMapper.xml. */
public interface TbMenuAuthDetailMapper {

  TbMenuAuthDetail selectOne(@Param("authId") int authId, @Param("menuId") int menuId);
}
