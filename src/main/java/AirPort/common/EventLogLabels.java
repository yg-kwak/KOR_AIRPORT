package AirPort.common;

import java.util.Map;

/**
 * BiostarX 이벤트 이름 → 화면 문구와 색. 그래픽맵 아래 이벤트 표(모든 이벤트)가 쓴다.
 *
 * <p>이름이 수백 가지라 다 옮기지 않는다. 자주 보는 것은 이름으로, 나머지는 <b>이름 규칙</b>(접두·포함)으로 잡고, 그래도 모르면 원래 이름을 그대로 보인다 —
 * 모르는 이벤트를 숨기면 "모든 로그"가 아니게 된다.
 */
public final class EventLogLabels {

  public static final String SUCCESS = "success";
  public static final String ERROR = "error";
  public static final String INFO = "info";

  private static final Map<String, String> BY_NAME =
      Map.ofEntries(
          Map.entry("DOOR_OPENED", "문 열림"),
          Map.entry("DOOR_CLOSED", "문 닫힘"),
          Map.entry("UNLOCKED", "문 잠금 해제됨"),
          Map.entry("LOCKED", "문 잠김"),
          Map.entry("DOOR_NORMALIZED", "문 정상 상태"),
          Map.entry("DOOR_FORCED_OPEN", "문 강제 열림"),
          Map.entry("DOOR_HELD_OPEN", "문 장시간 열림"),
          Map.entry("DOOR_OPERATOR_ID", "운영자 조작"),
          Map.entry("OPEN_DOOR_BY_OPERATOR", "운영자 개방"),
          Map.entry("UNLOCK_DOOR_BY_OPERATOR", "운영자 개방"),
          Map.entry("LOCK_DOOR_BY_OPERATOR", "운영자 잠금"),
          Map.entry("RELEASE_DOOR_BY_OPERATOR", "운영자 해제"),
          Map.entry("DEVICE_CONNECTED", "장치 연결"),
          Map.entry("DEVICE_DISCONNECTED", "장치 연결 끊김"),
          Map.entry("TAMPER_ON", "장치 탬퍼 감지"),
          Map.entry("TAMPER_OFF", "장치 탬퍼 해제"));

  private EventLogLabels() {}

  /** 화면 문구 — 모르면 원래 이름. */
  public static String label(String eventName, String eventCode) {
    if (eventName == null || eventName.isBlank()) {
      return eventCode == null ? "-" : "이벤트 " + eventCode;
    }
    String byName = BY_NAME.get(eventName);
    if (byName != null) {
      return byName;
    }
    if (eventName.startsWith("VERIFY_SUCCESS") || eventName.startsWith("IDENTIFY_SUCCESS")) {
      return "인증 성공";
    }
    if (eventName.startsWith("VERIFY_FAIL") || eventName.startsWith("IDENTIFY_FAIL")) {
      return "인증 실패";
    }
    if (eventName.startsWith("ACCESS_DENIED")) {
      return "출입 거부";
    }
    return eventName;
  }

  /** 색 — 통과 초록 · 거부/이상 빨강 · 그 밖 기본. */
  public static String tone(String eventName, boolean granted) {
    if (granted) {
      return SUCCESS;
    }
    if (eventName == null) {
      return INFO;
    }
    String n = eventName;
    boolean bad =
        n.contains("FAIL")
            || n.startsWith("ACCESS_DENIED")
            || n.contains("FORCED")
            || n.contains("HELD_OPEN")
            || n.contains("ALARM")
            || n.contains("TAMPER_ON")
            || n.contains("DISCONNECTED")
            || n.contains("ANTI_PASSBACK")
            || n.contains("DURESS");
    return bad ? ERROR : INFO;
  }
}
