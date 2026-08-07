package AirPort.model;

import AirPort.common.PageParam;

/** 감사추적(tb_system_log) 목록 검색 파라미터. 공통 페이징/정렬 + 유형·기간 필터. */
public class SystemLogSearchParam extends PageParam {

  private String actionType; // "" (전체) | tb_common AT code_id
  private Integer menuId; // null (전체) | tb_menu.menu_id
  // 배치·키오스크처럼 화면을 거치지 않은 기록만. menuId 와 함께 오지 않는다(목록의 [시스템])
  private boolean systemOnly;
  private String startDate; // yyyy-MM-dd
  private String endDate; // yyyy-MM-dd

  /** 이 프로젝트는 Lombok 을 쓰지 않는다 — 게터가 없으면 요청 바인딩도 MyBatis 조건 평가도 되지 않는다. */
  public boolean isSystemOnly() {
    return systemOnly;
  }

  public void setSystemOnly(boolean systemOnly) {
    this.systemOnly = systemOnly;
  }

  public Integer getMenuId() {
    return menuId;
  }

  public void setMenuId(Integer menuId) {
    this.menuId = menuId;
  }

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
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
