package AirPort;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.service.CardIssueService;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 카드 발급 가능 여부 단위 테스트 — 분실·정지처럼 <b>차단 상태인 카드를 새로 발급하면</b> 장비에서 막혀 문이 열리지 않고, 분실 카드가 회수 확인 없이 재사용된다.
 * 판정 기준은 블랙리스트와 같은 CS 코드의 {@code code_tag}. DB/Spring 없이 mock.
 */
class CardIssuableTest {

  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);

  private CardIssueService service() {
    return new CardIssueService(cardMapper, commonMapper, null);
  }

  /** 카드 1장 + 그 상태코드를 준비한다. tag='Y' 면 차단 상태(정상 아님). */
  private void card(int id, String no, String statusCode, String statusName, String tag) {
    TbCard c = new TbCard();
    c.setCardId(id);
    c.setBiostarCardValue(no);
    c.setCardStatus(statusCode);
    when(cardMapper.selectById(id)).thenReturn(c);
    TbCommon cs = new TbCommon();
    cs.setCodeId(statusCode);
    cs.setCodeName(statusName);
    cs.setCodeTag(tag);
    when(commonMapper.selectOne("CS", statusCode)).thenReturn(cs);
  }

  @Test
  void 정상_카드는_발급할_수_있다() {
    card(10, "1001", "CS01", "정상", "N");
    assertDoesNotThrow(() -> service().requireIssuable(10, null, "인원 P1"));
  }

  @Test
  void 분실_카드는_발급을_거부하고_사유를_알린다() {
    card(11, "1002", "CS02", "분실", "Y");

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().requireIssuable(11, null, "인원 P1"));

    assertTrue(ex.getMessage().contains("1002"), ex.getMessage()); // 어느 카드인지
    assertTrue(ex.getMessage().contains("분실"), ex.getMessage()); // 어떤 상태라서
    assertTrue(ex.getMessage().contains("카드등록관리"), ex.getMessage()); // 어떻게 푸는지
  }

  @Test
  void 반납_폐기_정지_카드도_모두_막는다() {
    card(12, "1003", "CS03", "반납", "Y");
    card(13, "1004", "CS04", "정지", "Y");
    card(14, "1005", "CS05", "폐기", "Y");
    for (int id : new int[] {12, 13, 14}) {
      assertThrows(BusinessException.class, () -> service().requireIssuable(id, null, null));
    }
  }

  @Test
  void 이미_그_대상이_들고_있던_카드는_막지_않는다() {
    // 분실 신고된 카드를 그대로 둔 채 다른 항목만 고치는 저장까지 막으면 정정이 불가능해진다
    card(15, "1006", "CS02", "분실", "Y");
    assertDoesNotThrow(() -> service().requireIssuable(15, Set.of(15), "인원 P1"));
  }

  @Test
  void 아직_행이_없는_새_카드번호는_통과한다() {
    when(cardMapper.selectById(99)).thenReturn(null);
    assertDoesNotThrow(() -> service().requireIssuable(99, null, null));
    assertDoesNotThrow(() -> service().requireIssuable(null, null, null)); // 카드 미선택
  }

  @Test
  void 상태코드가_공통코드에_없으면_정상으로_본다() {
    TbCard c = new TbCard();
    c.setCardId(20);
    c.setCardStatus("CSXX");
    when(cardMapper.selectById(20)).thenReturn(c);
    when(commonMapper.selectOne("CS", "CSXX")).thenReturn(null);
    assertDoesNotThrow(() -> service().requireIssuable(20, null, null));
  }
}
