package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbCar;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.VisitCarForm;
import AirPort.model.VisitForm;
import AirPort.model.VisitorForm;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 방문 로스터(인솔자·방문객·차량·출입그룹) 저장 전담 — {@link VisitService} 의 저장 하위 계층. (docs/backend.md)
 *
 * <p>방문객은 tb_person(person_type=방문유형), 차량은 tb_car 로 만들어 붙인다. 호출자의 트랜잭션에 참여한다 (별도 @Transactional 없음
 * — VisitService/KioskVisitService 가 경계를 연다).
 */
@Service
public class VisitRosterService {

  private final TbVisitMapper visitMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final TbCardMapper cardMapper;
  private final CardService cardService;
  private final VisitBiostarService visitBiostar;
  private final AuditService auditService;

  public VisitRosterService(
      TbVisitMapper visitMapper,
      TbPersonMapper personMapper,
      TbCarMapper carMapper,
      TbCardMapper cardMapper,
      CardService cardService,
      VisitBiostarService visitBiostar,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.cardMapper = cardMapper;
    this.cardService = cardService;
    this.visitBiostar = visitBiostar;
    this.auditService = auditService;
  }

  /**
   * 자식(인솔자·방문객·차량·출입그룹) 전체 재구성 저장. return=BiostarX 동기화 경고(성공/미대상 null).
   *
   * <p>카드를 부여할 때 장비 미등록 카드면 먼저 BiostarX 에 등록한다(실패 시 예외로 저장 취소).
   */
  public String saveChildren(int visitNo, VisitForm form, TbLoginUser actor, Integer menuId) {
    // 인솔자
    visitMapper.deleteManagers(visitNo);
    if (VisitService.notEmpty(form.getManagerIds())) {
      visitMapper.insertManagers(visitNo, form.getManagerIds());
    }
    // 사용자출입그룹 / 차량출입그룹
    visitMapper.deleteAcGroups(visitNo);
    if (VisitService.notEmpty(form.getAcGroupIds())) {
      visitMapper.insertAcGroups(visitNo, form.getAcGroupIds());
    }
    visitMapper.deleteCarAcGroups(visitNo);
    if (VisitService.notEmpty(form.getCarAcCodes())) {
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
    List<String> removed = new ArrayList<>();
    for (String pid : visitMapper.selectPersonIds(visitNo)) {
      if (!keptIds.contains(pid)) {
        // 카드 보유자 제거는 화면에서 막는다(카드 [선택] → [카드 없음] 이 먼저). 여기서 서버가 또 막으면
        // '해제 + 제거' 를 한 번에 저장하는 정상 흐름까지 거부되므로, 남아 있는 카드는 회수만 하고 진행한다.
        cardMapper.releaseByPerson(pid);
        personMapper.softDelete(pid);
        removed.add(pid);
      }
    }
    // 빠진 방문객은 BiostarX 사용자도 지운다 — 하지 않으면 장비에만 남아 계속 출입이 가능하다.
    // 방문 삭제(VisitService.delete)와 같은 정책: 실패하면 저장 전체를 롤백하고 사유를 알린다.
    String delFail = visitBiostar.deleteVisitors(form.getVisitType(), removed);
    if (delFail != null) {
      auditService.logAlways(
          actor, AuditService.DELETE, menuId, "방문객 제거 실패(방문 " + visitNo + "): " + delFail);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "BiostarX 사용자 삭제 실패로 저장이 취소되었습니다. 사유: " + delFail + " — 다시 시도하세요.");
    }
    // 재구성 전에 퇴실 이력을 읽어 둔다(deletePersons 로 행이 지워지므로 그대로 되돌려 넣어야 한다)
    java.util.Map<String, String> checkouts = new java.util.HashMap<>();
    for (String pid : keptIds) {
      String dt = visitMapper.selectVisitorCheckout(visitNo, pid);
      if (dt != null) {
        checkouts.put(pid, dt);
      }
    }
    visitMapper.deletePersons(visitNo);
    List<String> personIds = new ArrayList<>();
    java.util.Set<Integer> usedCards = new java.util.HashSet<>(); // 한 실물 카드는 한 사람에게만
    if (form.getVisitors() != null) {
      for (VisitorForm vf : form.getVisitors()) {
        String pid = upsertVisitor(vf, form);
        String checkoutDt = checkouts.get(pid);
        visitMapper.insertPerson(visitNo, pid, checkoutDt);
        cardMapper.releaseByPerson(pid); // 이전 카드 해제 후 재배정
        if (vf.getCardId() != null && checkoutDt != null) {
          // 퇴실한 방문객에게는 다시 카드를 줄 수 없다(화면도 선택 버튼을 숨긴다)
          throw new BusinessException(
              ErrorCode.INVALID_INPUT, "퇴실한 방문객(" + pid + ")에게는 카드를 발급할 수 없습니다.");
        }
        if (vf.getCardId() != null) {
          if (!usedCards.add(vf.getCardId())) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT, "같은 카드를 두 명 이상에게 발급할 수 없습니다. 방문객별로 다른 카드를 선택하세요.");
          }
          // 장비 미등록 카드면 지금 등록 — 실패하면 예외로 저장이 취소된다(문 안 열리는 카드 방지)
          cardService.ensureBiostarCard(vf.getCardId(), actor, menuId);
          cardMapper.assignPerson(vf.getCardId(), pid);
          visitMapper.updateVisitorLastCard(visitNo, pid, vf.getCardId()); // 마지막 카드 스냅샷
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

  /** 방문객 tb_person 저장 — personId 있으면 갱신(기존 인원 유지), 없으면 IS 채번 신규. (키오스크 재사용) */
  public String upsertVisitor(VisitorForm vf, VisitForm form) {
    VisitService.require(vf.getPersonName(), "방문객 성명");
    boolean isNew = vf.getPersonId() == null || vf.getPersonId().isBlank();
    TbPerson p = new TbPerson();
    p.setPersonId(isNew ? personMapper.selectNextVisitorId() : vf.getPersonId());
    p.setPersonName(ARIAUtil.ariaEncrypt(vf.getPersonName()));
    p.setBirthDate(VisitService.encryptOrNull(vf.getBirthDate()));
    p.setAffiliation(vf.getAffiliation());
    p.setPersonType(form.getVisitType());
    p.setStatusCode("01");
    p.setAccessStartDt(VisitService.withSeconds(form.getWorkStartDt()));
    p.setAccessEndDt(VisitService.withSeconds(form.getWorkEndDt()));
    if (isNew) {
      try {
        personMapper.insert(p);
      } catch (org.springframework.dao.DataIntegrityViolationException e) {
        p.setPersonId(personMapper.selectNextVisitorId()); // 동시 채번(MAX+1) 충돌 — 1회 재채번 후 재시도
        personMapper.insert(p);
      }
    } else {
      personMapper.update(p);
    }
    return p.getPersonId();
  }

  /** 방문 차량 tb_car 신규 저장. (키오스크 재사용) */
  public int insertVisitCar(VisitCarForm cf, VisitForm form) {
    VisitService.require(cf.getCarNo(), "차량번호");
    TbCar c = new TbCar();
    c.setCarNo(cf.getCarNo());
    c.setCarName(cf.getCarName());
    c.setCarType(cf.getCarType());
    carMapper.insert(c);
    return c.getCarId();
  }

  /** 방문의 방문객/차량을 정리 — 카드 회수 후 인원 소프트삭제·차량 소프트삭제. */
  public void clearRoster(int visitNo) {
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
}
