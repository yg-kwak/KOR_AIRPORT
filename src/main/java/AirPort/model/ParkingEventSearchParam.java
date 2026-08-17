package AirPort.model;

import AirPort.common.PageParam;

/** 주차 조회(tb_parking_event) 목록 검색 파라미터. 공통 페이징/정렬 + 구분·기간·차량번호 필터. */
public class ParkingEventSearchParam extends PageParam {

  /** "" (전체) | in(입차 계열) | out(출차 계열). eventType 원문이 아니라 계열로 고른다. */
  private String direction;

  /** true 면 차단기가 열리지 않은 건(…NotOpen)만. 미개방만 따로 보려는 화면 조건. */
  private boolean notOpenOnly;

  private String startDate; // yyyy-MM-dd
  private String endDate; // yyyy-MM-dd

  /** 이 프로젝트는 Lombok 을 쓰지 않는다 — 게터가 없으면 요청 바인딩도 MyBatis 조건 평가도 되지 않는다. */
  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public boolean isNotOpenOnly() {
    return notOpenOnly;
  }

  public void setNotOpenOnly(boolean notOpenOnly) {
    this.notOpenOnly = notOpenOnly;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }
}
