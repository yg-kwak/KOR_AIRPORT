package AirPort;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.adapter.BiostarCardAdapter;
import AirPort.adapter.BiostarResult;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.model.TbSystem;
import AirPort.service.AuditService;
import AirPort.service.CardService;
import AirPort.service.MenuAuthService;
import org.junit.jupiter.api.Test;

/**
 * 카드 블랙리스트 동기화 규칙 단위 테스트 — BiostarX 는 멱등하지 않아(이미 해제된 카드 재해제 시 HTTP 500) 차단 여부가 바뀐 경우에만 호출해야 한다. 실패
 * 정책도 비대칭(차단=롤백, 해제=경고). DB/Spring 없이 mock.
 */
class CardBlacklistSyncTest {

  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbSystemMapper systemMapper = mock(TbSystemMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final BiostarCardAdapter adapter = mock(BiostarCardAdapter.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final AuditService auditService = mock(AuditService.class);

  private CardService service() {
    TbSystem cfg = new TbSystem();
    cfg.setBiostarIp("10.0.0.1");
    cfg.setBiostarId("admin");
    when(systemMapper.selectOne()).thenReturn(cfg);
    // CS01 정상=비차단(N), CS02 분실=차단(Y)
    when(commonMapper.selectOne("CS", "CS01")).thenReturn(code("N"));
    when(commonMapper.selectOne("CS", "CS02")).thenReturn(code("Y"));
    return new CardService(
        cardMapper, systemMapper, commonMapper, adapter, menuAuthService, auditService);
  }

  private static TbCommon code(String tag) {
    TbCommon c = new TbCommon();
    c.setCodeTag(tag);
    return c;
  }

  private static TbCard card(String status) {
    TbCard c = new TbCard();
    c.setCardId(10);
    c.setBiostarCardId("77");
    c.setBiostarCardValue("11111111");
    c.setCardType("CDT01");
    c.setPassType("PT01");
    c.setCardName("출입카드");
    c.setCardStatus(status);
    c.setDelYn("N");
    return c;
  }

  @Test
  void 상태가_그대로면_장비를_호출하지_않는다() {
    when(cardMapper.selectById(10)).thenReturn(card("CS01"));
    service().updateCard(card("CS01"), null, 801); // 정상 → 정상
    verify(adapter, never()).removeBlacklist(anyString(), anyString(), anyString(), anyString());
    verify(adapter, never()).blacklistCard(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void 차단_실패는_저장을_롤백한다() {
    when(cardMapper.selectById(10)).thenReturn(card("CS01"));
    when(adapter.blacklistCard(anyString(), anyString(), any(), eq("77")))
        .thenReturn(BiostarResult.fail("HTTP 500"));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().updateCard(card("CS02"), null, 801));
    assertTrue(ex.getMessage().contains("차단 실패"));
    verify(auditService).logAlways(any(), any(), any(), any());
  }

  @Test
  void 해제_실패는_경고만_하고_저장은_유지한다() {
    when(cardMapper.selectById(10)).thenReturn(card("CS02")); // 분실(차단) → 정상(해제)
    when(adapter.removeBlacklist(anyString(), anyString(), any(), eq("77")))
        .thenReturn(BiostarResult.fail("HTTP 500"));

    assertDoesNotThrow(() -> service().updateCard(card("CS01"), null, 801));
    verify(auditService).logAlways(any(), any(), any(), any()); // 실패는 감사에 남긴다
  }
}
