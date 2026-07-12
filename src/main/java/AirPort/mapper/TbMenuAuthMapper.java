package AirPort.mapper;

import AirPort.model.TbMenuAuth;
import java.util.List;

/** 권한(그룹) 매퍼. SQL 은 mapper/TbMenuAuthMapper.xml. */
public interface TbMenuAuthMapper {

  /** 권한 목록(사용자 등록 화면 select 용). authId/authName 만 채워짐. */
  List<TbMenuAuth> selectList();
}
