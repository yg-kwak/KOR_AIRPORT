package AirPort.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 실시간 이벤트 소켓 주소 — 설정관리에 무엇을 넣든 {@code wss://{서버}/wsapi} 로 간다. */
class BiostarEventSocketTest {

  @Test
  void IP_만_넣으면_wss_로_붙인다() {
    assertEquals("wss://192.168.0.10/wsapi", BiostarEventSocket.wsUrl("192.168.0.10"));
  }

  @Test
  void 포트가_있으면_그대로_유지한다() {
    // 현장은 BiostarX 를 9443 같은 별도 포트로 올려 쓴다
    assertEquals("wss://192.168.0.10:9443/wsapi", BiostarEventSocket.wsUrl("192.168.0.10:9443"));
  }

  @Test
  void 스킴이_이미_있으면_그것을_따른다() {
    assertEquals("wss://192.168.0.10/wsapi", BiostarEventSocket.wsUrl("https://192.168.0.10"));
    assertEquals("ws://192.168.0.10/wsapi", BiostarEventSocket.wsUrl("http://192.168.0.10"));
  }

  /** 현장에서 실제로 받은 MESSAGE — 필드 하나라도 어긋나면 화면에 아무것도 안 뜬다. */
  private static final String REAL_MESSAGE =
      "{\"Event\":{\"id\":\"178641228105437370300000527832\","
          + "\"event_type_id\":{\"code\":\"4106\",\"name\":\"VERIFY_SUCCESS_CARD_FACE\",\"description\":\"\"},"
          + "\"index\":\"527832\",\"datetime\":\"2026-08-11T01:38:01.00Z\","
          + "\"server_datetime\":\"2026-08-11T10:38:00.00Z\","
          + "\"device_id\":{\"id\":\"543737030\",\"name\":\"FaceStation F2 543737030 (192.168.150.182)\"},"
          + "\"user_id\":{\"user_id\":\"400001\",\"name\":\"홍길동\",\"photo_exists\":\"false\"},"
          + "\"tna_key\":\"0\",\"parameter\":\"-1\",\"event_priority\":\"3\","
          + "\"image_id\":{\"image_data\":\"1786412281_543737030_527832\",\"image_type\":\"JPG\"}},"
          + "\"id\":\"178641228105437370300000527832\"}";

  @Test
  void 실제_MESSAGE_에서_필요한_값을_모두_뽑는다() throws Exception {
    BiostarAuthEvent e =
        BiostarEventSocket.parse(new com.fasterxml.jackson.databind.ObjectMapper(), REAL_MESSAGE);

    assertEquals("4106", e.eventCode());
    assertEquals("VERIFY_SUCCESS_CARD_FACE", e.eventName());
    assertEquals("543737030", e.deviceId());
    assertEquals("400001", e.userId());
    // 인증 사진 조회 키 — 이게 비면 인증 사진 칸이 빈 채로 남는다
    assertEquals("1786412281_543737030_527832", e.imageId());
  }

  @Test
  void 이벤트가_아닌_메시지는_버린다() throws Exception {
    assertNull(
        BiostarEventSocket.parse(
            new com.fasterxml.jackson.databind.ObjectMapper(), "{\"ping\":1}"));
  }
}
