package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbVisit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방문 퇴실 — 방문객 한 명씩, 또는 방문 전체. (임시인원등록·장기출입등록 공용)
 *
 * <p>{@link VisitService} 에서 떼어낸 조각이다. 그쪽은 등록·수정·삭제와 조회를 들고, 여기는 <b>내보내는 일</b>만 한다.
 *
 * <p>순서가 이 클래스의 전부다 — <b>BiostarX 비활성화가 성공해야</b> DB 카드를 회수한다. 실패한 채 회수하면 장비에서는 계속 열리는데 카드는 재대여돼
 * <b>이중 사용</b>이 된다. 그래서 실패는 예외로 올려 트랜잭션을 통째로 되돌린다.
 */
@Service
public class VisitCheckoutService {

  private final TbVisitMapper visitMapper;
  private final TbCardMapper cardMapper;
  private final VisitBiostarService visitBiostar;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public VisitCheckoutService(
      TbVisitMapper visitMapper,
      TbCardMapper cardMapper,
      VisitBiostarService visitBiostar,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.cardMapper = cardMapper;
    this.visitBiostar = visitBiostar;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /**
   * 방문객 개별 퇴실 — 카드를 발급받은 방문객은 행에서 뺄 수 없으므로 이 방식으로 내보낸다.
   *
   * <p>퇴실 기록이 남으면 그 방문객에게는 다시 카드를 줄 수 없다.
   *
   * @return 항상 null(성공) — 실패는 예외
   */
  @Transactional
  public String checkoutVisitor(int visitNo, String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    requireHolding(visitNo, "입실 중이거나 미반납인 방문의 방문객만 퇴실할 수 있습니다.");
    if (!visitMapper.selectPersonIds(visitNo).contains(personId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "이 방문의 방문객이 아닙니다.");
    }
    if (visitMapper.selectVisitorCheckout(visitNo, personId) != null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 퇴실한 방문객입니다.");
    }
    disableOrFail(List.of(personId), actor, menuId, "방문객 퇴실 실패(" + visitNo + "/" + personId + ")");
    cardMapper.releaseByPerson(personId); // 카드 회수(다른 사람이 재사용 가능)
    visitMapper.updateVisitorCheckout(visitNo, personId);
    auditService.log(actor, AuditService.UPDATE, menuId, "방문객 퇴실: " + visitNo + "/" + personId);
    // 마지막 한 명까지 나가면 방문도 끝난 것이다 — 한 명씩 내보낸 뒤 [퇴실]을 또 눌러야 끝나면
    // 잊기 쉽고, 그동안 방문은 입실 중(기간이 지났으면 미반납)으로 남는다.
    closeIfEmpty(visitNo, actor, menuId);
    return null;
  }

  /** 퇴실(입실중→퇴실완료) — BiostarX 사용자 비활성화 + 카드 제거, DB 카드 회수(재대여 가능). */
  @Transactional
  public String checkout(int visitNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    requireHolding(visitNo, "입실 중이거나 미반납인 방문만 퇴실할 수 있습니다.");
    List<String> personIds = visitMapper.selectPersonIds(visitNo);
    disableOrFail(personIds, actor, menuId, "방문 퇴실 실패(" + visitNo + ")");
    for (String pid : personIds) {
      cardMapper.releaseByPerson(pid); // 카드 재대여 가능하도록 DB 회수
      visitMapper.updateVisitorCheckout(visitNo, pid); // 개별 퇴실과 같은 표시(이미 퇴실이면 시각 유지)
    }
    releaseCars(visitNo);
    visitMapper.updateStatus(visitNo, VisitService.STATUS_LEFT); // VS04 퇴실 완료
    visitMapper.markCheckedOut(visitNo); // 정기 파기(1년)의 기준 시각
    auditService.log(actor, AuditService.UPDATE, menuId, "방문 퇴실: " + visitNo);
    return null;
  }

  /** 남은 방문객이 없으면 방문을 퇴실 완료로 마감한다 — 차량 카드 회수까지 방문 퇴실과 같게 처리한다. */
  private void closeIfEmpty(int visitNo, TbLoginUser actor, Integer menuId) {
    if (visitMapper.countStayingVisitors(visitNo) > 0) {
      return;
    }
    releaseCars(visitNo);
    visitMapper.updateStatus(visitNo, VisitService.STATUS_LEFT);
    visitMapper.markCheckedOut(visitNo); // 정기 파기(1년)의 기준 시각
    auditService.log(actor, AuditService.UPDATE, menuId, "방문 퇴실(마지막 방문객 퇴실로 자동): " + visitNo);
  }

  /** 사람이 다 나갔으면 방문 차량 카드도 놔 준다. */
  private void releaseCars(int visitNo) {
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      cardMapper.releaseByCar(carId);
    }
  }

  /** 카드를 들고 있는 방문인지 — 신청 상태에서는 방문객을 그냥 빼면 되고, 퇴실은 입실 이후의 절차다. */
  private void requireHolding(int visitNo, String notHoldingMessage) {
    TbVisit v = visitMapper.selectById(visitNo);
    if (v == null || "Y".equals(v.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (!VisitService.holding(v.getStatusCode())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, notHoldingMessage);
    }
  }

  /**
   * 장비 비활성화가 실패하면 <b>퇴실을 취소</b>한다.
   *
   * <p>DB 만 회수하면 장비에서는 계속 출입되는데 카드는 재대여돼 이중 사용이 된다. 실패는 감사에 남기고 예외로 올려 트랜잭션을 되돌린다.
   */
  private void disableOrFail(
      List<String> personIds, TbLoginUser actor, Integer menuId, String auditPrefix) {
    String warn = visitBiostar.disableVisitors(personIds);
    if (warn != null) {
      auditService.logAlways(actor, AuditService.UPDATE, menuId, auditPrefix + ": " + warn);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 비활성화 실패로 퇴실이 취소되었습니다. 사유: " + warn + " — 다시 시도하세요.");
    }
  }
}
