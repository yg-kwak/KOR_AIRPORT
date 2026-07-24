package AirPort.service;

import AirPort.adapter.BiostarResult;
import AirPort.adapter.BiostarUserAdapter;
import AirPort.adapter.BiostarUserRequest;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbCommon;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 방문객 ↔ BiostarX 동기화 — 저장 시 방문객(tb_person)을 <b>visit_type(PT)→PTD.code_tag 부모 그룹 아래</b>로 편입한다.
 * 정규(기관 그룹)와 달리 중간 기관 그룹을 만들지 않는다. (docs/integration.md)
 *
 * <p>연동 실패해도 방문 저장은 유지하고 경고만 돌려준다(정규 인원과 동일 정책). 출입그룹 access_groups 의 최상위→하위
 * materialize 는 승인 단계 과제로 남긴다(여기서는 부모 그룹 편입 + 작업기간 전파까지).
 */
@Service
public class VisitBiostarService {

  private final TbSystemMapper systemMapper;
  private final TbCommonMapper commonMapper;
  private final TbPersonMapper personMapper;
  private final BiostarUserAdapter biostarUserAdapter;

  public VisitBiostarService(
      TbSystemMapper systemMapper,
      TbCommonMapper commonMapper,
      TbPersonMapper personMapper,
      BiostarUserAdapter biostarUserAdapter) {
    this.systemMapper = systemMapper;
    this.commonMapper = commonMapper;
    this.personMapper = personMapper;
    this.biostarUserAdapter = biostarUserAdapter;
  }

  /**
   * 방문객들을 BiostarX 사용자로 편입한다(있으면 수정, 없으면 등록).
   *
   * @param acGroupIds 사용자출입그룹 — 현재 v1 은 미전송(materialize 후속). 시그니처는 유지해 확장에 대비.
   * @return 실패 방문객이 있으면 경고 문자열, 전부 성공/미대상이면 null
   */
  public String syncVisitors(String visitType, List<String> personIds, List<Integer> acGroupIds) {
    if (personIds == null || personIds.isEmpty()) {
      return null;
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다.";
    }
    Integer parentGroupId = parentGroupId(visitType);
    if (parentGroupId == null) {
      return "방문유형의 BiostarX 부모 그룹(PT→PTD code_tag)이 없습니다.";
    }
    String ip = cfg.getBiostarIp();
    String id = cfg.getBiostarId();
    String pw = cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());

    List<String> fails = new java.util.ArrayList<>();
    for (String pid : personIds) {
      TbPerson p = personMapper.selectById(pid);
      if (p == null) {
        continue;
      }
      BiostarUserRequest req =
          new BiostarUserRequest(
              pid,
              decrypt(p.getPersonName()),
              null,
              null,
              parentGroupId,
              null,
              datetime(p.getAccessStartDt(), "00:00"),
              datetime(p.getAccessEndDt(), "23:59"),
              null,
              null,
              null,
              null,
              null,
              null);
      boolean exists = biostarUserAdapter.userExists(ip, id, pw, pid);
      BiostarResult res =
          exists
              ? biostarUserAdapter.updateUser(ip, id, pw, empty(pid), req)
              : biostarUserAdapter.createUser(ip, id, pw, req);
      if (!res.success()) {
        fails.add(pid + "(" + res.message() + ")");
      } else {
        personMapper.updateBiostarUserId(pid, pid);
      }
    }
    return fails.isEmpty() ? null : String.join(", ", fails);
  }

  /** visit_type(PT).code_tag = PTDxx → PTDxx.code_tag = BiostarX 부모 그룹 ID. */
  private Integer parentGroupId(String visitType) {
    TbCommon pt = code("PT", visitType);
    if (pt == null || pt.getCodeTag() == null) {
      return null;
    }
    TbCommon ptd = code("PTD", pt.getCodeTag());
    if (ptd == null || ptd.getCodeTag() == null || ptd.getCodeTag().isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(ptd.getCodeTag().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private TbCommon code(String cmmId, String codeId) {
    return (codeId == null || codeId.isBlank()) ? null : commonMapper.selectOne(cmmId, codeId);
  }

  private static BiostarUserRequest empty(String userId) {
    return new BiostarUserRequest(
        userId, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static String datetime(String value, String defaultTime) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim();
    if (!v.contains("T")) {
      v = v + "T" + defaultTime;
    }
    if (v.length() == 16) {
      v = v + ":00";
    }
    return v + ".00Z";
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }
}
