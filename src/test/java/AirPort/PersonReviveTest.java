package AirPort;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.model.PersonForm;
import AirPort.model.TbPerson;
import AirPort.service.AuditService;
import AirPort.service.CardService;
import AirPort.service.CodeValidationService;
import AirPort.service.MenuAuthService;
import AirPort.service.PersonBiostarService;
import AirPort.service.PersonFileService;
import AirPort.service.PersonService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 삭제한 인원ID 재사용 검증 — 삭제는 소프트 삭제라 행이 남는다. person_id 는 PK 이므로 그대로 INSERT 하면 깨진다. 남은 행을 되살려(revive) 같은
 * 번호로 다시 등록할 수 있어야 한다. 삭제 때 BiostarX 사용자도 함께 지우므로 번호를 다시 써도 장비와 충돌하지 않는다.
 */
class PersonReviveTest {

  /** 성명 암호화에 ARIA 키가 필요하다. */
  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbPersonAcGroupMapper acGroupMapper = mock(TbPersonAcGroupMapper.class);
  private final PersonBiostarService personBiostar = mock(PersonBiostarService.class);
  private final PersonFileService personFileService = mock(PersonFileService.class);
  private final CardService cardService = mock(CardService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final CodeValidationService codeValidator = mock(CodeValidationService.class);

  private PersonService service() {
    return new PersonService(
        personMapper,
        null,
        acGroupMapper,
        personBiostar,
        personFileService,
        cardService,
        auditService,
        menuAuthService,
        codeValidator);
  }

  private static PersonForm form() {
    PersonForm f = new PersonForm();
    f.setPersonId("30006");
    f.setPersonName("홍길동");
    f.setCompanyCode("C001");
    f.setStatusCode("PS01");
    f.setAccessStartDt("2026-08-01");
    f.setAccessEndDt("2026-12-31");
    return f;
  }

  private static TbPerson row(String delYn) {
    TbPerson p = new TbPerson();
    p.setPersonId("30006");
    p.setDelYn(delYn);
    return p;
  }

  @Test
  void 삭제한_인원ID_로_다시_등록할_수_있다() {
    when(personMapper.selectById("30006")).thenReturn(row("Y")); // 삭제된 행이 남아 있다
    when(personBiostar.syncPersonToBiostar(any(), any())).thenReturn(null);

    service().create(form(), null, 201);

    verify(personMapper).revive(any()); // INSERT 대신 되살리기
    verify(personMapper, never()).insert(any());
  }

  @Test
  void 살아있는_인원ID_는_여전히_중복으로_막는다() {
    when(personMapper.selectById("30006")).thenReturn(row("N"));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().create(form(), null, 201));

    assertTrue(ex.getMessage().contains("이미 존재하는 인원ID"), ex.getMessage());
    verify(personMapper, never()).revive(any());
    verify(personMapper, never()).insert(any());
  }

  @Test
  void 처음_쓰는_인원ID_는_INSERT_한다() {
    when(personMapper.selectById("30006")).thenReturn(null);
    when(personBiostar.syncPersonToBiostar(any(), any())).thenReturn(null);

    service().create(form(), null, 201);

    verify(personMapper).insert(any());
    verify(personMapper, never()).revive(any());
  }
}
