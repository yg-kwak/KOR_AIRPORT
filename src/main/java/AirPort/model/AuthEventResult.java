package AirPort.model;

/**
 * 실시간 인증 이벤트 한 건 — 화면(모니터링 → 실시간 이벤트)으로 밀어 보내는 값.
 *
 * <p>장비 이벤트에 우리 DB 값(성명·소속·허가구역·등록사진)을 붙인 결과다. 성명은 <b>복호화해서</b> 담는다 — 화면에 그대로 뿌리는 값이라 여기까지가 복호화
 * 경계다.
 *
 * <p>사진 두 장은 base64 다: {@code registeredPhoto}=우리 DB(tb_person_photo) 등록 사진, {@code authPhoto}=장비가
 * 방금 찍은 인증 사진. 어느 쪽이든 없을 수 있다(등록 사진 미보유, 카드만으로 인증해 얼굴을 안 찍은 경우).
 *
 * <p>{@code resultLabel}·{@code granted} 는 <b>서버가 정한다</b>. 어떤 이벤트 코드가 무슨 뜻인지는 장비 규약이라 화면이 알 일이 아니고,
 * 한 곳에 모아 두어야 코드가 늘어날 때 화면을 건드리지 않는다.
 */
public class AuthEventResult {

  private String eventTime; // "HH:mm:ss" — 장치 시각
  private String deviceId;
  private String deviceName;
  private String personId; // 장비 user_id = 우리 인원ID. 미등록 인증이면 비어 있다
  private String personName;
  private String companyName;
  private String areas; // 허가구역 번호를 이어 붙인 값(1~5구역 → "12345")
  private String registeredPhoto;
  private String authPhoto;
  private String resultLabel; // 화면 표기 문구 ("O 인증 성공" / "X 안티 패스" / "X 출입제한구역")
  private boolean granted; // 통과 여부 — 화면 색이 여기서 갈린다(통과 초록, 거부 빨강)

  /**
   * 얼굴을 등록하는 인원인가(정규인원).
   *
   * <p>사진 없는 칸에 <b>무엇을 세울지</b>가 여기서 갈린다. 정규인원은 얼굴이 있어야 정상이라 빠진 것이 눈에 띄어야 하고(사람 모양), 임시·장기·상주처럼 카드로만
   * 인증하는 인원은 애초에 얼굴을 안 찍으므로 없는 것이 정상이다(카드 모양). 둘을 같은 그림으로 두면 "등록이 빠진 사람" 과 "원래 없는 사람" 이 구분되지 않는다.
   *
   * <p>인원 구분은 우리 DB 사정이라 화면이 알 일이 아니다 — 서버가 정해서 내려준다. 미등록 인증(우리 DB 에 없는 사용자)이면 false.
   */
  private boolean faceUser;

  public boolean isFaceUser() {
    return faceUser;
  }

  public void setFaceUser(boolean faceUser) {
    this.faceUser = faceUser;
  }

  public String getEventTime() {
    return eventTime;
  }

  public void setEventTime(String eventTime) {
    this.eventTime = eventTime;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(String deviceId) {
    this.deviceId = deviceId;
  }

  public String getDeviceName() {
    return deviceName;
  }

  public void setDeviceName(String deviceName) {
    this.deviceName = deviceName;
  }

  public String getPersonId() {
    return personId;
  }

  public void setPersonId(String personId) {
    this.personId = personId;
  }

  public String getPersonName() {
    return personName;
  }

  public void setPersonName(String personName) {
    this.personName = personName;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getAreas() {
    return areas;
  }

  public void setAreas(String areas) {
    this.areas = areas;
  }

  public String getRegisteredPhoto() {
    return registeredPhoto;
  }

  public void setRegisteredPhoto(String registeredPhoto) {
    this.registeredPhoto = registeredPhoto;
  }

  public String getAuthPhoto() {
    return authPhoto;
  }

  public void setAuthPhoto(String authPhoto) {
    this.authPhoto = authPhoto;
  }

  public String getResultLabel() {
    return resultLabel;
  }

  public void setResultLabel(String resultLabel) {
    this.resultLabel = resultLabel;
  }

  public boolean isGranted() {
    return granted;
  }

  public void setGranted(boolean granted) {
    this.granted = granted;
  }
}
