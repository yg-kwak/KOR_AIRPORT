package AirPort.mapper;

import AirPort.common.PageParam;
import AirPort.model.TbCommon;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 공통 코드 매퍼. SQL 은 mapper/TbCommonMapper.xml. */
public interface TbCommonMapper {

  List<TbCommon> selectList(PageParam param);

  long selectCount(PageParam param);

  TbCommon selectOne(@Param("cmmId") String cmmId, @Param("codeId") String codeId);

  int insert(TbCommon row);

  int update(TbCommon row);

  int delete(@Param("cmmId") String cmmId, @Param("codeId") String codeId);
}
