package AirPort.mapper;

import org.apache.ibatis.annotations.Param;

/** 인원 등록사진 매퍼 (tb_person_photo, tb_person 과 1:1). 목록 조회는 이 테이블을 조인하지 않는다(성능·생체정보 보호). */
public interface TbPersonPhotoMapper {

  /** 사진 저장(있으면 갱신, 없으면 삽입). */
  int upsert(@Param("personId") String personId, @Param("photoData") String photoData);

  String selectPhoto(@Param("personId") String personId);

  int deleteByPerson(@Param("personId") String personId);
}
