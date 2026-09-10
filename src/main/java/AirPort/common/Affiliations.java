package AirPort.common;

import AirPort.model.TbPerson;

/**
 * 사람의 <b>소속</b>을 무엇으로 볼 것인가 — 화면에 그대로 뿌리는 한 줄.
 *
 * <p>방문객은 기관(`tb_company`)에 매이지 않고 소속을 직접 적는다({@code affiliation}). 그 값이 있으면 그것이 정확하다. 정규인원은 보통 비어
 * 있으므로 기관명으로 물러선다.
 *
 * <p>이 규칙을 쓰는 화면이 넷이다 — 실시간 이벤트의 [출입자 정보], 키오스크·임시/장기 등록의 인솔자 검색, 방문 목록의 인솔자 소속. 각자 적어 두면 한쪽만 고쳐져
 * 같은 사람이 화면마다 다른 소속으로 보인다. <b>SQL 로 옮겨 적지 않는다</b> — 그 순간 규칙이 둘이 된다.
 */
public final class Affiliations {

  private Affiliations() {}

  /** 자유입력 소속 우선, 비어 있으면 기관명. 둘 다 없으면 {@code null}. */
  public static String of(TbPerson person) {
    return person == null ? null : of(person.getAffiliation(), person.getCompanyName());
  }

  /**
   * 값 두 개로 같은 판정 — 사람 한 명을 통째로 읽지 않고 <b>두 컬럼만</b> 끌어온 조회용.
   *
   * <p>방문 목록의 인솔자 소속이 그렇다. 목록 한 페이지의 인솔자를 한 명씩 다시 읽으면 30번을 왕복한다.
   */
  public static String of(String affiliation, String companyName) {
    return (affiliation != null && !affiliation.isBlank()) ? affiliation : companyName;
  }
}
