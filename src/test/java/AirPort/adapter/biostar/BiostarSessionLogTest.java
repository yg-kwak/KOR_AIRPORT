package AirPort.adapter.biostar;

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
 * BiostarX 요청·응답 DEBUG 로그 — 주차와 <b>같은 스위치</b>(logging.level.AirPort=DEBUG)로 켜져야 한다.
 *
 * <p>연동마다 켜는 방법이 다르면 현장에서 "주차는 보이는데 장비는 안 보인다"가 된다.
 */
class BiostarSessionLogTest {

  private HttpServer server;
  private ListAppender<ILoggingEvent> appender;
  private ch.qos.logback.classic.Logger root;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/", this::handle);
    server.start();

    root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("AirPort");
    appender = new ListAppender<>();
    appender.start();
    root.addAppender(appender);
    root.setLevel(Level.DEBUG);
  }

  @AfterEach
  void tearDown() {
    root.detachAppender(appender);
    server.stop(0);
  }

  private void handle(HttpExchange ex) throws IOException {
    byte[] out =
        "{\"User\":{\"user_id\":\"400001\",\"name\":\"홍길동\"}}".getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("bs-session-id", "SESSION-SECRET-123");
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
  void 요청과_응답을_남기되_비밀번호와_성명은_가린다() throws Exception {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    new BiostarSession(new ObjectMapper()).get(base, "admin", "장비비밀번호!", "/api/users/400001");

    String logs = logText();
    assertTrue(logs.contains("BiostarX 요청"), logs);
    assertTrue(logs.contains("BiostarX 응답"), logs);
    assertTrue(logs.contains("/api/users/400001"), logs); // 어느 리소스인지
    assertFalse(logs.contains("장비비밀번호!"), "장비 비밀번호가 로그에 남았다: " + logs);
    assertFalse(logs.contains("홍길동"), "성명이 로그에 남았다: " + logs);
    assertFalse(logs.contains("SESSION-SECRET-123"), "세션ID가 로그에 남았다: " + logs);
  }

  @Test
  void DEBUG_를_끄면_남지_않는다() throws Exception {
    root.setLevel(Level.INFO);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    new BiostarSession(new ObjectMapper()).get(base, "admin", "pw", "/api/users/400001");

    assertFalse(logText().contains("BiostarX 요청"), logText());
  }
}
