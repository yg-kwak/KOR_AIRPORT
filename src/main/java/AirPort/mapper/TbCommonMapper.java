package AirPort.mapper;

import AirPort.model.CommonSearchParam;
import AirPort.model.TbCommon;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 공통 코드 매퍼. SQL 은 mapper/TbCommonMapper.xml. */
public interface TbCommonMapper {

  List<TbCommon> selectList(CommonSearchParam param);

  /** 엑셀 다운로드용 — 동일 검색/정렬, 페이징 없음(전체). */
  List<TbCommon> selectListAll(CommonSearchParam param);

  long selectCount(CommonSearchParam param);

  TbCommon selectOne(@Param("cmmId") String cmmId, @Param("codeId") String codeId);

  /** 사용자 코드 추가가 허용된 코드구분 목록 (user_input='Y' 인 행이 있는 cmm_id). cmmId/cmmName 만 채워짐. */
  List<TbCommon> selectAddableGroups();

  /** 해당 cmm_id 가 허용 구분이면 코드구분명, 아니면 null. 등록 검증·cmm_name 파생에 사용. */
  String selectAddableGroupName(@Param("cmmId") String cmmId);

  int insert(TbCommon row);

  int update(TbCommon row);

  int delete(@Param("cmmId") String cmmId, @Param("codeId") String codeId);
}
