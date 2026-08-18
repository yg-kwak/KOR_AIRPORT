package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbPersonMapper;
import AirPort.model.PersonForm;
import AirPort.service.AuditService;
import AirPort.service.CodeValidationService;
import AirPort.service.MenuAuthService;
import AirPort.service.PersonService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 정규인원 출입종료일 — <b>상한과 기본값은 다르다</b>.
 *
 * <ul>
 *   <li>상한({@code app.person.access-end-max}) = 장비가 받아 주는 마지막 날짜. 넘기면 저장을 막는다. BiostarX 상한
 *       (2037-12-31T23:59)을 넘는 설정은 그 값으로 깎는다 — 넘겨서 등록하면 장비 등록이 실패한다.
 *   <li>기본값({@code app.person.access-end-default}) = 등록 모달에 채워지는 계약 기간. <b>넘겨도 저장된다</b> — 계약은 연장되는데
 *       한 값으로 두면 그 인원을 아예 등록할 수 없다.
 * </ul>
 *
 * <p>화면과 서버가 같은 값을 써야 하므로 서버가 둘 다 그대로 내려준다.
 */
class PersonAccessPeriodTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private static PersonService service(String max) {
    return service(max, max);
  }

  private static PersonService service(String max, String dflt) {
    return new PersonService(
        max,
        dflt,
        mock(TbPersonMapper.class),
        null,
        null,
        null,
        null,
        null,
        mock(AuditService.class),
        mock(MenuAuthService.class),
        mock(CodeValidationService.class));
  }

  private static PersonForm form(String endDt) {
    PersonForm f = new PersonForm();
    f.setPersonId("30010");
    f.setPersonName("홍길동");
    f.setCompanyCode("C001");
    f.setStatusCode("PS01");
    f.setAccessStartDt("2026-08-01");
    f.setAccessEndDt(endDt);
    return f;
  }

  @Test
  void 상한을_넘는_출입종료일은_저장을_막는다() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service("2028-05-31T23:59").create(form("2029-01-01T00:00"), null, 201));

    assertTrue(ex.getMessage().contains("2028-05-31 23:59"), ex.getMessage());
  }

  @Test
  void 화면과_서버가_같은_상한을_쓴다() {
    assertEquals("2028-05-31T23:59", service("2028-05-31T23:59").maxAccessEndDt());
  }

  @Test
  void 설정이_BiostarX_상한을_넘으면_장비_상한으로_깎는다() {
    // 넘겨서 등록하면 BiostarX 사용자 생성이 실패한다 — 설정 실수를 조용히 흘려보내지 않는다
    assertEquals("2037-12-31T23:59", service("2099-12-31T23:59").maxAccessEndDt());
  }

  // ── 기본값은 상한과 다르다 ────────────────────────────────

  @Test
  void 기본값을_넘겨도_상한_안이면_막지_않는다() {
    // 계약 기간(기본값)은 연장된다 — 여기서 막으면 연장된 인원을 아예 등록할 수 없다.
    // 이 픽스처는 의존을 다 채우지 않아 저장 끝까지 가지 못한다. 확인할 것은 하나다:
    // '날짜 때문에' 거부되지 않는다(BusinessException 은 검증 실패, 그 밖은 미구성 의존).
    PersonService svc = service("2037-12-31T23:59", "2028-05-31T23:59");

    Throwable t =
        assertThrows(Throwable.class, () -> svc.create(form("2030-06-30T18:00"), null, 201));

    assertFalse(t instanceof BusinessException, "날짜 검증에 걸렸다: " + t.getMessage());
  }

  @Test
  void 상한을_넘기면_장비_한계라고_알려준다() {
    PersonService svc = service("2037-12-31T23:59", "2028-05-31T23:59");

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> svc.create(form("2038-01-01T00:00"), null, 201));
    assertTrue(ex.getMessage().contains("2037-12-31 23:59"), ex.getMessage());
    assertTrue(ex.getMessage().contains("BiostarX"), ex.getMessage());
  }

  @Test
  void 기본값과_상한을_따로_내려준다() {
    PersonService svc = service("2037-12-31T23:59", "2028-05-31T23:59");

    assertEquals("2037-12-31T23:59", svc.maxAccessEndDt());
    assertEquals("2028-05-31T23:59", svc.defaultAccessEndDt());
  }

  @Test
  void 기본값이_상한을_넘으면_상한으로_눌러_둔다() {
    // 그대로 두면 모달을 열자마자 저장할 수 없는 값이 들어가 있다
    assertEquals(
        "2030-01-01T00:00", service("2030-01-01T00:00", "2035-12-31T23:59").defaultAccessEndDt());
  }
}
