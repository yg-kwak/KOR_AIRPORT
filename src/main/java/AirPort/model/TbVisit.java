package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 방문/작업 그룹 (임시·장기 출입) — tb_visit. 정규(tb_company 기반)와 달리 BiostarX 기관 그룹을 만들지 않고 visit_type(PT)→PTD
 * code_tag 부모 그룹 아래로 방문객을 편입한다. (docs/integration.md)
 */
@Data
public class TbVisit {

  private Integer visitNo;
  private String visitType; // → tb_common(PT). 방문객 person_type·카드 pass_type 결정
  private String statusCode; // → tb_common(VS)
  private String workPurpose;
  private String permitDt;
  private String workStartDt;
  private String workEndDt;
  private String companyType; // 업체구분 (자유입력)
  private String companyName; // 업체명 (자유입력)
  private String receiver;
  private String returner;
  private String evidenceFile;
  private String remark;
  private String delYn;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  // 목록 표시용 조인값
  private String visitTypeName;
  private String statusName;
  private Integer personCount;
  private Integer carCount;
}
