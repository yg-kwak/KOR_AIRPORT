package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** BiostarX 출입문 — 목록 파싱과 원격 제어 요청 모양. 응답 본문은 장비 실측값을 줄인 것이다. */
class BiostarDoorAdapterTest {

  private static final String DOORS =
      "{\"DoorCollection\":{\"total\":2,\"rows\":[{\"id\":5,\"name\":\"F2\",\"status\":0,"
          + "\"entry_device_id\":{\"id\":543737030,\"name\":\"FaceStation F2 543737030\",\"slave_devices\":[]},"
          + "\"door_group_id\":{\"id\":1,\"name\":\"All Door Groups\"}},"
          + "{\"id\":6,\"name\":\"창고\"}]},\"Response\":{\"code\":\"0\",\"message\":\"success\"}}";

  private static final String CONTROL_OK =
      "{\"DoorResponse\":{\"rows\":[{\"id\":\"5\",\"code\":\"0\"}]},"
          + "\"DeviceResponse\":{\"rows\":[{\"id\":\"543737030\",\"code\":\"0\"}],\"result\":\"true\"},"
          + "\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}";

  private final ObjectMapper mapper = new ObjectMapper();
  private final BiostarSession session = mock(BiostarSession.class);
  private final BiostarDoorAdapter adapter = new BiostarDoorAdapter(mapper, session);

  @SuppressWarnings("unchecked")
  private void answer(String body) throws Exception {
    HttpResponse<String> resp = mock(HttpResponse.class);
    when(resp.statusCode()).thenReturn(200);
    when(resp.body()).thenReturn(body);
    when(session.post(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(resp);
  }

  @Test
  void 목록은_전체_출입문_그룹으로_읽고_입구_단말기를_함께_꺼낸다() throws Exception {
    answer(DOORS);
    List<BiostarDoor> doors = adapter.searchDoors("10.0.0.1", "id", "pw");
    assertEquals(2, doors.size());
    assertEquals(5, doors.get(0).id());
    assertEquals("F2", doors.get(0).name());
    assertEquals("543737030", doors.get(0).entryDeviceId());
    assertNull(doors.get(1).entryDeviceId(), "입구 단말기가 없는 문");
    verify(session)
        .post(
            anyString(),
            anyString(),
            anyString(),
            eq("/api/v2/doors/search"),
            eq("{\"limit\":500,\"door_group_id\":1}"));
  }

  @Test
  void 개방_잠금_해제는_각_경로로_문_번호를_실어_보낸다() throws Exception {
    answer(CONTROL_OK);
    for (BiostarDoorAdapter.Action a : BiostarDoorAdapter.Action.values()) {
      assertTrue(adapter.control("10.0.0.1", "id", "pw", a, 5).success(), a.name());
      verify(session)
          .post(
              anyString(),
              anyString(),
              anyString(),
              eq("/api/doors/" + a.path),
              eq("{\"DoorCollection\":{\"rows\":[{\"id\":\"5\"}]}}"));
    }
  }

  @Test
  void 문_단위_코드가_0_이_아니면_실패다() throws Exception {
    // 전체 응답은 성공이어도 장비가 꺼져 있으면 그 문은 움직이지 않는다
    answer(
        "{\"DoorResponse\":{\"rows\":[{\"id\":\"5\",\"code\":\"1004\"}]},\"Response\":{\"code\":\"0\"}}");
    BiostarResult r = adapter.control("10.0.0.1", "id", "pw", BiostarDoorAdapter.Action.UNLOCK, 5);
    assertFalse(r.success());
    assertTrue(r.message().contains("1004"), r.message());
  }
}
