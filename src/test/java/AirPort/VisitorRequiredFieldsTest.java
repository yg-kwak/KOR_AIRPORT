package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbPerson;
import AirPort.model.VisitForm;
import AirPort.model.VisitorForm;
import AirPort.service.AuditService;
import AirPort.service.BlacklistService;
import AirPort.service.CardIssueService;
import AirPort.service.CardService;
import AirPort.service.ParkingPassService;
import AirPort.service.VisitBiostarService;
import AirPort.service.VisitRosterService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 방문객의 <b>성명·생년월일·소속은 필수</b>다 — 관리자 화면과 키오스크가 같은 규칙을 쓴다.
 *
 * <p>둘 다 {@link VisitRosterService#upsertVisitor} 를 지나므로 규칙이 한 곳에만 있다. 화면(JS)에도 같은 검사가 있지만 그것은 편의고,
 * 막는 것은 여기다 — 키오스크는 무인증이라 요청을 직접 만들어 보낼 수 있다.
 *
 * <p>세 값은 신청서에 그대로 찍히는 칸이고, 동명이인을 가려낼 단서도 이 셋뿐이다.
 */
class VisitorRequiredFieldsTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init(); // 성명·생년월일은 ARIA 암호문으로 저장된다
  }

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);

  private final VisitRosterService roster =
      new VisitRosterService(
          visitMapper,
          personMapper,
          carMapper,
          cardMapper,
          commonMapper,
          mock(CardService.class),
          mock(CardIssueService.class),
          mock(AirPort.service.VisitCardService.class),
          mock(VisitBiostarService.class),
          mock(ParkingPassService.class),
          mock(AuditService.class),
          mock(BlacklistService.class));

  private static VisitForm visit() {
    VisitForm f = new VisitForm();
    f.setVisitType("PT02");
    f.setWorkStartDt("2026-09-08T09:00");
    f.setWorkEndDt("2026-09-08T18:00");
    return f;
  }

  /** 셋 다 채운 정상 방문객. 테스트마다 한 칸씩 비워 본다. */
  private static VisitorForm visitor() {
    VisitorForm v = new VisitorForm();
    v.setPersonName("김방문");
    v.setBirthDate("1990-01-01");
    v.setAffiliation("㈜대한기술");
    return v;
  }

  private String save(VisitorForm v) {
    when(personMapper.selectNextVisitorId(anyString())).thenReturn("IS000001");
    return roster.upsertVisitor(v, visit());
  }

  @Test
  void 성명이_없으면_막는다() {
    VisitorForm v = visitor();
    v.setPersonName("  ");

    assertEquals(
        "방문객 성명은(는) 필수입니다.", assertThrows(BusinessException.class, () -> save(v)).getMessage());
  }

  @Test
  void 소속이_없으면_막는다() {
    VisitorForm v = visitor();
    v.setAffiliation(null);

    assertEquals(
        "방문객 소속은(는) 필수입니다.", assertThrows(BusinessException.class, () -> save(v)).getMessage());
  }

  @Test
  void 생년월일이_없으면_막는다() {
    VisitorForm v = visitor();
    v.setBirthDate(null);

    assertEquals(
        "방문객 생년월일은(는) 필수입니다.", assertThrows(BusinessException.class, () -> save(v)).getMessage());
  }

  @Test
  void 생년월일_형식이_틀리면_막는다() {
    VisitorForm v = visitor();
    v.setBirthDate("1990-02-30"); // 달력에 없는 날

    assertThrows(BusinessException.class, () -> save(v));
  }

  @Test
  void 셋이_다_있으면_저장한다() {
    assertEquals("IS000001", save(visitor()));
  }

  @Test
  void 생년월일은_형식을_맞춰_저장한다() {
    // 화면이 어디든 저장되는 모양은 하나여야 한다 — 암호문이라 나중에 정리할 수 없다
    VisitorForm v = visitor();
    v.setBirthDate("19900101");
    save(v);

    org.mockito.ArgumentCaptor<TbPerson> saved =
        org.mockito.ArgumentCaptor.forClass(TbPerson.class);
    org.mockito.Mockito.verify(personMapper).insert(saved.capture());
    assertEquals(
        "1990-01-01", AirPort.security.ARIAUtil.ariaDecrypt(saved.getValue().getBirthDate()));
  }

  @Test
  void 업체명은_더_이상_받지_않는다() {
    // 방문 저장에 업체명이 없어도 통과해야 한다(필수에서 뺐다)
    VisitForm f = visit();
    f.setCompanyName(null);
    when(personMapper.selectNextVisitorId(anyString())).thenReturn("IS000002");

    assertEquals("IS000002", roster.upsertVisitor(visitor(), f));
    org.mockito.Mockito.verify(personMapper).insert(any(TbPerson.class));
  }
}
