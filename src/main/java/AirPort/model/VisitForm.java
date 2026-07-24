package AirPort.model;

import java.util.List;
import lombok.Data;

/**
 * 임시인원(방문) 등록 요청 — 그룹정보 + 인솔자 + 방문객 + 차량 + 출입그룹.
 *
 * <p>방문객은 tb_person(person_type=visit_type), 차량은 tb_car 로 저장되고 저장 시 BiostarX 로 동기화된다(사용자권한·카드는
 * 정규와 동일 테이블 재사용). (docs/integration.md)
 */
@Data
public class VisitForm {

  private Integer visitNo;
  private String visitType;
  private String statusCode;
  private String workPurpose;
  private String permitDt;
  private String workStartDt;
  private String workEndDt;
  private String companyType;
  private String companyName;
  private String receiver;
  private String returner;
  private String remark;

  private List<String> managerIds; // 인솔자 = 정규인원(PT01) person_id
  private List<Integer> acGroupIds; // 사용자출입그룹 = tb_ac_group.ac_group_id
  private List<String> carAcCodes; // 차량출입그룹 = tb_common(CAR).code_id
  private List<VisitorForm> visitors; // 방문객
  private List<VisitCarForm> cars; // 방문 차량
}
