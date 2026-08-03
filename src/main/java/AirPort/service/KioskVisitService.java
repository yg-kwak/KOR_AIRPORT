package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCommonMapper;
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
  private final AuditService auditService;

  public KioskVisitService(
      VisitService visitService,
      VisitRosterService roster,
      TbVisitMapper visitMapper,
      AcGroupService acGroupService,
      TbCommonMapper commonMapper,
      AuditService auditService) {
    this.visitService = visitService;
    this.roster = roster;
    this.visitMapper = visitMapper;
    this.acGroupService = acGroupService;
    this.commonMapper = commonMapper;
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
    req(form.getWorkStartDt(), "작업기간 시작");
    req(form.getWorkEndDt(), "작업기간 종료");
    req(form.getCompanyName(), "업체명");
    req(form.getWorkPurpose(), "작업목적");
    bad(!has(form.getManagerIds()), "인솔자를 선택하세요.");
    bad(!has(form.getAcGroupIds()), "방문구역을 선택하세요.");
    bad(visitors.isEmpty(), "방문객 성명을 1명 이상 입력하세요.");
    bad(has(form.getCarAcCodes()) && cars.isEmpty(), "차량구역을 선택하면 차량정보를 입력해야 합니다.");

    form.setVisitType(VisitService.VISIT_TYPE);
    visitService.checkManagerOverlap(form, null); // 임시끼리 인솔자 겹침 금지
    TbVisit row = visitService.toRow(form);
    row.setVisitType(VisitService.VISIT_TYPE);
    row.setStatusCode(VisitService.DEFAULT_STATUS); // 신청
    visitMapper.insert(row);
    int visitNo = row.getVisitNo();
    visitMapper.insertManagers(visitNo, form.getManagerIds());
    visitMapper.insertAcGroups(visitNo, form.getAcGroupIds());
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
