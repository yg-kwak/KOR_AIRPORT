package AirPort.mapper;

import AirPort.model.PersonSearchParam;
import AirPort.model.TbPerson;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 인원 매퍼 (tb_person). SQL 은 mapper/TbPersonMapper.xml. 조회는 del_yn='N' 기준(소프트 삭제). */
public interface TbPersonMapper {

  List<TbPerson> selectList(PersonSearchParam param);

  long selectCount(PersonSearchParam param);

  /** PK 단건 조회(del_yn 무관 — 중복/존재 판정용). */
  TbPerson selectById(@Param("personId") String personId);

  int insert(TbPerson row);

  /** BiostarX 사용자 생성 성공 후 연동 ID 반영. */
  int updateBiostarUserId(
      @Param("personId") String personId, @Param("biostarUserId") String biostarUserId);
}
