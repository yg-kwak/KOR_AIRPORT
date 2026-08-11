package AirPort.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import AirPort.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 브라우저가 먼저 끊었을 때 — 서버 잘못이 아니므로 ERROR 도, 응답 본문도 남기지 않는다.
 *
 * <p>실시간 이벤트 화면은 하루 종일 열려 있고 그동안 스트림은 여러 번 끊겼다 붙는다. 그때마다 ERROR 스택이 쌓이면 정작 진짜 오류가 묻힌다. 게다가 본문을 쓰려 들면
 * 두 번째 실패가 난다 — 응답은 이미 나갔고 Content-Type 이 {@code text/event-stream} 이라 JSON 을 실을 수 없다.
 */
class ClientDisconnectTest {

  /** 컨테이너가 던지는 이름 그대로 흉내 낸다 — 타입이 아니라 이름으로 판별하기 때문이다. */
  static class ClientAbortException extends java.io.IOException {
    ClientAbortException(String m) {
      super(m);
    }
  }

  static class CloseNowException extends java.io.IOException {
    CloseNowException(String m) {
      super(m);
    }
  }

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void 클라이언트가_끊으면_본문을_쓰지_않는다() {
    Exception e =
        new CloseNowException("This stream is in state [CLOSED_RST_RX] and is not writable");

    assertNull(handler.handleEtc(e, new MockHttpServletResponse()));
  }

  @Test
  void 원인_사슬_안쪽에_있어도_알아본다() {
    // 실제로는 IllegalStateException 등으로 한 번 감싸여 올라온다
    Exception e =
        new IllegalStateException("Failed to send", new ClientAbortException("broken pipe"));

    assertNull(handler.handleEtc(e, new MockHttpServletResponse()));
  }

  @Test
  void 응답이_이미_나갔으면_본문을_쓰지_않는다() {
    // 끊김이 아니어도 마찬가지다 — 헤더가 나간 뒤에는 상태코드도 본문도 바꿀 수 없다
    MockHttpServletResponse committed = new MockHttpServletResponse();
    committed.setCommitted(true);

    assertNull(handler.handleEtc(new RuntimeException("무언가 잘못됨"), committed));
  }

  @Test
  void 진짜_서버_오류는_그대로_500_을_낸다() {
    var res = handler.handleEtc(new RuntimeException("널 참조"), new MockHttpServletResponse());

    assertNotNull(res, "평범한 오류까지 삼키면 장애가 조용해진다");
    assertEquals(500, res.getStatusCode().value());
  }
}
