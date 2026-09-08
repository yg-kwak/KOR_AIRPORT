package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.TestKeys;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbBlacklistMapper;
import AirPort.model.TbBlacklist;
import AirPort.security.ARIAUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 제재인원 대조 — <b>성명+생년월일 쌍</b>으로만 막는다.
 *
 * <p>대조는 암호문끼리 비교한다(ARIA 는 결정적이라 같은 평문이면 같은 암호문). 평문으로 조회하면 아무것도 못 찾고, <b>조용히 통과</b>한다 — 차단이 통째로
 * 무력화되는데 화면에는 아무 표시가 없다. 그래서 "무엇을 넘겼는가"를 여기서 고정한다.
 */
class BlacklistBlockTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private final TbBlacklistMapper blacklistMapper = mock(TbBlacklistMapper.class);
  private final BlacklistService service =
      new BlacklistService(blacklistMapper, mock(AuditService.class), mock(MenuAuthService.class));

  private static TbBlacklist ban(String start, String end, String remark) {
    TbBlacklist b = new TbBlacklist();
    b.setBanStartDt(start);
    b.setBanEndDt(end);
    b.setRemark(remark);
    return b;
  }

  /** 그 사람이 명단에 있는 상황을 만든다. */
  private void listed(String name, String birth, TbBlacklist row) {
    when(blacklistMapper.selectActiveBan(ARIAUtil.ariaEncrypt(name), ARIAUtil.ariaEncrypt(birth)))
        .thenReturn(row);
  }

  @Test
  void 대조는_암호문으로_넘긴다() {
    // 평문으로 조회하면 영영 못 찾는다 — 차단이 조용히 무력화되는 자리라 인자를 고정한다
    service.requireNotBanned("홍길동", "1990-01-01");

    verify(blacklistMapper)
        .selectActiveBan(ARIAUtil.ariaEncrypt("홍길동"), ARIAUtil.ariaEncrypt("1990-01-01"));
  }

  @Test
  void 명단에_있으면_막는다() {
    listed("홍길동", "1990-01-01", ban(null, null, null));

    BusinessException e =
        assertThrows(BusinessException.class, () -> service.requireNotBanned("홍길동", "1990-01-01"));
    assertEquals("제재인원에 등록된 사용자입니다.", e.getMessage());
  }

  @Test
  void 차단_문구에_제재_사유를_싣지_않는다() {
    // 비고에는 제재 경위가 적힌다. 이 문구는 방문 접수 창구·키오스크에서도 뜨므로
    // 당사자나 옆사람에게 그대로 보인다 — 무엇 때문에 막혔는지는 담당자만 본다
    listed("홍길동", "1990-01-01", ban("2026-09-01", "2026-12-31", "출입증 무단 대여"));

    String msg =
        assertThrows(BusinessException.class, () -> service.requireNotBanned("홍길동", "1990-01-01"))
            .getMessage();
    assertEquals("제재인원에 등록된 사용자입니다.", msg);
    assertFalse(msg.contains("출입증 무단 대여"), msg);
  }

  @Test
  void 명단에_없으면_통과한다() {
    service.requireNotBanned("홍길동", "1990-01-01"); // 예외 없음
  }

  @Test
  void 성명이나_생년월일이_없으면_대조하지_않는다() {
    // 한쪽만으로는 사람을 특정할 수 없다. 동명이인을 통째로 막는 쪽이 훨씬 나쁘다
    service.requireNotBanned(null, "1990-01-01");
    service.requireNotBanned("홍길동", "  ");

    verify(blacklistMapper, never()).selectActiveBan(any(), any());
  }

  // ── 목록 상태 표기 ────────────────────────────────────────────────────────

  @Test
  void 해제된_제재는_해제로_표기한다() {
    // 해제분도 목록에 남는다 — 이 화면의 이력이 그것이다. 기간이 남아 있어도 상태는 '해제'다
    TbBlacklist row = ban("2026-09-01", "2099-12-31", null);
    row.setDelYn("Y");
    row.setPersonName(ARIAUtil.ariaEncrypt("홍길동"));
    row.setBirthDate(ARIAUtil.ariaEncrypt("1990-01-01"));
    when(blacklistMapper.selectCount(any())).thenReturn(1);
    when(blacklistMapper.selectList(any())).thenReturn(java.util.List.of(row));

    var page = service.list(new AirPort.model.BlacklistSearchParam(), null, 503);

    assertEquals("해제", page.getContent().get(0).getBanStatus());
    assertEquals("홍길동", page.getContent().get(0).getPersonName()); // 복호화도 함께
  }

  // ── 인원상태 [정지] 연동 ──────────────────────────────────────────────────

  @Test
  void 정지로_바뀌면_소속과_오늘날짜로_제재인원에_올린다() {
    when(blacklistMapper.selectByPerson(any(), any(), isNull())).thenReturn(null);

    assertTrue(service.addFromPerson("홍길동", "1990-01-01", "㈜대한기술", null, 201));

    org.mockito.ArgumentCaptor<TbBlacklist> saved =
        org.mockito.ArgumentCaptor.forClass(TbBlacklist.class);
    verify(blacklistMapper).insert(saved.capture());
    assertEquals("㈜대한기술", saved.getValue().getAffiliation());
    // 정지로 바꾼 그날부터 막는다 — 비워 두면 언제부터인지 이력에 남지 않는다
    assertEquals(java.time.LocalDate.now().toString(), saved.getValue().getBanStartDt());
    assertNull(saved.getValue().getBanEndDt()); // 종료는 담당자가 정한다(무기한)
  }

  @Test
  void 이미_올라_있으면_다시_올리지_않는다() {
    when(blacklistMapper.selectByPerson(
            eq(ARIAUtil.ariaEncrypt("홍길동")), eq(ARIAUtil.ariaEncrypt("1990-01-01")), isNull()))
        .thenReturn(new TbBlacklist());

    assertEquals(false, service.addFromPerson("홍길동", "1990-01-01", null, null, 201));
    verify(blacklistMapper, never()).insert(any(TbBlacklist.class));
  }

  @Test
  void 생년월일이_없으면_올리지_않는다() {
    // 생년월일 없이 올리면 동명이인이 모두 막힌다
    assertEquals(false, service.addFromPerson("홍길동", null, null, null, 201));
    verify(blacklistMapper, never()).insert(any(TbBlacklist.class));
  }
}
