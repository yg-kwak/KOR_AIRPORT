package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbAcGroup;
import AirPort.model.TbCommon;
import AirPort.model.TbVisit;
import AirPort.model.VisitCarForm;
import AirPort.model.VisitForm;
import AirPort.model.VisitorForm;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 키오스크(무인증) 방문 신청 오케스트레이션 — 로그인 없이 인솔자·방문구역·방문객·차량을 접수한다. (docs/security.md)
 *
 * <p>임시(PT02)·신청(VS01) tb_visit 로 저장되어 관리자 임시인원등록에 뜨고, 관리자가 확인 후 카드를 부여한다(BiostarX 연동은 그때). 여기서는
 * 카드·BiostarX 쓰기가 없다. 행 매핑/방문객·차량 생성은 {@link VisitService} 의 재사용 헬퍼(package-private)를 그대로 쓴다.
 */
@Service
public class KioskVisitService {

  private final VisitService visitService;
  private final VisitRosterService roster;
  private final TbVisitMapper visitMapper;
  private final AcGroupService acGroupService;
  private final TbCommonMapper commonMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final AuditService auditService;

  public KioskVisitService(
      VisitService visitService,
      VisitRosterService roster,
      TbVisitMapper visitMapper,
      AcGroupService acGroupService,
      TbCommonMapper commonMapper,
      TbPersonMapper personMapper,
      TbCarMapper carMapper,
      AuditService auditService) {
    this.visitService = visitService;
    this.roster = roster;
    this.visitMapper = visitMapper;
    this.acGroupService = acGroupService;
    this.commonMapper = commonMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.auditService = auditService;
  }

  /** 방문구역(사용자 출입그룹) 트리 — 무인증, 구역범위 규칙 동일 적용. */
  public List<TbAcGroup> acGroupTree() {
    return visitService.pruneAreaScope(acGroupService.treeNoAuth());
  }

  /** 인솔자 후보 검색 — 무인증. */
  public List<AirPort.model.TbPerson> searchManagers(String keyword) {
    return visitService.searchManagersPublic(keyword);
  }

  /** 공통코드 목록 — 키오스크에서 쓰는 CAR(차량구역)·CT(차종)만 허용. */
  public List<TbCommon> codes(String cmmId) {
    if (!"CAR".equals(cmmId) && !"CT".equals(cmmId)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 코드구분입니다.");
    }
    return commonMapper.selectCodesForPicker(cmmId, null);
  }

  /** 방문 신청 저장 — 임시·신청 고정, 카드/BiostarX 없음. */
  @Transactional
  public int create(VisitForm form) {
    List<VisitorForm> visitors = nonBlankVisitors(form);
    List<VisitCarForm> cars = nonBlankCars(form);
    validate(form, visitors, cars);

    form.setVisitType(VisitService.VISIT_TYPE);
    visitService.checkManagerOverlap(form, null); // 임시끼리 인솔자 겹침 금지
    TbVisit row = visitService.toRow(form);
    row.setVisitType(VisitService.VISIT_TYPE);
    row.setStatusCode(VisitService.DEFAULT_STATUS); // 신청
    visitMapper.insert(row);
    int visitNo = row.getVisitNo();
    visitMapper.insertManagers(
        visitNo, AirPort.model.VisitManagerForm.encrypted(form.getManagers()));
    if (has(form.getAcGroupIds())) { // 차량만이면 비어 있다 — 빈 VALUES 는 SQL 오류
      visitMapper.insertAcGroups(visitNo, form.getAcGroupIds());
    }
    if (has(form.getCarAcCodes())) {
      visitMapper.insertCarAcGroups(visitNo, form.getCarAcCodes());
    }
    for (VisitorForm vf : visitors) {
      visitMapper.insertPerson(visitNo, roster.upsertVisitor(vf, form), null);
    }
    for (VisitCarForm cf : cars) {
      visitMapper.insertCar(visitNo, roster.insertVisitCar(cf, form));
    }
    auditService.log(null, AuditService.CREATE, null, "키오스크 방문 신청: " + visitNo);
    return visitNo;
  }

