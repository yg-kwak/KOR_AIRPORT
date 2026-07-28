package AirPort.model;

import AirPort.common.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 방문(tb_visit) 목록 검색 파라미터 — 공통 페이징/정렬 + 상태 필터 + 방문유형 계열(codeTag). */
@Getter
@Setter
public class VisitSearchParam extends PageParam {
  private String statusCode; // tb_common(VS)
  private String codeTag; // 방문유형 계열 필터 — PT.code_tag (PTD02 임시 / PTD03 장기·상주). 서버가 화면별로 설정
}
