package AirPort.model;

import AirPort.common.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 카드(tb_card) 목록 검색 파라미터 — 공통 페이징/정렬 + 카드상태·할당여부 필터. */
@Getter
@Setter
public class CardSearchParam extends PageParam {

  /** 카드상태 코드 (tb_common CS). 비면 전체. */
  private String cardStatus;

  /** 할당여부: 'Y'=인원에 할당됨, 'N'=미할당(회수), 비면 전체. */
  private String assigned;
}
