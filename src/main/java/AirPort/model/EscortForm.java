package AirPort.model;

import java.util.List;

/** 에스코트 사용자 추가 — 출입문 하나에 넣을 BiostarX 사용자ID 들. 이미 등록된 사람은 서버가 장비에서 다시 읽어 함께 보낸다. */
public class EscortForm {

  private Long doorId;
  private List<String> userIds;

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
}
