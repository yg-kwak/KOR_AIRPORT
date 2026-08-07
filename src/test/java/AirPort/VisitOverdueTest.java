package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.mapper.TbVisitMapper;
import AirPort.service.AuditService;
import AirPort.service.VisitOverdueService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 미반납(VS05) 자동 전환 검증 — 입실 중인데 작업기간이 끝난 방문은 카드를 돌려받아야 한다는 표시로 넘어간다. 대상이 없으면 아무것도 하지 않고 감사 이력도 남기지
 * 않는다(매시 실행이라 빈 이력이 쌓이면 안 된다).
 */
class VisitOverdueTest {

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final AuditService auditService = mock(AuditService.class);

  private VisitOverdueService service() {
    return new VisitOverdueService(visitMapper, auditService);
  }

  @Test
  void 작업기간이_끝난_입실중_방문을_미반납으로_바꾼다() {
    when(visitMapper.selectOverdueEntered()).thenReturn(List.of(28, 31));
    when(visitMapper.markUnreturned()).thenReturn(2);

    assertEquals(2, service().markOverdue());

    verify(visitMapper).markUnreturned();
    // 사람이 아니라 시스템이 바꾼 상태다 — 어느 방문인지 이력에 남는다
    verify(auditService)
        .log(isNull(), eq(AuditService.UPDATE), isNull(), org.mockito.ArgumentMatchers.contains("28, 31"));
  }

  @Test
  void 대상이_없으면_아무것도_바꾸지_않는다() {
    when(visitMapper.selectOverdueEntered()).thenReturn(List.of());

    assertEquals(0, service().markOverdue());

    verify(visitMapper, never()).markUnreturned();
    verify(auditService, never()).log(any(), anyString(), any(), anyString());
  }

  @Test
  void 대상이_많으면_이력_문구를_잘라_남긴다() {
    List<Integer> many = java.util.stream.IntStream.rangeClosed(1, 25).boxed().toList();
    when(visitMapper.selectOverdueEntered()).thenReturn(many);
    when(visitMapper.markUnreturned()).thenReturn(25);

    service().markOverdue();

    verify(auditService)
        .log(isNull(), eq(AuditService.UPDATE), isNull(), org.mockito.ArgumentMatchers.contains("외 5건"));
  }
}
