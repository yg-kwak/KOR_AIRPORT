package AirPort.adapter.biostar;

import java.util.Map;

/**
 * BiostarX 실시간 이벤트 한 건(소켓 MESSAGE) 중 화면에 필요한 값만.
 *
 * @param eventCode 이벤트 유형 코드(예: 4106)
 * @param eventName 이벤트 유형 이름(예: VERIFY_SUCCESS_CARD_FACE)
 * @param datetime 장치 시각(BiostarX 형식, 예: 2026-08-11T01:38:01.00Z)
 * @param deviceId 장치 ID — 화면에서 고른 장치와 비교해 거른다
 * @param deviceName 장치 이름
 * @param userId 인증한 사용자 ID(= 우리 인원ID). 미등록 카드 등은 비어 있다
 * @param imageId 인증 사진 ID(events/images 조회 키). 성공이어도 없을 수 있다
 */
public record BiostarAuthEvent(
    String eventCode,
    String eventName,
    String datetime,
    String deviceId,
    String deviceName,
    String userId,
    String imageId) {

  /** 통과 문구 — 인증 수단이 무엇이든 사람이 지나갔다는 사실은 하나다. */
  private static final String GRANTED = "O 인증 성공";

  /**
   * 화면에 표기할 이벤트와 문구 — 현장에서 확인한 코드다.
   *
   * <p>여기 없는 코드는 <b>화면에 올리지 않는다</b>. 장비는 문 열림·장치 연결 같은 것도 같은 소켓으로 흘려보내는데, 그것까지 띄우면 정작 사람이 지나간 기록이
   * 묻힌다.
   */
  private static final Map<String, String> RESULT_BY_CODE =
      Map.of(
          "4102", GRANTED, // 카드
          "4106", GRANTED, // 카드 + 얼굴
          "4867", GRANTED, // 얼굴
          "6401", "X 출입제한구역", // 출입거부 — 잘못된 출입그룹
          "6405", "X 안티 패스"); // 출입거부 — 하드 안티패스백

  /**
   * 화면에 띄울 문구. 표기 대상이 아니면 null.
   *
   * <p>코드로 먼저 찾고, 없으면 <b>이름 규칙</b>으로 통과를 잡는다. 성공 코드는 인증 수단 조합마다 갈라지는데(카드·얼굴·지문·조합) 표에 없는 조합이 현장에서
   * 쓰이면 그 사람만 화면에서 사라진다. 이름은 모두 {@code VERIFY_SUCCESS_*}/{@code IDENTIFY_SUCCESS_*} 로 시작하므로 그물이 된다.
   */
  public String resultLabel() {
    String byCode = eventCode == null ? null : RESULT_BY_CODE.get(eventCode);
    if (byCode != null) {
      return byCode;
    }
    boolean successName =
        eventName != null
            && (eventName.startsWith("VERIFY_SUCCESS") || eventName.startsWith("IDENTIFY_SUCCESS"));
    return successName ? GRANTED : null;
  }

  /** 화면에 올릴 이벤트인가 — 통과든 거부든 표기 대상이면 참. */
  public boolean displayable() {
    return resultLabel() != null;
  }

  /** 문을 통과했는가 — 화면 색이 여기서 갈린다(통과 초록, 거부 빨강). */
  public boolean granted() {
    return GRANTED.equals(resultLabel());
  }
}
