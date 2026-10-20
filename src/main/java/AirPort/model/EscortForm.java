package AirPort.model;

import java.util.List;

/**
 * 에스코트 사용자 반영 — 출입문 하나에 더할 사람({@code userIds})과 뺄 사람({@code removeIds}). 바꾸지 않는 등록자는 서버가 장비에서 다시 읽어
 * 함께 보낸다.
 */
public class EscortForm {

  private Long doorId;
  private List<String> userIds;
  private List<String> removeIds;

  public Long getDoorId() {
    return doorId;
  }

  public void setDoorId(Long doorId) {
    this.doorId = doorId;
  }

  public List<String> getUserIds() {
    return userIds;
  }

  public void setUserIds(List<String> userIds) {
    this.userIds = userIds;
  }

  public List<String> getRemoveIds() {
    return removeIds;
  }

  public void setRemoveIds(List<String> removeIds) {
    this.removeIds = removeIds;
  }
}
