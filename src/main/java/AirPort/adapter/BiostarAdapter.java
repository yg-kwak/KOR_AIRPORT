package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Suprema BiostarX 연동 어댑터 — 외부 연동은 이 계층으로만 격리한다(AGENTS §4). (docs/integration.md)
 *
 * <p>세션(bs-session-id)은 {@link BiostarSession} 이 캐시·갱신한다. API 호출마다 로그인하지 않고, 만료 시 자동 재로그인한다.
 */
@Component
public class BiostarAdapter {

  private static final Logger log = LoggerFactory.getLogger(BiostarAdapter.class);

  private final ObjectMapper objectMapper;
  private final BiostarSession session;

  public BiostarAdapter(ObjectMapper objectMapper, BiostarSession session) {
    this.objectMapper = objectMapper;
    this.session = session;
  }

  /** BiostarX 로그인(POST /api/login) 시도 — 성공 시 세션 캐시. 설정관리의 접속정보 검증용. */
  public BiostarResult testLogin(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarResult.fail("BiostarX IP가 비어 있습니다.");
    }
    try {
      session.login(baseUrl(ip), loginId, password);
      return BiostarResult.ok();
    } catch (BiostarSessionException e) {
      return BiostarResult.fail(e.getMessage());
    } catch (java.net.ConnectException e) {
      return BiostarResult.fail("서버에 연결할 수 없습니다. IP/포트를 확인하세요.");
    } catch (java.net.http.HttpConnectTimeoutException e) {
      return BiostarResult.fail("연결 시간이 초과되었습니다.");
    } catch (Exception e) {
      log.warn("BiostarX 연결 테스트 오류: {}", e.toString());
      return BiostarResult.fail(e.getClass().getSimpleName());
    }
  }

  /**
   * BiostarX 출입그룹 목록 조회 — {@code POST /api/v2/access_groups/search}. 세션은 {@link BiostarSession} 이 관리(없으면
   * 로그인, 만료면 재로그인). 응답 {@code AccessGroupCollection.rows[].{id,name}} 를 파싱한다.
   */
  public BiostarGroups searchAccessGroups(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarGroups.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      HttpResponse<String> resp =
          session.post(baseUrl(ip), loginId, password, "/api/v2/access_groups/search", "{}");
      if (resp.statusCode() != 200) {
        return BiostarGroups.fail("출입그룹 조회 실패 (HTTP " + resp.statusCode() + ")");
      }

      JsonNode rows = objectMapper.readTree(resp.body()).path("AccessGroupCollection").path("rows");
      List<BiostarGroup> groups = new ArrayList<>();
      if (rows.isArray()) {
        for (JsonNode n : rows) {
          Integer id = n.path("id").isMissingNode() ? null : n.path("id").asInt();
          String name = n.path("name").asText(null);
          if (id != null) {
            groups.add(new BiostarGroup(id, name));
          }
        }
      }
      return BiostarGroups.ok(groups);
    } catch (BiostarSessionException e) {
      return BiostarGroups.fail(e.getMessage());
    } catch (java.net.ConnectException e) {
      return BiostarGroups.fail("BiostarX 서버에 연결할 수 없습니다. IP/포트를 확인하세요.");
    } catch (java.net.http.HttpConnectTimeoutException e) {
      return BiostarGroups.fail("연결 시간이 초과되었습니다.");
    } catch (Exception e) {
      log.warn("BiostarX 출입그룹 조회 오류: {}", e.toString());
      return BiostarGroups.fail(e.getClass().getSimpleName());
    }
  }

  /**
   * BiostarX 장치 목록 조회 — {@code POST /api/v2/devices/search}(feature_types=[card]). 세션은 {@link
   * BiostarSession} 이 관리. 응답 {@code DeviceCollection.rows[].{id,name}} 를 파싱한다.
   */
  public BiostarDevices searchDevices(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarDevices.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip), loginId, password, "/api/v2/devices/search", "{\"feature_types\":[\"card\"]}");
      if (resp.statusCode() != 200) {
        return BiostarDevices.fail("장치 조회 실패 (HTTP " + resp.statusCode() + ")");
      }

      JsonNode rows = objectMapper.readTree(resp.body()).path("DeviceCollection").path("rows");
      List<BiostarDevice> devices = new ArrayList<>();
      if (rows.isArray()) {
        for (JsonNode n : rows) {
          if (n.path("id").isMissingNode()) {
            continue;
          }
          devices.add(new BiostarDevice(n.path("id").asLong(), n.path("name").asText(null)));
        }
      }
      return BiostarDevices.ok(devices);
    } catch (BiostarSessionException e) {
      return BiostarDevices.fail(e.getMessage());
    } catch (java.net.ConnectException e) {
      return BiostarDevices.fail("BiostarX 서버에 연결할 수 없습니다. IP/포트를 확인하세요.");
    } catch (java.net.http.HttpConnectTimeoutException e) {
      return BiostarDevices.fail("연결 시간이 초과되었습니다.");
    } catch (Exception e) {
      log.warn("BiostarX 장치 조회 오류: {}", e.toString());
      return BiostarDevices.fail(e.getClass().getSimpleName());
    }
  }

  // ── 사용자 그룹(user group) = 본 시스템의 '기관' (integration.md) ────────────

  private static final String USER_GROUP_SEARCH_BODY =
      "{\"min_index\":0,\"search_word\":\"\",\"toggle_hide_list\":[],\"group_checked_list\":[],"
          + "\"request_selected\":true,\"disable_option\":true}";

  /**
   * BiostarX 사용자 그룹 목록 조회 — {@code POST /api/v2/user_groups/search}. 응답 {@code
   * UserGroupCollection.rows[].{id,name,parent_id.id}} 를 파싱한다(부모 필터는 호출측에서).
   */
  public BiostarUserGroups searchUserGroups(String ip, String loginId, String password) {
    if (ip == null || ip.isBlank()) {
      return BiostarUserGroups.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      HttpResponse<String> resp =
          session.post(
              baseUrl(ip), loginId, password, "/api/v2/user_groups/search", USER_GROUP_SEARCH_BODY);
      String err = responseError(resp);
      if (err != null) {
        return BiostarUserGroups.fail("사용자그룹 조회 실패 (" + err + ")");
      }
      JsonNode rows = objectMapper.readTree(resp.body()).path("UserGroupCollection").path("rows");
      List<BiostarUserGroup> groups = new ArrayList<>();
      if (rows.isArray()) {
        for (JsonNode n : rows) {
          if (n.path("id").isMissingNode()) {
            continue;
          }
          JsonNode parent = n.path("parent_id").path("id");
          groups.add(
              new BiostarUserGroup(
                  n.path("id").asLong(),
                  n.path("name").asText(null),
                  parent.isMissingNode() ? null : parent.asLong()));
        }
      }
      return BiostarUserGroups.ok(groups);
    } catch (Exception e) {
      return BiostarUserGroups.fail(friendlyError(e, "사용자그룹 조회"));
    }
  }

  /**
   * BiostarX 사용자 그룹 생성 — {@code POST /api/user_groups}(parent_id 아래 depth 2). 실패 시 BiostarX 메시지를 그대로
   * 돌려준다(예: 이름 중복 code 65646). 생성된 id 를 응답에서 못 찾으면 검색으로 보완한다.
   */
  public BiostarGroupResult createUserGroup(
      String ip, String loginId, String password, long parentGroupId, String name) {
    if (ip == null || ip.isBlank()) {
      return BiostarGroupResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      String body =
          objectMapper.writeValueAsString(
              Map.of(
                  "UserGroup",
                  Map.of(
                      "parent_id", Map.of("id", String.valueOf(parentGroupId)),
                      "depth", 2,
                      "inherited", true,
                      "name", name,
                      "text", name)));
      HttpResponse<String> resp =
          session.post(baseUrl(ip), loginId, password, "/api/user_groups", body);
      String err = responseError(resp);
      if (err != null) {
        return BiostarGroupResult.fail(err);
      }
      Long id = extractGroupId(resp.body());
      if (id == null) {
        id = findGroupIdByName(ip, loginId, password, parentGroupId, name); // 응답에 id 없으면 보완 조회
      }
      return BiostarGroupResult.ok(id);
    } catch (Exception e) {
      return BiostarGroupResult.fail(friendlyError(e, "사용자그룹 생성"));
    }
  }

  /** BiostarX 사용자 그룹 이름 수정 — {@code PUT /api/user_groups/{groupId}}(본문 id 는 해당 그룹 자기 id). */
  public BiostarGroupResult updateUserGroupName(
      String ip, String loginId, String password, long groupId, String name) {
    if (ip == null || ip.isBlank()) {
      return BiostarGroupResult.fail("BiostarX IP가 설정되어 있지 않습니다. 설정관리에서 등록하세요.");
    }
    try {
      String body =
          objectMapper.writeValueAsString(Map.of("UserGroup", Map.of("id", groupId, "name", name)));
      HttpResponse<String> resp =
          session.put(baseUrl(ip), loginId, password, "/api/user_groups/" + groupId, body);
      String err = responseError(resp);
      return err != null ? BiostarGroupResult.fail(err) : BiostarGroupResult.ok(groupId);
    } catch (Exception e) {
      return BiostarGroupResult.fail(friendlyError(e, "사용자그룹 수정"));
    }
  }

  /** 생성 응답에서 그룹 id 추출(UserGroup.id 또는 id). 없으면 null. */
  private Long extractGroupId(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode idNode = root.path("UserGroup").path("id");
      if (idNode.isMissingNode() || !idNode.isValueNode()) {
        idNode = root.path("id");
      }
      if (idNode.isValueNode()) {
        long v = idNode.asLong(0);
        return v > 0 ? v : null;
      }
    } catch (Exception e) {
      log.debug("생성 응답에서 그룹 id 추출 실패");
    }
    return null;
  }

  /** 이름+부모로 그룹 id 보완 조회(생성 응답에 id 가 없을 때). */
  private Long findGroupIdByName(
      String ip, String loginId, String password, long parentGroupId, String name) {
    BiostarUserGroups found = searchUserGroups(ip, loginId, password);
    if (!found.success()) {
      return null;
    }
    return found.groups().stream()
        .filter(g -> Long.valueOf(parentGroupId).equals(g.parentId()) && name.equals(g.name()))
        .map(BiostarUserGroup::id)
        .findFirst()
        .orElse(null);
  }

  /** BiostarX 표준 응답 판정 — {@code Response.code=="0"} 이면 성공(null 반환), 아니면 오류 메시지. */
  private String responseError(HttpResponse<String> resp) {
    if (resp.statusCode() != 200) {
      return "HTTP " + resp.statusCode();
    }
    try {
      JsonNode r = objectMapper.readTree(resp.body()).path("Response");
      String code = r.path("code").asText("");
      if (code.isEmpty() || "0".equals(code)) {
        return null;
      }
      String msg = r.path("message").asText("");
      return msg.isEmpty() ? ("code " + code) : (msg + " (code " + code + ")");
    } catch (Exception e) {
      return null; // HTTP 200 인데 파싱 불가 — 성공으로 본다
    }
  }

  /** 통신 예외 → 사용자 메시지. */
  private String friendlyError(Exception e, String what) {
    if (e instanceof BiostarSessionException) {
      return e.getMessage();
    }
    if (e instanceof java.net.ConnectException) {
      return "BiostarX 서버에 연결할 수 없습니다. IP/포트를 확인하세요.";
    }
    if (e instanceof java.net.http.HttpConnectTimeoutException) {
      return "연결 시간이 초과되었습니다.";
    }
    log.warn("BiostarX {} 오류: {}", what, e.toString());
    return e.getClass().getSimpleName();
  }

  /** IP(스킴 없으면 https 부여) → 베이스 URL. */
  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
