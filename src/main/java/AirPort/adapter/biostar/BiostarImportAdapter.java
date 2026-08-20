package AirPort.adapter.biostar;

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
   * 사용자그룹 <b>전체</b> 목록 — {@code POST /api/v2/user_groups/search}.
   *
   * <p>{@link BiostarAdapter#searchUserGroups} 는 화면 트리용이라 펼쳐진 일부만 준다(총 10건). 가져오기는 부모-자식을 따라 하위 그룹을
   * 모두 찾아야 하므로 평면 전체 목록이 필요하다.
   */
  public List<BiostarUserGroup> searchUserGroups(String ip, String loginId, String password) {
    try {
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip),
              loginId,
              password,
              "/api/v2/user_groups/search",
              "{\"limit\":1000,\"offset\":0}");
      if (BiostarAdapter.responseError(objectMapper, resp) != null) {
        return List.of();
      }
      JsonNode rows = objectMapper.readTree(resp.body()).path("UserGroupCollection").path("rows");
      List<BiostarUserGroup> out = new java.util.ArrayList<>();
      for (JsonNode n : rows) {
        JsonNode parent = n.path("parent_id").path("id");
        out.add(
            new BiostarUserGroup(
                n.path("id").asLong(),
                n.path("name").asText(null),
                parent.isMissingNode() ? null : parent.asLong()));
      }
      return out;
    } catch (Exception e) {
      log.warn("BiostarX 사용자그룹 목록 조회 실패: {}", e.toString());
      return List.of();
    }
  }

  /** 한 번에 받아 오는 쪽수 — 장비가 한 응답에 담아 주는 최대치를 넘기지 않는 선에서 왕복을 줄인다. */
  private static final int PAGE_SIZE = 500;

  /** 폭주 방지 — 장비가 total 을 이상하게 주더라도 이 횟수를 넘겨 돌지 않는다. */
  private static final int MAX_PAGES = 200;

  /**
   * 사용자 목록 — {@code POST /api/v2/users/search} 를 <b>끝까지 넘겨 가며</b> 전부 받는다.
   *
   * <p>예전에는 {@code limit:1000, offset:0} 으로 <b>한 쪽만</b> 받았다. 현장 장비에 4000명이 넘게 등록돼 있으면 뒤쪽 3000명은 애초에
   * 조회되지 않아 화면에 나타나지 않는다(2026-08-19 현장 보고).
   *
   * <p>응답의 {@code total} 만큼 {@code offset} 을 옮겨 가며 받는다. 목록 응답에는 사진·카드번호 같은 상세가 없어 요약만 담는다 — 카드·얼굴은
   * <b>보유 개수</b>가 함께 오므로 그것만 채운다(상세는 {@link #fetchUser}).
   *
   * @return 사용자ID·성명·사용자그룹·카드/얼굴 보유수를 채운 목록
   */
  public List<BiostarUserDetail> searchUsers(String ip, String loginId, String password) {
    List<BiostarUserDetail> out = new java.util.ArrayList<>();
    try {
      int offset = 0;
      long total = Long.MAX_VALUE;
      for (int page = 0; page < MAX_PAGES && offset < total; page++) {
        HttpResponse<String> resp =
            session.post(
                baseUrl(ip),
                loginId,
                password,
                "/api/v2/users/search",
                "{\"limit\":" + PAGE_SIZE + ",\"offset\":" + offset + "}");
        if (BiostarAdapter.responseError(objectMapper, resp) != null) {
          break;
        }
        JsonNode col = objectMapper.readTree(resp.body()).path("UserCollection");
        total = col.path("total").asLong(0);
        JsonNode rows = col.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
          break; // 더 줄 것이 없다 — total 을 믿지 못해도 여기서 멈춘다
        }
        for (JsonNode r : rows) {
          out.add(toSummary(r));
        }
        offset += rows.size();
      }
      log.debug("BiostarX 사용자 목록 — {}명 조회", out.size());
      return out;
    } catch (Exception e) {
      log.warn("BiostarX 사용자 목록 조회 실패: {}", e.toString());
      // 여기까지 받은 것은 살린다 — 뒤쪽에서 끊겨도 앞쪽은 쓸 수 있다
      return out;
    }
  }

  /** 목록 한 행 → 요약. 카드·얼굴은 <b>보유 개수</b>가 목록에 함께 오므로 추가 호출 없이 알 수 있다. */
  private static BiostarUserDetail toSummary(JsonNode r) {
    JsonNode group = r.path("user_group_id").path("id");
    return new BiostarUserDetail(
        r.path("user_id").asText(null),
        r.path("name").asText(null),
        null,
        null,
        group.isMissingNode() ? null : group.asInt(),
        null,
        null,
        null,
        List.of(),
        List.of(),
        r.path("card_count").asInt(0),
        // 얼굴은 두 갈래다 — visual_face(사진 기반)와 face(적외선 템플릿). 둘 중 하나라도 있으면 보유로 본다.
        r.path("visual_face_count").asInt(0) + r.path("face_count").asInt(0));
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
