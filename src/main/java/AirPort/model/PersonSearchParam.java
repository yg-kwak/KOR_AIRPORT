package AirPort.model;

import AirPort.common.PageParam;

/** 인원(tb_person) 목록 검색 파라미터. 공통 페이징/정렬(PageParam) + 도메인 필터(기관/상태). */
public class PersonSearchParam extends PageParam {

  private String personType; // 발급유형 고정 필터(정규인원등록 = PT01). 서비스가 설정
  private String companyCode; // 기관 필터
  private String statusCode; // 상태 필터

  public String getPersonType() {
    return personType;
  }

  public void setPersonType(String personType) {
    this.personType = personType;
  }

  public String getCompanyCode() {
    return companyCode;
  }

  public void setCompanyCode(String companyCode) {
    this.companyCode = companyCode;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(String statusCode) {
    this.statusCode = statusCode;
  }
}
