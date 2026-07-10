package AirPort.mapper;

import AirPort.model.CommonSearchParam;
import AirPort.model.TbCommon;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 공통 코드 매퍼. SQL 은 mapper/TbCommonMapper.xml. */
public interface TbCommonMapper {

  List<TbCommon> selectList(CommonSearchParam param);

  long selectCount(CommonSearchParam param);

  TbCommon selectOne(@Param("cmmId") String cmmId, @Param("codeId") String codeId);

  int insert(TbCommon row);

  int update(TbCommon row);

  int delete(@Param("cmmId") String cmmId, @Param("codeId") String codeId);
}
