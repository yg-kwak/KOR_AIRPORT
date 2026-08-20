package AirPort.model;

/**
 * 가져오기 대상 사용자그룹 한 줄 — 그룹을 골라 나눠 불러오기 위한 목록.
 *
 * <p>장비에 수천 명이 있으면 한 번에 다 받는 것이 느리고, MSSQL 매개변수 한도(2100)에도 걸린다. 그룹 단위로 끊어 불러오면 필요한 기관만 보고 처리할 수 있다.
 */
public class ImportGroupResult {

  private long groupId; // BiostarX 사용자그룹 ID
  private String groupName; // 장비에 등록된 그룹명
  private String companyName; // 연결된 기관명 — 없으면 null(가져올 수 없는 그룹)

  public long getGroupId() {
    return groupId;
  }

  public void setGroupId(long groupId) {
    this.groupId = groupId;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }
}
