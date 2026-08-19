package AirPort.adapter.parking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 주차 연동 DEBUG 로그 — 현장에서 요청·응답을 눈으로 볼 수 있어야 한다.
 *
 * <p>동시에 <b>새면 안 되는 것</b>이 있다: 연동 비밀번호(Authorization)와 성명. 그 경계를 여기서 고정한다.
 */
class AmanoParkingLogTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private ListAppender<ILoggingEvent> appender;
  private ch.qos.logback.classic.Logger logger;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/interop/", this::handle);
    server.start();

    // 현장은 logging.level.AirPort=DEBUG 한 줄만 켠다 — 그 상위 로거에 붙여 실제로 내려오는지 본다
    logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("AirPort");
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    server.stop(0);
  }

  private void handle(HttpExchange ex) throws IOException {
    byte[] out =
        ("{\"status\":\"200\",\"data\":{\"success\":true,\"errorMessage\":\"\"}}")
            .getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(200, out.length);
    ex.getResponseBody().write(out);
    ex.close();
  }

  private String logText() {
    StringBuilder sb = new StringBuilder();
    appender.list.forEach(e -> sb.append(e.getFormattedMessage()).append('\n'));
    return sb.toString();
  }

  @Test
  void 요청과_응답을_DEBUG_로_남긴다() {
    new AmanoParkingAdapter(
            MAPPER, "http://127.0.0.1:" + server.getAddress().getPort(), "ezcare", "pw!", 20, true)
        .register(new ParkingPassRequest("109거9672", "홍길동", "passType2", "20260819", "20260930"));

    String logs = logText();
    assertTrue(logs.contains("주차 API 요청"), logs);
    assertTrue(logs.contains("주차 API 응답"), logs);
    assertTrue(logs.contains("setCustdefInfo.do"), logs); // 어느 리소스인지
    assertTrue(logs.contains("109거9672"), logs); // 차량번호는 남는다(문제 추적에 필요)
    assertTrue(logs.contains("passType2"), logs);
    assertTrue(logs.contains("20260930"), logs); // 기간
    assertTrue(logs.contains("\"success\":true"), logs); // 아마노가 뭐라 답했는지
  }

  @Test
  void 성명과_연동_비밀번호는_로그에_남기지_않는다() {
    new AmanoParkingAdapter(
            MAPPER, "http://127.0.0.1:" + server.getAddress().getPort(), "ezcare", "pw!", 20, true)
        .register(new ParkingPassRequest("109거9672", "홍길동", "passType2", "20260819", "20260930"));

    String logs = logText();
    assertFalse(logs.contains("홍길동"), "성명이 로그에 남았다: " + logs);
    assertTrue(logs.contains("***"), logs);
    assertFalse(logs.contains("pw!"), "연동 비밀번호가 로그에 남았다: " + logs);
    assertFalse(logs.toLowerCase().contains("authorization"), logs);
  }

  @Test
  void DEBUG_를_끄면_본문이_남지_않는다() {
    logger.setLevel(Level.INFO);

    new AmanoParkingAdapter(
            MAPPER, "http://127.0.0.1:" + server.getAddress().getPort(), "ezcare", "pw!", 20, true)
        .register(new ParkingPassRequest("109거9672", "홍길동", "passType2", "20260819", "20260930"));

    assertFalse(logText().contains("주차 API 요청"), logText());
  }
}
