package AirPort.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 시스템 자신의 사건을 감사에 남긴다 — 기동과 종료.
 *
 * <p>서비스가 언제 내려갔다 올라왔는지는 장애를 되짚을 때 가장 먼저 보는 정보다. 로그 파일에도 남지만 파일은 보관 기간이 짧고 현장에서 열어보기 번거롭다. 감사추적에서
 * <b>메뉴 [시스템]</b> 으로 골라 보면 기동·종료·자동 파기가 시간순으로 이어진다.
 *
 * <p>강제 종료(작업 관리자·전원 차단)에서는 종료 기록이 남지 않는다 — 그때는 다음 기동 기록만 있고 그 사이가 비어 있는 것으로 안다.
 */
@Service
public class SystemEventService {

  private static final Logger log = LoggerFactory.getLogger(SystemEventService.class);

  private final AuditService auditService;

  public SystemEventService(AuditService auditService) {
    this.auditService = auditService;
  }

  /** 기동 완료 — 요청을 받을 준비가 끝난 시점. */
  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    record(AuditService.STARTUP, "시스템 시작");
  }

  /** 종료 시작 — DB 연결이 아직 살아 있을 때 남긴다. */
  @EventListener(ContextClosedEvent.class)
  public void onShutdown() {
    record(AuditService.SHUTDOWN, "시스템 종료");
  }

  /** 감사 기록이 실패해도 기동·종료를 막지 않는다 — 서비스가 뜨지 못하면 그게 더 큰 문제다. */
  private void record(String actionType, String detail) {
    try {
      auditService.log(null, actionType, null, detail);
    } catch (RuntimeException e) {
      log.warn("{} 감사 기록 실패: {}", detail, e.toString());
    }
  }
}
