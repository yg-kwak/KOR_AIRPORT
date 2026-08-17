package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 주차 입·출차 이벤트 (tb_parking_event) — 아마노 주차관제가 밀어 준 이력. docs/database.md
 *
 * <p>{@code eventType} 은 EnteredCar/ExitedCar/…NotOpen/…RearCar, {@code passType} 은
 * passType1~8·normal·visitor 다. {@code parkingId}(iID)가 -1 이면 출입권한이 없는 차량이었다는 뜻이다.
 */
@Data
public class TbParkingEvent {
  private Integer eventId;
  private String eventType;
  private String eventName;
  private Integer lotArea;
  private Integer eqpmId;
  private String carNo;
  private LocalDateTime eventDt;
  private LocalDateTime inDt;
  private Integer inEqpmId;
  private String userName;
  private String passType;
  private String isCustDef; // Y/N
  private Integer parkingId;
  private String carImageUrl;
  private Integer historyId;
  private Integer lprTrnsId;
  private String rawJson;
  private LocalDateTime regDt;

  // 목록 표시용 조인값 — 저장 컬럼 아님
  private String carName; // tb_car.car_name (우리 DB 에 등록된 차량이면)
  private String companyName; // tb_company.company_name
}
