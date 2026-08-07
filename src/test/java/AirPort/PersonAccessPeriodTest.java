package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 정규인원 출입종료일 상한 검증 — 계약 기간이라 설정({@code app.person.access-end-max})으로 바꾼다. 화면과 서버가 같은 값을 써야 하므로 서버가
 * 그 값을 그대로 내려준다. BiostarX 유효기간 상한(2037-12-31T23:59)을 넘는 설정은 그 값으로 깎는다 — 넘겨서 등록하면 장비 등록이 실패한다.
 */
class PersonAccessPeriodTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private static PersonService service(String max) {
    return new PersonService(
        max,
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
}
