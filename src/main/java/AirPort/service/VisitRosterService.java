package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
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

  /** 공통코드(PIP)에 접두가 없을 때 쓰는 값 — 기존 방문객 ID 체계와 같은 IS. */
  private static final String DEFAULT_ID_PREFIX = "IS";

  private final TbVisitMapper visitMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;
  private final CardService cardService;
  private final CardIssueService cardIssue;
  private final VisitBiostarService visitBiostar;
  private final ParkingPassService parkingPass;
  private final AuditService auditService;

  public VisitRosterService(
      TbVisitMapper visitMapper,
      TbPersonMapper personMapper,
      TbCarMapper carMapper,
      TbCardMapper cardMapper,
      TbCommonMapper commonMapper,
      CardService cardService,
      CardIssueService cardIssue,
      VisitBiostarService visitBiostar,
      ParkingPassService parkingPass,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
    this.cardService = cardService;
    this.cardIssue = cardIssue;
    this.visitBiostar = visitBiostar;
    this.parkingPass = parkingPass;
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
    if (!form.managerIds().isEmpty()) {
      visitMapper.insertManagers(
          visitNo, AirPort.model.VisitManagerForm.encrypted(form.getManagers()));
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
    // 회수 전에 방문객별 보유 카드를 읽어 둔다 — 자기가 이미 들고 있던 카드는 발급 제한에서 뺀다
    java.util.Map<String, java.util.Set<Integer>> heldByVisitor = new java.util.HashMap<>();
    for (String pid : keptIds) {
      heldByVisitor.put(pid, cardIssue.heldCardIds(pid));
    }
    visitMapper.deletePersons(visitNo);
    List<String> personIds = new ArrayList<>();
    int carded = 0; // 카드를 받은 방문객 수 — 전원 발급이라야 BiostarX 에 올린다
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
          // 정상이 아닌 카드(분실·정지·반납·폐기)는 발급하지 않는다 — 장비에서 차단돼 문이 열리지 않는다
          cardIssue.requireIssuable(vf.getCardId(), heldByVisitor.get(pid), "방문객 " + pid);
          // 장비 미등록 카드면 지금 등록 — 실패하면 예외로 저장이 취소된다(문 안 열리는 카드 방지)
          cardService.ensureBiostarCard(vf.getCardId(), actor, menuId);
          cardMapper.assignPerson(vf.getCardId(), pid);
          visitMapper.updateVisitorLastCard(visitNo, pid, vf.getCardId()); // 마지막 카드 스냅샷
          carded++;
        }
        personIds.add(pid);
      }
    }
    // 방문 차량(tb_car) — 전체 재구성(기존 차량 카드 회수·소프트삭제 후 새로 발급)
    // 재구성하면 예전 차량번호를 알 수 없게 되므로, 지우기 전에 읽어 둔다(주차 정기권 회수 대상 판정용)
    java.util.Set<String> parkedBefore = parkingPass.visitCarNos(visitNo);
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      cardMapper.releaseByCar(carId);
      carMapper.softDelete(carId);
    }
    visitMapper.deleteCars(visitNo);
    java.util.Set<String> usedCarNos = new java.util.HashSet<>(); // 한 방문에 같은 차량은 한 번만
    if (form.getCars() != null) {
      for (VisitCarForm cf : form.getCars()) {
        if (cf.getCarNo() == null || cf.getCarNo().isBlank()) {
          continue; // 차량은 선택 — 번호 없는 행은 저장하지 않는다
        }
        // 같은 차량을 두 줄로 넣으면 카드가 갈리고 출입 이력도 나뉜다. 공백·대소문자 차이는 같은 번호로 본다.
        String key = cf.getCarNo().replaceAll("\\s+", "").toUpperCase();
        if (!usedCarNos.add(key)) {
          throw new BusinessException(
              ErrorCode.INVALID_INPUT,
              "같은 차량번호(" + cf.getCarNo().trim() + ")를 두 번 등록할 수 없습니다. 중복된 차량 행을 지우세요.");
        }
        int carId = insertVisitCar(cf, form);
        visitMapper.insertCar(visitNo, carId);
        if (cf.getCardId() != null) {
          // 차량 카드도 정상 상태만 발급한다(차량은 매번 새로 만들므로 보유 예외 없음)
          cardIssue.requireIssuable(cf.getCardId(), null, "차량 " + cf.getCarNo().trim());
          if (!usedCards.add(cf.getCardId())) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT, "같은 카드를 두 대상에게 발급할 수 없습니다. 대상별로 다른 카드를 선택하세요.");
          }
          cardMapper.assignCar(cf.getCardId(), carId);
        }
      }
    }
    // 주차 차단기 — 차량구역이 붙은 차량은 정기권을 다시 등록하고, 빠진 차량은 회수한다.
    // 실패해도 저장을 취소하지 않는다(차단기는 부가 기능) — 경고로 올려 놓친 차량이 드러나게 한다.
    String parkWarn = parkingPass.syncVisit(visitNo, form, parkedBefore, actor, menuId);
    // 전원 카드를 받아야 BiostarX 에 올린다.
    // 한 명이라도 카드가 없으면 '신청'으로 남는데, 그 상태에서 장비에 사용자가 올라가 있으면
    // 삭제는 "BiostarX 에 등록된 방문객이 있다"고 막히고 퇴실은 '입실 중'이 아니라 못 해 방문이 갇힌다.
    if (personIds.isEmpty() || carded < personIds.size()) {
      return join(
          parkWarn,
          personIds.isEmpty()
              ? null
              : "카드를 받지 않은 방문객이 "
                  + (personIds.size() - carded)
                  + "명 있어 신청 상태로 두었습니다. 전원 카드를 발급하면 입실 중으로 바뀌고 BiostarX 에 등록됩니다.");
    }
    // BiostarX 방문객 동기화(PT→PTD code_tag 부모 그룹 + 선택 출입그룹)
    String fail = visitBiostar.syncVisitors(form.getVisitType(), personIds, form.getAcGroupIds());
    if (fail == null) {
      return parkWarn;
    }
    // 장비에 못 올렸으면 입실로 볼 수 없다. 상태만 '입실 중'으로 바뀌면 카드가 문을 열지 못하는데도
    // 처리가 끝난 것처럼 보인다 — 신청으로 되돌려 무엇이 남았는지 드러낸다.
    visitMapper.updateStatus(visitNo, VisitService.DEFAULT_STATUS);
    auditService.logAlways(
        actor,
        AuditService.UPDATE,
        menuId,
        "BiostarX 동기화 실패로 신청 상태 유지(방문 " + visitNo + "): " + fail);
    return join(
        parkWarn, "BiostarX 동기화에 실패해 '신청' 상태로 두었습니다. 사유: " + fail + " — 원인을 해결한 뒤 다시 저장하세요.");
  }

  /** 경고 문구 합치기 — 둘 다 없으면 null(성공). 화면은 문구 하나만 띄운다. */
  private static String join(String first, String second) {
    if (first == null) {
      return second;
    }
    return second == null ? first : first + "\n" + second;
  }

  /** 방문객 tb_person 저장 — personId 있으면 갱신(기존 인원 유지), 없으면 IS 채번 신규. (키오스크 재사용) */
  public String upsertVisitor(VisitorForm vf, VisitForm form) {
    VisitService.require(vf.getPersonName(), "방문객 성명");
    boolean isNew = vf.getPersonId() == null || vf.getPersonId().isBlank();
    TbPerson p = new TbPerson();
    p.setPersonId(
        isNew ? personMapper.selectNextVisitorId(idPrefix(form.getVisitType())) : vf.getPersonId());
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
        // 동시 채번(MAX+1) 충돌 — 1회 재채번 후 재시도
        p.setPersonId(personMapper.selectNextVisitorId(idPrefix(form.getVisitType())));
        personMapper.insert(p);
      }
    } else {
      personMapper.update(p);
    }
    return p.getPersonId();
  }

  /**
   * 방문유형별 인원ID 접두 — 공통코드 {@code PIP}(임시 IS / 장기 LT / 상주 RS)에서 읽는다.
   *
   * <p>유형이 늘어도 코드만 추가하면 되고, 코드가 없으면 임시(IS)로 떨어뜨려 채번이 멈추지 않게 한다. <b>ID 는 발급 후 바꾸지 않는다</b> — BiostarX
   * 사용자ID 와 같은 키라서, 나중에 유형이 바뀌어도 접두는 최초 등록 유형을 뜻한다.
   */
  private String idPrefix(String visitType) {
    if (visitType == null || visitType.isBlank()) {
      return DEFAULT_ID_PREFIX;
    }
    AirPort.model.TbCommon code = commonMapper.selectOne("PIP", visitType);
    return (code == null || code.getCodeName() == null || code.getCodeName().isBlank())
        ? DEFAULT_ID_PREFIX
        : code.getCodeName().trim();
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

  /**
   * 방문의 방문객/차량을 정리 — 카드 회수 후 인원 소프트삭제·차량 소프트삭제.
   *
   * <p>차량이 사라지면 주차 차단기도 함께 닫는다. 이걸 빼면 방문을 지워도 정기권이 남아 그 차는 계속 들어올 수 있다.
   */
  public void clearRoster(int visitNo, TbLoginUser actor, Integer menuId) {
    parkingPass.removeAll(
        "방문 " + visitNo, parkingPass.visitCarNos(visitNo), visitNo, null, actor, menuId);
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
