package AirPort.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
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

  /** IP(스킴 없으면 https 부여) → 베이스 URL. */
  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
