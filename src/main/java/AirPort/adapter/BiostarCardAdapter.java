package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 카드 연동 어댑터 — 카드 등록 및 장치 리더 읽기. (docs/integration.md)
 *
 * <p>카드 종류는 <b>CSN 고정</b>이다(우리 시스템의 카드구분 tb_common(CDT)은 업무 분류용이라 BiostarX 로 넘기지 않는다).
 * 사용자에게 카드를 붙이는 것은 사용자 payload 의 {@code cards[]} 담당 — {@link #appendCard} 참고.
 */
@Component
public class BiostarCardAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarCardAdapter.class);

  /** CSN 카드 종류 — BiostarX card_type 고정값. */
  private static final String CSN_ID = "0";

  private static final String CSN_NAME = "CSN";
  private static final String CSN_TYPE = "1";

  private final ObjectMapper objectMapper;
  private final BiostarSession session;

  public BiostarCardAdapter(ObjectMapper objectMapper, BiostarSession session) {
    this.objectMapper = objectMapper;
    this.session = session;
  }

  /**
   * 카드 등록 — {@code POST /api/cards}. 응답 {@code CardCollection.rows[0]} 의 {@code id}/{@code card_id}
   * 를 돌려준다(각각 tb_card.biostar_card_id / biostar_card_value).
   */
  public BiostarCard createCard(String ip, String loginId, String password, String cardNo) {
    if (ip == null || ip.isBlank()) {
      return BiostarCard.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    if (cardNo == null || cardNo.isBlank()) {
      return BiostarCard.fail("카드번호가 없습니다.");
    }
    try {
      ObjectNode row = objectMapper.createObjectNode();
      ObjectNode type = row.putObject("card_type");
      type.put("id", CSN_ID).put("name", CSN_NAME).put("type", CSN_TYPE).put("mode", "C");
      row.put("display_card_id", cardNo).put("card_id", cardNo);
      ObjectNode root = objectMapper.createObjectNode();
      root.putObject("CardCollection").putArray("rows").add(row);

      HttpResponse<String> resp =
          session.post(
              baseUrl(ip), loginId, password, "/api/cards", objectMapper.writeValueAsString(root));
      String err = BiostarAdapter.responseError(objectMapper, resp);
      if (err != null) {
        return BiostarCard.fail(err);
      }
      JsonNode rows = objectMapper.readTree(resp.body()).path("CardCollection").path("rows");
      if (!rows.isArray() || rows.isEmpty()) {
        return BiostarCard.fail("카드 등록 응답에 카드 정보가 없습니다.");
      }
      JsonNode created = rows.get(0);
      return BiostarCard.ok(
          created.path("id").asText(null), created.path("card_id").asText(cardNo));
    } catch (Exception e) {
      return BiostarCard.fail(friendlyError(e, "카드 등록"));
    }
  }

  /**
   * 카드 차단(블랙리스트 등록) — {@code POST /api/cards/blacklist}, {@code
   * {"Blacklist":{"card_id":{"id":"<biostarCardId>"}}}}. biostarCardId 는 카드 등록 응답의 id(tb_card.biostar_card_id).
   */
  public BiostarResult blacklistCard(
      String ip, String loginId, String password, String biostarCardId) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    if (biostarCardId == null || biostarCardId.isBlank()) {
      return BiostarResult.fail("BiostarX 카드ID가 없습니다.");
    }
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.putObject("Blacklist").putObject("card_id").put("id", biostarCardId);
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip),
              loginId,
              password,
              "/api/cards/blacklist",
              objectMapper.writeValueAsString(root));
      String err = BiostarAdapter.responseError(objectMapper, resp);
      return err == null ? BiostarResult.ok() : BiostarResult.fail(err);
    } catch (Exception e) {
      return BiostarResult.fail(friendlyError(e, "카드 차단"));
    }
  }

  /** 카드 차단 해제 — {@code DELETE /api/cards/blacklist?id={biostarCardId}}. */
  public BiostarResult removeBlacklist(
      String ip, String loginId, String password, String biostarCardId) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    if (biostarCardId == null || biostarCardId.isBlank()) {
      return BiostarResult.fail("BiostarX 카드ID가 없습니다.");
    }
    try {
      HttpResponse<String> resp =
          session.delete(baseUrl(ip), loginId, password, "/api/cards/blacklist?id=" + biostarCardId);
      String err = BiostarAdapter.responseError(objectMapper, resp);
      return err == null ? BiostarResult.ok() : BiostarResult.fail(err);
    } catch (Exception e) {
      return BiostarResult.fail(friendlyError(e, "카드 차단 해제"));
    }
  }

  /**
   * 장치 리더로 카드 읽기 — {@code POST /api/devices/{devId}/scan_card}. 응답 {@code Card.card_id} 만 사용한다(등록
   * 전이라 {@code id} 는 "0").
   */
  public BiostarCard scanCard(String ip, String loginId, String password, String devId) {
    if (ip == null || ip.isBlank()) {
      return BiostarCard.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    if (devId == null || devId.isBlank()) {
      return BiostarCard.fail("로그인 계정에 장치ID가 없습니다. 사용자관리에서 장치를 지정하세요.");
    }
    try {
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip),
              loginId,
              password,
              "/api/devices/" + devId + "/scan_card",
              "{\"noblockui\":true}");
      String err = BiostarAdapter.responseError(objectMapper, resp);
      if (err != null) {
        return BiostarCard.fail(err);
      }
      String cardNo = objectMapper.readTree(resp.body()).path("Card").path("card_id").asText(null);
      if (cardNo == null || cardNo.isBlank() || "0".equals(cardNo)) {
        return BiostarCard.fail("읽은 카드가 없습니다. 장치에 카드를 다시 태그하세요.");
      }
      return BiostarCard.ok(null, cardNo);
    } catch (Exception e) {
      return BiostarCard.fail(friendlyError(e, "카드 읽기"));
    }
  }

  /** 사용자 payload 의 {@code cards[]} 한 건을 채운다 — 카드 부여(is_assigned=true). */
  public static void appendCard(ObjectNode node, BiostarUserCard card) {
    node.put("id", card.id())
        .put("card_id", card.cardNo())
        .put("display_card_id", card.cardNo())
        .put("is_assigned", "true")
        .put("is_lock_override", "false");
    node.putObject("card_type").put("id", CSN_ID).put("name", CSN_NAME).put("type", CSN_TYPE);
    node.put("mobile_card", "false")
        .put("issue_count", "2")
        .put("card_slot", "1")
        .put("card_mask", "0");
    node.putObject("wiegand_format_id").put("id", "0");
  }

  private String friendlyError(Exception e, String what) {
    if (e instanceof BiostarSessionException) {
      return e.getMessage();
    }
    if (e instanceof java.net.ConnectException) {
      return "BiostarX 서버에 연결할 수 없습니다. IP/포트를 확인하세요.";
    }
    log.warn("BiostarX {} 오류: {}", what, e.toString());
    return e.getClass().getSimpleName();
  }

  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
