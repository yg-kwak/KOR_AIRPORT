package AirPort.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 출입그룹 이름 목록 → <b>허가구역 번호</b> 표기. 예: [인원구역3, 인원구역1, 인원구역2] → {@code "123"}
 *
 * <p>같은 값을 네 곳이 쓴다 — 실시간 이벤트의 [허가 구역], 신청서의 출입구역, 카드명칭({@link CardNames}), BiostarX 사용자의 부서
 * (department). 각자 적어 두면 같은 사람의 구역이 화면마다 다르게 보이고, <b>어느 쪽이 맞는지 화면으로는 알 수 없다.</b>
 *
 * <p>규칙:
 *
 * <ul>
 *   <li><b>번호 오름차순</b> — 고른 순서나 그룹ID 순서로 붙이면 같은 구역 조합인데 자리마다 다른 값이 나온다. 사람이 적을 때도 작은 번호부터 적는다
 *   <li><b>같은 번호는 한 번만</b> — 구역 트리는 상위를 고르면 하위까지 함께 선택된다(인원구역2 → "인원구역2 안쪽" → "2-1-2"). 그 하위들도 결국
 *       2번 구역이라, 그대로 이으면 "222" 가 된다
 *   <li><b>번호가 없는 이름은 그대로</b> 뒤에 붙인다 — 조용히 버리면 어느 구역이 빠졌는지 알 수 없고, 카드 후보를 고를 때는 그 구역을 <b>못 여는
 *       카드</b>를 쥐어 주게 된다
 * </ul>
 */
public final class AccessAreas {

  private AccessAreas() {}

  /** 구역명에서 번호만 — "인원구역3" → 3, "차량구역1" → 1. */
  private static final Pattern AREA_NO = Pattern.compile("(\\d+)");

  /** 이어 붙인 표기 — {@code "125"}. 카드명칭·BiostarX 부서·실시간 이벤트가 쓴다. */
  public static String key(List<String> acGroupNames) {
    return join(acGroupNames, "");
  }

  /** 콤마로 벌린 표기 — {@code "1,2,5"}. 신청서처럼 사람이 한 칸씩 읽는 자리에 쓴다. */
  public static String csv(List<String> acGroupNames) {
    return join(acGroupNames, ",");
  }

  private static String join(List<String> acGroupNames, String separator) {
    if (acGroupNames == null || acGroupNames.isEmpty()) {
      return "";
    }
    List<Long> numbers = new ArrayList<>();
    List<String> others = new ArrayList<>(); // 번호가 없는 이름 — 뒤에 그대로 붙인다
    for (String name : acGroupNames) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Matcher m = AREA_NO.matcher(name);
      if (!m.find()) {
        if (!others.contains(name.trim())) {
          others.add(name.trim());
        }
        continue;
      }
      try {
        Long no = Long.valueOf(m.group(1));
        if (!numbers.contains(no)) { // 상위와 그 하위는 같은 구역이다
          numbers.add(no);
        }
      } catch (NumberFormatException ignored) {
        // 자릿수가 지나치게 큰 이름 — 구역 번호로 볼 수 없으니 이름 그대로 남긴다
        if (!others.contains(name.trim())) {
          others.add(name.trim());
        }
      }
    }
    numbers.sort(null);
    List<String> parts = new ArrayList<>(numbers.size() + others.size());
    numbers.forEach(no -> parts.add(String.valueOf(no)));
    parts.addAll(others);
    return String.join(separator, parts);
  }
}
