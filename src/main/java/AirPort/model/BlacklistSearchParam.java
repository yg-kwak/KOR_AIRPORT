package AirPort.model;

import AirPort.common.PageParam;

/**
 * 제재인원(tb_blacklist) 목록 검색 파라미터.
 *
 * <p>성명·생년월일은 ARIA 암호문이라 부분검색이 되지 않는다. 공통 {@code keyword} 는 <b>소속·비고</b>(평문)에만 걸고, 성명은 완전일치일 때만
 * 서비스가 암호문으로 바꿔 비교한다({@code PageParam.keywordEnc} 와 같은 선례).
 */
public class BlacklistSearchParam extends PageParam {

  /** "" (전체) | "active" 정지 중만 */
  private String banState;

  public String getBanState() {
    return banState;
  }

  public void setBanState(String banState) {
    this.banState = banState;
  }
}
