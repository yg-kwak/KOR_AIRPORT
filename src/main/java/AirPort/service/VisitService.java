package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbAcGroup;
import AirPort.model.TbCar;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import AirPort.model.VisitCarForm;
import AirPort.model.VisitForm;
import AirPort.model.VisitSearchParam;
import AirPort.model.VisitorForm;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* 임시인원(방문) 등록 — 그룹(tb_visit) + 인솔자/방문객/차량/출입그룹. (docs/backend.md)
방문객은 tb_person(person_type=visit_type), 차량은 tb_car. 출입그룹·카드는 정규와 같은 테이블 재사용.
BiostarX 방문객 동기화(PT→PTD code_tag 부모 그룹 편입)는 VisitBiostarService 담당(어댑터 경계). */
@Service
public class VisitService {

  // 방문 상태(tb_common VS) — 신청 / 신청취소(삭제 가능) / 입실중(전원 카드 시 자동 승격) / 퇴실완료(되돌림 없음)
  static final String DEFAULT_STATUS = "VS01";
  private static final String STATUS_CANCELLED = "VS02";
  private static final String STATUS_ENTERED = "VS03";
  private static final String STATUS_LEFT = "VS04";

  /** 임시인원등록 방문유형 — 임시 고정(tb_common PT02). */
  static final String VISIT_TYPE = "PT02";

