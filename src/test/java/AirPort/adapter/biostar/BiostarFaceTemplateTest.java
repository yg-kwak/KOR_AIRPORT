package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * upload_picture 얼굴 템플릿 정규화 검증 — BiostarX 응답(고정 버퍼라 뒤가 널로 채워짐)을 사용자 payload 의 template_ex 형식(널 꼬리
 * 제거 + 표준 base64)으로 바꾸는지 실제 응답 값으로 확인한다. JSON 이스케이프 해제도 함께 검증.
 */
class BiostarFaceTemplateTest {

  /** BiostarX 응답 원문의 image_template — JSON 이스케이프 포함, 뒤쪽 널 패딩 있음. */
  private static final String RESPONSE_TEMPLATE =
      "AAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB4hHKTeYKKg4SGh3qSfoiLbneHhn2FYYKBo41sfnaGfZCGjIeJa3F3gIR3go92iqFphWaLk4KCgJibhmyBjGFtc45loo6UgpCUYJ9vc3uNeXN8eIaMcnZng297lJp5i4aPhYF\\/i2iJhYCGgX5ygnaTf3lfen+FhHV8dI6AgX9zkJWMjnqCZ4qJjGx8g4h6jZR2pmh\\/jIqFboZ4ep2OaoR3eH6BaHWVioB1gHd9kpJ8j4dri5NndYt9eHqGf4p4k2OJk36ChHJ8hJCOeIeDfIN+cnqMbHpwdm52hI6MipByeIOZiXSAdniFfWp\\/lI2Bi2WQkG93k2t6e4dja3eRdnR2eHVugWuKaXuUcpyChYpteJF0cHh2ZZuKXIV6eHZzhnyHhnB8nH12iXNriZVuqJGAe3J9fXOMg3l8fXB0gI12cZSKfXCBgX10a3Z\\/jmd4kI2RjmJ1dnyFboKHgICkhY2EkXaFd3p\\/bHuIfaB9e16Cin+SfIN4kn+LdG14g4WYdnmDjoKKjIdzkYCHnYJ\\/kY9pmIx9n3h7f3uJc3eLfXt0fHGHbpF8hW9vkYSKloxwj49thIGCcmxzcHd5YYade3OSf3KGe4F6d3GFfn9\\/X4mPeIyKd4F1oHOJcmJtlnmJkYGFl4d7iYaCjWJ9j3iJh4F3eo6Zi36WYXh1oId5dGJ+ZwAAAAAAAAAA";

  /** 사용자 payload 로 나가야 하는 template_ex. */
  private static final String EXPECTED_TEMPLATE_EX =
      "AAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB4hHKTeYKKg4SGh3qSfoiLbneHhn2FYYKBo41sfnaGfZCGjIeJa3F3gIR3go92iqFphWaLk4KCgJibhmyBjGFtc45loo6UgpCUYJ9vc3uNeXN8eIaMcnZng297lJp5i4aPhYF/i2iJhYCGgX5ygnaTf3lfen+FhHV8dI6AgX9zkJWMjnqCZ4qJjGx8g4h6jZR2pmh/jIqFboZ4ep2OaoR3eH6BaHWVioB1gHd9kpJ8j4dri5NndYt9eHqGf4p4k2OJk36ChHJ8hJCOeIeDfIN+cnqMbHpwdm52hI6MipByeIOZiXSAdniFfWp/lI2Bi2WQkG93k2t6e4dja3eRdnR2eHVugWuKaXuUcpyChYpteJF0cHh2ZZuKXIV6eHZzhnyHhnB8nH12iXNriZVuqJGAe3J9fXOMg3l8fXB0gI12cZSKfXCBgX10a3Z/jmd4kI2RjmJ1dnyFboKHgICkhY2EkXaFd3p/bHuIfaB9e16Cin+SfIN4kn+LdG14g4WYdnmDjoKKjIdzkYCHnYJ/kY9pmIx9n3h7f3uJc3eLfXt0fHGHbpF8hW9vkYSKloxwj49thIGCcmxzcHd5YYade3OSf3KGe4F6d3GFfn9/X4mPeIyKd4F1oHOJcmJtlnmJkYGFl4d7iYaCjWJ9j3iJh4F3eo6Zi36WYXh1oId5dGJ+Zw==";

  @Test
  void 응답템플릿은_이스케이프해제와_널꼬리제거를_거쳐_template_ex_가_된다() throws Exception {
    String json = "{\"image_template\":\"" + RESPONSE_TEMPLATE + "\"}";
    String parsed = new ObjectMapper().readTree(json).path("image_template").asText();
    assertFalse(parsed.contains("\\"), "JSON 파싱 후에는 역슬래시가 남지 않는다");

    assertEquals(EXPECTED_TEMPLATE_EX, BiostarUserAdapter.normalizeTemplate(parsed));
  }

  @Test
  void 널꼬리가_없으면_그대로_두고_빈값은_null() {
    assertEquals("QUJD", BiostarUserAdapter.normalizeTemplate("QUJD")); // "ABC"
    assertNull(BiostarUserAdapter.normalizeTemplate(null));
    assertNull(BiostarUserAdapter.normalizeTemplate("  "));
  }

  /**
   * 실제 사고 사례 — 응답 버퍼 뒤가 널이 아닌 잔여 바이트로 끝나면 널 꼬리 제거만으로는 안 잘려서, 장비 등록값(544B)보다 긴 템플릿이 전송됐다. 그 상태로 저장된
   * 사용자는 장치 얼굴 인증이 실패한다.
   */
  @Test
  void 잔여_바이트가_붙어_와도_템플릿은_544바이트로_잘린다() {
    byte[] buf = new byte[550];
    for (int i = 0; i < 544; i++) {
      buf[i] = (byte) (i % 251);
    }
    // 544 뒤에 널이 아닌 잔여 데이터(실제 관측값: 08 4f 3f 5f f6 7f)
    byte[] junk = {0x08, 0x4f, 0x3f, 0x5f, (byte) 0xf6, 0x7f};
    System.arraycopy(junk, 0, buf, 544, junk.length);

    String out = BiostarUserAdapter.normalizeTemplate(Base64.getEncoder().encodeToString(buf));
    byte[] sent = Base64.getDecoder().decode(out);
    assertEquals(544, sent.length, "장비가 저장하는 길이와 같아야 한다");
    assertArrayEquals(Arrays.copyOf(buf, 544), sent);
  }

  @Test
  void 널로_채워져_온_버퍼도_544바이트가_된다() {
    byte[] buf = new byte[552]; // 널 꼬리 8바이트(이전에 확인된 형태)
    for (int i = 0; i < 544; i++) {
      buf[i] = (byte) ((i % 250) + 1); // 0 이 아닌 값으로 채워 꼬리와 구분
    }
    String out = BiostarUserAdapter.normalizeTemplate(Base64.getEncoder().encodeToString(buf));
    assertEquals(544, Base64.getDecoder().decode(out).length);
  }
}
