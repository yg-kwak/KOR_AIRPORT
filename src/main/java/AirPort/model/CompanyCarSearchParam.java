package AirPort.model;

import AirPort.common.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 기관차량등록(tb_car) 목록 검색 파라미터 — 공통 페이징/정렬 + 기관·차종 필터. */
@Getter
@Setter
public class CompanyCarSearchParam extends PageParam {

  /** 기관코드 (tb_company). 비면 전체. */
  private String companyCode;

  /** 차종 코드 (tb_common CT). 비면 전체. */
  private String carType;
}
