package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import AirPort.service.VisitBiostarService;
import AirPort.service.VisitRosterService;
import AirPort.service.VisitService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 방문 엄격 정책 단위 테스트 — (1) 퇴실은 BiostarX 비활성화 성공해야 진행(실패=예외, 카드 회수 안 함), (2) 입실중(VS03)엔 카드 교환만
 * 허용(회수·방문객 제외 금지). DB/Spring 없이 mock.
 */
class VisitServiceStrictTest {

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final VisitBiostarService visitBiostar = mock(VisitBiostarService.class);
  private final VisitRosterService roster = mock(VisitRosterService.class);
  private final AcGroupService acGroupService = mock(AcGroupService.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final AuditService auditService = mock(AuditService.class);

  private VisitService service() {
    return new VisitService(
        visitMapper,
        personMapper,
        carMapper,
        cardMapper,
        commonMapper,
        visitBiostar,
        roster,
        acGroupService,
        menuAuthService,
        auditService);
  }

  private static TbVisit visit(String status) {
    TbVisit v = new TbVisit();
    v.setVisitNo(28);
    v.setStatusCode(status);
    v.setVisitType("PT02"); // 임시 — BiostarX 부모 그룹 결정에 쓰인다
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

  @Test
  void 퇴실한_방문객이_섞여_있어도_카드_교체는_막지_않는다() {
    // 회귀 방지: 퇴실자는 카드가 없는 게 정상인데 '카드 없는 사람 있음'으로 걸려 교체가 아예 막혔다
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001", "IS000002"));
    when(visitMapper.selectVisitorCheckout(28, "IS000002"))
        .thenReturn("2026-07-30 14:00:00"); // 퇴실자
    when(commonMapper.selectOne(any(), any())).thenReturn(null);

    VisitForm form = new VisitForm();
    form.setVisitNo(28);
    form.setVisitType("PT02");
    form.setCompanyName("한빛설비");
    form.setWorkStartDt("2026-07-30T09:00");
    form.setWorkEndDt("2026-07-30T18:00");
    form.setWorkPurpose("정비");
    form.setManagerIds(List.of("400001"));
    VisitorForm keep = new VisitorForm();
    keep.setPersonId("IS000001");
    keep.setPersonName("재실자");
    keep.setCardId(99); // 카드 교체
    VisitorForm out = new VisitorForm();
    out.setPersonId("IS000002");
    out.setPersonName("퇴실자"); // 카드 없음(퇴실했으므로 정상)
    form.setVisitors(List.of(keep, out));

    service().update(form, null, 101); // 예외 없이 저장돼야 한다
    verify(roster).saveChildren(eq(28), any(), any(), any());
  }

  @Test
  void 신청_상태의_방문객은_개별_퇴실할_수_없다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS01")); // 신청
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service().checkoutVisitor(28, "IS000001", null, 101));
    assertTrue(ex.getMessage().contains("퇴실할 수 있습니다"), ex.getMessage());
    verify(visitBiostar, never()).disableVisitors(any()); // 장비 호출 전에 막는다
  }

  @Test
  void 방문_퇴실은_방문객마다_퇴실일시를_남긴다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001", "IS000002"));
    when(visitBiostar.disableVisitors(any())).thenReturn(null);

    service().checkout(28, null, 101);

