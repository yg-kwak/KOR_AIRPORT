package AirPort.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실물 카드 명칭 규칙 — 이름이 곧 용도다. 방문객에게 어떤 카드를 줄 수 있는지 <b>이름만 보고</b> 판정한다.
 *
 * <ul>
 *   <li><b>구역 카드</b> {@code {유형}{구역}-{일련}} — {@code 임시234-0001}, {@code 상주2-0001}. 그 구역 전용이라 <b>고른
 *       구역과 정확히 같을 때만</b> 쓸 수 있다
 *   <li><b>공용 카드</b> {@code {유형}-{일련}} — {@code 대여-0001}. 이름에 구역이 없으니 <b>어느 구역에나</b> 쓸 수 있다
 * </ul>
 *
 * <p>유형 목록(임시·상주·대여)을 여기 적지 않는다. 종류가 늘어도 이름 규칙만 지키면 그대로 동작하고, 반대로 목록을 두면 <b>등록을 잊은 종류가 조용히
 * 사라진다.</b> 방문유형(임시 등록이냐 장기 등록이냐)도 보지 않는다 — 실물 카드는 3종뿐이고 어느 방문에서나 쓴다.
 *
 * <p>두 모양 어디에도 맞지 않는 이름은 <b>규칙 밖</b>이다({@code 임시11111112}, {@code 400001}). 구역을 알 수 없어 어느 방문에 맞는지
 * 판정할 수 없으므로 후보에서 뺀다 — 틀린 카드를 쥐어 주는 것보다 안전하다.
 */
public final class CardNames {

  private CardNames() {}

  /** {@code 임시234-0001} → 유형 "임시" · 구역 "234" · 일련 "0001". */
  private static final Pattern AREA_CARD = Pattern.compile("^(\\D+?)(\\d+)-(\\d+)$");

  /** {@code 대여-0001} → 유형 "대여" · 일련 "0001". 구역이 없다. */
  private static final Pattern PLAIN_CARD = Pattern.compile("^(\\D+?)-(\\d+)$");

  /**
   * 그 카드를 이 방문에 쓸 수 있는가 — 구역 카드면 구역이 같아야 하고, 공용 카드면 언제나 쓸 수 있다.
   *
   * @param areaKey 고른 출입그룹의 구역 번호({@link AccessAreas#key}). 비면 판정할 수 없어 무엇도 맞지 않다고 본다
   */
  public static boolean matchesArea(String cardName, String areaKey) {
    if (cardName == null || areaKey == null || areaKey.isBlank()) {
      return false;
    }
    String area = areaOf(cardName);
    return area == null ? isPlain(cardName) : area.equals(areaKey.trim());
  }

  /** 이름이 말하는 구역 — {@code 임시234-0001} → "234". 구역 카드가 아니면 {@code null}. */
  public static String areaOf(String cardName) {
    Matcher m = matcher(AREA_CARD, cardName);
    return m == null ? null : m.group(2);
  }

  /** 구역이 없는 공용 카드인가 — {@code 대여-0001}. */
  public static boolean isPlain(String cardName) {
    return matcher(PLAIN_CARD, cardName) != null;
  }

  /**
   * 이름이 말하는 유형 — {@code 임시234-0001} → "임시", {@code 대여-0001} → "대여". 규칙 밖이면 {@code null}.
   *
   * <p>카드의 패스구분과 대조하는 데 쓴다. 둘이 어긋나면 잘못 등록된 카드다.
   */
  public static String typeOf(String cardName) {
    Matcher area = matcher(AREA_CARD, cardName);
    if (area != null) {
      return area.group(1);
    }
    Matcher plain = matcher(PLAIN_CARD, cardName);
    return plain == null ? null : plain.group(1);
  }

  /** 규칙 안의 이름인가 — 구역 카드이거나 공용 카드. */
  public static boolean isWellFormed(String cardName) {
    return typeOf(cardName) != null;
  }

  private static Matcher matcher(Pattern pattern, String cardName) {
    if (cardName == null) {
      return null;
    }
    Matcher m = pattern.matcher(cardName.trim());
    return m.matches() ? m : null;
  }
}
