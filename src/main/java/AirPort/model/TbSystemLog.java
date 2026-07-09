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
}
