package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.VisitForm;
import AirPort.model.VisitorForm;
import AirPort.service.AuditService;
import AirPort.service.CardService;
import AirPort.service.VisitBiostarService;
import AirPort.service.VisitRosterService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 방문객 제거·카드 중복 규칙 단위 테스트 — 저장 시 폼에서 빠진 방문객은 <b>BiostarX 사용자도 삭제</b>해야 한다(안 하면 장비에만 남아 계속 출입 가능). 한
 * 실물 카드를 두 명에게 발급하는 것도 서버가 막는다. DB/Spring 없이 mock.
 */
class VisitRosterRemoveTest {

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final AirPort.mapper.TbCommonMapper commonMapper =
      mock(AirPort.mapper.TbCommonMapper.class);
  private final CardService cardService = mock(CardService.class);
  private final VisitBiostarService visitBiostar = mock(VisitBiostarService.class);
  private final AuditService auditService = mock(AuditService.class);

  private VisitRosterService service() {
    return new VisitRosterService(
        visitMapper,
        personMapper,
        carMapper,
        cardMapper,
        commonMapper,
        cardService,
        visitBiostar,
        auditService);
  }

  /** 방문객 2명(P1 유지, P2 제거)인 폼. */
  private static VisitForm formKeeping(String... keepIds) {
    VisitForm f = new VisitForm();
    f.setVisitType("PT02");
    f.setVisitors(
        java.util.Arrays.stream(keepIds)
            .map(
                id -> {
                  VisitorForm v = new VisitorForm();
                  v.setPersonId(id);
                  v.setPersonName("방문객" + id);
                  return v;
                })
            .toList());
    return f;
  }

  @Test
  void 폼에서_빠진_방문객은_BiostarX_에서도_삭제한다() {
    when(visitMapper.selectPersonIds(9110)).thenReturn(List.of("P1", "P2", "P3"));
    when(visitBiostar.deleteVisitors(anyString(), any())).thenReturn(null); // 장비 삭제 성공
    when(personMapper.selectNextVisitorId(anyString())).thenReturn("P9");

    service().saveChildren(9110, formKeeping("P1"), null, 101);

    // 빠진 P2·P3 만 장비에서 삭제 대상이 된다
    verify(visitBiostar).deleteVisitors("PT02", List.of("P2", "P3"));
    verify(personMapper).softDelete("P2");
    verify(personMapper).softDelete("P3");
    verify(personMapper, never()).softDelete("P1");
  }

  @Test
  void 장비_삭제가_실패하면_저장을_롤백한다() {
    when(visitMapper.selectPersonIds(9110)).thenReturn(List.of("P1", "P2"));
    when(visitBiostar.deleteVisitors(anyString(), any())).thenReturn("P2(HTTP 500)");

    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service().saveChildren(9110, formKeeping("P1"), null, 101));
    assertTrue(ex.getMessage().contains("사용자 삭제 실패"), ex.getMessage());
    verify(auditService).logAlways(any(), any(), anyInt(), anyString()); // 실패는 감사에 남긴다
  }

  @Test
  void 퇴실한_방문객에게는_카드를_다시_발급하지_않는다() {
    when(visitMapper.selectPersonIds(9110)).thenReturn(List.of("P1"));
    when(visitBiostar.deleteVisitors(anyString(), any())).thenReturn(null);
    when(visitMapper.selectVisitorCheckout(9110, "P1")).thenReturn("2026-07-30 14:00:00"); // 이미 퇴실

    VisitForm form = formKeeping("P1");
    form.getVisitors().get(0).setCardId(77);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().saveChildren(9110, form, null, 101));
    assertTrue(ex.getMessage().contains("퇴실한 방문객"), ex.getMessage());
  }

  @Test
  void 퇴실_이력은_저장으로_재구성해도_남는다() {
    when(visitMapper.selectPersonIds(9110)).thenReturn(List.of("P1"));
    when(visitBiostar.deleteVisitors(anyString(), any())).thenReturn(null);
    when(visitMapper.selectVisitorCheckout(9110, "P1")).thenReturn("2026-07-30 14:00:00");

    service().saveChildren(9110, formKeeping("P1"), null, 101); // 카드 없이 저장

    verify(visitMapper).insertPerson(9110, "P1", "2026-07-30 14:00:00"); // 퇴실일시 그대로 복원
  }

  @Test
  void 같은_카드를_두_명에게_발급하면_거부한다() {
    when(visitMapper.selectPersonIds(9110)).thenReturn(List.of());
    when(visitBiostar.deleteVisitors(anyString(), any())).thenReturn(null);
    when(personMapper.selectNextVisitorId(anyString())).thenReturn("P9");

    VisitForm form = formKeeping("P1", "P2");
    form.getVisitors().get(0).setCardId(77);
    form.getVisitors().get(1).setCardId(77); // 같은 실물 카드

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().saveChildren(9110, form, null, 101));
    assertTrue(ex.getMessage().contains("두 명 이상"), ex.getMessage());
  }

  @Test
  void 방문유형에_맞는_접두로_인원ID_를_채번한다() {
    // 장기(PT03) → LT. 공통코드 PIP 에서 접두를 읽는다(코드 수정 없이 유형 추가 가능)
    AirPort.model.TbCommon prefix = new AirPort.model.TbCommon();
    prefix.setCodeName("LT");
    when(commonMapper.selectOne("PIP", "PT03")).thenReturn(prefix);
    when(personMapper.selectNextVisitorId("LT")).thenReturn("LT000001");

    VisitForm form = new VisitForm();
    form.setVisitType("PT03");
    VisitorForm v = new VisitorForm();
    v.setPersonName("장기방문객");
    form.setVisitors(List.of(v));

    assertEquals("LT000001", service().upsertVisitor(v, form));
  }

  @Test
  void 접두_코드가_없으면_기존_체계인_IS_로_채번한다() {
    when(commonMapper.selectOne("PIP", "PT09")).thenReturn(null); // 아직 등록 안 된 유형
    when(personMapper.selectNextVisitorId("IS")).thenReturn("IS000045");

    VisitForm form = new VisitForm();
    form.setVisitType("PT09");
    VisitorForm v = new VisitorForm();
    v.setPersonName("신규유형");
    form.setVisitors(List.of(v));

    assertEquals("IS000045", service().upsertVisitor(v, form));
  }
}
