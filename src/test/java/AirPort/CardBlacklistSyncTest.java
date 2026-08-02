package AirPort;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import AirPort.service.CodeValidationService;
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
        cardMapper,
        systemMapper,
        commonMapper,
        adapter,
        menuAuthService,
        auditService,
        mock(CodeValidationService.class)); // 코드 존재 검증은 CodeValidationServiceTest 담당
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

  @Test
  void 장비_미등록_인원카드는_발급_시점에_등록한다() {
    TbCard unregistered = card("CS01");
    unregistered.setBiostarCardId(null); // 장비 미등록(테스트 시드 카드 등)
    when(cardMapper.selectById(10)).thenReturn(unregistered);
    when(adapter.createCard(anyString(), anyString(), any(), eq("11111111")))
        .thenReturn(AirPort.adapter.BiostarCard.ok("99", "11111111"));

    org.springframework.transaction.support.TransactionSynchronizationManager
        .setActualTransactionActive(true);
    try {
      service().ensureBiostarCard(10, null, 801);
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .setActualTransactionActive(false);
    }
    verify(cardMapper).updateBiostarCardId(10, "99"); // 등록 후 id 저장
  }

  @Test
  void 장비_등록_실패면_예외로_발급이_취소된다() {
    TbCard unregistered = card("CS01");
    unregistered.setBiostarCardId(null);
    when(cardMapper.selectById(10)).thenReturn(unregistered);
    when(adapter.createCard(anyString(), anyString(), any(), eq("11111111")))
        .thenReturn(AirPort.adapter.BiostarCard.fail("HTTP 500"));

    org.springframework.transaction.support.TransactionSynchronizationManager
        .setActualTransactionActive(true);
    try {
      BusinessException ex =
          assertThrows(BusinessException.class, () -> service().ensureBiostarCard(10, null, 801));
      assertTrue(ex.getMessage().contains("카드 등록 실패"));
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .setActualTransactionActive(false);
    }
    verify(cardMapper, never()).updateBiostarCardId(anyInt(), anyString());
  }

  @Test
  void 차량카드는_장비_등록_대상이_아니다() {
    TbCard carCard = card("CS01");
    carCard.setCardType("CDT02");
    carCard.setBiostarCardId(null);
    when(cardMapper.selectById(10)).thenReturn(carCard);
    service().ensureBiostarCard(10, null, 801);
    verify(adapter, never()).createCard(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 장비에_이미_있는_카드는_다시_등록하지_않는다() {
    TbCard registered = card("CS01"); // biostar_card_id = 77
    when(cardMapper.selectById(10)).thenReturn(registered);
    when(adapter.registeredCardIds(anyString(), anyString(), any()))
        .thenReturn(java.util.Set.of("77", "88"));

    service().ensureBiostarCard(10, null, 801);
    verify(adapter, never()).createCard(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 장비에서_삭제된_카드는_재등록해_새_id로_맞춘다() {
    TbCard stale = card("CS01"); // DB 에는 77 이지만 장비에는 없음
    when(cardMapper.selectById(10)).thenReturn(stale);
    when(adapter.registeredCardIds(anyString(), anyString(), any()))
        .thenReturn(java.util.Set.of("88")); // 77 없음
    when(adapter.createCard(anyString(), anyString(), any(), eq("11111111")))
        .thenReturn(AirPort.adapter.BiostarCard.ok("99", "11111111"));

    org.springframework.transaction.support.TransactionSynchronizationManager
        .setActualTransactionActive(true);
    try {
      service().ensureBiostarCard(10, null, 801);
    } finally {
      org.springframework.transaction.support.TransactionSynchronizationManager
          .setActualTransactionActive(false);
    }
    verify(cardMapper).updateBiostarCardId(10, "99"); // 새 id 로 갱신
  }

  @Test
  void 카드목록_조회_실패면_있는것처럼_진행하지_않고_예외() {
    when(cardMapper.selectById(10)).thenReturn(card("CS01"));
    when(adapter.registeredCardIds(anyString(), anyString(), any()))
        .thenThrow(new AirPort.adapter.BiostarSessionException("연결 실패"));

    assertThrows(
        AirPort.adapter.BiostarSessionException.class,
        () -> service().ensureBiostarCard(10, null, 801));
    verify(adapter, never()).createCard(anyString(), anyString(), any(), anyString());
  }
}
