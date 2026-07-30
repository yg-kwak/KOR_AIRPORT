package AirPort.adapter;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * BiostarX 응답 판정 검증 — <b>4xx/5xx 응답도 본문에 사유를 담아 보낸다</b>. 상태코드만 보고 "HTTP 400" 을 돌려주면 화면에 원인이 남지 않으므로
 * 본문의 {@code Response.message}/{@code code} 를 우선 쓰는지, 사유가 없을 때만 상태코드별 안내로 대체하는지 확인한다.
 */
class BiostarResponseErrorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String error(int status, String body) {
    return BiostarAdapter.responseError(MAPPER, resp(status, body));
  }

  @Test
  void 오류본문의_사유를_상태코드와_함께_돌려준다() {
    String msg =
        error(400, "{\"Response\":{\"code\":\"101\",\"message\":\"Invalid picture format\"}}");
    assertTrue(msg.contains("Invalid picture format"), "장비가 준 사유가 남아야 한다: " + msg);
    assertTrue(msg.contains("101"), "코드도 함께 남는다: " + msg);
    assertTrue(msg.contains("400"), "상태코드도 함께 남는다: " + msg);
  }

  @Test
  void 사유가_없는_400은_조치를_알_수_있는_안내로_바뀐다() {
    String msg = error(400, "<html>Bad Request</html>"); // JSON 이 아니라 사유를 못 읽는 경우
    assertTrue(msg.contains("요청을 거부"), "무엇이 문제인지 안내한다: " + msg);
    assertTrue(msg.contains("400"), msg);
  }

  @Test
  void 권한_미지원_서버오류는_각각_다른_안내를_준다() {
    assertTrue(error(403, "").contains("권한"));
    assertTrue(error(404, "").contains("지원하지 않"));
    assertTrue(error(500, "").contains("서버 오류"));
  }

  @Test
  void 정상응답은_null_이고_200_이라도_code_가_있으면_오류다() {
    assertNull(error(200, "{\"Response\":{\"code\":\"0\"}}"));
    assertNull(error(200, "not json")); // 200 인데 파싱 불가 — 성공으로 본다(기존 동작 유지)
    assertTrue(
        error(200, "{\"Response\":{\"code\":\"103\",\"message\":\"not supported\"}}")
            .contains("not supported"));
  }

  /** 테스트용 최소 HttpResponse — 판정에 쓰는 statusCode/body 만 채운다. */
  private static HttpResponse<String> resp(int status, String body) {
    return new HttpResponse<>() {
      @Override
      public int statusCode() {
        return status;
      }

      @Override
      public String body() {
        return body;
      }

      @Override
      public java.net.http.HttpRequest request() {
        return null;
      }

      @Override
      public java.util.Optional<HttpResponse<String>> previousResponse() {
        return java.util.Optional.empty();
      }

      @Override
      public java.net.http.HttpHeaders headers() {
        return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
      }

      @Override
      public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
        return java.util.Optional.empty();
      }

      @Override
      public java.net.URI uri() {
        return java.net.URI.create("https://test/api");
      }

      @Override
      public java.net.http.HttpClient.Version version() {
        return java.net.http.HttpClient.Version.HTTP_1_1;
      }
    };
  }
}
