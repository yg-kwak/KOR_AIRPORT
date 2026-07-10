package AirPort.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Suprema BiostarX 연동 어댑터 — 외부 연동은 이 계층으로만 격리한다(AGENTS §4). (docs/integration.md)
 *
 * <p>BiostarX 는 내부망 self-signed 인증서를 쓰므로 TLS 신뢰를 완화한다(내부 어플라이언스 전제).
 */
@Component
public class BiostarAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarAdapter.class);

  private final ObjectMapper objectMapper;

  public BiostarAdapter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** BiostarX 로그인(POST /api/login) 시도. 성공 판정: 200 + bs-session-id 헤더 또는 Response.code=="0". */
  public BiostarResult testLogin(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 비어 있습니다.");
    }
    try {
      String base = (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
      String url = base + "/api/login";
      String body =
          objectMapper.writeValueAsString(
              Map.of(
                  "User",
                  Map.of(
                      "login_id", loginId == null ? "" : loginId,
                      "password", password == null ? "" : password)));

      HttpClient client =
          HttpClient.newBuilder()
              .sslContext(trustAllSsl())
              .connectTimeout(Duration.ofSeconds(5))
              .build();
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(7))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      boolean ok =
          resp.statusCode() == 200
              && (resp.headers().firstValue("bs-session-id").isPresent()
                  || (resp.body() != null && resp.body().contains("\"code\":\"0\"")));
      return ok
          ? BiostarResult.ok()
          : BiostarResult.fail("인증 실패 (HTTP " + resp.statusCode() + ", 세션 미발급)");
    } catch (java.net.ConnectException e) {
      return BiostarResult.fail("서버에 연결할 수 없습니다. IP/포트를 확인하세요.");
    } catch (java.net.http.HttpConnectTimeoutException e) {
      return BiostarResult.fail("연결 시간이 초과되었습니다.");
    } catch (Exception e) {
      log.warn("BiostarX 연결 테스트 오류: {}", e.toString());
      return BiostarResult.fail(e.getClass().getSimpleName());
    }
  }

  private static SSLContext trustAllSsl() throws Exception {
    SSLContext ctx = SSLContext.getInstance("TLS");
    TrustManager[] trustAll = {
      new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}

        public void checkServerTrusted(X509Certificate[] c, String a) {}

        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }
    };
    ctx.init(null, trustAll, new SecureRandom());
    return ctx;
  }
}
