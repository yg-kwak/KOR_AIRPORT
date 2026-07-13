package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/** 감사 이력 (tb_system_log). docs/security.md, docs/database.md */
@Data
public class TbSystemLog {
  private Long logId;
  private String userId;
  private String userName;
  private String actionType; // tb_common cmm_id='AT'
  private Integer menuId;
  private String actionDetail;
  private String remark;
  private LocalDateTime regDt;

  private String actionTypeName; // 목록 표시용(tb_common AT 조인) — 저장 컬럼 아님
  private String menuName; // 목록 표시용(tb_menu 조인) — 저장 컬럼 아님
}
