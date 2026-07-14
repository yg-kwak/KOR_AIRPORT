package AirPort.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 하위 그룹 추가 폼 — 상위 노드 + 선택한 BiostarX 출입그룹들(biostarAcId/biostarAcName). */
@Data
public class AcGroupAddForm {
  private Integer parentId;
  private List<TbAcGroup> groups = new ArrayList<>();
}
