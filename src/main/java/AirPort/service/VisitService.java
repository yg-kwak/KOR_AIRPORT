package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.VisitKinds;
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
import AirPort.model.VisitManagerForm;
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

  // 방문 상태(tb_common VS) — 신청(삭제 가능) / 입실중(전원 카드 시 자동 승격) / 퇴실완료(되돌림 없음)
  static final String DEFAULT_STATUS = "VS01";
  private static final String STATUS_ENTERED = "VS03";
  static final String STATUS_LEFT = "VS04";

  /** 작업기간이 끝났는데 카드를 반납하지 않은 상태 — 입실 중과 같이 다루되(카드 보유) 회수가 밀렸다는 표시. */
  private static final String STATUS_UNRETURNED = "VS05";

  /** 카드를 들고 있는 상태 — 퇴실로만 벗어난다. */
  static boolean holding(String status) {
    return STATUS_ENTERED.equals(status) || STATUS_UNRETURNED.equals(status);
  }

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
    for (TbVisit row : rows) {
      row.setManagerName(decrypt(row.getManagerName())); // 성명은 ARIA 암호문이라 SQL 이 풀지 못한다
      // 소속 판정은 한 곳에서만 한다 — SQL 에 옮겨 적으면 화면마다 다른 소속이 보이기 시작한다
      row.setManagerAffiliation(
          AirPort.common.Affiliations.of(row.getManagerAffiliation(), row.getManagerCompanyName()));
    }
    auditService.log(actor, AuditService.READ, menuId, "방문 목록 조회 (결과 " + total + "건)");
    // 미반납(카드 미회수)은 목록을 넘겨 가며 셀 값이 아니다 — 같은 조건 전체에서 세어 위에 늘 보여 준다
    return new AirPort.model.VisitPageResult(
        rows, total, param.getPage(), param.getSize(), visitMapper.selectUnreturnedCount(param));
  }

  /** 인솔자 후보(정규인원 PT01) — 성명 복호화. */
  public List<TbPerson> searchManagers(String keyword, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return searchManagersPublic(keyword);
  }

  /**
   * 인솔자 후보(무인증 키오스크 겸용) — 빈 검색어=결과 없음, ID 부분일치·성명 완전일치만(명단 훑기 방지), 최대 50건.
   *
   * <p>비활성 상태(정지·퇴사·회수·분실)는 조회에서 이미 빠진다 — 출입이 막힌 사람을 인솔자로 세울 수 없다. 판정 기준은 공통코드
   * `tb_common(PS).code_tag='true'` 이고 mapper 가 들고 있다.
   *
   * <p>소속을 함께 내려 준다. 성명 완전일치로 찾으므로 <b>동명이인이 나란히 나오는 일이 흔하고</b>, 그때 누구를 고를지 가릴 단서가 소속뿐이다. 화면 표기 전용이라
   * 방문에 저장하지는 않는다.
   */
  public List<TbPerson> searchManagersPublic(String keyword) {
    String kw = keyword == null ? "" : keyword.trim();
    List<TbPerson> rows = new ArrayList<>();
    if (kw.isEmpty()) return rows;
    for (TbPerson p : personMapper.selectRegular()) {
      p.setPersonName(decrypt(p.getPersonName()));
      boolean hit = p.getPersonId().contains(kw) || kw.equals(p.getPersonName());
      if (hit && rows.size() < 50) {
        // 자유입력 소속 우선, 없으면 기관명 — 실시간 이벤트 화면과 같은 규칙(Affiliations)
        p.setAffiliation(AirPort.common.Affiliations.of(p));
        rows.add(p);
      }
    }
    return rows;
  }

  /** 단건 상세 — 그룹 + 인솔자/방문객/차량/출입그룹 로드(수정 모달용). */
  public VisitDetail detail(int visitNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return detailOf(visitNo);
  }

  /**
   * 상세 본문 — 권한 판정 없이 읽는다(키오스크 겸용, package-private).
   *
   * <p>키오스크는 메뉴 권한이 아니라 <b>"그 방문의 인솔자인가"</b>로 판정한다. 그 판정은 호출자가 먼저 하고 여기로 온다.
   */
  VisitDetail detailOf(int visitNo) {
    TbVisit visit = visitMapper.selectById(visitNo);
    if (visit == null || "Y".equals(visit.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    VisitDetail d = new VisitDetail();
    d.visit = visit;
    d.managers = new ArrayList<>();
    for (AirPort.model.TbVisitManager m : visitMapper.selectManagers(visitNo)) {
      VisitorForm mf = new VisitorForm();
      mf.setPersonId(m.getPersonId());
      TbPerson mp = personMapper.selectById(m.getPersonId());
      mf.setPersonName(mp != null ? decrypt(mp.getPersonName()) : "");
      // 소속은 표기 전용 — 방문에 저장하지 않는다. 동명이인이 인솔자로 나란히 있을 때 누구인지 가릴 단서다
      mf.setAffiliation(AirPort.common.Affiliations.of(mp));
      // 그 방문에 적어 둔 연락처 — 정규인원의 번호가 아니다
      mf.setPhone(decrypt(m.getManagerPhone()));
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
          f.setCardName(pc.get(0).getCardName()); // 화면은 번호가 아니라 명칭을 보여준다
        }
        f.setLastCardNo(visitMapper.selectVisitorLastCard(visitNo, pid)); // 회수 후에도 보존된 마지막 카드
        f.setLastCardName(visitMapper.selectVisitorLastCardName(visitNo, pid));
        f.setCheckoutDt(visitMapper.selectVisitorCheckout(visitNo, pid)); // 값이 있으면 퇴실(카드 재발급 불가)
        f.setBiostarUserId(p.getBiostarUserId()); // 장비에 실제 생성된 뒤에만 값이 있다(화면 인원ID 표시 기준)
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
        f.setAffiliation(c.getAffiliation());
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
    // 카드를 들고 있는 동안(입실 중·미반납)엔 카드 '교환'만 허용 — 카드 회수(빈 카드)나 방문객 제외는 퇴실 처리로만 가능.
    // 단, 이미 개별 퇴실한 방문객은 카드가 없는 게 정상이므로 이 검사에서 뺀다(빼지 않으면 카드 교체가 아예 막힌다).
    if (holding(existing.getStatusCode())) {
      if (VisitKinds.CAR.equals(form.getVisitKind())) {
        // 차량만인 방문은 차량 카드가 입실의 근거다 — 같은 규칙을 차량에 적용한다(차량은 매번 다시 만들므로 대수로 본다)
        boolean carNoCard =
            form.getCars() == null || form.getCars().stream().anyMatch(c -> c.getCardId() == null);
        check(
            carNoCard || form.getCars().size() < visitMapper.selectCarIds(form.getVisitNo()).size(),
            "입실 중인 방문은 카드 교환만 가능합니다. 차량 카드 회수·차량 제외는 퇴실 처리로 해주세요.");
      } else {
        boolean noCard =
            form.getVisitors() == null
                || form.getVisitors().stream()
                    .anyMatch(
                        vf ->
                            vf.getCardId() == null
                                && visitMapper.selectVisitorCheckout(
                                        form.getVisitNo(), vf.getPersonId())
                                    == null);
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
    // 신청(VS01) 상태만 삭제 가능 — 입실중/퇴실완료는 이력 보존
    if (!DEFAULT_STATUS.equals(v.getStatusCode())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "신청 상태의 방문만 삭제할 수 있습니다.");
    }
    // 여기까지 왔으면 '신청'이다 — 입실한 적이 없으니 지킬 장비 이력도 없다.
    // 전원 카드 발급 전에는 BiostarX 에 올리지 않으므로(VisitRosterService) 보통 지울 사용자도 없다.
    // 예전 방식으로 올라가 있던 방문객이 남아 있으면 여기서 함께 정리한다 — 그렇지 않으면
    // 삭제도 퇴실('입실 중'만 가능)도 안 되어 방문이 갇힌다.
    // 실패=롤백(장비 유령 사용자 방지)
    String warn =
        visitBiostar.deleteVisitors(v.getVisitType(), visitMapper.selectPersonIds(visitNo));
    if (warn != null) {
      auditService.logAlways(
          actor, AuditService.DELETE, menuId, "방문 삭제 실패(" + visitNo + "): " + warn);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "BiostarX 사용자 삭제 실패로 방문 삭제가 취소되었습니다. 사유: " + warn + " — 다시 시도하세요.");
    }
    roster.clearRoster(visitNo, actor, menuId); // 방문객/차량 정리(카드 회수·주차 정기권 회수 포함)
    visitMapper.deleteManagers(visitNo);
    visitMapper.deleteAcGroups(visitNo);
    visitMapper.deleteCarAcGroups(visitNo);
    visitMapper.softDelete(visitNo);
    auditService.log(actor, AuditService.DELETE, menuId, "방문 삭제: " + visitNo);
    return warn;
  }

  TbVisit toRow(VisitForm form) {
    TbVisit r = new TbVisit();
    r.setVisitNo(form.getVisitNo());
    r.setVisitType(form.getVisitType());
    r.setStatusCode(form.getStatusCode());
    r.setVisitKind(form.getVisitKind());
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
    // 키오스크와 같은 필수값을 쓴다 — 같은 방문인데 접수 창구에 따라 빈 칸이 갈리면 안 된다.
    // 작업기간은 방문객의 BiostarX 유효기간이 되므로 비면 상시 유효로 물러선다(문이 계속 열린다).
    require(form.getWorkStartDt(), "작업기간 시작");
    require(form.getWorkEndDt(), "작업기간 종료");
    require(form.getWorkPurpose(), "작업목적");
    boolean hasVisitors = form.getVisitors() != null && !form.getVisitors().isEmpty();
    boolean hasCars =
        form.getCars() != null
            && form.getCars().stream()
                .anyMatch(c -> c.getCarNo() != null && !c.getCarNo().isBlank());
    // 방문구분(인원/차량/인원+차량) — 고른 쪽은 있어야 하고 고르지 않은 쪽은 없어야 한다(규칙은 VisitKinds 한 곳)
    VisitKinds.check(
        form.getVisitKind(),
        hasVisitors,
        hasCars,
        notEmpty(form.getAcGroupIds()),
        notEmpty(form.getCarAcCodes()));
    // 출입그룹을 선택했으면 대상(방문객/차량) 입력 강제, 방문객이 있으면 인솔자 필수
    check(notEmpty(form.getAcGroupIds()) && !hasVisitors, "사용자 출입그룹을 선택하면 방문객을 입력해야 합니다.");
    check(notEmpty(form.getCarAcCodes()) && !hasCars, "차량 출입그룹을 선택하면 차량을 입력해야 합니다.");
    check(hasVisitors && form.managerIds().isEmpty(), "방문객이 있으면 인솔자를 지정해야 합니다.");
    // 연락처는 방문마다 손으로 적는다 — 정규인원 정보에서 당겨오지 않으므로 비면 신청서에 빈 칸이 남는다.
    // 키오스크도 같은 규칙을 쓴다(VisitManagerForm 에 모아 둠).
    VisitManagerForm.requirePhones(form.getManagers());
  }

  /** 임시(PT02)끼리 인솔자 겹침 금지 — 진행중 다른 임시 방문의 인솔자면 차단(임시↔장기·상주, 장기끼리는 허용). */
  void checkManagerOverlap(VisitForm form, Integer excludeVisitNo) {
    if (!VISIT_TYPE.equals(form.getVisitType()) || form.managerIds().isEmpty()) {
      return;
    }
    List<String> dup = visitMapper.selectActiveTempManagers(form.managerIds(), excludeVisitNo);
    check(notEmpty(dup), "이미 진행 중인 임시 방문의 인솔자입니다(임시끼리 중복 불가): " + String.join(", ", dup));
  }

  /**
   * 저장할 방문 상태 — 서버가 정한다(사용자가 고를 수 없다).
   *
   * <p>저장되는 값은 신청·입실 중·퇴실 완료 셋뿐이다. <b>미반납은 저장하지 않는다</b> — "작업기간이 끝났는데 아직 입실 중"은 시간이 지나면 저절로 참이 되는
   * 사실이라, 적어 두면 그 순간부터 DB 가 틀린 값을 들고 있게 된다. 조회할 때 계산한다(TbVisitMapper.statusExpr).
   *
   * <p>그래서 넘어온 base 가 미반납이면(화면이 계산된 값을 되돌려준다) 입실 중으로 되돌려 저장한다.
   */
  private static String effectiveStatus(String base, VisitForm form) {
    String stored = STATUS_UNRETURNED.equals(base) ? STATUS_ENTERED : base;
    if (STATUS_LEFT.equals(stored)) {
      return stored; // 퇴실 완료는 되돌리지 않는다
    }
    return allCarded(form) ? STATUS_ENTERED : stored;
  }

  /**
   * 전원 카드 발급 — 입실 중으로 올리는 조건.
   *
   * <p>사람이 있는 방문(인원·인원+차량)은 <b>방문객 전원</b>이 기준이다 — 차량 카드는 함께 있어도 없어도 사람이 다 들어갔으면 입실이다. <b>차량만</b>인
   * 방문은 사람이 없으니 <b>차량 전원</b>의 카드가 기준이다(그렇지 않으면 차량만인 방문은 영영 신청 상태에 머문다).
   */
  private static boolean allCarded(VisitForm form) {
    if (VisitKinds.CAR.equals(form.getVisitKind())) {
      List<VisitCarForm> cs = form.getCars();
      return cs != null && !cs.isEmpty() && cs.stream().allMatch(c -> c.getCardId() != null);
    }
    List<VisitorForm> vs = form.getVisitors();
    return vs != null && !vs.isEmpty() && vs.stream().allMatch(v -> v.getCardId() != null);
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
