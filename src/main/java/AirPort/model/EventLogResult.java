package AirPort.model;

import lombok.Data;

/**
 * 실시간 이벤트 한 건(모든 종류) — 그래픽맵 아래 이벤트 표의 한 줄. 인증만이 아니라 문 열림·잠김·운영자 조작·장치 연결까지 소켓이 흘려 주는 대로 싣는다.
 *
 * <p>사진·DB 보강은 하지 않는다(모든 이벤트마다 하면 밀린다). 사람 이름은 장비가 실어 준 값이다.
 */
@Data
public class EventLogResult {
  private String eventTime; // "yyyy-MM-dd HH:mm:ss" — 서버 시간대
  private String eventCode;
  private String eventName; // BiostarX 원래 이름(예: DOOR_OPENED)
  private String label; // 화면 문구(예: 문 열림)
  private String tone; // success(통과) · error(거부·이상) · info(그 밖)
  private String deviceId;
  private String deviceName;
  private String userId;
  private String userName;
  private String doorId;
  private String doorName;
}
