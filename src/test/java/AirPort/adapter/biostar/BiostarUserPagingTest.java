package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 사용자 목록은 <b>끝까지 넘겨 가며</b> 받아야 한다.
 *
 * <p>예전에는 한 쪽만 받아, 장비에 4000명이 넘게 등록된 현장에서 뒤쪽이 통째로 보이지 않았다(2026-08-19 보고).
 */
class BiostarUserPagingTest {

  private HttpServer server;
  private final List<String> bodies = new ArrayList<>();
  private int total;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/", this::handle);
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void handle(HttpExchange ex) throws IOException {
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String out;
    if (ex.getRequestURI().getPath().endsWith("/login")) {
      ex.getResponseHeaders().add("bs-session-id", "S1");
      out = "{}";
    } else {
      bodies.add(body);
      int offset = intAfter(body, "\"offset\":");
      int limit = intAfter(body, "\"limit\":");
      StringBuilder rows = new StringBuilder();
      for (int i = offset; i < Math.min(offset + limit, total); i++) {
        if (rows.length() > 0) rows.append(',');
        rows.append("{\"user_id\":\"U")
            .append(i)
            .append("\",\"name\":\"이름")
            .append(i)
            .append("\",\"user_group_id\":{\"id\":7},\"card_count\":")
            .append(i % 2)
            .append(",\"visual_face_count\":")
            .append(i % 3 == 0 ? 1 : 0)
            .append(",\"face_count\":0}");
      }
      out = "{\"UserCollection\":{\"total\":" + total + ",\"rows\":[" + rows + "]}}";
    }
    byte[] b = out.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(200, b.length);
    ex.getResponseBody().write(b);
    ex.close();
  }

  /** 본문에서 키 뒤의 숫자를 읽는다 — 정규식 없이. */
  private static int intAfter(String body, String key) {
    int i = body.indexOf(key) + key.length();
    int j = i;
    while (j < body.length() && Character.isDigit(body.charAt(j))) {
      j++;
    }
    return Integer.parseInt(body.substring(i, j));
  }

  private List<BiostarUserDetail> fetch() {
    ObjectMapper m = new ObjectMapper();
    return new BiostarImportAdapter(m, new BiostarSession(m))
        .searchUsers("http://127.0.0.1:" + server.getAddress().getPort(), "admin", "pw");
  }

  @Test
  void 네_쪽에_걸친_사용자를_전부_받는다() {
    total = 4321; // 현장과 비슷한 규모

    List<BiostarUserDetail> users = fetch();

    assertEquals(4321, users.size());
    assertEquals("U0", users.get(0).userId());
    assertEquals("U4320", users.get(users.size() - 1).userId()); // 마지막까지 왔다
  }

  @Test
  void 한_쪽으로_끝나면_한_번만_부른다() {
    total = 10;

    assertEquals(10, fetch().size());
    assertEquals(1, bodies.size());
  }

  @Test
  void 카드와_얼굴_보유수를_목록에서_함께_읽는다() {
    // 목록 응답에 개수가 들어 있어 인원마다 상세를 부르지 않아도 된다
    total = 6;

    List<BiostarUserDetail> users = fetch();

    assertEquals(0, users.get(0).cardCount()); // 0 % 2
    assertEquals(1, users.get(1).cardCount()); // 1 % 2
    assertEquals(1, users.get(0).faceCount()); // 0 % 3 == 0
    assertEquals(0, users.get(1).faceCount());
  }
}
