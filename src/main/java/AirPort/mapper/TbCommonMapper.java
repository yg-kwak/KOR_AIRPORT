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

  /** 코드구분(cmm_id) 전체 목록 (등록 화면 select 용). cmmId/cmmName 만 채워짐. */
  List<TbCommon> selectGroups();

  /** 해당 cmm_id 의 코드구분명(없으면 null). 등록 검증·cmm_name 파생에 사용. */
  String selectGroupName(@Param("cmmId") String cmmId);

  /** 코드 선택 팝업용 — cmm_id 의 사용중 코드 목록(코드/코드명, 검색어 LIKE). 다른 화면의 tb_common 참조에 공용. */
  List<TbCommon> selectCodesForPicker(
      @Param("cmmId") String cmmId, @Param("keyword") String keyword);

  int insert(TbCommon row);

  int update(TbCommon row);

  int delete(@Param("cmmId") String cmmId, @Param("codeId") String codeId);
}
