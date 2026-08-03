package AirPort;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbCommonMapper;
import AirPort.model.TbCommon;
import AirPort.service.CodeValidationService;
import org.junit.jupiter.api.Test;

/**
 * 공통코드 값 검증 단위 테스트 — 엑셀 일괄등록은 코드ID 를 사용자가 직접 적으므로, 없는 코드(오타)가 그대로 저장되면 목록의 구분·상태 칸이 빈 채로 남는다. 저장 전에
 * 막고 무엇을 고쳐야 하는지 알리는지 확인한다.
 */
class CodeValidationServiceTest {

  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final CodeValidationService validator = new CodeValidationService(commonMapper);

  private static TbCommon code(String name, String useYn) {
    TbCommon c = new TbCommon();
    c.setCodeName(name);
    c.setUseYn(useYn);
    return c;
  }

  @Test
  void 없는_코드는_어떤_코드구분에_넣어야_하는지_알려준다() {
    when(commonMapper.selectOne("CDT", "CDT99")).thenReturn(null);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> validator.validate("CDT", "CDT99", "카드구분"));
    assertTrue(ex.getMessage().contains("CDT99"), "입력한 값을 그대로 보여준다: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("카드구분"), ex.getMessage());
    assertTrue(ex.getMessage().contains("공통코드관리"), "어디서 고치는지 안내: " + ex.getMessage());
  }

  @Test
  void 사용중지된_코드도_거부한다() {
    when(commonMapper.selectOne("PS", "05")).thenReturn(code("보류", "N"));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> validator.validate("PS", "05", "상태"));
    assertTrue(ex.getMessage().contains("사용중지"), ex.getMessage());
    assertTrue(ex.getMessage().contains("보류"), "코드명을 함께 보여준다: " + ex.getMessage());
  }

  @Test
  void 사용중인_코드와_빈값은_통과한다() {
    when(commonMapper.selectOne("CS", "CS01")).thenReturn(code("정상", "Y"));
    assertDoesNotThrow(() -> validator.validate("CS", "CS01", "카드상태"));
    assertDoesNotThrow(() -> validator.validate("CS", null, "카드상태")); // 필수 여부는 각 서비스가 따로 본다
    assertDoesNotThrow(() -> validator.validate("CS", "  ", "카드상태"));
  }

  @Test
  void 바뀌지_않은_값은_지금_유효하지_않아도_통과한다() {
    // 운영에서 코드를 삭제·중지해도, 그 코드를 쓰던 기존 행의 수정(메모만 고치는 등)이 막히면 안 된다
    when(commonMapper.selectOne("UT", "UT09")).thenReturn(null); // 삭제된 코드
    assertDoesNotThrow(() -> validator.validate("UT", "UT09", "직위", "UT09"));

    when(commonMapper.selectOne("PS", "05")).thenReturn(code("보류", "N")); // 사용중지된 코드
    assertDoesNotThrow(() -> validator.validate("PS", "05", "상태", "05"));
  }

  @Test
  void 값이_바뀌면_새_값은_검증한다() {
    when(commonMapper.selectOne("UT", "UT99")).thenReturn(null);
    assertThrows(BusinessException.class, () -> validator.validate("UT", "UT99", "직위", "UT01"));
  }

  @Test
  void 앞뒤_공백은_다듬어_조회한다() {
    when(commonMapper.selectOne("CDT", "CDT01")).thenReturn(code("출입카드", "Y"));
    assertDoesNotThrow(() -> validator.validate("CDT", " CDT01 ", "카드구분"));
  }
}
