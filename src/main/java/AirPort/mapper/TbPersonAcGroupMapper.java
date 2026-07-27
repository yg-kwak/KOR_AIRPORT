package AirPort.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 인원 출입그룹 매퍼 (tb_person_ac_group) — 인원 ↔ tb_ac_group 매핑. */
public interface TbPersonAcGroupMapper {

  int deleteByPerson(@Param("personId") String personId);

  int insertBatch(
      @Param("personId") String personId, @Param("acGroupIds") List<Integer> acGroupIds);

  List<Integer> selectAcGroupIds(@Param("personId") String personId);

  /** BiostarX 사용자 생성 payload 의 access_groups 용 — 매핑된 출입그룹의 biostar_ac_id 목록. */
  List<Integer> selectBiostarAcIds(@Param("personId") String personId);

  /** 카드 구역 표기용 — 매핑된 출입그룹의 최상위 구역코드(ar_code, 자식은 부모 상속) 중복 제거 목록. */
  List<String> selectAreaCodes(@Param("personId") String personId);
}
