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

  /** 퇴실 완료 시각 기록 — 정기 파기의 기준. 이미 있으면 덮지 않는다. */
  int markCheckedOut(@Param("visitNo") int visitNo);

  // ── 정기 파기 ──

  /** 보존기간이 지난 퇴실 완료 방문번호 — 1회 실행 상한(limit)까지. */
  List<Integer> selectPurgeTargets(@Param("keepDays") int keepDays, @Param("limit") int limit);

  /** 이 방문에서만 등장하는 방문객(정규인원 제외) — 다른 방문에도 있으면 남긴다. */
  List<String> selectPurgeVisitorIds(@Param("visitNo") int visitNo);

  /** 방문 차량 ID — 기관차량은 포함되지 않는다. */
  List<Integer> selectPurgeCarIds(@Param("visitNo") int visitNo);

  /** 방문 본체와 매핑 행 삭제(자식 → 부모 순). 되돌릴 수 없다. */
  int purgeVisitRows(@Param("visitNo") int visitNo);

  /** 어느 방문에도 속하지 않은 채 남은 방문객(소프트 삭제분) — 개인정보만 남아 있다. */
  List<String> selectOrphanVisitorIds(@Param("keepDays") int keepDays, @Param("limit") int limit);

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

  int insertPerson(
      @Param("visitNo") int visitNo,
      @Param("personId") String personId,
      @Param("checkoutDt") String checkoutDt);

  /** 개별 퇴실 기록(최초 1회만 — 이미 퇴실이면 0행). */
  /** 아직 퇴실하지 않은 방문객 수 — 0 이면 방문도 끝난 것으로 본다. */
  int countStayingVisitors(@Param("visitNo") int visitNo);

  int updateVisitorCheckout(@Param("visitNo") int visitNo, @Param("personId") String personId);

  /** 방문객의 퇴실일시("yyyy-MM-dd HH:mm:ss"). 재실이면 null. */
  String selectVisitorCheckout(@Param("visitNo") int visitNo, @Param("personId") String personId);

  /** 방문객의 마지막 배정 카드번호 스냅샷 기록(카드 회수·재사용 후에도 보존). */
  int updateVisitorLastCard(
      @Param("visitNo") int visitNo,
      @Param("personId") String personId,
      @Param("cardId") int cardId);

  /** 방문객의 마지막 배정 카드번호 조회. */
  String selectVisitorLastCard(@Param("visitNo") int visitNo, @Param("personId") String personId);

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
