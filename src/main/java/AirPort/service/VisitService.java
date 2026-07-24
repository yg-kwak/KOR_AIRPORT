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

/**
 * 임시인원(방문) 등록 — 그룹(tb_visit) + 인솔자/방문객/차량/출입그룹. (docs/backend.md)
 *
 * <p>방문객은 tb_person(person_type=visit_type)로, 차량은 tb_car 로 저장한다. 출입그룹·카드는 정규와 같은 테이블을 재사용한다.
 * BiostarX 방문객 동기화(PT→PTD code_tag 부모 그룹 편입)는 {@link VisitBiostarService} 가 담당한다(어댑터 경계).
 */
@Service
public class VisitService {

  /** 방문 기본 상태 — tb_common(VS) 신청. */
  private static final String DEFAULT_STATUS = "VS01";

  /** 임시인원등록 방문유형 — 임시 고정(tb_common PT02). */
  private static final String VISIT_TYPE = "PT02";

  private final TbVisitMapper visitMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;
  private final VisitBiostarService visitBiostar;
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
      AcGroupService acGroupService,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
    this.visitBiostar = visitBiostar;
    this.acGroupService = acGroupService;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /**
   * 사용자출입그룹 트리 — 방문유형 구역범위(tb_common PT.code_remark)가 'Y'가 아니면 최상위 그룹만 노출한다. 임시(PT02)는
   * 최상위만(code_remark='N'). tb_ac_group 조회는 정규와 동일 트리를 재사용한다.
   */
  public List<TbAcGroup> acGroupTree(TbLoginUser actor, Integer menuId) {
    List<TbAcGroup> tree = acGroupService.tree(actor, menuId);
    TbCommon pt = commonMapper.selectOne("PT", VISIT_TYPE);
    boolean detail = pt != null && "Y".equals(pt.getCodeRemark());
    if (!detail) {
      for (TbAcGroup root : tree) {
        root.getChildren().clear(); // 최상위만 — 하위 세부트리 숨김
      }
    }
    return tree;
  }

