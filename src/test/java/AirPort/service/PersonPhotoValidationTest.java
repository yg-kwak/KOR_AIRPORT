package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * 사진 사전 검증 단위 테스트 — BiostarX 까지 보내면 "HTTP 400" 만 돌아오므로, 형식·용량은 서버에서 먼저 걸러 무엇을 바꿔야 하는지 알려야 한다. 확장자가
 * 아니라 선두 바이트로 판정하는지도 확인한다(이름만 .jpg 인 HEIC 차단).
 */
class PersonPhotoValidationTest {

  /** 선두 바이트 + 뒤쪽 더미로 만든 최소 이미지. */
  private static String encode(byte[] head, int totalBytes) {
    byte[] raw = new byte[totalBytes];
    System.arraycopy(head, 0, raw, 0, head.length);
    return Base64.getEncoder().encodeToString(raw);
  }

  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
  private static final byte[] HEIC = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};

  @Test
  void JPG_와_PNG_는_통과한다() {
    assertNull(PersonFaceService.rejectReason(encode(JPEG, 1024)));
    assertNull(PersonFaceService.rejectReason(encode(PNG, 1024)));
  }

  @Test
  void 이름만_jpg_인_HEIC_는_선두바이트로_걸린다() {
    String reason = PersonFaceService.rejectReason(encode(HEIC, 1024));
    assertTrue(reason != null && reason.contains("JPG 또는 PNG"), "형식을 알려준다: " + reason);
    assertTrue(reason.contains("HEIC"), "변환 방법을 안내한다: " + reason);
  }

  @Test
  void 용량_한도를_넘으면_실제_크기와_함께_거부한다() {
    String reason = PersonFaceService.rejectReason(encode(JPEG, 5 * 1024 * 1024));
    assertTrue(reason != null && reason.contains("너무 큽니다"), reason);
    assertTrue(reason.contains("5.0MB"), "실제 크기를 보여준다: " + reason);
    assertTrue(reason.contains("4MB"), "허용 한도를 보여준다: " + reason);
  }

  @Test
  void 줄바꿈이_섞인_base64_도_받는다() {
    // MIME 인코딩(76자마다 개행)으로 와도 '데이터가 올바르지 않습니다'로 오판하면 안 된다
    String mime = Base64.getMimeEncoder().encodeToString(bytes(JPEG, 4096));
    assertTrue(mime.contains("\r\n"), "테스트 전제: 개행이 포함된 base64");
    assertNull(PersonFaceService.rejectReason(mime));
  }

  @Test
  void base64_가_아니면_사유를_알린다() {
    String reason = PersonFaceService.rejectReason("!!! not base64 !!!");
    assertTrue(reason != null && reason.contains("올바르지 않습니다"), reason);
  }

  private static byte[] bytes(byte[] head, int totalBytes) {
    byte[] raw = new byte[totalBytes];
    System.arraycopy(head, 0, raw, 0, head.length);
    return raw;
  }
}
