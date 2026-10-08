package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * 장비 소켓은 언제나 <b>하나</b>다 — 둘이 되면 같은 인증이 화면에 두 번 뜬다.
 *
 * <p>지키는 것은 셋이다: 실패한 연결 시도의 소켓은 닫는다 / 끊김 알림·이벤트는 지금 쓰는 소켓의 것만 받는다 / 이미 붙어 있으면 새로 붙지 않는다. 연결 상태는 장비
 * 없이 세우려고 필드에 직접 넣는다.
 */
class BiostarEventSocketSingleTest {

  private static final String EVENT =
      "{\"Event\":{\"event_type_id\":{\"code\":\"4867\",\"name\":\"IDENTIFY_SUCCESS_FACE\"},"
          + "\"datetime\":\"2026-10-08T05:00:00.00Z\",\"device_id\":{\"id\":\"1\",\"name\":\"F2\"},"
          + "\"user_id\":{\"user_id\":\"2\",\"name\":\"ZZ\"}}}";

  private final BiostarSession session = mock(BiostarSession.class);
  private final BiostarEventSocket s =
      new BiostarEventSocket(new ObjectMapper(), session, mock(BiostarEventAdapter.class));
  private final AtomicInteger delivered = new AtomicInteger();

  /** 소켓 하나가 붙어 이벤트를 받는 중인 상태. */
  private WebSocket connected() throws Exception {
    WebSocket ws = mock(WebSocket.class);
    set("wanted", true);
    set("ready", true);
    set("socket", ws);
    @SuppressWarnings("unchecked")
    Map<String, Consumer<BiostarAuthEvent>> sinks =
        (Map<String, Consumer<BiostarAuthEvent>>) get("sinks");
    sinks.put("monitor", e -> delivered.incrementAndGet());
    return ws;
  }

  /** 장비 소켓의 수신기 — 소켓마다 하나씩 만들어진다. */
  private WebSocket.Listener listener() throws Exception {
    Class<?> c = Class.forName(BiostarEventSocket.class.getName() + "$Listener");
    var ctor = c.getDeclaredConstructor(BiostarEventSocket.class, CompletableFuture.class);
    ctor.setAccessible(true);
    return (WebSocket.Listener) ctor.newInstance(s, new CompletableFuture<String>());
  }

  @Test
  void 버린_소켓이_보낸_이벤트는_화면에_넘기지_않는다() throws Exception {
    WebSocket current = connected();
    WebSocket stale = mock(WebSocket.class);

    listener().onText(stale, EVENT, true);
    assertEquals(0, delivered.get(), "버린 소켓의 이벤트까지 넘기면 같은 인증이 두 번 뜬다");

    listener().onText(current, EVENT, true);
    assertEquals(1, delivered.get());
  }

  @Test
  void 버린_소켓의_끊김_알림은_지금_소켓을_끊지_않는다() throws Exception {
    WebSocket current = connected();

    listener().onClose(mock(WebSocket.class), 1006, "");
    listener().onError(mock(WebSocket.class), new java.io.IOException("reset"));

    assertTrue(s.isReady());
    verify(current, never()).abort();
  }

  @Test
  void 지금_소켓의_끊김_알림은_받아들인다() throws Exception {
    when(session.sessionId(anyString(), any(), any(), anyBoolean()))
        .thenThrow(new BiostarSessionException("재연결은 이 테스트에서 실패시킨다"));
    WebSocket current = connected();

    listener().onClose(current, 1006, "");

    assertFalse(s.isReady());
    verify(current).abort();
    s.stop("monitor");
  }

  @Test
  void 이미_붙어_있으면_새로_붙지_않는다() throws Exception {
    connected();

    invokeConnect();

    verify(session, never()).sessionId(anyString(), any(), any(), anyBoolean());
  }

  @Test
  void 실패한_연결_시도의_소켓은_닫는다() throws Exception {
    // 소켓은 열렸는데 세션 알림 전송이 실패 — 버려 두면 장비 쪽 연결이 둘이 된다
    WebSocket half = mock(WebSocket.class);
    when(half.sendText(any(), anyBoolean()))
        .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("broken pipe")));
    WebSocket.Builder builder = mock(WebSocket.Builder.class);
    when(builder.buildAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(half));
    HttpClient client = mock(HttpClient.class);
    when(client.newWebSocketBuilder()).thenReturn(builder);
    when(session.client()).thenReturn(client);
    when(session.sessionId(anyString(), any(), any(), anyBoolean())).thenReturn("sid");
    set("wanted", true);
    set("ip", "10.0.0.1");

    invokeConnect();

    verify(half).abort();
    assertFalse(s.isReady());
    s.stop("monitor");
  }

  private void invokeConnect() throws Exception {
    var m = BiostarEventSocket.class.getDeclaredMethod("connect");
    m.setAccessible(true);
    m.invoke(s);
  }

  private void set(String field, Object value) throws Exception {
    var f = BiostarEventSocket.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(s, value);
  }

  private Object get(String field) throws Exception {
    var f = BiostarEventSocket.class.getDeclaredField(field);
    f.setAccessible(true);
    return f.get(s);
  }
}
