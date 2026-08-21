package AirPort.model;

import lombok.Data;

/** 방문객 1명 — 생년월일·성명·소속 + 카드(선택). 저장 시 tb_person 으로 만들어진다. */
@Data
public class VisitorForm {

  private String personId; // 기존 방문객이면 유지, 신규면 서버가 채번
  private String personName;
  private String birthDate;
  private String affiliation; // 소속 (자유입력)
  private Integer cardId; // 선택한 카드(tb_card.card_id), 없으면 null
  private String cardLabel; // 표시용 카드번호(응답 전용) — 저장 시 무시
  private String lastCardNo; // 마지막 배정 카드번호(응답 전용) — 회수 후에도 보존, 저장 시 무시
  private String checkoutDt; // 개별 퇴실 일시(응답 전용) — 값이 있으면 재실이 아니라 카드 재발급 불가
  private String phone; // 인솔자 연락처(응답 전용) — 그 방문에 적어 둔 번호
  private String biostarUserId; // BiostarX 사용자ID(응답 전용) — 장비 생성이 성공해야 채워진다(화면 인원ID 표시 기준)
}
