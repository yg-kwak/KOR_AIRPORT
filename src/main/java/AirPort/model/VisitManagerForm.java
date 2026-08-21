package AirPort.model;

import lombok.Data;

/**
 * 방문 인솔자 한 명 — 정규인원 선택 + <b>그 방문에서 연락 받을 번호</b>.
 *
 * <p>연락처를 정규인원 정보에서 당겨오지 않는다. 같은 사람이라도 방문마다 연락 받을 번호가 다를 수 있고(현장 담당 휴대폰 등), 신청서에 찍히는 것은 그 방문의 연락처여야
 * 한다. 그래서 방문마다 손으로 적고 필수로 받는다.
 */
@Data
public class VisitManagerForm {

  private String personId; // 인솔자 = 정규인원(PT01) person_id
  private String phone; // 그 방문의 연락처 — 저장 직전 ARIA 암호화

  /**
   * 연락처가 비었으면 거부한다 — <b>인솔자를 받는 모든 화면이 같은 규칙을 쓴다</b>.
   *
   * <p>임시인원등록·장기출입등록·키오스크 세 경로가 인솔자를 받는다. 규칙을 각자 적어 두면 한쪽만 고쳐져 조용히 갈린다. 아래 {@link #encrypted} 와 짝이라
   * 함께 둔다 — 받는 규칙과 저장하는 규칙이 떨어지면 안 된다.
   */
  public static void requirePhones(java.util.List<VisitManagerForm> managers) {
    if (managers == null) {
      return;
    }
    for (VisitManagerForm m : managers) {
      if (m.getPersonId() == null || m.getPersonId().isBlank()) {
        continue; // 사람이 안 골라진 빈 줄은 여기서 따지지 않는다
      }
      if (m.getPhone() == null || m.getPhone().isBlank()) {
        throw new AirPort.common.exception.BusinessException(
            AirPort.common.exception.ErrorCode.INVALID_INPUT, "인솔자 연락처를 입력하세요.");
      }
    }
  }

  /**
   * 저장용 복사본 — 연락처를 ARIA 로 암호화한다.
   *
   * <p>연락처는 개인정보라 평문으로 두지 않는다(AGENTS §4). 폼 객체를 그대로 넣으면 화면이 보낸 평문이 그대로 저장되므로 복사본에 암호문을 담는다. 인솔자를 받는
   * 화면이 둘(임시인원등록·키오스크)이라 규칙을 여기 한곳에 둔다.
   */
  public static java.util.List<VisitManagerForm> encrypted(java.util.List<VisitManagerForm> src) {
    java.util.List<VisitManagerForm> out = new java.util.ArrayList<>();
    if (src == null) {
      return out;
    }
    for (VisitManagerForm m : src) {
      if (m.getPersonId() == null || m.getPersonId().isBlank()) {
        continue;
      }
      VisitManagerForm row = new VisitManagerForm();
      row.setPersonId(m.getPersonId());
      row.setPhone(
          m.getPhone() == null || m.getPhone().isBlank()
              ? null
              : AirPort.security.ARIAUtil.ariaEncrypt(m.getPhone()));
      out.add(row);
    }
    return out;
  }
}
