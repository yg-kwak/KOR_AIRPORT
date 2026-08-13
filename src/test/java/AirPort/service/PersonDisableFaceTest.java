package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.TestKeys;
import AirPort.adapter.BiostarResult;
import AirPort.adapter.BiostarUserAdapter;
import AirPort.adapter.BiostarUserRequest;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.PersonForm;
import AirPort.model.TbCommon;
import AirPort.model.TbCompany;
import AirPort.model.TbSystem;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 비활성 상태로 저장하면 <b>얼굴을 함께 지운다</b>.
 *
 * <p>출입을 막아 놓고 생체정보만 남겨 두면 상태를 되돌리는 순간 예전 얼굴로 문이 열린다. 퇴사·분실처럼 사람이 떠났거나 카드를 잃은 상태에서 얼굴을 보관할 이유도
 * 없다(개인정보 최소화).
 *
 * <p><b>장비와 우리 DB 를 같은 판정으로 지워야 한다.</b> 한쪽만 지우면 다음 저장에서 되살아난다 — 그래서 판정({@code isDisabled})을 한 곳에 두고
 * 양쪽이 그것을 쓴다.
 */
class PersonDisableFaceTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private final TbSystemMapper systemMapper = mock(TbSystemMapper.class);
  private final TbCompanyMapper companyMapper = mock(TbCompanyMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbPersonAcGroupMapper acGroupMapper = mock(TbPersonAcGroupMapper.class);
  private final BiostarUserAdapter userAdapter = mock(BiostarUserAdapter.class);

  private final PersonBiostarService service =
      new PersonBiostarService(
          systemMapper,
          companyMapper,
          commonMapper,
          cardMapper,
          personMapper,
          acGroupMapper,
          userAdapter);

  /** 현장 공통코드 그대로 — 신규·재발급은 활성(false), 정지·퇴사·회수·분실은 비활성(true). */
  private void statusCodes() {
    code("01", "false");
    code("02", "true");
    code("03", "true");
    code("04", "true");
    code("05", "false");
    code("06", "true");
  }

  private void code(String codeId, String tag) {
    TbCommon c = new TbCommon();
    c.setCodeId(codeId);
    c.setCodeTag(tag);
    when(commonMapper.selectOne("PS", codeId)).thenReturn(c);
  }

  private PersonForm form(String statusCode) {
    PersonForm f = new PersonForm();
    f.setPersonId("400001");
    f.setPersonName("박상준");
    f.setStatusCode(statusCode);
    f.setCompanyCode("C001");
    f.setFacePhoto("BASE64-원본사진");
    f.setFaceImage("BASE64-정규화얼굴");
    f.setFaceTemplate9("T9");
    f.setFaceTemplate5("T5");
    return f;
  }

  /** 장비로 실제 나간 요청을 잡아 본다. */
  private BiostarUserRequest sent(String statusCode) {
    statusCodes();
    TbSystem cfg = new TbSystem();
    cfg.setBiostarIp("10.0.0.1");
    cfg.setBiostarId("admin");
    when(systemMapper.selectOne()).thenReturn(cfg);
    TbCompany company = new TbCompany();
    company.setBiostarGroupId(1003);
    when(companyMapper.selectById("C001")).thenReturn(company);
    when(userAdapter.userExists(any(), any(), any(), anyString())).thenReturn(true);
    when(userAdapter.updateUser(any(), any(), any(), any(), any())).thenReturn(BiostarResult.ok());

    service.syncPersonToBiostar(form(statusCode), PersonBiostarService.empty("400001"));

    ArgumentCaptor<BiostarUserRequest> after = ArgumentCaptor.forClass(BiostarUserRequest.class);
    verify(userAdapter).updateUser(any(), any(), any(), any(), after.capture());
    return after.getValue();
  }

  // ── 판정 ─────────────────────────────────────────────────────

  @Test
  void 비활성_판정은_공통코드가_원천이다() {
    statusCodes();

    // 코드 번호를 코드에 박지 않는다 — 현장에서 상태를 추가해도 판정이 따라간다
    for (String disabled : new String[] {"02", "03", "04", "06"}) {
      assertTrue(service.isDisabled(disabled), disabled);
    }
    for (String active : new String[] {"01", "05"}) {
      assertFalse(service.isDisabled(active), active);
    }
  }

  @Test
  void 없는_상태코드는_비활성으로_보지_않는다() {
    // 판단이 안 서면 지우지 않는다 — 되돌릴 수 없는 쪽으로 기울면 안 된다
    assertFalse(service.isDisabled("99"));
    assertFalse(service.isDisabled(null));
  }

  @Test
  void 화면에_내려줄_비활성_코드_목록() {
    TbCommon a = new TbCommon();
    a.setCodeId("02");
    TbCommon b = new TbCommon();
    b.setCodeId("06");
    when(commonMapper.selectByCodeTag("PS", "true")).thenReturn(List.of(a, b));

    assertEquals(List.of("02", "06"), service.disabledStatusCodes());
  }

  // ── 장비로 나가는 값 ──────────────────────────────────────────

  @Test
  void 비활성이면_얼굴을_빼고_보낸다() {
    BiostarUserRequest req = sent("03"); // 퇴사

    assertEquals("true", req.disabled());
    assertEquals(null, req.faceImage(), "인증용 얼굴이 남으면 상태를 되돌리는 순간 그 얼굴로 문이 열린다");
    assertEquals(null, req.faceTemplate9());
    assertEquals(null, req.faceTemplate5());
    assertEquals(null, req.photo(), "사용자 사진도 함께 지운다 — 우리 DB 에서도 지우므로 한쪽만 남기면 어긋난다");
  }

  @Test
  void 활성이면_얼굴을_그대로_보낸다() {
    BiostarUserRequest req = sent("01"); // 신규

    assertEquals("false", req.disabled());
    assertEquals("BASE64-원본사진", req.photo());
    assertEquals("T9", req.faceTemplate9());
    assertEquals("T5", req.faceTemplate5());
  }

  @Test
  void 재발급은_활성이라_얼굴을_지우지_않는다() {
    // 05(재발급)는 이름만 보면 헷갈리지만 code_tag 가 false 다 — 카드만 다시 내주는 상태다
    assertEquals("BASE64-원본사진", sent("05").photo());
  }
}
