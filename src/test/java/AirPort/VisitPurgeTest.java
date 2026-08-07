package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import AirPort.service.AuditService;
import AirPort.service.VisitBiostarService;
import AirPort.service.VisitPurgeItemService;
import AirPort.service.VisitPurgeService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 방문 데이터 정기 파기 검증 — 되돌릴 수 없는 물리 삭제라 안전장치가 실제로 걸리는지 본다.
 *
 * <p>핵심: (1) 장비 삭제가 실패하면 DB 를 건드리지 않는다, (2) 미리보기에서는 아무것도 지우지 않는다, (3) 0건이어도 실행 이력을 남긴다.
 */
class VisitPurgeTest {

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final VisitBiostarService visitBiostar = mock(VisitBiostarService.class);
  private final AuditService auditService = mock(AuditService.class);

  private VisitPurgeItemService item() {
    return new VisitPurgeItemService(
        visitMapper, personMapper, carMapper, cardMapper, visitBiostar);
  }

  private VisitPurgeService service(boolean dryRun) {
    return new VisitPurgeService(visitMapper, item(), auditService, 365, 200, dryRun);
  }

  private static TbVisit visit() {
    TbVisit v = new TbVisit();
    v.setVisitNo(28);
    v.setVisitType("PT02");
    return v;
  }

  @Test
  void 대상_방문과_소속_방문객_차량을_지운다() {
    when(visitMapper.selectPurgeTargets(365, 200)).thenReturn(List.of(28));
    when(visitMapper.selectOrphanVisitorIds(365, 200)).thenReturn(List.of());
    when(visitMapper.selectById(28)).thenReturn(visit());
    when(visitMapper.selectPurgeVisitorIds(28)).thenReturn(List.of("IS000001"));
    when(visitMapper.selectPurgeCarIds(28)).thenReturn(List.of(7));
    when(visitBiostar.deleteVisitors(any(), any())).thenReturn(null);

    assertEquals(1, service(false).run());

    verify(visitBiostar).deleteVisitors("PT02", List.of("IS000001")); // 장비 먼저
    verify(cardMapper).releaseByPerson("IS000001"); // 카드는 자산이라 귀속만 푼다
    verify(personMapper).purge("IS000001");
    verify(carMapper).purge(7);
    verify(visitMapper).purgeVisitRows(28);
  }

  @Test
  void 장비_삭제가_실패하면_DB_를_건드리지_않는다() {
    // DB 만 지우면 장비에 유령 사용자가 남고, 어떤 사용자였는지 되짚을 수 없다
    when(visitMapper.selectPurgeTargets(365, 200)).thenReturn(List.of(28));
    when(visitMapper.selectOrphanVisitorIds(365, 200)).thenReturn(List.of());
    when(visitMapper.selectById(28)).thenReturn(visit());
    when(visitMapper.selectPurgeVisitorIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.deleteVisitors(any(), any())).thenReturn("IS000001(HTTP 500)");

    assertEquals(0, service(false).run());

    verify(personMapper, never()).purge(anyString());
    verify(visitMapper, never()).purgeVisitRows(anyInt());
    verify(auditService).log(isNull(), any(), isNull(), contains("실패 1건"));
  }

  @Test
  void 미리보기에서는_아무것도_지우지_않는다() {
    when(visitMapper.selectPurgeTargets(365, 200)).thenReturn(List.of(28, 31));
    when(visitMapper.selectOrphanVisitorIds(365, 200)).thenReturn(List.of("IS000009"));

    assertEquals(0, service(true).run());

    verify(visitMapper, never()).purgeVisitRows(anyInt());
    verify(personMapper, never()).purge(anyString());
    verify(visitBiostar, never()).deleteVisitors(any(), any());
    verify(auditService).log(isNull(), any(), isNull(), contains("미리보기"));
  }

  @Test
  void 지울_것이_없어도_실행_이력을_남긴다() {
    // 배치가 멈춘 것과 지울 게 없는 것은 다르다 — 돌았다는 사실이 남아야 한다
    when(visitMapper.selectPurgeTargets(365, 200)).thenReturn(List.of());
    when(visitMapper.selectOrphanVisitorIds(365, 200)).thenReturn(List.of());

    assertEquals(0, service(false).run());

    verify(auditService).log(isNull(), eq(AuditService.PURGE), isNull(), contains("방문 0건 삭제"));
  }

  @Test
  void 방문에서_빠진_잔여_방문객도_지운다() {
    when(visitMapper.selectPurgeTargets(365, 200)).thenReturn(List.of());
    when(visitMapper.selectOrphanVisitorIds(365, 200)).thenReturn(List.of("IS000009"));
    TbPerson p = new TbPerson();
    p.setPersonId("IS000009");
    p.setPersonType("PT02");
    when(personMapper.selectById("IS000009")).thenReturn(p);
    when(visitBiostar.deleteVisitors(any(), any())).thenReturn(null);

    service(false).run();

    verify(visitBiostar).deleteVisitors("PT02", List.of("IS000009"));
    verify(personMapper).purge("IS000009");
    verify(auditService).log(isNull(), any(), isNull(), contains("방문객 1명 삭제"));
  }
}
