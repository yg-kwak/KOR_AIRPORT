package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.adapter.biostar.BiostarAuthEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 실시간 이벤트 모니터링의 판정·표기 규칙 검증.
 *
 * <p>가장 위험한 것은 <b>조용히 안 보이는 것</b>이다 — 성공 판정이 좁으면 방문객(카드 전용 인증)이 화면에 아예 뜨지 않고, 그것이 "지금 아무도 안 지나간다"와
 * 구분되지 않는다.
 */
class MonitorEventTest {

  private static BiostarAuthEvent event(String code, String name) {
    return new BiostarAuthEvent(
        code, name, "2026-08-11T01:38:01.00Z", "543737030", "F2", "400001", "img");
  }

  @Test
  void 통과_이벤트_세_가지는_모두_인증_성공으로_표기한다() {
    // 카드(4102) · 카드+얼굴(4106) · 얼굴(4867) — 수단은 달라도 지나갔다는 사실은 하나다
    for (String code : new String[] {"4102", "4106", "4867"}) {
      BiostarAuthEvent e = event(code, "VERIFY_SUCCESS");
      assertEquals("O 인증 성공", e.resultLabel(), code);
      assertTrue(e.granted(), code);
    }
  }

  @Test
  void 출입거부는_사유대로_표기하고_거부로_본다() {
    BiostarAuthEvent anti = event("6405", "ACCESS_DENIED_ANTI_PASSBACK");
    assertEquals("X 안티 패스", anti.resultLabel());
    assertFalse(anti.granted());
    assertTrue(anti.displayable(), "거부도 화면에 올려야 한다 — 못 들어간 사람이야말로 봐야 한다");

    BiostarAuthEvent zone = event("6401", "ACCESS_DENIED_INVALID_GROUP");
    assertEquals("X 출입제한구역", zone.resultLabel());
    assertFalse(zone.granted());
  }

  @Test
  void 표에_없는_성공은_이름으로_잡는다() {
    // 표에 없는 인증 수단 조합이 현장에서 쓰이면 그 사람만 화면에서 사라진다
    BiostarAuthEvent e = event("4999", "VERIFY_SUCCESS_FINGERPRINT");
    assertEquals("O 인증 성공", e.resultLabel());
    assertTrue(e.granted());
  }

  @Test
  void 이름이_비어_와도_알려진_코드는_표기한다() {
    assertTrue(event("4102", null).granted());
    assertEquals("X 안티 패스", event("6405", "").resultLabel());
  }

  @Test
  void 표기_대상이_아닌_이벤트는_올리지_않는다() {
    // 문 열림·장치 연결까지 띄우면 정작 사람이 지나간 기록이 묻힌다
    assertFalse(event("5000", "DEVICE_TCP_CONNECTED").displayable());
    assertFalse(event("4104", "VERIFY_FAIL_CARD").displayable());
    assertNull(event("5000", null).resultLabel());
  }

  @Test
  void 허가구역_표기는_공용_규칙을_쓴다() {
    // 표기 규칙 자체는 AccessAreasTest 가 지킨다. 여기서는 이 화면이 그 규칙을 쓰는지만 본다 —
    // 제 나름대로 이어 붙이기 시작하면 같은 사람의 구역이 신청서·카드·장비 부서와 갈린다
    assertEquals("125", AirPort.common.AccessAreas.key(List.of("인원구역1", "인원구역2", "인원구역5")));
    assertEquals("1게이트", AirPort.common.AccessAreas.key(List.of("인원구역1", "게이트")));
    assertEquals("", AirPort.common.AccessAreas.key(List.of()));
  }

  @Test
  void 이벤트_시각은_시분초만_남긴다() {
    assertEquals("01:38:01", MonitorEnrichService.time("2026-08-11T01:38:01.00Z"));
  }

  @Test
  void 형식이_다른_시각은_원문을_남긴다() {
    assertEquals("어제", MonitorEnrichService.time("어제"));
  }
}
