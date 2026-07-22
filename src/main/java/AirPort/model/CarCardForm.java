package AirPort.model;

import lombok.Data;

/** 차량용 카드 발급 요청 — 기관차량등록 화면의 카드 발급 모달. 카드구분은 서버가 차량으로 고정한다. */
@Data
public class CarCardForm {

  private Integer carId;
  private String cardNo;
  private String cardName;
  private String cardStatus;
  private String feePaidDt;
  private String issueReason;
  private String remark;
}
