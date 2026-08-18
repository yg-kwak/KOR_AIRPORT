package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.TestKeys;
import AirPort.adapter.biostar.BiostarAdapter;
import AirPort.adapter.biostar.BiostarAuthEvent;
import AirPort.adapter.biostar.BiostarEventAdapter;
import AirPort.adapter.biostar.BiostarEventSocket;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.AuthEventResult;
import AirPort.model.TbCompany;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 실시간 이벤트에 붙이는 우리 DB 값 — <b>소속</b>과 <b>허가구역</b>의 출처 판정.
 *
 * <p>둘 다 현장에서 틀리게 나와 고친 자리다. 소속은 방문객이 늘 비어 보였고(기관만 봤다), 허가구역은 매핑된 하위 그룹까지 세어 `12345` 가 `2122345` 로
 * 나왔다. 규칙이 조용히 뒤집히면 화면은 그럴듯한 값을 계속 보여 주므로 여기서 고정한다.
 */
class MonitorEnrichTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init(); // 성명은 ARIA 암호문이라 복호화 키가 필요하다
  }

  private final TbSystemMapper systemMapper = mock(TbSystemMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbPersonPhotoMapper photoMapper = mock(TbPersonPhotoMapper.class);
  private final TbPersonAcGroupMapper acGroupMapper = mock(TbPersonAcGroupMapper.class);
  private final TbCompanyMapper companyMapper = mock(TbCompanyMapper.class);
  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final BiostarAdapter biostarAdapter = mock(BiostarAdapter.class);
  private final BiostarEventAdapter eventAdapter = mock(BiostarEventAdapter.class);
  private final BiostarEventSocket eventSocket = mock(BiostarEventSocket.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final AuditService auditService = mock(AuditService.class);

  private final MonitorService service =
      new MonitorService(
          systemMapper,
          personMapper,
          photoMapper,
          acGroupMapper,
          companyMapper,
          visitMapper,
          biostarAdapter,
          eventAdapter,
          eventSocket,
          menuAuthService,
          auditService);

  private static final BiostarAuthEvent EVENT =
      new BiostarAuthEvent(
          "4106",
          "VERIFY_SUCCESS_CARD_FACE",
          "2026-08-12T01:38:01.00Z",
          "543737030",
          "F2",
          "400001",
          null);

  /** 화면에 뜰 사람 하나를 세운다. */
  private TbPerson person(String type, String affiliation, String companyCode) {
    TbPerson p = new TbPerson();
    p.setPersonId("400001");
    p.setPersonName(ARIAUtil.ariaEncrypt("박상준"));
    p.setPersonType(type);
    p.setAffiliation(affiliation);
    p.setCompanyCode(companyCode);
    when(personMapper.selectById("400001")).thenReturn(p);
    return p;
  }

  private void company(String code, String name) {
    TbCompany c = new TbCompany();
    c.setCompanyName(name);
    when(companyMapper.selectById(code)).thenReturn(c);
  }

  // ── 사진 없는 칸에 세울 그림 ───────────────────────────────

  @Test
  void 정규인원은_얼굴이_있어야_정상이라_사람_모양으로_표시한다() {
    person("PT01", null, null);

    assertTrue(service.enrich(EVENT).isFaceUser());
  }

  @Test
  void 카드로만_인증하는_인원은_얼굴이_없는_것이_정상이다() {
    // 임시·장기·상주·순찰·대여 — 장비가 얼굴을 찍지 않는다. 사람 모양으로 두면 '등록이 빠진 것'처럼 보인다
    for (String type : new String[] {"PT02", "PT03", "PT04", "PT05", "PT06"}) {
      person(type, null, null);
      assertFalse(service.enrich(EVENT).isFaceUser(), type);
    }
  }

  @Test
  void 우리_DB_에_없는_사용자는_사람_모양으로_표시하지_않는다() {
    when(personMapper.selectById("400001")).thenReturn(null);

    assertFalse(service.enrich(EVENT).isFaceUser());
  }

  // ── 허가구역 출처 ─────────────────────────────────────────────

  @Test
  void 정규인원은_사람에게_붙은_출입그룹을_본다() {
    person("PT01", null, "C001");
    company("C001", "한국공항공사 청주지사");
    when(acGroupMapper.selectAcGroupNames("400001"))
        .thenReturn(List.of("인원구역1", "인원구역2", "인원구역3", "인원구역4", "인원구역5"));

    AuthEventResult row = service.enrich(EVENT);

    assertEquals("12345", row.getAreas());
    verify(visitMapper, never()).selectAcGroupNamesByPerson(anyString());
  }

  @Test
  void 방문객은_방문_단위_구역을_본다() {
    // 방문객은 tb_person_ac_group 이 비어 있다(materialize 미구현) — 거기만 보면 늘 공란이 된다
    person("PT02", "㈜대한기술", null);
    when(visitMapper.selectAcGroupNamesByPerson("400001")).thenReturn(List.of("인원구역1", "인원구역3"));

    AuthEventResult row = service.enrich(EVENT);

    assertEquals("13", row.getAreas());
    verify(acGroupMapper, never()).selectAcGroupNames(anyString());
  }

  @Test
  void 순찰_대여도_방문객과_같은_출처를_본다() {
    // 정규(PT01)가 아니면 전부 방문 단위다 — 구분이 늘어나도 분기를 다시 손대지 않는다
    for (String type : new String[] {"PT03", "PT04", "PT05", "PT06"}) {
      TbVisitMapper visits = mock(TbVisitMapper.class);
      when(visits.selectAcGroupNamesByPerson("400001")).thenReturn(List.of("인원구역5"));
      MonitorService s =
          new MonitorService(
              systemMapper,
              personMapper,
              photoMapper,
              acGroupMapper,
              companyMapper,
              visits,
              biostarAdapter,
              eventAdapter,
              eventSocket,
              menuAuthService,
              auditService);
      person(type, null, null);

      assertEquals("5", s.enrich(EVENT).getAreas(), type);
    }
  }

  // ── 소속 출처 ────────────────────────────────────────────────

  @Test
  void 소속은_자유입력한_값을_먼저_쓴다() {
    // 방문객은 기관에 매이지 않고 소속을 직접 적는다 — 그 값이 정확하다
    person("PT02", "㈜대한기술", "C001");
    company("C001", "한국공항공사 청주지사");

    assertEquals("㈜대한기술", service.enrich(EVENT).getCompanyName());
  }

  @Test
  void 자유입력이_없으면_기관명으로_물러선다() {
    person("PT01", null, "C001");
    company("C001", "한국공항공사 청주지사");

    assertEquals("한국공항공사 청주지사", service.enrich(EVENT).getCompanyName());
  }

  @Test
  void 자유입력도_기관도_없으면_비운다() {
    person("PT01", "   ", null); // 공백만 있는 값도 없는 것으로 본다

    assertNull(service.enrich(EVENT).getCompanyName());
  }

  // ── 우리 DB 에 없는 사람 ──────────────────────────────────────

  @Test
  void 등록되지_않은_사람도_화면에는_올린다() {
    // 장비에 있고 우리 DB 에 없는 사람 — 누가 지나갔는지가 정보다. DB 를 더 뒤지지 않는다
    when(personMapper.selectById("400001")).thenReturn(null);

    AuthEventResult row = service.enrich(EVENT);

    assertEquals("400001", row.getPersonId());
    assertNull(row.getPersonName());
    assertNull(row.getCompanyName());
    assertNull(row.getAreas());
    verify(photoMapper, never()).selectPhoto(any());
  }

  @Test
  void 성명은_복호화해서_담는다() {
    // 화면에 그대로 뿌리는 값이라 여기까지가 복호화 경계다
    person("PT01", null, null);

    assertEquals("박상준", service.enrich(EVENT).getPersonName());
  }
}
