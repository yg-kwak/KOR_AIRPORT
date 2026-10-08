package AirPort.adapter.biostar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** BiostarX 소켓 프레임 해석 — {@link BiostarEventSocket} 가 받은 MESSAGE 를 이벤트로 바꾼다(연결 수명과 떼어 둔다). */
final class BiostarEventFrames {

  private BiostarEventFrames() {}

  /** MESSAGE 본문 → 이벤트. {@code Event} 가 없으면(하트비트 등) null. 테스트에서 직접 확인한다. */
  static BiostarAuthEvent parse(ObjectMapper mapper, String message) throws Exception {
    JsonNode event = mapper.readTree(message).path("Event");
    if (event.isMissingNode() || event.isNull()) {
      return null;
    }
    JsonNode type = event.path("event_type_id");
    JsonNode user = event.path("user_id");
    // 문 — 소켓 프레임은 door_id_list, 검색 API 는 door_id(둘 다 배열 [{id,name}], 장비 실측)
    JsonNode door = event.has("door_id_list") ? event.path("door_id_list") : event.path("door_id");
    door = door.isArray() ? door.path(0) : door; // 빈 배열이면 MissingNode — text() 가 null
    String userName = text(user, "name");
    return new BiostarAuthEvent(
        text(type, "code"),
        text(type, "name"),
        text(event, "datetime"),
        text(event.path("device_id"), "id"),
        text(event.path("device_id"), "name"),
        text(user, "user_id"),
        text(event.path("image_id"), "image_data"),
        "-".equals(userName) ? null : userName, // 미등록 카드는 이름 자리에 '-' 가 온다
        text(door, "id"),
        text(door, "name"));
  }

  static String text(JsonNode node, String field) {
    String v = node.path(field).asText(null);
    return (v == null || v.isBlank()) ? null : v;
  }

  /** 로그용 절단 — 예상 밖 프레임이 길 수 있다. */
  static String abbreviate(String s) {
    if (s == null) {
      return "(없음)";
    }
    return s.length() <= 300 ? s : s.substring(0, 300) + "…";
  }

  /** 이벤트가 아닌 응답 프레임({@code {"Response":{"code":"0"}}})의 코드. 응답이 아니거나 해석할 수 없으면 null. */
  static String responseCode(ObjectMapper mapper, String message) {
    try {
      JsonNode resp = mapper.readTree(message).path("Response");
      return resp.isMissingNode() ? null : resp.path("code").asText("");
    } catch (Exception e) {
      return null;
    }
  }

  /** {@code 192.168.0.10[:9443]} 또는 {@code https://...} → {@code wss://.../wsapi}. */
  static String wsUrl(String ip) {
    return baseUrl(ip).replaceFirst("^http", "ws") + "/wsapi";
  }

  static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }

  /** 로그 한 줄 — 이름(코드) 장치 인원 사진ID. */
  static String describe(BiostarAuthEvent e) {
    return e.eventName()
        + "("
        + e.eventCode()
        + ") 장치="
        + e.deviceId()
        + " 인원="
        + e.userId()
        + " 사진ID="
        + e.imageId();
  }
}
