package AirPort.adapter.biostar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 출입문 — 목록 조회와 원격 제어(개방·잠금·해제). 그래픽맵(모니터링 903)이 쓴다. (docs/integration.md)
 *
 * <ul>
 *   <li>목록: {@code POST /api/v2/doors/search} {@code {"limit":500,"door_group_id":1}}
 *   <li>제어: {@code POST /api/doors/unlock|lock|release} {@code
 *       {"DoorCollection":{"rows":[{"id":"5"}]}}}
 * </ul>
 */
@Component
public class BiostarDoorAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarDoorAdapter.class);

  /** 전체 출입문 그룹("All Door Groups") — 하위 그룹의 문까지 함께 온다. */
  static final int ALL_DOOR_GROUP = 1;

  /** 한 번에 읽는 문 수 — 현장 규모(수십~수백)를 넉넉히 덮는다. */
  static final int SEARCH_LIMIT = 500;

  /** 원격 제어 — 화면 버튼과 BiostarX 경로. */
  public enum Action {
    UNLOCK("unlock", "개방"),
    LOCK("lock", "잠금"),
    RELEASE("release", "해제");

    final String path;
    public final String label;

    Action(String path, String label) {
      this.path = path;
      this.label = label;
    }
  }

  private final ObjectMapper objectMapper;
  private final BiostarSession session;

  public BiostarDoorAdapter(ObjectMapper objectMapper, BiostarSession session) {
    this.objectMapper = objectMapper;
    this.session = session;
  }

  /** 출입문 목록 — 실패면 예외(BiostarSessionException, 사유 문구). */
  public List<BiostarDoor> searchDoors(String ip, String loginId, String password) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("limit", SEARCH_LIMIT).put("door_group_id", ALL_DOOR_GROUP);
      HttpResponse<String> resp =
          session.post(baseUrl(ip), loginId, password, "/api/v2/doors/search", body.toString());
      String err = BiostarAdapter.responseError(objectMapper, resp);
      if (err != null) {
        throw new BiostarSessionException("출입문 조회 실패: " + err);
      }
      return parseDoors(objectMapper.readTree(resp.body()));
    } catch (BiostarSessionException e) {
      throw e;
    } catch (Exception e) {
      log.warn("BiostarX 출입문 조회 오류: {}", e.toString());
      throw new BiostarSessionException("BiostarX 출입문 조회 실패: " + e.getClass().getSimpleName());
    }
  }

  static List<BiostarDoor> parseDoors(JsonNode root) {
    List<BiostarDoor> out = new ArrayList<>();
    JsonNode rows = root.path("DoorCollection").path("rows");
    if (rows.isArray()) {
      for (JsonNode n : rows) {
        if (!n.hasNonNull("id")) {
          continue;
        }
        JsonNode entry = n.path("entry_device_id");
        out.add(
            new BiostarDoor(
                n.path("id").asLong(),
                n.path("name").asText(null),
                entry.hasNonNull("id") ? entry.path("id").asText() : null,
                entry.path("name").asText(null)));
      }
    }
    return out;
  }

  /**
   * 원격 제어 — 개방(unlock)·잠금(lock)·해제(release). 문 단위 결과({@code DoorResponse.rows[].code})까지 본다.
   *
   * <p>전체 응답이 성공이어도 그 문의 코드가 0 이 아니면 실패다 — 장비가 꺼져 있으면 요청은 받았지만 문은 움직이지 않는다.
   */
  public BiostarResult control(
      String ip, String loginId, String password, Action action, long doorId) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.putObject("DoorCollection")
          .putArray("rows")
          .addObject()
          .put("id", String.valueOf(doorId));
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip), loginId, password, "/api/doors/" + action.path, body.toString());
      String err = BiostarAdapter.responseError(objectMapper, resp);
      if (err != null) {
        return BiostarResult.fail(err);
      }
      return doorResult(objectMapper.readTree(resp.body()), doorId);
    } catch (BiostarSessionException e) {
      return BiostarResult.fail(e.getMessage());
    } catch (Exception e) {
      log.warn("BiostarX 출입문 {} 오류(문 {}): {}", action.path, doorId, e.toString());
      return BiostarResult.fail(
          "BiostarX 출입문 " + action.label + " 실패: " + e.getClass().getSimpleName());
    }
  }

  static BiostarResult doorResult(JsonNode root, long doorId) {
    for (JsonNode r : root.path("DoorResponse").path("rows")) {
      if (String.valueOf(doorId).equals(r.path("id").asText())) {
        String code = r.path("code").asText("0");
        return "0".equals(code)
            ? BiostarResult.ok()
            : BiostarResult.fail("장비가 처리하지 못했습니다 (code " + code + ")");
      }
    }
    return BiostarResult.ok(); // 문 단위 결과가 없으면 전체 응답(성공)을 따른다
  }

  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
