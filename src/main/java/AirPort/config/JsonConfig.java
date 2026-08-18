package AirPort.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 요청 JSON 의 문자열은 <b>앞뒤 공백을 떼고</b> 받는다.
 *
 * <p>왜 여기서 하나 — 성명·대표자명처럼 ARIA 로 암호화하는 항목은 {@code ARIAUtil.ariaDecrypt} 가 블록 패딩을 벗기려고 {@code
 * trim()} 을 건다. 그래서 <b>암호화 컬럼은 앞뒤 공백이 사라지고 평문 컬럼은 남는</b> 상태였다. 같은 화면 안에서도 항목마다 동작이 달라, 사용자가 실수로 넣은
 * 공백이 어떤 칸에서는 지워지고 어떤 칸에서는 검색을 빗나가게 만든다.
 *
 * <p>서비스마다 {@code trim()} 을 흩어 놓으면 새 항목이 생길 때마다 빠진다. 들어오는 문 하나에서 통일한다.
 */
@Configuration
public class JsonConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer trimIncomingStrings() {
    return builder -> builder.deserializerByType(String.class, new TrimmingStringDeserializer());
  }

  /** 문자열을 읽는 즉시 {@code trim()}. 값이 공백뿐이면 빈 문자열이 되어 기존 {@code isBlank()} 검증에 그대로 걸린다. */
  private static class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

    TrimmingStringDeserializer() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      String value = p.getValueAsString();
      return value == null ? null : value.trim();
    }
  }
}
