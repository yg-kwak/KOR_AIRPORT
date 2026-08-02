package AirPort;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.model.TbPerson;
import AirPort.service.AuditService;
import AirPort.service.CardService;
import AirPort.service.MenuAuthService;
import AirPort.service.PersonBiostarService;
import AirPort.service.PersonService;
import org.junit.jupiter.api.Test;

/**
 * 정규인원 삭제 시 카드 회수 검증 — 회수하지 않으면 사라진 인원에 카드가 물린 채 목록에 '발급중'으로 남아 다른 인원에게 발급할 수 없다. BiostarX 삭제가 실패해
 * 롤백되는 경우에는 회수도 일어나지 않아야 한다(DB/Spring 없이 mock).
 */
class PersonDeleteCardReleaseTest {

  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbPersonAcGroupMapper acGroupMapper = mock(TbPersonAcGroupMapper.class);
  private final PersonBiostarService personBiostar = mock(PersonBiostarService.class);
  private final CardService cardService = mock(CardService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);

  private PersonService service() {
    TbPerson existing = new TbPerson();
    existing.setPersonId("P001");
    existing.setCompanyCode("C001");
    existing.setDelYn("N");
    when(personMapper.selectById("P001")).thenReturn(existing);
    return new PersonService(
        personMapper,
        null,
        acGroupMapper,
        personBiostar,
        null,
        cardService,
        auditService,
        menuAuthService,
        null);
  }

  @Test
  void 인원을_삭제하면_보유카드가_회수되어_재발급_가능해진다() {
    when(personBiostar.deleteUser(anyString(), anyString())).thenReturn(null); // BiostarX 삭제 성공
    when(cardService.releasePersonCards("P001")).thenReturn(1);

    service().delete("P001", null, 201);

    verify(cardService).releasePersonCards("P001"); // person_id=NULL → 미발급 상태
    verify(acGroupMapper).deleteByPerson("P001");
    verify(personMapper).softDelete("P001");
  }

  @Test
  void BiostarX_삭제가_실패하면_카드도_회수하지_않는다() {
    when(personBiostar.deleteUser(anyString(), anyString())).thenReturn("HTTP 500");

    assertThrows(BusinessException.class, () -> service().delete("P001", null, 201));

    verify(cardService, never()).releasePersonCards(anyString());
    verify(personMapper, never()).softDelete(anyString());
    verify(auditService).logAlways(any(), any(), anyInt(), anyString()); // 실패는 감사에 남긴다
  }
}
