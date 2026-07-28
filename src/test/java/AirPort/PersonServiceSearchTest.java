package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import AirPort.mapper.TbPersonMapper;
import AirPort.model.PersonSearchParam;
import AirPort.security.ARIAUtil;
import AirPort.service.AuditService;
import AirPort.service.PersonService;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 정규인원 검색의 성명 완전일치 배선 단위 테스트 — 암호화 컬럼(person_name)은 부분검색 불가라, 서비스가 keyword 를 trim 후 ARIA
 * 암호화해 {@code keywordEnc} 로 넘긴다(매퍼는 {@code = #{keywordEnc}} 로 비교). DB/Spring 없이 매퍼 mock 으로 확인.
 */
class PersonServiceSearchTest {

  @BeforeAll
  static void setAriaKey() {
    // application.properties 의 개발 기본키(비밀 아님). ARIA 는 결정적(ECB)이라 완전일치 검색이 성립.
    new ARIAUtil().setKey("01234567890123456789012345678901");
  }

  private static PersonService service(TbPersonMapper personMapper) {
    when(personMapper.selectCount(any())).thenReturn(0L);
    when(personMapper.selectList(any())).thenReturn(List.of());
    AuditService audit = mock(AuditService.class);
    // list() 는 personMapper·auditService·ARIA(static) 만 사용 → 나머지 의존성은 null 로 충분
    return new PersonService(
        personMapper, null, null, null, null, null, null, null, null, null, audit, null);
  }

  @Test
  void 성명검색_keyword는_trim후_ARIA암호화되어_keywordEnc로_설정된다() {
    TbPersonMapper personMapper = mock(TbPersonMapper.class);
    PersonSearchParam p = new PersonSearchParam();
    p.setKeyword("  홍길동  ");
    service(personMapper).list(p, null, 201);
    assertEquals(
        ARIAUtil.ariaEncrypt("홍길동"), p.getKeywordEnc(), "앞뒤 공백 제거 후 암호화(완전일치 매칭)");
  }

  @Test
  void keyword가_null이면_keywordEnc도_null이라_NPE_없이_통과() {
    TbPersonMapper personMapper = mock(TbPersonMapper.class);
    PersonSearchParam p = new PersonSearchParam();
    service(personMapper).list(p, null, 201);
    assertNull(p.getKeywordEnc());
  }
}
