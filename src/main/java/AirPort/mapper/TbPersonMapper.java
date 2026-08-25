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

  /** 실시간 이벤트 화면의 허가기간 — 출입기간을 초까지 읽는다. 목록·수정용 조회는 분까지다. */
  TbPerson selectAccessPeriod(@Param("personId") String personId);

  /**
   * 주어진 인원ID 중 <b>살아 있는(del_yn='N')</b> 것만 — 존재 여부만 필요할 때.
   *
   * <p>가져오기 대상 목록이 장비 사용자 1명마다 {@link #selectById} 를 부르면 인원 수만큼 질의가 나간다. 목록에는 '등록됨/신규' 만 필요하므로 한 번에
   * 확인한다.
   */
  List<String> selectExistingIds(@Param("personIds") List<String> personIds);

  /** 정규인원(PT01) 중 숫자형 인원ID 의 다음 값(자동 채번). 없으면 기본 400001. */
  String selectNextPersonId();

  /** 임시(방문)인원 다음 ID — IS000001 부터 채번. */
  String selectNextVisitorId(@Param("prefix") String prefix);

  int insert(TbPerson row);

  int update(TbPerson row);

  /**
   * BiostarX 가져오기(갱신) 전용 — <b>장비가 원천인 컬럼만</b> 덮어쓴다.
   *
   * <p>{@link #update} 를 쓰면 생년월일·신원조회·보안교육·인원상태처럼 <b>우리 화면에서만 채우는 값</b>이 함께 비워진다. 장비에는 그런 개념이 없어
   * 가져온 행에는 값이 없기 때문이다.
   */
  int updateFromBiostar(TbPerson row);

  /** 소프트 삭제 — del_yn='Y'. */
  int softDelete(@Param("personId") String personId);

  /** 삭제된 인원ID 로 다시 등록 — 남아 있는 행을 되살린다(person_id 는 PK 라 INSERT 불가). */
  int revive(TbPerson row);

  /** 정기 파기 — 사진·첨부·출입권한까지 물리 삭제. 되돌릴 수 없다. */
  int purge(@Param("personId") String personId);

  /** BiostarX 사용자 생성 성공 후 연동 ID 반영. */
  int updateBiostarUserId(
      @Param("personId") String personId, @Param("biostarUserId") String biostarUserId);
}
