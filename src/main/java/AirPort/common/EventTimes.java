package AirPort.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * BiostarX 이벤트 시각 → 이 서버 시각.
 *
 * <p>이벤트의 {@code datetime} 은 <b>진짜 UTC</b> 다(장비 실측: {@code 2026-10-08T03:50:09.00Z} 가 한국 시각
 * 12:50:09 의 이벤트, 같은 이벤트의 {@code server_datetime} 은 {@code 12:50:12.00Z} — 그쪽은 Z 를 달았지만 현지 시각이다).
 * 문자열을 그대로 잘라 쓰면 9시간 이른 시각이 화면에 나간다. UTC 로 읽어 서버 시간대로 바꾼다.
 */
public final class EventTimes {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private EventTimes() {}

  /** "2026-10-08T03:50:09.00Z" → "12:50:09"(서버 시간대). 알아볼 수 없으면 원문 — 조용히 비우지 않는다. */
  public static String time(String datetime) {
    return format(datetime, TIME, ZoneId.systemDefault());
  }

  /** "2026-10-08T03:50:09.00Z" → "2026-10-08 12:50:09"(서버 시간대). */
  public static String dateTime(String datetime) {
    return format(datetime, DATE_TIME, ZoneId.systemDefault());
  }

  /** 시간대를 정해 바꾼다 — 테스트가 서버 시간대와 무관하게 확인하려고 공개한다. */
  public static String format(String datetime, DateTimeFormatter fmt, ZoneId zone) {
    if (datetime == null || datetime.isBlank()) {
      return datetime;
    }
    try {
      return fmt.format(Instant.parse(datetime.trim()).atZone(zone));
    } catch (DateTimeParseException e) {
      return datetime;
    }
  }
}
