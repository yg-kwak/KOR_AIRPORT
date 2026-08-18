package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 차량 (tb_car). 삭제는 물리 DELETE 금지 — {@code del_yn='Y'} 소프트 삭제(이력 보존). docs/database.md
 *
 * <p>{@code car_type} 은 tb_common(cmm_id='CT').code_id. {@code car_manager_id} 는
 * tb_login_user.user_id(FK 미강제, 화면은 추후 구현).
 */
@Data
public class TbCar {
  private Integer carId;
  private String carNo;
  private String carName;
  private String carType; // tb_common(CT) code_id
  private String carManagerId; // → tb_person.person_id (소속 기관의 정규인원, 기관차량등록에서 지정)
  private String companyCode; // → tb_company.company_code (기관차량등록)
  private String delYn;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  // 목록 표시용 조인값 — 저장 컬럼 아님
  private String carTypeName; // tb_common(CT)
  private String companyName; // tb_company
  private Integer cardCount; // 발급된 차량카드 수
  private String carManagerName; // tb_person 성명(ARIA 복호화는 서비스에서)
  private String acCodeNames; // 부여된 출입구역명(콤마 연결)
}
