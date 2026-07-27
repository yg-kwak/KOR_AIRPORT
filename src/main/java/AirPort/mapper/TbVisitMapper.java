package AirPort.mapper;

import AirPort.model.TbVisit;
import AirPort.model.VisitSearchParam;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 방문(tb_visit) + 매핑 5종 매퍼. 매핑은 하드 삭제 후 재삽입(tb_person_ac_group 패턴). */
public interface TbVisitMapper {

  List<TbVisit> selectList(VisitSearchParam param);

  long selectCount(VisitSearchParam param);

  TbVisit selectById(@Param("visitNo") int visitNo);

  int insert(TbVisit row);

  int update(TbVisit row);

  int softDelete(@Param("visitNo") int visitNo);

  /** 상태만 변경 — 퇴실(VS04) 처리용. */
  int updateStatus(@Param("visitNo") int visitNo, @Param("statusCode") String statusCode);

  // ── 인솔자 ──
  List<String> selectManagerIds(@Param("visitNo") int visitNo);

  int deleteManagers(@Param("visitNo") int visitNo);

  int insertManagers(@Param("visitNo") int visitNo, @Param("personIds") List<String> personIds);

  /** 임시(PT02) 인솔자 겹침 — 진행중(신청·입실중) 다른 임시 방문에 이미 인솔자인 person_id 목록. */
  List<String> selectActiveTempManagers(
      @Param("personIds") List<String> personIds, @Param("excludeVisitNo") Integer excludeVisitNo);

  // ── 방문객(person_id) ──
  List<String> selectPersonIds(@Param("visitNo") int visitNo);

  int deletePersons(@Param("visitNo") int visitNo);

  int insertPerson(@Param("visitNo") int visitNo, @Param("personId") String personId);

  // ── 방문 차량(car_id) ──
  List<Integer> selectCarIds(@Param("visitNo") int visitNo);

  int deleteCars(@Param("visitNo") int visitNo);

  int insertCar(@Param("visitNo") int visitNo, @Param("carId") int carId);

  // ── 사용자출입그룹(ac_group_id) ──
  List<Integer> selectAcGroupIds(@Param("visitNo") int visitNo);

  int deleteAcGroups(@Param("visitNo") int visitNo);

  int insertAcGroups(@Param("visitNo") int visitNo, @Param("acGroupIds") List<Integer> acGroupIds);

  // ── 차량출입그룹(CAR code_id) ──
  List<String> selectCarAcCodes(@Param("visitNo") int visitNo);

  int deleteCarAcGroups(@Param("visitNo") int visitNo);

  int insertCarAcGroups(@Param("visitNo") int visitNo, @Param("codeIds") List<String> codeIds);
}
