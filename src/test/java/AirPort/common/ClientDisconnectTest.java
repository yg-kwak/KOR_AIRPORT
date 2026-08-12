package AirPort.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.common.exception.GlobalExceptionHandler;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
    // 헤더가 나간 뒤에는 상태코드도 본문도 바꿀 수 없다 — 본문만 생략한다.
    // (스택을 남기는지는 아래 테스트가 지킨다)
    MockHttpServletResponse committed = new MockHttpServletResponse();
    committed.setCommitted(true);

    assertNull(handler.handleEtc(new RuntimeException("무언가 잘못됨"), committed));
  }

  /**
   * 응답이 나간 뒤에 난 <b>진짜 오류</b>는 끊김과 다르게 다뤄야 한다.
   *
   * <p>엑셀 다운로드처럼 응답에 직접 쓰는 경로는 실패해도 헤더가 이미 커밋된 상태다. 이것을 끊김과 한 갈래로 묶으면 장애가 "클라이언트가 끊었다"는 INFO 한 줄로
   * 조용해진다 — 본문은 못 쓰더라도 스택은 반드시 남아야 한다.
   */
  @Test
  void 응답이_나간_뒤의_진짜_오류도_로그에는_남는다() {
    MockHttpServletResponse committed = new MockHttpServletResponse();
    committed.setCommitted(true);
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    logger.addAppender(captured);
    try {
      handler.handleEtc(new RuntimeException("엑셀 쓰는 중 실패"), committed);
    } finally {
      logger.detachAppender(captured);
    }

    assertTrue(
        captured.list.stream().anyMatch(ev -> ev.getLevel() == Level.ERROR),
        "커밋된 응답에서 난 진짜 오류가 ERROR 로 남지 않으면 장애를 놓친다: " + captured.list);
  }

  @Test
  void 끊김은_ERROR_로_남기지_않는다() {
    // 화면을 닫을 때마다 나는 정상적인 일이다 — ERROR 로 쌓이면 진짜 오류가 묻힌다
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    logger.addAppender(captured);
    try {
      handler.handleEtc(
          new CloseNowException("stream is not writable"), new MockHttpServletResponse());
    } finally {
      logger.detachAppender(captured);
    }

    assertTrue(
        captured.list.stream().noneMatch(ev -> ev.getLevel() == Level.ERROR),
        "끊김은 ERROR 가 아니다: " + captured.list);
  }

  @Test
  void 진짜_서버_오류는_그대로_500_을_낸다() {
    var res = handler.handleEtc(new RuntimeException("널 참조"), new MockHttpServletResponse());

    assertNotNull(res, "평범한 오류까지 삼키면 장애가 조용해진다");
    assertEquals(500, res.getStatusCode().value());
  }
}
