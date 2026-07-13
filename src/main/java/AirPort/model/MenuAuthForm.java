package AirPort.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 권한 등록/수정 폼 — 권한명 + 메뉴별 권한 매트릭스(details). 화면(AJAX) 요청 바디. */
@Data
public class MenuAuthForm {
  private Integer authId;
  private String authName;
  private List<TbMenuAuthDetail> details = new ArrayList<>();
}