  private final TbVisitMapper visitMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;
  private final VisitBiostarService visitBiostar;
  private final VisitRosterService roster;
  private final AcGroupService acGroupService;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public VisitService(
      TbVisitMapper visitMapper,
      TbPersonMapper personMapper,
      TbCarMapper carMapper,
      TbCardMapper cardMapper,
      TbCommonMapper commonMapper,
      VisitBiostarService visitBiostar,
      VisitRosterService roster,
      AcGroupService acGroupService,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
    this.visitBiostar = visitBiostar;
    this.roster = roster;
    this.acGroupService = acGroupService;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 사용자출입그룹 트리 — 구역범위(PT.code_remark)가 'Y' 아니면 최상위만 노출(임시=최상위만). 정규와 동일 트리 재사용. */
  public List<TbAcGroup> acGroupTree(String visitType, TbLoginUser actor, Integer menuId) {
    return pruneAreaScope(acGroupService.tree(actor, menuId), visitType);
  }

  /** 방문유형별 선택지 — PT 중 code_tag(발급구분) 계열 목록(장기출입등록 방문유형 select 등). */
  public List<TbCommon> visitTypes(String codeTag) {
    return commonMapper.selectByCodeTag("PT", codeTag);
  }

  /** 방문유형 구역범위(code_remark)가 'Y'가 아니면 최상위 그룹만 남긴다. (키오스크 재사용 — package-private) */
  List<TbAcGroup> pruneAreaScope(List<TbAcGroup> tree) {
    return pruneAreaScope(tree, VISIT_TYPE);
  }

  List<TbAcGroup> pruneAreaScope(List<TbAcGroup> tree, String visitType) {
    TbCommon pt = commonMapper.selectOne("PT", visitType);
    boolean detail = pt != null && "Y".equals(pt.getCodeRemark());
    if (!detail) {
      tree.forEach(root -> root.getChildren().clear()); // 최상위만 — 하위 세부트리 숨김
    }
    return tree;
  }

  public PageResult<TbVisit> list(VisitSearchParam param, TbLoginUser actor, Integer menuId) {
    // 방문객·인솔자 성명(ARIA 암호문)은 완전일치로만 검색 — keyword 를 암호화해 넘긴다
    param.setKeywordEnc(
        param.getKeyword() == null ? null : encryptOrNull(param.getKeyword().trim()));
    long total = visitMapper.selectCount(param);
    List<TbVisit> rows = visitMapper.selectList(param);
    auditService.log(actor, AuditService.READ, menuId, "방문 목록 조회 (결과 " + total + "건)");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  /** 인솔자 후보(정규인원 PT01) — 성명 복호화. */
  public List<TbPerson> searchManagers(String keyword, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return searchManagersPublic(keyword);
  }

  /** 인솔자 후보(무인증 키오스크 겸용) — 빈 검색어=결과 없음, ID 부분일치·성명 완전일치만(명단 훑기 방지), 최대 50건. */
  public List<TbPerson> searchManagersPublic(String keyword) {
    String kw = keyword == null ? "" : keyword.trim();
    List<TbPerson> rows = new ArrayList<>();
    if (kw.isEmpty()) return rows;
    for (TbPerson p : personMapper.selectRegular()) {
      p.setPersonName(decrypt(p.getPersonName()));
      boolean hit = p.getPersonId().contains(kw) || kw.equals(p.getPersonName());
      if (hit && rows.size() < 50) rows.add(p);
    }
    return rows;
  }

  /** 단건 상세 — 그룹 + 인솔자/방문객/차량/출입그룹 로드(수정 모달용). */
  public VisitDetail detail(int visitNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbVisit visit = visitMapper.selectById(visitNo);
    if (visit == null || "Y".equals(visit.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    VisitDetail d = new VisitDetail();
    d.visit = visit;
    d.managers = new ArrayList<>();
    for (String pid : visitMapper.selectManagerIds(visitNo)) {
      VisitorForm mf = new VisitorForm();
      mf.setPersonId(pid);
      TbPerson mp = personMapper.selectById(pid);
      mf.setPersonName(mp != null ? decrypt(mp.getPersonName()) : "");
      d.managers.add(mf);
    }
    d.acGroupIds = visitMapper.selectAcGroupIds(visitNo);
    d.carAcCodes = visitMapper.selectCarAcCodes(visitNo);
    d.visitors = new ArrayList<>();
    for (String pid : visitMapper.selectPersonIds(visitNo)) {
      TbPerson p = personMapper.selectById(pid);
      if (p != null) {
        VisitorForm f = new VisitorForm();
        f.setPersonId(p.getPersonId());
        f.setPersonName(decrypt(p.getPersonName()));
        f.setBirthDate(decrypt(p.getBirthDate()));
        f.setAffiliation(p.getAffiliation());
        List<TbCard> pc = cardMapper.selectByPerson(pid);
        if (!pc.isEmpty()) {
          f.setCardId(pc.get(0).getCardId());
          f.setCardLabel(pc.get(0).getBiostarCardValue());
        }
        f.setLastCardNo(visitMapper.selectVisitorLastCard(visitNo, pid)); // 회수 후에도 보존된 마지막 카드
        f.setCheckoutDt(visitMapper.selectVisitorCheckout(visitNo, pid)); // 값이 있으면 퇴실(카드 재발급 불가)
        d.visitors.add(f);
      }
    }
    d.cars = new ArrayList<>();
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      TbCar c = carMapper.selectById(carId);
      if (c != null) {
        VisitCarForm f = new VisitCarForm();
        f.setCarId(c.getCarId());
        f.setCarNo(c.getCarNo());
        f.setCarName(c.getCarName());
        f.setCarType(c.getCarType());
        List<TbCard> cc = cardMapper.selectByCar(carId);
        if (!cc.isEmpty()) {
          f.setCardId(cc.get(0).getCardId());
          f.setCardLabel(cc.get(0).getBiostarCardValue());
        }
        d.cars.add(f);
      }
    }
    return d;
  }

  @Transactional
  public String create(VisitForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(form);
    checkManagerOverlap(form, null);
    TbVisit row = toRow(form);
    row.setStatusCode(effectiveStatus(DEFAULT_STATUS, form)); // 상태는 서버가 관리(신청→전원카드 시 입실중)
    visitMapper.insert(row);
    String warn = roster.saveChildren(row.getVisitNo(), form, actor, menuId);
    auditService.log(actor, AuditService.CREATE, menuId, "방문 등록: " + row.getVisitNo());
    return warn;
  }

  @Transactional
  public String update(VisitForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (form.getVisitNo() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "방문번호가 필요합니다.");
    }
    validate(form);
    checkManagerOverlap(form, form.getVisitNo());
    TbVisit existing = visitMapper.selectById(form.getVisitNo());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (STATUS_LEFT.equals(existing.getStatusCode())) { // 퇴실완료는 수정 불가
      throw new BusinessException(ErrorCode.INVALID_INPUT, "퇴실 완료된 방문은 수정할 수 없습니다.");
    }
    // 입실중(VS03)엔 카드 '교환'만 허용 — 카드 회수(빈 카드)나 방문객 제외는 퇴실 처리로만 가능
    if (STATUS_ENTERED.equals(existing.getStatusCode())) {
      boolean noCard =
          form.getVisitors() == null
              || form.getVisitors().stream().anyMatch(vf -> vf.getCardId() == null);
      List<String> kept =
          form.getVisitors() == null
              ? List.of()
              : form.getVisitors().stream()
                  .map(VisitorForm::getPersonId)
                  .filter(java.util.Objects::nonNull)
                  .toList();
      check(
          noCard || !kept.containsAll(visitMapper.selectPersonIds(form.getVisitNo())),
          "입실 중인 방문은 카드 교환만 가능합니다. 카드 회수·방문객 제외는 퇴실 처리로 해주세요.");
    }
    TbVisit row = toRow(form);
    // 상태는 서버가 관리(사용자 변경 불가) — 기존 상태를 기준으로 전원 카드 발급 시 입실중 승격
    row.setStatusCode(effectiveStatus(existing.getStatusCode(), form));
    visitMapper.update(row);
    String warn = roster.saveChildren(form.getVisitNo(), form, actor, menuId);
    auditService.log(actor, AuditService.UPDATE, menuId, "방문 수정: " + form.getVisitNo());
    return warn;
  }

  @Transactional
  public String delete(int visitNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbVisit v = visitMapper.selectById(visitNo);
    if (v == null || "Y".equals(v.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    // 신청(VS01)·신청취소(VS02) 상태만 삭제 가능 — 입실중/퇴실완료는 이력 보존
    if (!DEFAULT_STATUS.equals(v.getStatusCode()) && !STATUS_CANCELLED.equals(v.getStatusCode())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "신청·신청취소 상태의 방문만 삭제할 수 있습니다.");
    }
    // BiostarX 방문객 사용자 삭제가 성공해야 방문 삭제를 커밋(실패=롤백 — 장비 유령 사용자 방지)
    String warn =
        visitBiostar.deleteVisitors(v.getVisitType(), visitMapper.selectPersonIds(visitNo));
    if (warn != null) {
      auditService.logAlways(
          actor, AuditService.DELETE, menuId, "방문 삭제 실패(" + visitNo + "): " + warn);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "BiostarX 사용자 삭제 실패로 방문 삭제가 취소되었습니다. 사유: " + warn + " — 다시 시도하세요.");
    }
    roster.clearRoster(visitNo); // 방문객/차량 정리(카드 회수 포함)
    visitMapper.deleteManagers(visitNo);
    visitMapper.deleteAcGroups(visitNo);
    visitMapper.deleteCarAcGroups(visitNo);
    visitMapper.softDelete(visitNo);
    auditService.log(actor, AuditService.DELETE, menuId, "방문 삭제: " + visitNo);
    return warn;
  }

  /**
   * 방문객 개별 퇴실 — 카드를 발급받은 방문객은 행에서 뺄 수 없으므로 이 방식으로 내보낸다.
   *
   * <p>방문 전체 퇴실과 같은 순서다: <b>BiostarX 비활성화가 성공해야</b> DB 카드를 회수한다(실패한 채 회수하면 장비에서는 계속 열리는데 카드는 재대여돼
   * 이중 사용이 된다). 퇴실 기록이 남으면 그 방문객에게는 다시 카드를 줄 수 없다.
   *
   * @return 항상 null(성공) — 실패는 예외
   */
  @Transactional
  public String checkoutVisitor(int visitNo, String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbVisit v = visitMapper.selectById(visitNo);
    if (v == null || "Y".equals(v.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (!visitMapper.selectPersonIds(visitNo).contains(personId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "이 방문의 방문객이 아닙니다.");
    }
    if (visitMapper.selectVisitorCheckout(visitNo, personId) != null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 퇴실한 방문객입니다.");
    }
    String warn = visitBiostar.disableVisitors(List.of(personId));
    if (warn != null) {
      auditService.logAlways(
          actor,
          AuditService.UPDATE,
          menuId,
          "방문객 퇴실 실패(" + visitNo + "/" + personId + "): " + warn);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 비활성화 실패로 퇴실이 취소되었습니다. 사유: " + warn + " — 다시 시도하세요.");
    }
    cardMapper.releaseByPerson(personId); // 카드 회수(다른 사람이 재사용 가능)
    visitMapper.updateVisitorCheckout(visitNo, personId);
    auditService.log(actor, AuditService.UPDATE, menuId, "방문객 퇴실: " + visitNo + "/" + personId);
    return null;
  }

  /** 퇴실(입실중→퇴실완료) — BiostarX 사용자 비활성화 + 카드 제거, DB 카드 회수(재대여 가능). */
  @Transactional
  public String checkout(int visitNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbVisit v = visitMapper.selectById(visitNo);
    if (v == null || "Y".equals(v.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (!STATUS_ENTERED.equals(v.getStatusCode())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "입실 중인 방문만 퇴실할 수 있습니다.");
    }
    List<String> personIds = visitMapper.selectPersonIds(visitNo);
    // 장비 비활성화(사용자 disable + 카드 제거)가 성공해야 퇴실 커밋 — 실패한 채 DB 만 회수하면
    // 장비에서 계속 출입 가능 + 카드 재대여로 이중 사용이 되므로 롤백하고 재시도를 유도한다
    String warn = visitBiostar.disableVisitors(personIds);
    if (warn != null) {
      auditService.logAlways(
          actor, AuditService.UPDATE, menuId, "방문 퇴실 실패(" + visitNo + "): " + warn);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 비활성화 실패로 퇴실이 취소되었습니다. 사유: " + warn + " — 다시 시도하세요.");
    }
    for (String pid : personIds) {
      cardMapper.releaseByPerson(pid); // 카드 재대여 가능하도록 DB 회수
    }
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      cardMapper.releaseByCar(carId);
    }
    visitMapper.updateStatus(visitNo, STATUS_LEFT); // VS04 퇴실 완료
    auditService.log(actor, AuditService.UPDATE, menuId, "방문 퇴실: " + visitNo);
    return warn;
  }

  TbVisit toRow(VisitForm form) {
    TbVisit r = new TbVisit();
    r.setVisitNo(form.getVisitNo());
    r.setVisitType(form.getVisitType());
    r.setStatusCode(form.getStatusCode());
    r.setWorkPurpose(form.getWorkPurpose());
    r.setPermitDt(blankToNull(form.getPermitDt()));
    r.setWorkStartDt(withSeconds(form.getWorkStartDt()));
    r.setWorkEndDt(withSeconds(form.getWorkEndDt()));
    r.setCompanyType(form.getCompanyType());
    r.setCompanyName(form.getCompanyName());
    r.setReceiver(form.getReceiver());
    r.setReturner(form.getReturner());
    r.setRemark(form.getRemark());
    return r;
  }

  private void validate(VisitForm form) {
    require(form.getVisitType(), "방문유형");
    require(form.getCompanyName(), "업체명");
    boolean hasVisitors = form.getVisitors() != null && !form.getVisitors().isEmpty();
    boolean hasCars =
        form.getCars() != null
            && form.getCars().stream()
                .anyMatch(c -> c.getCarNo() != null && !c.getCarNo().isBlank());
    // 출입그룹을 선택했으면 대상(방문객/차량) 입력 강제, 방문객이 있으면 인솔자 필수
    check(notEmpty(form.getAcGroupIds()) && !hasVisitors, "사용자 출입그룹을 선택하면 방문객을 입력해야 합니다.");
    check(notEmpty(form.getCarAcCodes()) && !hasCars, "차량 출입그룹을 선택하면 차량을 입력해야 합니다.");
    check(hasVisitors && !notEmpty(form.getManagerIds()), "방문객이 있으면 인솔자를 지정해야 합니다.");
  }

  /** 임시(PT02)끼리 인솔자 겹침 금지 — 진행중 다른 임시 방문의 인솔자면 차단(임시↔장기·상주, 장기끼리는 허용). */
  void checkManagerOverlap(VisitForm form, Integer excludeVisitNo) {
    if (!VISIT_TYPE.equals(form.getVisitType()) || !notEmpty(form.getManagerIds())) {
      return;
    }
    List<String> dup = visitMapper.selectActiveTempManagers(form.getManagerIds(), excludeVisitNo);
    check(notEmpty(dup), "이미 진행 중인 임시 방문의 인솔자입니다(임시끼리 중복 불가): " + String.join(", ", dup));
  }

  /** 방문 상태 — 방문객이 있고 전원 카드 발급이면 '입실 중'(VS03)으로 승격. 퇴실완료는 유지. base 는 서버가 정한다. */
  private static String effectiveStatus(String base, VisitForm form) {
    List<VisitorForm> vs = form.getVisitors();
    boolean allCarded =
        vs != null && !vs.isEmpty() && vs.stream().allMatch(v -> v.getCardId() != null);
    return (allCarded && !STATUS_LEFT.equals(base)) ? STATUS_ENTERED : base;
  }

  static void require(String v, String label) {
    check(v == null || v.isBlank(), label + "은(는) 필수입니다.");
  }

  /** 조건이 참이면 입력 오류. */
  private static void check(boolean bad, String message) {
    if (bad) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
  }

  static boolean notEmpty(List<?> l) {
    return l != null && !l.isEmpty();
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }

  /** datetime-local("YYYY-MM-DDTHH:mm")에 초를 채운다 — datetime2 변환 오류 방지. */
  static String withSeconds(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    String t = v.trim();
    return t.length() == 16 ? t + ":00" : t;
  }

  static String encryptOrNull(String plain) {
    return (plain == null || plain.isBlank()) ? null : ARIAUtil.ariaEncrypt(plain);
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }

  /** 방문 상세(수정 모달용) — 그룹 + 자식 목록. */
  public static class VisitDetail {
    public TbVisit visit;
    public List<VisitorForm> managers;
    public List<Integer> acGroupIds;
    public List<String> carAcCodes;
    public List<VisitorForm> visitors;
    public List<VisitCarForm> cars;
  }
}
