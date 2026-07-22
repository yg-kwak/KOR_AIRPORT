package AirPort.model;

import lombok.Data;

/**
 * 인원 모달에서 추가한 카드 1건 — 인원 저장 시 tb_card 로 저장되고 BiostarX 사용자 payload 의 cards[] 에 실린다.
 *
 * <p>{@code biostarCardId}/{@code cardNo} 는 <b>카드 추가 시점에 이미 BiostarX 에 등록</b>된 결과값이다(정책: 즉시 등록).
 */
@Data
public class CardForm {

  private Integer cardId; // 기존 카드면 tb_card.card_id, 새 카드면 null
  private String cardType;
  private String cardName;
  private String cardStatus;
  private String passType;
  private String feePaidDt;
  private String issueReason;
  private String remark;
  private String biostarCardId;
  private String cardNo; // = tb_card.biostar_card_value
}
