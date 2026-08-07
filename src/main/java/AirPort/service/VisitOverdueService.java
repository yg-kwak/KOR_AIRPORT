package AirPort.service;

import AirPort.mapper.TbVisitMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미반납(VS05) 자동 전환 — 입실 중인데 작업기간이 끝난 방문을 찾아 상태를 바꾼다.
 *
 * <p><b>왜 주기 실행인가</b> — 목록을 그릴 때 계산하면 화면마다 결과가 달라지고, 검색·통계·감사에서 상태가 서로 어긋난다. 상태는 DB 한 곳에 적어 두고 모두가
 * 같은 값을 본다.
 *
 * <p><b>왜 BiostarX 를 건드리지 않는가</b> — 작업기간이 그대로 BiostarX 사용자 유효기간(start/expiry)이라 기간이 지나면 카드는 이미 문을
 * 열지 못한다. 미반납은 "출입을 막아야 한다"가 아니라 "카드를 돌려받아야 한다"는 표시다. 장비 정리(비활성화·카드 회수)는 실제 반납 시점인 퇴실 처리에서 한다. 야간에
 * 수백 건의 장비 호출을 실패 처리까지 해 가며 돌릴 이유가 없다.
 */
@Service
public class VisitOverdueService {

  private static final Logger log = LoggerFactory.getLogger(VisitOverdueService.class);

  private final TbVisitMapper visitMapper;
  private final AuditService auditService;

  public VisitOverdueService(TbVisitMapper visitMapper, AuditService auditService) {
    this.visitMapper = visitMapper;
    this.auditService = auditService;
  }

  /**
   * 매시 5분에 검사한다. 작업기간 종료는 분 단위라 한 시간 안에는 반영된다.
   *
   * <p>주기를 바꾸려면 {@code app.visit.overdue-cron}. 끄려면 {@code -}.
   */
  @Scheduled(cron = "${app.visit.overdue-cron:0 5 * * * *}")
  public void sweep() {
    int changed = markOverdue();
    if (changed > 0) {
      log.info("미반납 전환 {}건", changed);
    }
  }

  /**
   * 작업기간이 끝난 입실 중 방문을 미반납으로 바꾼다.
   *
   * @return 바뀐 방문 수
   */
  @Transactional
  public int markOverdue() {
    List<Integer> targets = visitMapper.selectOverdueEntered();
    if (targets.isEmpty()) {
      return 0;
    }
    int changed = visitMapper.markUnreturned();
    // 사람이 아니라 시스템이 바꾼 상태다 — 어느 방문이 왜 넘어갔는지 남겨야 나중에 설명할 수 있다.
    auditService.log(
        null, // actor 없음 → 감사 이력에 SYSTEM 으로 남는다
        AuditService.UPDATE,
        null,
        "작업기간 종료로 미반납 전환: " + changed + "건 (방문번호 " + join(targets) + ")");
    return changed;
  }

  /** 대상이 많을 때 이력이 지나치게 길어지지 않게 앞쪽만 남긴다. */
  private static String join(List<Integer> visitNos) {
    int shown = Math.min(visitNos.size(), 20);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < shown; i++) {
      sb.append(i == 0 ? "" : ", ").append(visitNos.get(i));
    }
    if (visitNos.size() > shown) {
      sb.append(" 외 ").append(visitNos.size() - shown).append("건");
    }
    return sb.toString();
  }
}
