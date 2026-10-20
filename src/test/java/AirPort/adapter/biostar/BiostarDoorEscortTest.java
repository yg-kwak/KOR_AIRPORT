package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 출입문 에스코트(auth_door_control) — 응답 해석과 BiostarX 로 보내는 본문. 응답은 장비 실측값을 줄인 것이다(값이 모두 문자열로 온다). */
class BiostarDoorEscortTest {

  private static final String DOOR =
      "{\"Door\":{\"id\":\"15\",\"name\":\"CS_40\",\"status\":\"0\","
          + "\"auth_door_control\":{\"enabled\":\"true\",\"schedule_id\":{\"id\":\"1\",\"name\":\"Always\"},"
          + "\"users\":[{\"user_id\":\"1\",\"name\":\"Administrator\",\"photo_exists\":\"false\"},"
          + "{\"user_id\":\"202606100001\",\"name\":\"박O준\"}]},\"unlocked\":\"false\"},"
          + "\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}";

  private static final String ACCESSIBLE =
      "{\"AccessibleUserCollection\":{\"total\":\"502\",\"rows\":["
          + "{\"user\":{\"user_id\":\"1\",\"name\":\"Administrator\",\"user_group_id\":{\"id\":\"1\",\"name\":\"All Users\"}}},"
          + "{\"user\":{\"user_id\":\"202606110002\",\"name\":\"이O준\",\"user_group_id\":{\"id\":\"1009\",\"name\":\"인사팀\"}}}]},"
          + "\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}";

  private static final String OK = "{\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}";

  private final ObjectMapper mapper = new ObjectMapper();
  private final BiostarSession session = mock(BiostarSession.class);
  private final BiostarDoorAdapter adapter = new BiostarDoorAdapter(mapper, session);

  @SuppressWarnings("unchecked")
  private static HttpResponse<String> resp(String body) {
    HttpResponse<String> r = mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(200);
    when(r.body()).thenReturn(body);
    return r;
  }

  @Test
  void 출입문_하나의_에스코트_설정과_등록된_사용자를_읽는다() throws Exception {
    HttpResponse<String> r = resp(DOOR);
    when(session.get(anyString(), anyString(), anyString(), eq("/api/doors/15"))).thenReturn(r);
    BiostarDoorAuth a = adapter.authControl("10.0.0.1", "id", "pw", 15);
    assertEquals(15, a.doorId());
    assertEquals("CS_40", a.doorName());
    assertTrue(a.enabled(), "\"true\" 문자열로 온다");
    assertEquals("1", a.scheduleId());
    assertEquals("Always", a.scheduleName());
    assertEquals(2, a.users().size());
    assertEquals("202606100001", a.users().get(1).userId());
    assertEquals("박O준", a.users().get(1).name());
  }

  @Test
  void 에스코트가_꺼졌거나_사용자가_없으면_그대로_읽는다() throws Exception {
    // 사용자가 없으면 users 가 배열이 아니라 빈 문자열로 온다 — 다른 필드(input.supervised_inputs 등)가 그렇다
    BiostarDoorAuth a =
        BiostarDoorAdapter.parseAuth(
            mapper.readTree(
                "{\"Door\":{\"id\":\"7\",\"name\":\"창고\",\"auth_door_control\":{\"enabled\":\"false\",\"users\":\"\"}}}"),
            7);
    assertFalse(a.enabled());
    assertTrue(a.users().isEmpty());
    assertEquals(null, a.scheduleId(), "일정이 없으면 null — 서비스가 기본 일정(1)을 쓴다");
  }

