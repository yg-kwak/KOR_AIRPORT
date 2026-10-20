package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import AirPort.mapper.TbSystemLogMapper;
import AirPort.model.TbSystemLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 감사 문구가 칸(nvarchar(1000))보다 길어도 기록은 실패하지 않는다.
 *
 * <p>넘쳐 예외가 나면, 이미 끝난 일(BiostarX 반영 등)이 화면에는 실패로 보인다 — 에스코트 288명 추가에서 실제로 났다.
 */
class AuditClipTest {

  @Test
  void 칸보다_긴_문구는_잘라서_남긴다() {
    TbSystemLogMapper mapper = mock(TbSystemLogMapper.class);
    new AuditService(mapper)
        .log(null, AuditService.UPDATE, 504, "가".repeat(4000), "나".repeat(1001));
    ArgumentCaptor<TbSystemLog> row = ArgumentCaptor.forClass(TbSystemLog.class);
    verify(mapper).insert(row.capture());
    assertEquals(AuditService.TEXT_MAX, row.getValue().getActionDetail().length());
    assertTrue(row.getValue().getActionDetail().endsWith("…"), "잘린 것이 보여야 한다");
    assertEquals(AuditService.TEXT_MAX, row.getValue().getRemark().length());
  }

  @Test
  void 짧은_문구와_빈_값은_그대로다() {
    assertEquals("조회", AuditService.clip("조회"));
    assertEquals("가".repeat(1000), AuditService.clip("가".repeat(1000)));
    assertNull(AuditService.clip(null));
  }
}
