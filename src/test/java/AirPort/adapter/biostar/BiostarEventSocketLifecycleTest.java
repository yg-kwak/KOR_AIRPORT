package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * 소켓 수명 검증 — 이 화면은 하루 종일 켜 두므로 <b>죽은 줄 모르는 것</b>이 가장 큰 위험이다.
 *
 * <p>여기서 지키는 불변식은 하나다: <b>이벤트를 받을 수 없으면 {@code isReady()} 가 거짓이고 사유가 남는다.</b> 소켓만 열린 상태를 '연결됨'으로
 * 보고하면 화면은 "수신 중"인데 이벤트는 영영 오지 않는다 — 상황실에서 아무도 눈치채지 못한다.
 */
class BiostarEventSocketLifecycleTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final BiostarSession session = mock(BiostarSession.class);
  private final BiostarEventAdapter eventAdapter = mock(BiostarEventAdapter.class);

  private BiostarEventSocket socket() {
    return new BiostarEventSocket(mapper, session, eventAdapter);
  }

  /** 연결은 비동기라 상태가 바뀔 때까지 기다린다. */
  private static void awaitTrue(BooleanSupplier condition, String what) {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    fail(what + " — 5초 안에 이뤄지지 않았다");
  }

  private void loginFails() throws Exception {
    when(session.sessionId(anyString(), any(), any(), anyBoolean()))
        .thenThrow(new BiostarSessionException("로그인 ID 또는 비밀번호가 맞지 않습니다."));
  }

  @Test
  void 로그인이_안_되면_연결됨으로_보고하지_않는다() throws Exception {
    loginFails();
    BiostarEventSocket s = socket();
    AtomicInteger notified = new AtomicInteger();

    s.start("10.0.0.1", "admin", "pw", e -> {}, notified::incrementAndGet);
    awaitTrue(() -> s.error() != null, "실패 사유가 남아야 한다");

    assertFalse(s.isReady(), "이벤트를 받을 수 없는데 준비됨으로 보고하면 안 된다");
    assertNotNull(s.error());
    assertEquals(1, notified.get(), "실패도 화면에 알려야 한다");
    s.stop();
  }

  @Test
  void 구독자가_없어지면_남은_오류가_지워진다() throws Exception {
    loginFails();
    BiostarEventSocket s = socket();
    s.start("10.0.0.1", "admin", "pw", e -> {}, () -> {});
    awaitTrue(() -> s.error() != null, "실패 사유가 남아야 한다");

    s.stop();

    assertFalse(s.isReady());
    assertNull(s.error(), "다음 구독자에게 지난 오류가 새어 나가면 안 된다");
  }

  @Test
  void 안_돌고_있으면_장비를_두드리지_않는다() {
    socket().verify(); // 구독자가 없는 상태

    verify(eventAdapter, never()).start(any(), any(), any());
  }

  @Test
  void 재연결을_기다리는_중에는_확인하지_않는다() throws Exception {
    // 이미 실패해 재연결 대기 중인데 또 확인하면 장비를 이중으로 두드린다
    loginFails();
    BiostarEventSocket s = socket();
    s.start("10.0.0.1", "admin", "pw", e -> {}, () -> {});
    awaitTrue(() -> s.error() != null, "실패 사유가 남아야 한다");

    s.verify();

    verify(eventAdapter, never()).start(any(), any(), any());
    s.stop();
  }
}
