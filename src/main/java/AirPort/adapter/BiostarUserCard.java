package AirPort.adapter;

/**
 * 사용자에게 부여할 BiostarX 카드 — 사용자 payload 의 {@code User.cards[]} 한 건.
 *
 * <p>{@code id} 는 카드 등록 응답의 id(tb_card.biostar_card_id), {@code cardNo} 는 카드번호(biostar_card_value).
 */
public record BiostarUserCard(String id, String cardNo) {}
