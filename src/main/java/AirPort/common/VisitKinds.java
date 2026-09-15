package AirPort.common;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;

/**
 * 방문구분 — 인원 / 차량 / 인원+차량. tb_visit.visit_kind 의 값이고 화면은 고른 쪽만 보여 준다.
 *
 * <p>공통코드로 두지 않는다 — 값마다 <b>화면이 어느 칸을 감추고 서버가 무엇을 요구하는지</b>가 코드에 묶여 있어, 코드표에서 하나를 더 만든다고 동작이 생기지
 * 않는다. 그런 값은 코드가 원천이다({@code CardNames} 와 같은 판단).
 *
 * <p>받는 화면이 셋(임시인원등록·장기출입등록·키오스크)이라 "고른 쪽은 있어야 하고 고르지 않은 쪽은 없어야 한다"는 규칙을 여기 한 곳에만 둔다.
 */
public final class VisitKinds {

  public static final String PERSON = "PERSON";
  public static final String CAR = "CAR";
  public static final String BOTH = "BOTH";

  private VisitKinds() {}

  /** 인원 칸(방문객·사용자 출입그룹)을 쓰는 구분인가. */
  public static boolean person(String kind) {
    return PERSON.equals(kind) || BOTH.equals(kind);
  }

  /** 차량 칸(차량·차량 출입그룹)을 쓰는 구분인가. */
  public static boolean car(String kind) {
    return CAR.equals(kind) || BOTH.equals(kind);
  }

  public static boolean isValid(String kind) {
    return PERSON.equals(kind) || CAR.equals(kind) || BOTH.equals(kind);
  }

  /** 저장값이 없는 옛 방문 — 명단으로 되짚는다. 둘 다 없으면 모든 칸을 여는 인원+차량으로 본다. */
  public static String infer(boolean hasVisitors, boolean hasCars) {
    if (hasVisitors && !hasCars) {
      return PERSON;
    }
    if (hasCars && !hasVisitors) {
      return CAR;
    }
    return BOTH;
  }

  public static String label(String kind) {
    if (PERSON.equals(kind)) {
      return "인원";
    }
    if (CAR.equals(kind)) {
      return "차량";
    }
    return BOTH.equals(kind) ? "인원+차량" : "";
  }

  /**
   * 구분과 입력이 맞는지 — 고른 쪽은 <b>있어야</b> 하고, 고르지 않은 쪽은 <b>없어야</b> 한다.
   *
   * <p>화면은 고르지 않은 칸을 감추고 빈 값을 보내므로 정상 흐름에서는 뒤쪽 검사가 걸리지 않는다. 그래도 서버가 본다 — 감춘 칸의 값을 조용히 버리면 "인원" 으로
   * 바꿔 저장한 순간 차량과 그 카드가 이유 없이 사라진다. 어긋나면 저장을 거절해 사람이 보게 한다.
   */
  public static void check(
      String kind, boolean hasVisitors, boolean hasCars, boolean hasAcGroups, boolean hasCarAc) {
    bad(!isValid(kind), "방문구분(인원/차량/인원+차량)을 선택하세요.");
    bad(!person(kind) && (hasVisitors || hasAcGroups), "방문구분이 '차량'이면 방문객·사용자 출입그룹을 입력할 수 없습니다.");
    bad(!car(kind) && (hasCars || hasCarAc), "방문구분이 '인원'이면 차량·차량 출입그룹을 입력할 수 없습니다.");
    bad(person(kind) && !hasVisitors, "방문구분에 인원이 있으면 방문객을 1명 이상 입력하세요.");
    bad(car(kind) && !hasCars, "방문구분에 차량이 있으면 차량을 1대 이상 입력하세요.");
  }

  private static void bad(boolean invalid, String message) {
    if (invalid) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
  }
}
