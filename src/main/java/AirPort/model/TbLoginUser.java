package AirPort.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  /** 비밀번호(ARIA). 요청 본문으로 받되 응답 JSON 으로는 절대 내보내지 않는다(WRITE_ONLY). */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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

  private String authName; // 목록 표시용(tb_menu_auth 조인) — 저장 컬럼 아님

  public boolean isRoot() {
    return "Y".equals(rootYn);
  }
}
