package AirPort.service;

import AirPort.mapper.TbVisitMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 방문 데이터 정기 파기 — 퇴실 완료 후 보존기간이 지난 방문을 지운다. (개인정보 파기)
 *
 * <p><b>되돌릴 수 없다.</b> 소프트 삭제가 아니라 물리 삭제이며 BiostarX 사용자도 함께 지운다. 파기가 목적이라 행을 남기면 의미가 없다.
 *
 * <p>대상은 <b>퇴실 완료(VS04)</b> 뿐이다 — 신청 상태로 삭제된 방문은 건드리지 않는다. 방문 명단에서 빠져 어디에도 속하지 않은 방문객(소프트 삭제분)은
 * 개인정보만 남아 있으므로 같은 보존기간을 적용해 함께 지운다.
 *
 * <p>실제 삭제는 {@link VisitPurgeItemService} 가 방문 1건씩 트랜잭션으로 처리한다. 한 건이 막혀도 나머지는 진행하고, 막힌 건은 다음 회차에 다시
 * 시도된다.
 *
 * <p><b>처음에는 미리보기(dry-run)로 돈다.</b> 조건이 의도대로 걸리는지 실제 데이터로 확인한 뒤 {@code
 * app.visit.purge.dry-run=false} 로 켠다.
 */
@Service
public class VisitPurgeService {

  private static final Logger log = LoggerFactory.getLogger(VisitPurgeService.class);

  private final TbVisitMapper visitMapper;
  private final VisitPurgeItemService item;
  private final AuditService auditService;

  private final int keepDays;
  private final int limit;
  private final boolean dryRun;

  public VisitPurgeService(
      TbVisitMapper visitMapper,
      VisitPurgeItemService item,
      AuditService auditService,
      @Value("${app.visit.purge.keep-days:365}") int keepDays,
      @Value("${app.visit.purge.limit:200}") int limit,
      @Value("${app.visit.purge.dry-run:true}") boolean dryRun) {
    this.visitMapper = visitMapper;
    this.item = item;
    this.auditService = auditService;
    this.keepDays = keepDays;
    this.limit = limit;
    this.dryRun = dryRun;
  }

  /** 매일 새벽 3시 10분. 장비를 호출하므로 업무 시간을 피한다. 주기는 {@code app.visit.purge.cron}. */
  @Scheduled(cron = "${app.visit.purge.cron:0 10 3 * * *}")
  public void sweep() {
    run();
  }

  /**
   * 파기 1회 실행.
   *
   * <p>돌았다는 사실 자체가 기록으로 남아야 하므로 <b>0건이어도 감사 이력을 남긴다</b> — 배치가 멈춘 것과 지울 게 없는 것은 다르다.
   *
   * @return 지운 방문 수(미리보기면 0)
   */
  public int run() {
    List<Integer> targets = visitMapper.selectPurgeTargets(keepDays, limit);
    List<String> orphans = visitMapper.selectOrphanVisitorIds(keepDays, limit);

    if (dryRun) {
      record(targets.size(), orphans.size(), 0, targets, List.of(), true);
      return 0;
    }

    int visits = 0;
    int persons = 0;
    List<String> failed = new ArrayList<>();
    for (Integer visitNo : targets) {
      try {
        persons += item.purgeVisit(visitNo);
        visits++;
      } catch (RuntimeException e) {
        log.warn("방문 파기 실패({}) — 다음 회차 재시도: {}", visitNo, e.toString());
        failed.add(String.valueOf(visitNo));
      }
    }
    int orphansPurged = 0;
    for (String personId : orphans) {
      try {
        orphansPurged += item.purgeOrphan(personId);
      } catch (RuntimeException e) {
        log.warn("잔여 방문객 파기 실패({}) — 다음 회차 재시도: {}", personId, e.toString());
        failed.add(personId);
      }
    }
    record(visits, orphansPurged, persons, targets, failed, false);
    return visits;
  }

  /**
   * 실행 이력 — 화면에서 들어온 행위가 아니라 배치가 한 일이라 {@code actor=null}(SYSTEM)·{@code menuId=null} 로 남긴다. 감사추적은
   * 이 조합을 <b>[시스템]</b> 으로 보여준다.
   */
  private void record(
      int visits,
      int orphans,
      int persons,
      List<Integer> targets,
      List<String> failed,
      boolean preview) {
    StringBuilder sb = new StringBuilder("방문 데이터 정기 파기");
    if (preview) {
      sb.append("(미리보기 — 실제로 지우지 않음)");
    }
    sb.append(" — 보존 ")
        .append(keepDays)
        .append("일, 방문 ")
        .append(visits)
        .append(preview ? "건 대상" : "건 삭제")
        .append(", 잔여 방문객 ")
        .append(orphans)
        .append(preview ? "명 대상" : "명 삭제");
    if (!preview && persons > 0) {
      sb.append(" (방문 소속 방문객 ").append(persons).append("명 포함)");
    }
    if (!targets.isEmpty()) {
      sb.append(" [방문번호 ").append(brief(targets)).append("]");
    }
    if (!failed.isEmpty()) {
      sb.append(" 실패 ").append(failed.size()).append("건(다음 회차 재시도)");
    }
    String detail = sb.toString();
    auditService.log(null, AuditService.PURGE, null, detail);
    log.info(detail);
  }

  /** 방문번호가 많으면 앞쪽만 — 이력 한 줄이 지나치게 길어지지 않게. */
  private static String brief(List<Integer> visitNos) {
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