    verify(visitMapper).updateVisitorCheckout(28, "IS000001"); // 개별 퇴실과 같은 표시가 되도록
    verify(visitMapper).updateVisitorCheckout(28, "IS000002");
  }

  @Test
  void 미반납_방문도_퇴실할_수_있다() {
    // 카드를 아직 들고 있는 상태다 — 퇴실을 막으면 카드를 돌려받을 길이 없다
    when(visitMapper.selectById(28)).thenReturn(visit("VS05"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.disableVisitors(any())).thenReturn(null);

    service().checkout(28, null, 101);

    verify(visitMapper).updateStatus(28, "VS04");
  }

  @Test
  void 미반납_방문의_방문객도_개별_퇴실할_수_있다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS05"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.disableVisitors(any())).thenReturn(null);

    service().checkoutVisitor(28, "IS000001", null, 101);

    verify(visitMapper).updateVisitorCheckout(28, "IS000001");
  }

  @Test
  void 마지막_방문객이_퇴실하면_방문도_퇴실_완료가_된다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.disableVisitors(any())).thenReturn(null);
    when(visitMapper.countStayingVisitors(28)).thenReturn(0); // 이 사람이 마지막
    when(visitMapper.selectCarIds(28)).thenReturn(List.of(7));

    service().checkoutVisitor(28, "IS000001", null, 101);

    verify(visitMapper).updateStatus(28, "VS04");
    verify(cardMapper).releaseByCar(7); // 사람이 다 나갔으면 차량 카드도 회수
  }

  @Test
  void 남은_방문객이_있으면_방문_상태는_그대로_둔다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001", "IS000002"));
    when(visitBiostar.disableVisitors(any())).thenReturn(null);
    when(visitMapper.countStayingVisitors(28)).thenReturn(1); // 아직 한 명 남음

    service().checkoutVisitor(28, "IS000001", null, 101);

    verify(visitMapper, never()).updateStatus(anyInt(), anyString());
    verify(cardMapper, never()).releaseByCar(anyInt());
  }

  @Test
  void 미반납_방문을_저장하면_상태를_입실중으로_되돌려_저장한다() {
    // 미반납은 저장하지 않는다(조회 때 계산) — 화면이 돌려준 계산값을 그대로 적으면 DB 가 틀린 값을 들고 있게 된다
    when(visitMapper.selectById(28)).thenReturn(visit("VS05"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(commonMapper.selectOne(any(), any())).thenReturn(null);

    VisitForm form = new VisitForm();
    form.setVisitNo(28);
    form.setVisitType("PT02");
    form.setCompanyName("한빛설비");
    form.setWorkStartDt("2026-07-30T09:00");
    form.setWorkEndDt("2026-07-30T18:00"); // 이미 지난 기간
    form.setWorkPurpose("정비");
    form.setManagerIds(List.of("400001"));
    VisitorForm vf = new VisitorForm();
    vf.setPersonId("IS000001");
    vf.setPersonName("재실자");
    vf.setCardId(99);
    form.setVisitors(List.of(vf));

    service().update(form, null, 101);

    org.mockito.ArgumentCaptor<TbVisit> saved = org.mockito.ArgumentCaptor.forClass(TbVisit.class);
    verify(visitMapper).update(saved.capture());
    assertEquals("VS03", saved.getValue().getStatusCode());
  }

  @Test
  void 신청_방문은_장비에_남은_방문객까지_정리하고_삭제한다() {
    // 전원 카드 발급 전에는 BiostarX 에 올리지 않지만, 예전 방식으로 올라간 방문객이 남아 있을 수 있다.
    // 삭제를 막으면 퇴실('입실 중'만 가능)도 안 되어 방문이 갇힌다 — 함께 지운다.
    when(visitMapper.selectById(28)).thenReturn(visit("VS01"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.deleteVisitors(any(), any())).thenReturn(null);

    service().delete(28, null, 101);

    verify(visitBiostar).deleteVisitors("PT02", List.of("IS000001"));
    verify(visitMapper).softDelete(28);
  }

  @Test
  void 장비_삭제가_실패하면_방문을_삭제하지_않는다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS01"));
    when(visitMapper.selectPersonIds(28)).thenReturn(List.of("IS000001"));
    when(visitBiostar.deleteVisitors(any(), any())).thenReturn("IS000001(HTTP 500)");

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().delete(28, null, 101));
    assertTrue(ex.getMessage().contains("사용자 삭제 실패"), ex.getMessage());
    verify(visitMapper, never()).softDelete(anyInt());
  }

  @Test
  void 입실_중인_방문은_삭제할_수_없다() {
    when(visitMapper.selectById(28)).thenReturn(visit("VS03"));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().delete(28, null, 101));
    assertTrue(ex.getMessage().contains("신청 상태의 방문만"), ex.getMessage());
    verify(visitMapper, never()).softDelete(anyInt());
  }
}
