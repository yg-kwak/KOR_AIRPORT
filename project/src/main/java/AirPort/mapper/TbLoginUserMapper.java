package AirPort.mapper;

import AirPort.model.TbLoginUser;
import org.apache.ibatis.annotations.Param;

/** 로그인 사용자 매퍼. SQL 은 mapper/TbLoginUserMapper.xml. */
public interface TbLoginUserMapper {

  TbLoginUser selectById(@Param("userId") String userId);

  int updateLoginFailCnt(@Param("userId") String userId, @Param("cnt") int cnt);
}
