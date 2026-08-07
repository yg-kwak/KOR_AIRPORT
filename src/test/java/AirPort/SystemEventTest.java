package AirPort;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import AirPort.service.AuditService;
import AirPort.service.SystemEventService;
import org.junit.jupiter.api.Test;

/**
 * 시스템 기동·종료 감사 검증 — 감사추적의 메뉴 [시스템] 은 이 기록과 자동 파기만 보여준다.
 *
 * <p>기록이 실패해도 기동·종료를 막지 않아야 한다(서비스가 뜨지 못하는 쪽이 훨씬 큰 문제다).
 */
class SystemEventTest {

  private final AuditService auditService = mock(AuditService.class);
  private final SystemEventService service = new SystemEventService(auditService);

  @Test
  void 기동하면_시스템_시작을_남긴다() {
    service.onStartup();

    verify(auditService).log(isNull(), eq(AuditService.STARTUP), isNull(), eq("시스템 시작"));
  }

  @Test
  void 종료하면_시스템_종료를_남긴다() {
    service.onShutdown();

    verify(auditService).log(isNull(), eq(AuditService.SHUTDOWN), isNull(), eq("시스템 종료"));
  }

  @Test
  void 감사_기록이_실패해도_기동을_막지_않는다() {
    doThrow(new IllegalStateException("DB 연결 없음"))
        .when(auditService)
        .log(isNull(), eq(AuditService.STARTUP), isNull(), eq("시스템 시작"));

    service.onStartup(); // 예외가 밖으로 나가면 기동이 실패한다
  }
}
