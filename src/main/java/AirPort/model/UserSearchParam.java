package AirPort.model;

import AirPort.common.PageParam;

/** 사용자(tb_login_user) 목록 검색 파라미터. 공통 페이징/정렬(PageParam) + 도메인 필터(사용여부). */
public class UserSearchParam extends PageParam {

  private String useYn; // "" (전체) | "Y" | "N"

  public String getUseYn() {
    return useYn;
  }

  public void setUseYn(String useYn) {
    this.useYn = useYn;
  }
}
