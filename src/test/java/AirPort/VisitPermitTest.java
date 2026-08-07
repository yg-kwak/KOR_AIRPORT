package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.PermitForm;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import AirPort.security.ARIAUtil;
import AirPort.service.AuditService;
import AirPort.service.MenuAuthService;
import AirPort.service.VisitPermitService;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 출입허가 신청서 데이터 검증 — 양식은 손으로 채우는 칸이 많아, 시스템이 넣는 값만 정확하면 된다.
 *
 * <p>확인 대상: 출입구역을 번호만 뽑는지, 회수된 카드도 명칭으로 되짚는지, 인솔자가 없을 때 신청인이 비는지.
 */
class VisitPermitTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final TbAcGroupMapper acGroupMapper = mock(TbAcGroupMapper.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final AuditService auditService = mock(AuditService.class);

  private VisitPermitService service() {
    return new VisitPermitService(
        visitMapper,
        personMapper,
        carMapper,
        cardMapper,
        commonMapper,
        acGroupMapper,
        menuAuthService,
        auditService);
  }

  private void visitExists() {
    TbVisit v = new TbVisit();
    v.setVisitNo(17);
    v.setVisitType("PT02");
    v.setDelYn("N");
    v.setWorkStartDt("2026-08-07T16:14");
    v.setWorkEndDt("2026-08-07T18:00");
    v.setWorkPurpose("정비");
    when(visitMapper.selectById(17)).thenReturn(v);
  }

  private static TbPerson person(String id, String name) {
    TbPerson p = new TbPerson();
    p.setPersonId(id);
    p.setPersonName(ARIAUtil.ariaEncrypt(name));
    return p;
  }

  private static TbCard card(String name) {
    TbCard c = new TbCard();
    c.setCardName(name);
    return c;
  }

  @Test
  void 출입구역은_번호만_뽑는다() {
    visitExists();
    when(visitMapper.selectAcGroupIds(17)).thenReturn(List.of(5, 6, 7));
    when(acGroupMapper.selectNamesByIds(any())).thenReturn(List.of("인원구역1", "인원구역3", "인원구역2 안쪽"));
    when(visitMapper.selectCarAcCodes(17)).thenReturn(List.of("CAR01", "CAR02"));
    when(commonMapper.selectOne(eq("CAR"), anyString()))
        .thenReturn(common("차량구역1"), common("차량구역2"));

    PermitForm f = service().permit(17, null, 101);

    assertEquals("1,3,2", f.getPersonAreas());
    assertEquals("1,2", f.getCarAreas());
  }

  @Test
  void 번호가_없는_구역명은_그대로_남긴다() {
    // 조용히 사라지면 어느 구역이 빠졌는지 알 수 없다
    visitExists();
    when(visitMapper.selectAcGroupIds(17)).thenReturn(List.of(5));
    when(acGroupMapper.selectNamesByIds(any())).thenReturn(List.of("계류장"));

    assertEquals("계류장", service().permit(17, null, 101).getPersonAreas());
  }

  @Test
  void 출입증번호는_카드명칭으로_적는다() {
    visitExists();
    when(visitMapper.selectPersonIds(17)).thenReturn(List.of("IS000022"));
    when(personMapper.selectById("IS000022")).thenReturn(person("IS000022", "홍길동"));
    when(cardMapper.selectByPerson("IS000022")).thenReturn(List.of(card("임시11111114")));

    PermitForm.Visitor v = service().permit(17, null, 101).getVisitors().get(0);

    assertEquals("홍길동", v.getName());
    assertEquals("임시11111114", v.getCardName());
  }

  @Test
  void 회수된_카드도_마지막_카드로_명칭을_되짚는다() {
    // 퇴실한 방문도 신청서를 다시 뽑을 수 있어야 한다
    visitExists();
    when(visitMapper.selectPersonIds(17)).thenReturn(List.of("IS000022"));
    when(personMapper.selectById("IS000022")).thenReturn(person("IS000022", "홍길동"));
    when(cardMapper.selectByPerson("IS000022")).thenReturn(List.of()); // 회수됨
    when(visitMapper.selectVisitorLastCard(17, "IS000022")).thenReturn("1111114");
    when(cardMapper.selectByCardNo("1111114")).thenReturn(card("임시11111114"));

    assertEquals("임시11111114", service().permit(17, null, 101).getVisitors().get(0).getCardName());
  }

  @Test
  void 그_카드마저_사라졌으면_번호라도_남긴다() {
    visitExists();
    when(visitMapper.selectPersonIds(17)).thenReturn(List.of("IS000022"));
    when(personMapper.selectById("IS000022")).thenReturn(person("IS000022", "홍길동"));
    when(cardMapper.selectByPerson("IS000022")).thenReturn(List.of());
    when(visitMapper.selectVisitorLastCard(17, "IS000022")).thenReturn("1111114");
    when(cardMapper.selectByCardNo("1111114")).thenReturn(null);

    assertEquals("1111114", service().permit(17, null, 101).getVisitors().get(0).getCardName());
  }

  @Test
  void 신청인은_첫_인솔자다() {
    visitExists();
    when(visitMapper.selectManagerIds(17)).thenReturn(List.of("400001", "400002"));
    TbPerson first = person("400001", "박상준");
    first.setCompanyName("슈프리마");
    when(personMapper.selectById("400001")).thenReturn(first);
    when(personMapper.selectById("400002")).thenReturn(person("400002", "홍길동"));

    PermitForm f = service().permit(17, null, 101);

    assertEquals("슈프리마", f.getApplicantCompany());
    assertEquals("박상준", f.getApplicantName());
    assertEquals(2, f.getManagers().size());
  }

  @Test
  void 인솔자가_없으면_신청인은_비운다() {
    visitExists();
    when(visitMapper.selectManagerIds(17)).thenReturn(List.of());

    PermitForm f = service().permit(17, null, 101);

    assertNull(f.getApplicantName());
    assertNull(f.getApplicantCompany());
  }

  @Test
  void 출력은_감사에_남긴다() {
    visitExists();

    service().permit(17, null, 101);

    verify(auditService).log(any(), eq(AuditService.DOWNLOAD), anyInt(), anyString());
  }

  @Test
  void 없는_방문은_거부한다() {
    when(visitMapper.selectById(99)).thenReturn(null);

    assertThrows(BusinessException.class, () -> service().permit(99, null, 101));
  }

  private static TbCommon common(String name) {
    TbCommon c = new TbCommon();
    c.setCodeName(name);
    return c;
  }
}
