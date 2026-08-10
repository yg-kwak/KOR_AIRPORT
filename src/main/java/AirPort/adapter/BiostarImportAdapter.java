package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 사용자 <b>읽기 전용</b> 어댑터 — 장비의 정규 사용자를 우리 DB 로 옮길 때 쓴다. (docs/integration.md)
 *
 * <p>여기에는 쓰기 메서드를 두지 않는다. 반대 방향으로 쓰면 현장에 이미 올라간 얼굴·카드·출입그룹을 덮어쓴다 — 가져오기는 단방향이어야 한다.
 */
@Component
public class BiostarImportAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarImportAdapter.class);

  private final ObjectMapper objectMapper;
  private final BiostarSession session;

  public BiostarImportAdapter(ObjectMapper objectMapper, BiostarSession session) {
    this.objectMapper = objectMapper;
    this.session = session;
  }

  /** BiostarX 는 내부망 self-signed 라 스킴이 없으면 https 를 붙인다(다른 어댑터와 동일). */
  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }

  /**
   * 사용자 목록 — {@code POST /api/v2/users/search}. 목록 응답에는 사진·카드 상세가 없어 요약만 담는다.
   *
   * @return 사용자ID·성명·사용자그룹만 채운 목록(사진/카드/출입그룹은 {@link #fetchUser} 로)
   */
  public List<BiostarUserDetail> searchUsers(String ip, String loginId, String password) {
    try {
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip),
              loginId,
              password,
              "/api/v2/users/search",
              "{\"limit\":1000,\"offset\":0}");
      if (BiostarAdapter.responseError(objectMapper, resp) != null) {
        return List.of();
      }
      JsonNode rows = objectMapper.readTree(resp.body()).path("UserCollection").path("rows");
      List<BiostarUserDetail> out = new java.util.ArrayList<>();
      for (JsonNode r : rows) {
        out.add(
            new BiostarUserDetail(
                r.path("user_id").asText(null),
                r.path("name").asText(null),
                null,
                null,
                r.path("user_group_id").path("id").isMissingNode()
                    ? null
                    : r.path("user_group_id").path("id").asInt(),
                null,
                null,
                null,
                List.of(),
                List.of()));
      }
      return out;
    } catch (Exception e) {
      log.warn("BiostarX 사용자 목록 조회 실패: {}", e.toString());
      return List.of();
    }
  }

  /**
   * 사용자 1명 상세 — {@code GET /api/users/{id}}. 사진(BASE64)·카드번호·출입그룹까지 담는다.
   *
   * @return 없거나 오류면 null
   */
  public BiostarUserDetail fetchUser(String ip, String loginId, String password, String userId) {
    try {
      String path =
          "/api/users/"
              + java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8);
      HttpResponse<String> resp = session.get(baseUrl(ip), loginId, password, path);
      if (BiostarAdapter.responseError(objectMapper, resp) != null) {
        return null;
      }
      JsonNode u = objectMapper.readTree(resp.body()).path("User");
      List<String> cards = new java.util.ArrayList<>();
      for (JsonNode c : u.path("cards")) {
        String no = c.path("display_card_id").asText(null);
        if (no != null && !no.isBlank()) {
          cards.add(no);
        }
      }
      List<Integer> acs = new java.util.ArrayList<>();
      for (JsonNode g : u.path("access_groups")) {
        acs.add(g.path("id").asInt());
      }
      return new BiostarUserDetail(
          u.path("user_id").asText(null),
          u.path("name").asText(null),
          text(u, "phone"),
          text(u, "user_title"),
          u.path("user_group_id").path("id").isMissingNode()
              ? null
              : u.path("user_group_id").path("id").asInt(),
          text(u, "start_datetime"),
          text(u, "expiry_datetime"),
          text(u, "photo"),
          cards,
          acs);
    } catch (Exception e) {
      log.warn("BiostarX 사용자 상세 조회 실패({}): {}", userId, e.toString());
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    String v = node.path(field).asText(null);
    return (v == null || v.isBlank()) ? null : v;
  }
}
