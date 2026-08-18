package AirPort.adapter.biostar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 실시간 이벤트 HTTP 어댑터 — 이벤트 송출 시작과 인증 사진 조회. 소켓 자체는 {@link BiostarEventSocket} 담당.
 * (docs/integration.md)
 *
 * <p><b>읽기 전용이다.</b> 장비 상태를 바꾸는 호출은 여기 두지 않는다.
 */
@Component
public class BiostarEventAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarEventAdapter.class);

  private final ObjectMapper objectMapper;
  private final BiostarSession session;

  public BiostarEventAdapter(ObjectMapper objectMapper, BiostarSession session) {
    this.objectMapper = objectMapper;
    this.session = session;
  }

  /**
   * 실시간 이벤트 송출 시작 — {@code POST /api/events/start}.
   *
   * <p>소켓을 연 뒤 이 호출을 해야 MESSAGE 가 흐른다. 소켓과 <b>같은 세션</b>이어야 하므로 소켓을 연 직후에 부른다.
   *
   * @return 실패 사유, 성공이면 null
   */
  public String start(String ip, String loginId, String password) {
    try {
      HttpResponse<String> resp = session.post(baseUrl(ip), loginId, password, START_PATH, "{}");
      return BiostarAdapter.responseError(objectMapper, resp);
    } catch (BiostarSessionException e) {
      return e.getMessage();
    } catch (Exception e) {
      log.warn("BiostarX 이벤트 시작 오류: {}", e.toString());
      return "실시간 이벤트를 시작하지 못했습니다. (" + e.getClass().getSimpleName() + ")";
    }
  }

  static final String START_PATH = "/api/events/start";

  /**
   * 인증 사진 조회 — {@code GET /api/events/images/{imageId}} → {@code ImageLog.data}(base64).
   *
   * <p>브라우저는 BiostarX 를 직접 부를 수 없어(인증서·세션) 서버가 대신 읽어 화면으로 넘긴다. 사진이 없으면 null — 인증 자체는 성공했을 수 있으므로
   * 실패로 다루지 않는다(등록 사진만 보여 준다).
   */
  public String authImage(String ip, String loginId, String password, String imageId) {
    if (imageId == null || imageId.isBlank()) {
      return null;
    }
    try {
      HttpResponse<String> resp =
          session.get(
              baseUrl(ip),
              loginId,
              password,
              "/api/events/images/"
                  + java.net.URLEncoder.encode(imageId, java.nio.charset.StandardCharsets.UTF_8));
      if (BiostarAdapter.responseError(objectMapper, resp) != null) {
        return null;
      }
      JsonNode data = objectMapper.readTree(resp.body()).path("ImageLog").path("data");
      String base64 = data.asText(null);
      return (base64 == null || base64.isBlank()) ? null : base64;
    } catch (Exception e) {
      log.warn("BiostarX 인증 사진 조회 실패({}): {}", imageId, e.toString());
      return null;
    }
  }

  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
