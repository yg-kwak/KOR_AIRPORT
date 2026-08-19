package AirPort.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import AirPort.adapter.parking.ParkingEventNotice;
import AirPort.service.ParkingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 입·출차 이벤트 수신의 IP 통제.
 *
 * <p>비워 두면 <b>어디서든 받는다</b> — 설치 초기에는 주차서버 주소를 모르는 채로 연동을 붙여 봐야 하고, 여기서 막히면 "보냈다는데 이력이 없다"의 원인이 IP
 * 인지 다른 것인지 가릴 수 없다. 주소를 넣으면 그때부터 그 목록만 받는다.
 */
class ParkingAllowIpsTest {

  private static final String BODY =
      "{\"eventType\":\"6\",\"carNumber\":\"12가3456\",\"eventTime\":\"20260819140000\"}";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private int receive(String allowIps, String from) throws Exception {
    ParkingEventApiController c =
        new ParkingEventApiController(Mockito.mock(ParkingEventService.class), MAPPER, allowIps);
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRemoteAddr(from);
    return c.receive(MAPPER.readValue(BODY, ParkingEventNotice.class), req).getStatusCode().value();
  }

  @Test
  void 비워_두면_어디서든_받는다() throws Exception {
    assertEquals(200, receive("", "203.0.113.9"));
    assertEquals(200, receive(null, "198.51.100.4"));
    assertEquals(200, receive("   ", "192.0.2.7"));
  }

  @Test
  void 넣어_두면_그_주소만_받는다() throws Exception {
    assertEquals(200, receive("10.0.0.5", "10.0.0.5"));
    assertEquals(403, receive("10.0.0.5", "203.0.113.9"));
  }

  @Test
  void 여러_주소는_콤마로_구분하고_공백은_무시한다() throws Exception {
    assertEquals(200, receive(" 10.0.0.5 , 10.0.0.6 ", "10.0.0.6"));
    assertEquals(403, receive(" 10.0.0.5 , 10.0.0.6 ", "10.0.0.7"));
  }
}
