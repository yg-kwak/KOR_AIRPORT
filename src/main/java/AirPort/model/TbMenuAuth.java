package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/** 권한(그룹) (tb_menu_auth). 사용자에 부여하는 권한 단위. docs/database.md */
@Data
public class TbMenuAuth {
  private Integer authId;
  private String authName;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  private Integer menuCount; // 목록 표시용(부여된 메뉴 권한 수) — 저장 컬럼 아님
}