  @Test
  void 추가할_수_있는_사용자는_문_조건과_쪽으로_묻는다() throws Exception {
    HttpResponse<String> r = resp(ACCESSIBLE);
    when(session.post(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(r);
    BiostarDoorUserPage p =
        adapter.accessibleUsers("10.0.0.1", "id", "pw", 15, 200, 200, "김", null);
    assertEquals(502, p.total(), "건수도 문자열로 온다");
    assertFalse(p.more(), "한 명 더 물었는데 둘만 왔다 — 다음 쪽 없음");
    assertEquals(2, p.rows().size());
    assertEquals("인사팀", p.rows().get(1).groupName());
    verify(session)
        .post(
            anyString(),
            anyString(),
            anyString(),
            eq("/api/doors/accessible_users"),
            eq(
                "{\"Query\":{\"offset\":200,\"limit\":201,\"conditions\":[{\"column\":\"door.id\",\"operator\":2,"
                    + "\"values\":[\"15\"]}],\"search_text\":\"김\",\"total\":true,\"user_only\":true}}"));
  }

  @Test
  void 사용자_그룹으로_거르면_조건을_하나_더_붙인다() throws Exception {
    HttpResponse<String> r = resp(ACCESSIBLE);
    when(session.post(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(r);
    adapter.accessibleUsers("10.0.0.1", "id", "pw", 15, 0, 200, "", 1009L);
    verify(session)
        .post(
            anyString(),
            anyString(),
            anyString(),
            eq("/api/doors/accessible_users"),
            eq(
                "{\"Query\":{\"offset\":0,\"limit\":201,\"conditions\":["
                    + "{\"column\":\"door.id\",\"operator\":2,\"values\":[\"15\"]},"
                    + "{\"column\":\"user.user_group_id.id\",\"operator\":2,\"values\":[\"1009\"]}],"
                    + "\"search_text\":\"\",\"total\":true,\"user_only\":true}}"));
  }

  @Test
  void 그룹으로_거르면_건수가_없다_마지막_쪽이면_세고_아니면_모른다() throws Exception {
    // 장비 실측: 사용자 그룹 조건을 붙이면 total 이 오지 않는다
    String three =
        "{\"AccessibleUserCollection\":{\"rows\":[{\"user\":{\"user_id\":\"a\"}},"
            + "{\"user\":{\"user_id\":\"b\"}},{\"user\":{\"user_id\":\"c\"}}]}}";
    BiostarDoorUserPage more = BiostarDoorAdapter.parseAccessible(mapper.readTree(three), 4, 2);
    assertTrue(more.more(), "2명을 달라 했는데 3명 — 다음 쪽 있음");
    assertEquals(2, more.rows().size(), "넘친 한 명은 버린다");
    assertEquals(-1, more.total(), "건수를 모른다");
    BiostarDoorUserPage last = BiostarDoorAdapter.parseAccessible(mapper.readTree(three), 4, 5);
    assertFalse(last.more());
    assertEquals(7, last.total(), "마지막 쪽이면 앞 쪽 4명 + 이번 3명");
  }

  @Test
  void 반영은_사용자_전체와_일정을_실어_PUT_한다() throws Exception {
    HttpResponse<String> r = resp(OK);
    when(session.put(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(r);
    assertTrue(
        adapter
            .updateAuthUsers("10.0.0.1", "id", "pw", 15, "1", List.of("1", "202606100001"))
            .success());
    verify(session)
        .put(
            anyString(),
            anyString(),
            anyString(),
            eq("/api/doors/15"),
            eq(
                "{\"Door\":{\"id\":\"15\",\"auth_door_control\":{\"enabled\":\"true\","
                    + "\"users\":[{\"user_id\":\"1\"},{\"user_id\":\"202606100001\"}],\"schedule_id\":{\"id\":\"1\"}}}}"));
  }

  @Test
  void 장비가_거절하면_사유를_돌려준다() throws Exception {
    HttpResponse<String> r = resp("{\"Response\":{\"code\":\"30\",\"message\":\"Invalid user\"}}");
    when(session.put(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(r);
    BiostarResult res = adapter.updateAuthUsers("10.0.0.1", "id", "pw", 15, "1", List.of("x"));
    assertFalse(res.success());
    assertTrue(res.message().contains("Invalid user"), res.message());
  }
}