  public PageResult<TbVisit> list(VisitSearchParam param, TbLoginUser actor, Integer menuId) {
    long total = visitMapper.selectCount(param);
    List<TbVisit> rows = visitMapper.selectList(param);
    auditService.log(actor, AuditService.READ, menuId, "방문 목록 조회 (결과 " + total + "건)");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  /** 인솔자 후보(정규인원 PT01) — 성명 복호화. */
  public List<TbPerson> searchManagers(String keyword, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    List<TbPerson> rows = personMapper.selectRegular(keyword);
    rows.forEach(p -> p.setPersonName(decrypt(p.getPersonName())));
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
        cardMapper.selectByPerson(pid).stream().findFirst().ifPresent(c -> f.setCardId(c.getCardId()));
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
        cardMapper.selectByCar(carId).stream().findFirst().ifPresent(k -> f.setCardId(k.getCardId()));
        d.cars.add(f);
      }
    }
    return d;
  }

  @Transactional
  public String create(VisitForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(form);
    TbVisit row = toRow(form);
    row.setStatusCode(form.getStatusCode() == null ? DEFAULT_STATUS : form.getStatusCode());
    visitMapper.insert(row);
    String warn = saveChildren(row.getVisitNo(), form);
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
    TbVisit existing = visitMapper.selectById(form.getVisitNo());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    visitMapper.update(toRow(form));
    String warn = saveChildren(form.getVisitNo(), form);
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
    // BiostarX 방문객 사용자 삭제(부모 그룹에서 제거) — 실패해도 방문 삭제는 진행
    String warn = visitBiostar.deleteVisitors(v.getVisitType(), visitMapper.selectPersonIds(visitNo));
    clearRoster(visitNo); // 방문객/차량 정리(카드 회수 포함)
    visitMapper.deleteManagers(visitNo);
    visitMapper.deleteAcGroups(visitNo);
    visitMapper.deleteCarAcGroups(visitNo);
    visitMapper.softDelete(visitNo);
    auditService.log(actor, AuditService.DELETE, menuId, "방문 삭제: " + visitNo);
    return warn;
  }

  /**
   * 자식(인솔자·방문객·차량·출입그룹) 저장 — 전체 재구성. 수정이면 기존 방문객/차량을 먼저 정리(소프트삭제+카드회수).
   *
   * @return BiostarX 방문객 동기화 경고(성공/미대상이면 null)
   */
  private String saveChildren(int visitNo, VisitForm form) {
    // 인솔자
    visitMapper.deleteManagers(visitNo);
    if (notEmpty(form.getManagerIds())) {
      visitMapper.insertManagers(visitNo, form.getManagerIds());
    }
    // 사용자출입그룹 / 차량출입그룹
    visitMapper.deleteAcGroups(visitNo);
    if (notEmpty(form.getAcGroupIds())) {
      visitMapper.insertAcGroups(visitNo, form.getAcGroupIds());
    }
    visitMapper.deleteCarAcGroups(visitNo);
    if (notEmpty(form.getCarAcCodes())) {
      visitMapper.insertCarAcGroups(visitNo, form.getCarAcCodes());
    }
    // 방문객(tb_person) — 유지 인원은 갱신, 폼에서 빠진 인원만 카드 회수 후 소프트삭제
    List<String> keptIds = new ArrayList<>();
    if (form.getVisitors() != null) {
      for (VisitorForm vf : form.getVisitors()) {
        if (vf.getPersonId() != null && !vf.getPersonId().isBlank()) {
          keptIds.add(vf.getPersonId());
        }
      }
    }
    for (String pid : visitMapper.selectPersonIds(visitNo)) {
      if (!keptIds.contains(pid)) {
        cardMapper.releaseByPerson(pid);
        personMapper.softDelete(pid);
      }
    }
    visitMapper.deletePersons(visitNo);
    List<String> personIds = new ArrayList<>();
    if (form.getVisitors() != null) {
      for (VisitorForm vf : form.getVisitors()) {
        String pid = upsertVisitor(vf, form);
        visitMapper.insertPerson(visitNo, pid);
        cardMapper.releaseByPerson(pid); // 이전 카드 해제 후 재배정
        if (vf.getCardId() != null) {
          cardMapper.assignPerson(vf.getCardId(), pid);
        }
        personIds.add(pid);
      }
    }
    // 방문 차량(tb_car) — 전체 재구성(기존 차량 카드 회수·소프트삭제 후 새로 발급)
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      cardMapper.releaseByCar(carId);
      carMapper.softDelete(carId);
    }
    visitMapper.deleteCars(visitNo);
    if (form.getCars() != null) {
      for (VisitCarForm cf : form.getCars()) {
        if (cf.getCarNo() == null || cf.getCarNo().isBlank()) {
          continue; // 차량은 선택 — 번호 없는 행은 저장하지 않는다
        }
        int carId = insertVisitCar(cf, form);
        visitMapper.insertCar(visitNo, carId);
        if (cf.getCardId() != null) {
          cardMapper.assignCar(cf.getCardId(), carId);
        }
      }
    }
    // BiostarX 방문객 동기화(PT→PTD code_tag 부모 그룹 + 선택 출입그룹) — 실패해도 저장은 유지
    return visitBiostar.syncVisitors(form.getVisitType(), personIds, form.getAcGroupIds());
  }

  /** 방문객 tb_person 저장 — personId 있으면 갱신(기존 인원 유지), 없으면 IS 채번 신규. */
  private String upsertVisitor(VisitorForm vf, VisitForm form) {
    require(vf.getPersonName(), "방문객 성명");
    boolean isNew = vf.getPersonId() == null || vf.getPersonId().isBlank();
    TbPerson p = new TbPerson();
    p.setPersonId(isNew ? personMapper.selectNextVisitorId() : vf.getPersonId());
    p.setPersonName(ARIAUtil.ariaEncrypt(vf.getPersonName()));
    p.setBirthDate(encryptOrNull(vf.getBirthDate()));
    p.setAffiliation(vf.getAffiliation());
    p.setPersonType(form.getVisitType());
    p.setStatusCode("01");
    p.setAccessStartDt(withSeconds(form.getWorkStartDt()));
    p.setAccessEndDt(withSeconds(form.getWorkEndDt()));
    if (isNew) {
      personMapper.insert(p);
    } else {
      personMapper.update(p);
    }
    return p.getPersonId();
  }

  private int insertVisitCar(VisitCarForm cf, VisitForm form) {
    require(cf.getCarNo(), "차량번호");
    TbCar c = new TbCar();
    c.setCarNo(cf.getCarNo());
    c.setCarName(cf.getCarName());
    c.setCarType(cf.getCarType());
    carMapper.insert(c);
    return c.getCarId();
  }

  /** 방문의 방문객/차량을 정리 — 카드 회수 후 인원 소프트삭제·차량 소프트삭제. */
  private void clearRoster(int visitNo) {
    for (String pid : visitMapper.selectPersonIds(visitNo)) {
      cardMapper.releaseByPerson(pid);
      personMapper.softDelete(pid);
    }
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      cardMapper.releaseByCar(carId);
      carMapper.softDelete(carId);
    }
    visitMapper.deletePersons(visitNo);
    visitMapper.deleteCars(visitNo);
  }

  private TbVisit toRow(VisitForm form) {
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
  }

  private static void require(String v, String label) {
    if (v == null || v.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, label + "은(는) 필수입니다.");
    }
  }

  private static boolean notEmpty(List<?> l) {
    return l != null && !l.isEmpty();
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }

  /** datetime-local("YYYY-MM-DDTHH:mm")에 초를 채운다 — datetime2 변환 오류 방지. */
  private static String withSeconds(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    String t = v.trim();
    return t.length() == 16 ? t + ":00" : t;
  }

  private static String encryptOrNull(String plain) {
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
