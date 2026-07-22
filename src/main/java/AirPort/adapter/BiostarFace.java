package AirPort.adapter;

/**
 * BiostarX 얼굴 등록 데이터 — 정규화 이미지 + 템플릿 2종.
 *
 * <p>파일 업로드({@code /api/users/check/upload_picture})와 장치 촬영({@code
 * /api/devices/{id}/credentials/face}) 응답을 같은 형태로 정규화한다. 사용자 생성 시 {@code
 * credentials.visualFaces} 로 전송된다.
 */
public record BiostarFace(
    boolean success, String message, String image, String template9, String template5) {

  public static BiostarFace ok(String image, String template9, String template5) {
    return new BiostarFace(true, "OK", image, template9, template5);
  }

  public static BiostarFace fail(String message) {
    return new BiostarFace(false, message, null, null, null);
  }
}
