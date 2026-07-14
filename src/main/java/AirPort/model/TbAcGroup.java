package AirPort.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 출입권한 그룹 (tb_ac_group). 트리 구조.
 *
 * <p>최상위(level 1)는 tb_common(cmm_id='AR')과 동기화 — ar_code=code_id. 하위는 BiostarX 출입그룹을 매핑
 * (biostar_ac_id/biostar_ac_name, ar_code 는 상위에서 상속). docs/database.md
 */
@Data
public class TbAcGroup {
  private Integer acGroupId;
  private String acGroupName;
  private Integer parentAcGroupId;
  private String arCode; // → tb_common(cmm_id='AR').code_id (하위는 상위 상속)
  private Integer acGroupLevel;
  private Integer acGroupOrder;
  private Integer biostarAcId;
  private String biostarAcName;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  private final List<TbAcGroup> children = new ArrayList<>(); // 트리 표시용(저장 컬럼 아님)

  public boolean isTop() {
    return parentAcGroupId == null;
  }
}
