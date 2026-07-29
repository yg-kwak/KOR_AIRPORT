package AirPort.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
