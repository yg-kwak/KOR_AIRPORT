package AirPort.model;

import AirPort.common.PageParam;

/** 기관(tb_company) 목록 검색 파라미터. 공통 페이징/정렬(PageParam) + 도메인 필터(사용유무). */
public class CompanySearchParam extends PageParam {

  private String useYn; // "" (전체) | "Y" | "N"
  private boolean searchCar; // true 면 기본 검색어에 소속 차량번호(tb_car.car_no)도 포함 — 기관차량등록만 설정

  public String getUseYn() {
    return useYn;
  }

  public void setUseYn(String useYn) {
    this.useYn = useYn;
  }

  public boolean isSearchCar() {
    return searchCar;
  }

  public void setSearchCar(boolean searchCar) {
    this.searchCar = searchCar;
  }
}
