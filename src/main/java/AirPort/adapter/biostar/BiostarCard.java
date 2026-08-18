package AirPort.adapter.biostar;

/**
 * BiostarX 카드 연동 결과 — 카드 등록({@code POST /api/cards}) · 장치 읽기({@code scan_card}) 공통.
 *
 * <p>{@code biostarCardId} 는 응답의 {@code id}(→ tb_card.biostar_card_id), {@code cardNo} 는 {@code
 * card_id}(→ tb_card.biostar_card_value). 장치 읽기 응답의 {@code id} 는 "0"(미등록)이라 카드번호만 쓴다.
 */
public record BiostarCard(boolean success, String message, String biostarCardId, String cardNo) {

  public static BiostarCard ok(String biostarCardId, String cardNo) {
    return new BiostarCard(true, null, biostarCardId, cardNo);
  }

  public static BiostarCard fail(String message) {
    return new BiostarCard(false, message, null, null);
  }
}
