package AirPort.model;

import java.util.List;
import lombok.Data;

/**
 * 기관차량등록의 차량 저장 요청 — 차량 정보 + 출입구역(tb_car_ac_group).
 *
 * <p>{@code carManagerId} 는 소속 기관의 정규인원(tb_person.person_id), {@code acCodes} 는 tb_common(cmm_id='CAR')
 * 구역 코드 목록이다.
 */
@Data
public class CarForm {

  private Integer carId; // 신규면 null
  private String companyCode;
  private String carNo;
  private String carName;
  private String carType;
  private String carManagerId;
  private List<String> acCodes;
}
