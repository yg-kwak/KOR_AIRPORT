package AirPort.model;

import AirPort.common.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 방문(tb_visit) 목록 검색 파라미터 — 공통 페이징/정렬 + 방문유형·상태 필터. */
@Getter
@Setter
public class VisitSearchParam extends PageParam {
  private String visitType; // tb_common(PT)
  private String statusCode; // tb_common(VS)
}
