package AirPort.model;

import lombok.Data;

/**
 * 방문 인솔자 행 — {@code tb_visit_manager}.
 *
 * <p>연락처를 여기에 둔다. 정규인원({@code tb_person.person_phone})의 번호를 당겨 쓰지 않는다 — 같은 사람이라도 방문마다 연락 받을 번호가 다를
 * 수 있고, 신청서에 찍히는 것은 그 방문의 연락처여야 한다.
 */
@Data
public class TbVisitManager {

  private Integer visitNo;
  private Integer seq;
  private String personId; // → tb_person.person_id (PT01)
  private String managerPhone; // ARIA 암호문
}
