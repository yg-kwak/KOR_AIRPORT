package AirPort.model;

import lombok.Data;

/** 공통 코드 (tb_common). 코드구분(cmmId) + 코드(codeId) 복합키. docs/database.md */
@Data
public class TbCommon {
  private String cmmId;
  private String cmmName;
  private String codeId;
  private String codeName;
  private String codeTag;
  private String codeRemark;
  private String userIpnut; // 사용자등록여부 (설계 컬럼명 유지)
  private String useYn;
}
