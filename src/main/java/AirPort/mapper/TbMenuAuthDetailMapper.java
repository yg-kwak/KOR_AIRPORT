package AirPort.mapper;

import AirPort.model.TbMenuAuthDetail;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 권한별 메뉴 CRUD 권한 매퍼. SQL 은 mapper/TbMenuAuthDetailMapper.xml. */
public interface TbMenuAuthDetailMapper {

  TbMenuAuthDetail selectOne(@Param("authId") int authId, @Param("menuId") int menuId);

  /** 권한의 메뉴권한 목록(권한메뉴관리 편집 로드용). */
  List<TbMenuAuthDetail> selectByAuthId(@Param("authId") int authId);

  int deleteByAuthId(@Param("authId") int authId);

  int insert(TbMenuAuthDetail row);
}
