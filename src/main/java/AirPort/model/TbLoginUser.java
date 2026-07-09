package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 사용자/로그인 계정 (tb_login_user). 세션 사용자 객체로도 사용한다.
 *
 * <p>userName/password 는 ARIA 암호화 대상(docs/security.md). 세션에 담을 때 password 는 비운다.
 */
@Data
public class TbLoginUser {
  private String userId;
  private String userName;
  private String password;
  private String deptName;
  private String useYn;
  private String rootYn;
  private Integer authId;
  private Integer loginFailCnt;
  private LocalDateTime passwordChangeDt;
  private Integer startMenuId;
  private String workLocationCode;
  private String workType;
  private String deskIp;
  private String devId;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  public boolean isRoot() {
    return "Y".equals(rootYn);
  }
}
