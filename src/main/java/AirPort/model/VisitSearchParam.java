package AirPort.model;

import AirPort.common.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 방문(tb_visit) 목록 검색 파라미터 — 공통 페이징/정렬 + 상태·출입시작 기간 필터 + 방문유형 계열(codeTag). */
@Getter
@Setter
public class VisitSearchParam extends PageParam {
  private String statusCode; // tb_common(VS)
  private String codeTag; // 방문유형 계열 필터 — PT.code_tag (PTD02 임시 / PTD03 장기·상주). 서버가 화면별로 설정
  private String startDate; // 출입시작(work_start_dt) 기간 시작 yyyy-MM-dd — 빈값이면 전체
  private String endDate; // 출입시작 기간 종료 yyyy-MM-dd (그날 24시까지 포함)
}
