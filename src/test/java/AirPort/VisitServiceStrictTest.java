package AirPort;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbVisit;
import AirPort.model.VisitForm;
import AirPort.model.VisitorForm;
import AirPort.service.AcGroupService;
import AirPort.service.AuditService;
import AirPort.service.MenuAuthService;
import AirPort.service.VisitService;
import AirPort.service.VisitBiostarService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 방문 엄격 정책 단위 테스트 — (1) 퇴실은 BiostarX 비활성화 성공해야 진행(실패=예외, 카드 회수 안 함), (2) 입실중(VS03)엔 카드
 * 교환만 허용(회수·방문객 제외 금지). DB/Spring 없이 mock.
 */
class VisitServiceStrictTest {

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final VisitBiostarService visitBiostar = mock(VisitBiostarService.class);
  private final AcGroupService acGroupService = mock(AcGroupService.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final AuditService auditService = mock(AuditService.class);

  private VisitService service() {
    return new VisitService(
        visitMapper, personMapper, carMapper, cardMapper, commonMapper,
        visitBiostar, acGroupService, menuAuthService, auditService);
  }

  private static TbVisit visit(String status) {
    TbVisit v = new TbVisit();
    v.setVisitNo(28);
    v.setStatusCode(status);
    v.setDelYn("N");
    return v;
  }

  @Test
  void 퇴실은_BiostarX_비활성화_실패면_예외로_취소되고_카드를_회수하지_않는다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.disableVisitors(any())).thenReturn("IS000001(연결 실패)"); // 장비 실패

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().checkout(28, null, 101));
    assertTrue(ex.getMessage().contains("퇴실이 취소"));
    verify(cardMapper, never()).releaseByPerson(anyString()); // DB 카드 회수 없음(이중 사용 방지)
    verify(visitMapper, never()).updateStatus(anyInt(), anyString());
    verify(auditService).logAlways(any(), any(), any(), any()); // 실패도 감사에 남긴다
  }

  @Test
  void 입실중_방문은_카드_회수가_불가하다_교환만_허용() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));
    when(visitMapper.selectActiveTempManagers(any(), any())).thenReturn(List.of());

    VisitForm form = new VisitForm();
    form.setVisitNo(28);
    form.setVisitType("PT02");
    form.setCompanyName("TEST");
    form.setManagerIds(List.of("400001"));
    VisitorForm vf = new VisitorForm();
    vf.setPersonId("IS000001");
    vf.setPersonName("홍길동");
    vf.setCardId(null); // 카드 회수 시도
    form.setVisitors(List.of(vf));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().update(form, null, 101));
    assertTrue(ex.getMessage().contains("카드 교환만"));
    verify(visitMapper, never()).update(any());
  }
}
