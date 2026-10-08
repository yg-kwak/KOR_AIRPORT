package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * 소켓이 조용히 죽은 것을 알아채는가 — "오래 켜 두면 인증해도 화면이 안 바뀌고, 새로고침해도 그대로"의 원인.
 *
 * <p>REST 확인(events/start·세션 비교)은 통과하는데 소켓만 죽은 상태를 ping/pong 과 마지막 프레임 시각으로 잡는다.
 */
class BiostarSocketProbeTest {

  /** ping 을 보내면 장비가 곧바로 답하는 소켓. */
  private static WebSocket answering(BiostarSocketProbe probe) {
    WebSocket ws = mock(WebSocket.class);
    when(ws.sendPing(any(ByteBuffer.class)))
        .thenAnswer(
            inv -> {
              probe.pong(); // 실제로는 Listener.onPong 이 부른다
              return CompletableFuture.completedFuture(ws);
            });
    return ws;
  }

  /** ping 은 나가지만(소켓 객체는 열려 있다) 답이 오지 않는 소켓 — 방화벽이 연결을 버린 경우. */
  private static WebSocket silent() {
    WebSocket ws = mock(WebSocket.class);
    when(ws.sendPing(any(ByteBuffer.class))).thenReturn(CompletableFuture.completedFuture(ws));
    return ws;
  }

  @Test
  void 답이_오고_최근에_받은_프레임이_있으면_살아_있다() {
    BiostarSocketProbe probe = new BiostarSocketProbe();
    probe.frame();
    assertEquals(BiostarSocketProbe.Result.ALIVE, probe.check(answering(probe), 1));
  }

  @Test
  void ping_에_답이_없으면_죽은_것이다() {
    BiostarSocketProbe probe = new BiostarSocketProbe();
    probe.frame();
    assertEquals(BiostarSocketProbe.Result.DEAD, probe.check(silent(), 1));
  }

  @Test
  void ping_을_보내지도_못하면_죽은_것이다() {
    BiostarSocketProbe probe = new BiostarSocketProbe();
    probe.frame();
    WebSocket ws = mock(WebSocket.class);
    when(ws.sendPing(any(ByteBuffer.class)))
        .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("Output closed")));
    assertEquals(BiostarSocketProbe.Result.DEAD, probe.check(ws, 1));
  }

  @Test
  void 답은_하는데_오래_받은_것이_없으면_다시_연다() {
    BiostarSocketProbe probe = new BiostarSocketProbe(); // 프레임을 받은 적 없음 = 아주 오래 조용함
    assertEquals(BiostarSocketProbe.Result.QUIET, probe.check(answering(probe), 1));
  }

  @Test
  void 새_구독자로_인한_확인은_몰려도_한_번만() {
    BiostarSocketProbe probe = new BiostarSocketProbe();
    assertTrue(probe.claim(), "처음 온 화면은 확인한다");
    assertFalse(probe.claim(), "EventSource 가 3초마다 다시 붙어도 장비를 두드리지 않는다");
  }

  /** 소켓 쪽 — 죽은 소켓은 '준비됨'에서 내려가고 사유가 화면까지 간다. 확인 전에 상태를 직접 세운다(연결은 장비가 있어야 한다). */
  @Test
  void 죽은_소켓은_준비됨에서_내려가고_사유가_남는다() throws Exception {
    BiostarSession session = mock(BiostarSession.class);
    when(session.sessionId(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
        .thenThrow(new BiostarSessionException("재연결 시도 — 이 테스트에서는 실패시킨다"));
    BiostarEventSocket s =
        new BiostarEventSocket(new ObjectMapper(), session, mock(BiostarEventAdapter.class));
    WebSocket ws = silent();
    set(s, "wanted", true);
    set(s, "ready", true);
    set(s, "socket", ws);

    s.probe(1);

    assertFalse(s.isReady(), "죽은 소켓을 '수신 중'으로 두면 안 된다");
    assertTrue(s.error() != null && s.error().contains("ping"), "사유: " + s.error());
    verify(ws).abort();
    s.stop("monitor");
  }

  @Test
  void 확인할_소켓이_없으면_아무것도_하지_않는다() {
    BiostarEventSocket s =
        new BiostarEventSocket(
            new ObjectMapper(), mock(BiostarSession.class), mock(BiostarEventAdapter.class));
    s.probe(1);
    assertFalse(s.isReady());
    assertNull(s.error());
  }

  @Test
  void 살아_있는_소켓은_건드리지_않는다() throws Exception {
    BiostarEventSocket s =
        new BiostarEventSocket(
            new ObjectMapper(), mock(BiostarSession.class), mock(BiostarEventAdapter.class));
    BiostarSocketProbe liveness = (BiostarSocketProbe) get(s, "liveness");
    liveness.frame();
    WebSocket ws = answering(liveness);
    set(s, "wanted", true);
    set(s, "ready", true);
    set(s, "socket", ws);

    s.probe(1);

    assertTrue(s.isReady());
    verify(ws, never()).abort();
  }

  private static void set(Object target, String field, Object value) throws Exception {
    java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Object get(Object target, String field) throws Exception {
    java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return f.get(target);
  }
}
