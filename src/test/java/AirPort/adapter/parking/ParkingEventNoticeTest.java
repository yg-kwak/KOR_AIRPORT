package AirPort.adapter.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 주차서버가 보내오는 입·출차 이벤트 payload 해석 테스트.
 *
 * <p>이 규격은 우리가 정하지 않는다 — 아마노 문서의 예시를 그대로 넣어 파싱을 고정한다. 특히 (1) 모르는 필드가 늘어도 깨지지 않을 것, (2) 여섯 가지
 * eventType 을 입·출차 × 차단기 개방 여부로 정확히 가를 것, (3) 미인식이 빈 값이 아니라 {@code No_Detection} 문자열로 온다는 것.
 */
class ParkingEventNoticeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 아마노 문서의 입차 이벤트 예시 그대로. */
  private static final String ENTERED =
      """
      {"eventName":"Entered car event","eventType":"EnteredCar","aptIdx":"1","eqpmID":9,
       "lotArea":1,"carNumber":"58가7868","eventTime":"20200717210432","dongcode":"","hocode":"",
       "userName":"","isCustDef":false,"iID":12933,"inEqpmID":9,"inDtm":"20210414140136",
       "passType":"normal","isCustDc":false,"custDefUserID":100,"custDcUserID":11,
       "carImagePath":"http://x/image/a.jpg","historyID":1502,"lprTrnsID":8952,
       "groupID":0,"groupName":""}
      """;

  private static ParkingEventNotice parse(String json) throws Exception {
    return MAPPER.readValue(json, ParkingEventNotice.class);
  }

  @Test
  void 문서_예시를_그대로_읽는다() throws Exception {
    ParkingEventNotice n = parse(ENTERED);

    assertEquals("EnteredCar", n.eventType());
    assertEquals(9, n.eqpmID());
    assertEquals(1, n.lotArea());
    assertEquals("58가7868", n.carNumber());
    assertEquals(LocalDateTime.of(2020, 7, 17, 21, 4, 32), n.eventDateTime());
    assertEquals(LocalDateTime.of(2021, 4, 14, 14, 1, 36), n.inDateTime());
    assertEquals("normal", n.passType());
    assertEquals(12933, n.iID());
  }

  @Test
  void 모르는_필드가_있어도_깨지지_않는다() throws Exception {
    // 아마노 문서가 "필드는 경우에 따라 추가될 수 있다"고 못박고 있다. 예시에도 우리가 안 쓰는
    // aptIdx·dongcode·custDcUserID·groupName 이 들어 있다 — 늘어난 필드로 수신이 멈추면 안 된다.
    ParkingEventNotice n = parse(ENTERED.replace("\"groupID\":0", "\"newFieldNextYear\":\"x\""));

    assertEquals("EnteredCar", n.eventType());
  }

  @Test
  void 입출차와_차단기_개방을_가른다() throws Exception {
    // 여섯 가지 eventType 을 화면 두 칸(구분 / 차단기)으로 나눈다
    assertTrue(parse("{\"eventType\":\"EnteredCar\"}").entered());
    assertTrue(parse("{\"eventType\":\"EnteredCarNotOpen\"}").entered());
    assertTrue(parse("{\"eventType\":\"EnteredRearCar\"}").entered());
    assertFalse(parse("{\"eventType\":\"ExitedCar\"}").entered());
    assertFalse(parse("{\"eventType\":\"ExitedCarNotOpen\"}").entered());

    assertTrue(parse("{\"eventType\":\"EnteredCar\"}").opened());
    assertFalse(parse("{\"eventType\":\"EnteredCarNotOpen\"}").opened());
    assertFalse(parse("{\"eventType\":\"ExitedCarNotOpen\"}").opened());
  }

  @Test
  void 미인식은_빈_값이_아니라_No_Detection_으로_온다() throws Exception {
    assertTrue(parse("{\"carNumber\":\"No_Detection\"}").unrecognized());
    assertTrue(parse("{\"carNumber\":\"\"}").unrecognized());
    assertTrue(parse("{}").unrecognized());
    assertFalse(parse("{\"carNumber\":\"58가7868\"}").unrecognized()); // 정상 인식
    assertFalse(parse("{\"carNumber\":\"01X01X1\"}").unrecognized()); // 부분 인식도 이력이다
  }

  @Test
  void 시각_형식이_어긋나면_null_이다() throws Exception {
    // 이 값이 null 이면 수신 컨트롤러가 저장하지 않는다 — 언제 일어난 일인지 모르는 이력은 만들지 않는다
    assertNull(parse("{\"eventTime\":\"2020-07-17 21:04:32\"}").eventDateTime());
    assertNull(parse("{\"eventTime\":\"20200717\"}").eventDateTime());
    assertNull(parse("{\"eventTime\":\"20201317210432\"}").eventDateTime()); // 13월
    assertNull(parse("{}").eventDateTime());
    assertNull(parse("{\"eventTime\":\"20200717210432\"}").inDateTime()); // inDtm 없음
  }
}
