package AirPort.adapter.parking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 아마노 주차관제가 보내 주는 입·출차 이벤트 1건. (docs/integration.md)
 *
 * <p>방향이 반대다 — 우리가 부르러 가는 게 아니라 <b>주차서버가 우리 {@code POST /api/InOutCar} 로 밀어 준다</b>. 그래서 이 레코드는 우리가
 * 정하는 모양이 아니라 저쪽 규격이고, {@code adapter} 에 둔다.
 *
 * <p>필드가 늘어도 깨지지 않도록 모르는 키는 무시한다 — 아마노 문서가 "필드는 경우에 따라 추가될 수 있다"고 못박고 있다. 원문은 {@code raw_json} 에
 * 그대로 보관하므로, 나중에 규격이 늘어도 지난 이벤트를 다시 읽을 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParkingEventNotice(
    String eventName,
    String eventType,
    Integer eqpmID,
    Integer lotArea,
    String carNumber,
    String eventTime,
    String userName,
    Boolean isCustDef,
    Integer iID,
    Integer inEqpmID,
    String inDtm,
    String passType,
    String carImagePath,
    Integer historyID,
    Integer lprTrnsID) {

  /** 주차서버가 쓰는 시각 형식. */
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  /** 번호를 읽지 못했을 때 오는 값(빈 값이 아니라 이 문자열이 온다). */
  public static final String NO_DETECTION = "No_Detection";

  /** 입차 계열인가 — 후면 인식·차단기 미개방(준입차)도 입차로 본다. */
  public boolean entered() {
    return eventType != null && eventType.startsWith("Entered");
  }

  /** 차단기가 실제로 열렸는가 — {@code ...NotOpen} 은 인식만 하고 열리지 않은 것이다. */
  public boolean opened() {
    return eventType != null && !eventType.endsWith("NotOpen");
  }

  /** 번호를 읽지 못한 이벤트인가. 화면에 '미인식'으로 표시한다. */
  public boolean unrecognized() {
    return carNumber == null || carNumber.isBlank() || NO_DETECTION.equals(carNumber);
  }

  /** 이벤트 시각. 형식이 어긋나면 null — 저장 단계에서 거른다. */
  public LocalDateTime eventDateTime() {
    return parse(eventTime);
  }

  /** 입차 시각 — 출차 이벤트에 함께 온다(그 차가 언제 들어왔는지). 없으면 null. */
  public LocalDateTime inDateTime() {
    return parse(inDtm);
  }

  private static LocalDateTime parse(String stamp) {
    if (stamp == null || stamp.length() != 14) {
      return null;
    }
    try {
      return LocalDateTime.parse(stamp, STAMP);
    } catch (Exception e) {
      return null; // 형식이 어긋난 값으로 이력을 만들지 않는다
    }
  }
}
