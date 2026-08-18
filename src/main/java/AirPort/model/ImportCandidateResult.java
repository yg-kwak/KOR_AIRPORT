package AirPort.model;

import lombok.Data;

/**
 * BiostarX 가져오기 후보 1명 — 선택 목록에 뿌리는 값.
 *
 * <p>여기서는 <b>장비 상세를 읽지 않는다</b>. 카드·출입그룹은 사용자마다 별도 조회라, 목록을 그리자고 인원 수만큼 왕복하면 화면이 열리지 않는다. 무엇이 달라지는지는
 * 고른 뒤 [미리보기]가 알려 준다.
 */
@Data
public class ImportCandidateResult {

  private String userId; // 장비 사용자ID = 우리 인원ID
  private String userName; // 장비에 등록된 성명
  private String companyName; // 매핑된 기관명 — 없으면 null(가져올 수 없다)
  private boolean registered; // 우리 DB 에 이미 있는가 — 있으면 '갱신', 없으면 '신규'
  private boolean importable; // 기관 매핑이 있어 가져올 수 있는가
  private String reason; // 가져올 수 없는 사유(importable=false 일 때)
}