  // ── [등록 수정] — 인솔자가 자기 신청을 고친다 ──────────────────────────────

  /**
   * 이 인솔자의 <b>신청 상태</b> 방문 목록.
   *
   * <p>무인증 화면이라 인원ID 하나로는 명단을 훑을 수 있다 — 성명까지 <b>둘 다</b> 맞아야 보여 준다. 신청 상태만이다: 관리자가 카드를 붙인 뒤에는 방문객이
   * 스스로 고치면 안 된다.
   */
  public List<TbVisit> applied(String managerId, String managerName) {
    return byManager(managerId, managerName, null);
  }

  /** 상세 — 그 방문이 정말 이 인솔자의 신청인지 먼저 확인한다. */
  public VisitService.VisitDetail detail(int visitNo, String managerId, String managerName) {
    requireMine(visitNo, managerId, managerName);
    return visitService.detailOf(visitNo);
  }

  /**
   * 수정 — 자식(인솔자·구역·방문객·차량)을 통째로 다시 만든다. 신청 상태라 카드·BiostarX·주차 연동이 아직 없어 외부 호출이 없다.
   *
   * <p>상태와 유형은 서버가 지킨다 — 화면이 무엇을 보내든 임시·신청 그대로다.
   */
  @Transactional
  public void update(VisitForm form, String managerId, String managerName) {
    bad(form.getVisitNo() == null, "방문번호가 필요합니다.");
    int visitNo = form.getVisitNo();
    requireMine(visitNo, managerId, managerName);
    List<VisitorForm> visitors = nonBlankVisitors(form);
    List<VisitCarForm> cars = nonBlankCars(form);
    validate(form, visitors, cars);

    form.setVisitType(VisitService.VISIT_TYPE);
    visitService.checkManagerOverlap(form, visitNo);
    TbVisit row = visitService.toRow(form);
    row.setVisitNo(visitNo);
    row.setVisitType(VisitService.VISIT_TYPE);
    row.setStatusCode(VisitService.DEFAULT_STATUS);
    visitMapper.update(row);

    visitMapper.deleteManagers(visitNo);
    visitMapper.insertManagers(
        visitNo, AirPort.model.VisitManagerForm.encrypted(form.getManagers()));
    visitMapper.deleteAcGroups(visitNo);
    if (has(form.getAcGroupIds())) { // 차량만이면 비어 있다 — 빈 VALUES 는 SQL 오류
      visitMapper.insertAcGroups(visitNo, form.getAcGroupIds());
    }
    visitMapper.deleteCarAcGroups(visitNo);
    if (has(form.getCarAcCodes())) {
      visitMapper.insertCarAcGroups(visitNo, form.getCarAcCodes());
    }
    // 방문객 — 남은 사람은 갱신, 폼에서 빠진 사람만 소프트삭제(카드가 없으니 회수할 것도 없다).
    // 무인증 요청이라 personId 는 이 방문의 명단에 있는 것만 믿는다 — 아무 값이나 받아 갱신하면
    // 방문번호와 무관한 인원(정규인원까지)의 성명·생년월일을 덮어쓸 수 있다. 명단 밖 ID 는 신규로 취급한다.
    java.util.Set<String> mine = new java.util.HashSet<>(visitMapper.selectPersonIds(visitNo));
    java.util.Set<String> kept = new java.util.HashSet<>();
    for (VisitorForm vf : visitors) {
      if (vf.getPersonId() != null && mine.contains(vf.getPersonId())) {
        kept.add(vf.getPersonId());
      } else {
        vf.setPersonId(null);
      }
    }
    for (String pid : mine) {
      if (!kept.contains(pid)) {
        personMapper.softDelete(pid);
      }
    }
    visitMapper.deletePersons(visitNo);
    for (VisitorForm vf : visitors) {
      visitMapper.insertPerson(visitNo, roster.upsertVisitor(vf, form), null);
    }
    // 차량 — 관리자 화면과 같이 매번 새로 만든다
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      carMapper.softDelete(carId);
    }
    visitMapper.deleteCars(visitNo);
    for (VisitCarForm cf : cars) {
      visitMapper.insertCar(visitNo, roster.insertVisitCar(cf, form));
    }
    auditService.log(
        null, AuditService.UPDATE, null, "키오스크 방문 수정: " + visitNo + " (인솔자 " + managerId + ")");
  }

  /**
   * 정말 이 사람의 신청인가 — 목록·상세·수정 <b>세 요청 모두</b> 여기를 지난다. 목록만 거르면 방문번호를 바꿔 넣는 것만으로 남의 신청을 고칠 수 있다.
   *
   * <p>무엇이 틀렸는지는 말하지 않는다 — "그 ID 는 있는데 성명이 다르다" 같은 답은 그 자체로 명단 정보다.
   */
  private void requireMine(int visitNo, String managerId, String managerName) {
    if (byManager(managerId, managerName, visitNo).isEmpty()) {
      throw new BusinessException(
          ErrorCode.NOT_FOUND, "수정할 수 있는 신청을 찾을 수 없습니다. 인원ID·성명이 맞는지, 아직 신청 상태인지 확인하세요.");
    }
  }

  private List<TbVisit> byManager(String managerId, String managerName, Integer visitNo) {
    req(managerId, "인원ID");
    req(managerName, "성명");
    return visitMapper.selectAppliedByManager(
        managerId.trim(),
        VisitService.encryptOrNull(managerName.trim()), // 암호문끼리 비교한다
        visitNo,
        VisitService.VISIT_TYPE,
        VisitService.DEFAULT_STATUS);
  }

  /** 등록·수정 공통 필수값 — 관리자 화면(VisitService.validate)과 같은 규칙이되 키오스크 문구로. */
  private static void validate(
      VisitForm form, List<VisitorForm> visitors, List<VisitCarForm> cars) {
    req(form.getWorkStartDt(), "작업기간 시작");
    req(form.getWorkEndDt(), "작업기간 종료");
    req(form.getWorkPurpose(), "작업목적");
    bad(form.managerIds().isEmpty(), "인솔자를 선택하세요."); // 차량만이어도 — [등록 수정]이 인솔자로 찾는다
    // 연락처 필수 — 관리자 화면과 같은 규칙을 쓴다(VisitManagerForm 에 모아 둠)
    AirPort.model.VisitManagerForm.requirePhones(form.getManagers());
    // 방문구분 — 고른 쪽은 있어야 하고 고르지 않은 쪽은 없어야 한다(규칙은 VisitKinds 한 곳, 관리자 화면과 같다)
    String kind = form.getVisitKind();
    AirPort.common.VisitKinds.check(
        kind,
        !visitors.isEmpty(),
        !cars.isEmpty(),
        has(form.getAcGroupIds()),
        has(form.getCarAcCodes()));
    // 키오스크는 구역까지 필수다 — 관리자가 카드를 붙일 때 어디를 열지 신청서에 적혀 있어야 한다
    bad(AirPort.common.VisitKinds.person(kind) && !has(form.getAcGroupIds()), "방문구역을 선택하세요.");
    bad(AirPort.common.VisitKinds.car(kind) && !has(form.getCarAcCodes()), "차량구역을 선택하세요.");
  }

  private static List<VisitorForm> nonBlankVisitors(VisitForm form) {
    List<VisitorForm> out = new ArrayList<>();
    if (form.getVisitors() != null) {
      for (VisitorForm v : form.getVisitors()) {
        if (v.getPersonName() != null && !v.getPersonName().isBlank()) out.add(v);
      }
    }
    return out;
  }

  private static List<VisitCarForm> nonBlankCars(VisitForm form) {
    List<VisitCarForm> out = new ArrayList<>();
    if (form.getCars() != null) {
      for (VisitCarForm c : form.getCars()) {
        if (c.getCarNo() != null && !c.getCarNo().isBlank()) out.add(c);
      }
    }
    return out;
  }

  private static boolean has(List<?> l) {
    return l != null && !l.isEmpty();
  }

  private static void req(String v, String label) {
    bad(v == null || v.isBlank(), label + "은(는) 필수입니다.");
  }

  private static void bad(boolean invalid, String message) {
    if (invalid) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
  }
}
