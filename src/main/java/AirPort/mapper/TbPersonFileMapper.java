package AirPort.mapper;

import AirPort.model.TbPersonFile;
import org.apache.ibatis.annotations.Param;

/** 인원 증빙문서 매퍼 (tb_person_file). 인원 목록은 이 테이블을 조인하지 않는다(행 크기). */
public interface TbPersonFileMapper {

  /** 문서 저장(있으면 교체, 없으면 삽입) — 인원별 file_type 당 1건. */
  int upsert(TbPersonFile file);

  /** 다운로드용 단건 조회(파일 실체 포함). */
  TbPersonFile selectOne(
      @Param("personId") String personId, @Param("fileType") String fileType);

  int delete(@Param("personId") String personId, @Param("fileType") String fileType);

  int deleteByPerson(@Param("personId") String personId);
}
