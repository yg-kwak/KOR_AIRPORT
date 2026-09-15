package AirPort.model;

import lombok.Data;

/**
 * 키오스크 [등록 수정] 요청 — 인솔자 인원ID·성명으로 자기 신청을 찾는다.
 *
 * <p>성명은 개인정보라 URL 쿼리가 아니라 <b>본문</b>으로 받는다 — 공용 단말의 브라우저 이력·접근 로그에 남지 않게. 목록은 인원ID·성명만, 상세는 방문번호까지,
 * 수정은 고친 방문({@link #form})까지 담는다. (docs/security.md)
 */
@Data
public class KioskEditForm {
  private String managerId;
  private String managerName;
  private Integer visitNo; // 상세·수정
  private VisitForm form; // 수정
}
