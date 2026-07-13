package AirPort.mapper;

import AirPort.model.MenuAuthSearchParam;
import AirPort.model.TbMenuAuth;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 권한(그룹) 매퍼. SQL 은 mapper/TbMenuAuthMapper.xml. */
public interface TbMenuAuthMapper {

  /** 권한 목록(다른 화면 select 옵션용). authId/authName 만 채워짐. */
  List<TbMenuAuth> selectOptions();

  // ── 권한메뉴관리 화면 CRUD ──────────────────────────────
  List<TbMenuAuth> selectList(MenuAuthSearchParam param);

  List<TbMenuAuth> selectListAll(MenuAuthSearchParam param);

  long selectCount(MenuAuthSearchParam param);

  TbMenuAuth selectById(@Param("authId") int authId);

  int insert(TbMenuAuth row); // useGeneratedKeys → authId

  int update(TbMenuAuth row);

  int delete(@Param("authId") int authId);
}
