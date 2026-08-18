package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.adapter.parking.ParkingEventNotice;
import AirPort.controller.ParkingEventApiController;
import AirPort.mapper.TbParkingEventMapper;
import AirPort.model.TbParkingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

/**
 * 주차 입·출차 이벤트 수신 테스트 — payload → tb_parking_event 행, 그리고 수신 경로의 응답 규칙.
 *
 * <p>이 경로는 <b>주차서버가 우리를 호출</b>한다. 그래서 지켜야 할 것이 평소와 다르다: 못 알아들은 요청에도 200 을 줘야 하고(500 을 주면 저쪽이 같은 건을
 * 무한 재전송한다), 세션이 없으니 IP 로 막아야 한다.
 */
class ParkingEventReceiveTest {

  private final TbParkingEventMapper eventMapper = mock(TbParkingEventMapper.class);
  private final AuditService auditService = mock(AuditService.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ParkingEventService service =
      new ParkingEventService(eventMapper, auditService, menuAuthService);

  private static final String EXITED =
      """
      {"eventName":"Exited car event","eventType":"ExitedCar","eqpmID":9,"lotArea":20,
       "carNumber":"58가7868","eventTime":"20260813150000","userName":"홍길동","isCustDef":true,
       "iID":12933,"inEqpmID":3,"inDtm":"20260813120000","passType":"passType2",
       "carImagePath":"http://x/a.jpg","historyID":1502,"lprTrnsID":8952}
      """;

  private ParkingEventNotice notice(String json) throws Exception {
    return objectMapper.readValue(json, ParkingEventNotice.class);
  }

  private TbParkingEvent captureInsert() {
    ArgumentCaptor<TbParkingEvent> cap = ArgumentCaptor.forClass(TbParkingEvent.class);
    verify(eventMapper).insert(cap.capture());
    return cap.getValue();
  }

  private ParkingEventApiController controller(String allowIps) {
    return new ParkingEventApiController(service, objectMapper, allowIps);
  }

  private static HttpServletRequest from(String ip) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRemoteAddr()).thenReturn(ip);
    return req;
  }

  @Test
  void 받은_이벤트를_이력_행으로_옮긴다() throws Exception {
    when(eventMapper.insert(any())).thenReturn(1);

    assertTrue(service.receive(notice(EXITED), EXITED));

    TbParkingEvent row = captureInsert();
    assertEquals("ExitedCar", row.getEventType());
    assertEquals("58가7868", row.getCarNo());
    assertEquals(LocalDateTime.of(2026, 8, 13, 15, 0, 0), row.getEventDt());
    assertEquals(LocalDateTime.of(2026, 8, 13, 12, 0, 0), row.getInDt()); // 출차 건에 함께 오는 입차 시각
    assertEquals("passType2", row.getPassType());
    assertEquals("Y", row.getIsCustDef()); // boolean → Y/N
    assertEquals(20, row.getLotArea());
    assertNotNull(row.getRawJson()); // 규격이 늘어도 다시 읽을 수 있게 원문 보존
  }

  @Test
  void 미인식_차량도_이력으로_남긴다() throws Exception {
    // 번호를 못 읽었어도 차단기가 어떻게 됐는지는 그것대로 기록이다
    when(eventMapper.insert(any())).thenReturn(1);

    service.receive(
        notice(
            "{\"eventType\":\"EnteredCarNotOpen\",\"carNumber\":\"No_Detection\","
                + "\"eventTime\":\"20260813150000\"}"),
        "{}");

    assertEquals("No_Detection", captureInsert().getCarNo());
  }

  @Test
  void 이미_받은_건이면_다시_넣지_않는다() throws Exception {
    // 주차서버는 응답을 못 받으면 같은 건을 다시 보낸다. mapper 가 NOT EXISTS 로 0건을 돌려준다.
    when(eventMapper.insert(any())).thenReturn(0);

    assertFalse(service.receive(notice(EXITED), EXITED));
  }

  @Test
  void 알아듣지_못한_요청에도_200_을_준다() throws Exception {
    // 500 을 주면 주차서버가 같은 건을 무한히 다시 보낸다 — 우리 문제로 저쪽 큐를 막지 않는다
    ResponseEntity<Map<String, Object>> noType =
        controller("").receive(notice("{\"carNumber\":\"58가7868\"}"), from("10.0.0.9"));
    ResponseEntity<Map<String, Object>> badTime =
        controller("")
            .receive(
                notice("{\"eventType\":\"EnteredCar\",\"eventTime\":\"2026-08-13\"}"),
                from("10.0.0.9"));

    assertEquals(200, noType.getStatusCode().value());
    assertEquals(200, badTime.getStatusCode().value());
    verify(eventMapper, org.mockito.Mockito.never()).insert(any()); // 이력은 만들지 않는다
  }

  @Test
  void 허용_IP_를_정하면_그_주소만_받는다() throws Exception {
    when(eventMapper.insert(any())).thenReturn(1);
    ParkingEventApiController c = controller("10.0.0.9, 10.0.0.10");

    assertEquals(200, c.receive(notice(EXITED), from("10.0.0.10")).getStatusCode().value());
    assertEquals(403, c.receive(notice(EXITED), from("203.0.113.7")).getStatusCode().value());
    verify(eventMapper, org.mockito.Mockito.times(1)).insert(any()); // 거부된 건은 저장되지 않는다
  }

  @Test
  void 허용_IP_를_비워_두면_모두_받는다() throws Exception {
    // 설치 초기 기본값 — 운영에서는 주차서버 IP 를 반드시 넣는다(application.properties 주석)
    when(eventMapper.insert(any())).thenReturn(1);

    assertEquals(
        200, controller("").receive(notice(EXITED), from("203.0.113.7")).getStatusCode().value());
  }
}
