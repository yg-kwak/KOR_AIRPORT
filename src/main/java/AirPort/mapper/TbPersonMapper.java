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
  /** 기관의 정규인원 목록 — 차량관리자 선택 팝업(성명은 ARIA 암호문, 서비스가 복호화). */
  List<TbPerson> selectByCompany(@Param("companyCode") String companyCode);

  /** 정규인원(PT01) 키워드 조회 — 인솔자 선택 팝업. 성명은 암호문이라 인원ID 로 검색. */
  /** 정규인원(PT01) 후보 전체 — 성명이 ARIA 암호문이라 키워드 검색은 서비스에서 복호화 후 필터. */
  List<TbPerson> selectRegular();

  TbPerson selectById(@Param("personId") String personId);

  /** 정규인원(PT01) 중 숫자형 인원ID 의 다음 값(자동 채번). 없으면 기본 400001. */
  String selectNextPersonId();

  /** 임시(방문)인원 다음 ID — IS000001 부터 채번. */
  String selectNextVisitorId(@Param("prefix") String prefix);

  int insert(TbPerson row);

  int update(TbPerson row);

  /** 소프트 삭제 — del_yn='Y'. */
  int softDelete(@Param("personId") String personId);

  /** 삭제된 인원ID 로 다시 등록 — 남아 있는 행을 되살린다(person_id 는 PK 라 INSERT 불가). */
  int revive(TbPerson row);

  /** BiostarX 사용자 생성 성공 후 연동 ID 반영. */
  int updateBiostarUserId(
      @Param("personId") String personId, @Param("biostarUserId") String biostarUserId);
}
