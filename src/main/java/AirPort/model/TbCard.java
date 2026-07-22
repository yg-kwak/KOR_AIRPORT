package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 카드 (tb_card) — 인원 1 : 카드 N. 카드 상태의 진실의 원천은 {@code cardStatus} 단일 컬럼이다. (docs/database.md)
 *
 * <p>{@code biostarCardId}/{@code biostarCardValue} 는 BiostarX 카드 등록({@code POST /api/cards}) 응답의
 * {@code id}/{@code card_id} 다. 사용자에게 붙이는 것은 사용자 payload 의 cards[] 가 담당한다.
 */
@Data
public class TbCard {

  private Integer cardId;
  private String cardType; // → tb_common(CDT)
  private String cardName;
  private String cardStatus; // → tb_common(CS)
  private String passType; // 패스구분 → tb_common(PT)
  private String feePaidDt; // 발급료 납부일 (문자열 바인딩 "YYYY-MM-DD")
  private String issueDt; // 카드발급일
  private String issueType; // → tb_common(IS)
  private String issueReason; // 발급근거
  private String lostDt;
  private String returnDt;
  private String remark;
  private String personId;
  private Integer carId; // → tb_car.car_id (차량 카드)
  private String biostarCardId;
  private String biostarCardValue; // 카드번호
  private String useYn;
  private String delYn;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  // 화면 표시용 조인값
  private String cardTypeName;
  private String cardStatusName;
  private String passTypeName;
}
