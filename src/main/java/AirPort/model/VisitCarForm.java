package AirPort.model;

import lombok.Data;

/** 방문 차량 1대 — 차량번호·명칭·차종 + 카드(선택). 저장 시 tb_car 로 만들어진다. */
@Data
public class VisitCarForm {

  private Integer carId; // 기존이면 유지, 신규면 IDENTITY
  private String carNo;
  private String carName;
  private String carType; // → tb_common(CT)
  private Integer cardId; // 선택한 카드(tb_card.card_id), 없으면 null
}
