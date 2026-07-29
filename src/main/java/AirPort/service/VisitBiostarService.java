package AirPort.service;

import AirPort.adapter.BiostarResult;
import AirPort.adapter.BiostarUserAdapter;
import AirPort.adapter.BiostarUserCard;
import AirPort.adapter.BiostarUserRequest;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
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
 * <p>저장(syncVisitors)은 실패해도 방문을 유지하고 경고만 돌려준다(장비에 없으면 출입 불가라 안전한 방향 + 재저장 upsert 자가치유).
 * 퇴실(disable)·삭제(delete)는 실패 시 호출자가 롤백한다(정합성 우선). 출입그룹 materialize 는 승인 단계 과제로 남긴다.
 */
@Service
public class VisitBiostarService {

  /** BiostarX 상시 유효기간 폴백 — 작업기간 미입력 시 사용(start/expiry 필수). */
  private static final String PERMANENT_START = "2001-01-01T00:00:00.00Z";

  private static final String PERMANENT_EXPIRY = "2037-12-31T23:59:00.00Z";

  private final TbSystemMapper systemMapper;
  private final TbCommonMapper commonMapper;
  private final TbPersonMapper personMapper;
  private final TbAcGroupMapper acGroupMapper;
  private final TbCardMapper cardMapper;
  private final BiostarUserAdapter biostarUserAdapter;

  public VisitBiostarService(
      TbSystemMapper systemMapper,
      TbCommonMapper commonMapper,
      TbPersonMapper personMapper,
      TbAcGroupMapper acGroupMapper,
      TbCardMapper cardMapper,
      BiostarUserAdapter biostarUserAdapter) {
    this.systemMapper = systemMapper;
    this.commonMapper = commonMapper;
    this.personMapper = personMapper;
    this.acGroupMapper = acGroupMapper;
    this.cardMapper = cardMapper;
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
    // 선택한 사용자출입그룹(tb_ac_group) → BiostarX 출입그룹(biostar_ac_id) 목록
    List<Integer> accessGroupIds =
        (acGroupIds == null || acGroupIds.isEmpty())
            ? null
            : acGroupMapper.selectBiostarAcIdsByGroupIds(acGroupIds);

    List<String> fails = new java.util.ArrayList<>();
    for (String pid : personIds) {
      TbPerson p = personMapper.selectById(pid);
      if (p == null) {
        continue;
      }
      // BiostarX 는 start/expiry 필수(없으면 사용자 생성 실패) — 작업기간이 비면 상시 유효기간으로 폴백
      String start = datetime(p.getAccessStartDt(), "00:00");
      String expiry = datetime(p.getAccessEndDt(), "23:59");
      if (start == null) {
        start = PERMANENT_START;
      }
      if (expiry == null) {
        expiry = PERMANENT_EXPIRY;
      }
      // 방문객에게 배정된 카드 → BiostarX 사용자 payload 의 cards[]
      List<BiostarUserCard> cards = CardService.toBiostarCardsOf(cardMapper.selectByPerson(pid));
      BiostarUserRequest req =
          new BiostarUserRequest(
              pid,
              decrypt(p.getPersonName()),
              null,
              null,
              parentGroupId,
              null,
              start,
              expiry,
              null,
              accessGroupIds,
              null,
              null,
              null,
              cards);
      try {
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
      } catch (AirPort.adapter.BiostarSessionException e) {
        fails.add(pid + "(" + e.getMessage() + ")"); // 통신·세션 오류도 실패로 집계(성공 오판 방지)
      }
    }
    return fails.isEmpty() ? null : String.join(", ", fails);
  }

  /**
   * 방문객 BiostarX 사용자 삭제(방문 삭제 시) — 부모 그룹에서 제거. 통신 오류도 실패로 집계한다.
   *
   * @return 실패 방문객이 있으면 경고 문자열(호출자가 롤백), 전부 성공/미대상이면 null
   */
  public String deleteVisitors(String visitType, List<String> personIds) {
    if (personIds == null || personIds.isEmpty()) {
      return null;
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다.";
    }
    Integer parentGroupId = parentGroupId(visitType);
    String ip = cfg.getBiostarIp();
    String id = cfg.getBiostarId();
    String pw = cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());

    List<String> fails = new java.util.ArrayList<>();
    for (String pid : personIds) {
      try {
        if (!biostarUserAdapter.userExists(ip, id, pw, pid)) {
          continue; // 장비에 없음(미동기) — 삭제 대상 아님
        }
        BiostarResult res = biostarUserAdapter.deleteUser(ip, id, pw, pid, parentGroupId);
        if (!res.success()) {
          fails.add(pid + "(" + res.message() + ")");
        }
      } catch (AirPort.adapter.BiostarSessionException e) {
        fails.add(pid + "(" + e.getMessage() + ")"); // 통신·세션 오류 — '없음'으로 오판하지 않는다
      }
    }
    return fails.isEmpty() ? null : String.join(", ", fails);
  }

  /**
   * 방문객 BiostarX 사용자 비활성화(퇴실) — {@code disabled=true} 로 바꾸고 부착 카드를 제거해 카드를 재대여 가능하게 한다. 사용자·출입그룹은
   * 남겨 이력을 유지하되 출입은 막는다. 통신 오류도 실패로 집계한다.
   *
   * @return 실패 방문객이 있으면 경고 문자열(호출자가 롤백), 전부 성공/미대상이면 null
   */
  public String disableVisitors(List<String> personIds) {
    if (personIds == null || personIds.isEmpty()) {
      return null;
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다.";
    }
    String ip = cfg.getBiostarIp();
    String id = cfg.getBiostarId();
    String pw = cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());

    List<String> fails = new java.util.ArrayList<>();
    for (String pid : personIds) {
      try {
        if (!biostarUserAdapter.userExists(ip, id, pw, pid)) {
          continue; // 장비에 없음(미동기) — 비활성화 대상 아님
        }
      } catch (AirPort.adapter.BiostarSessionException e) {
        fails.add(pid + "(" + e.getMessage() + ")"); // 통신·세션 오류 — '없음'으로 오판하지 않는다
        continue;
      }
      // before 에 현재 카드를 담아야 after=[] 로 장비에서 카드가 제거된다(대여 가능화).
      List<BiostarUserCard> current = CardService.toBiostarCardsOf(cardMapper.selectByPerson(pid));
      BiostarUserRequest before =
          new BiostarUserRequest(
              pid, null, null, null, null, null, null, null, null, null, null, null, null, current);
      BiostarUserRequest after =
          new BiostarUserRequest(
              pid,
              null,
              null,
              null,
              null,
              "true", // disabled
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              java.util.List.of()); // 카드 전체 제거
      BiostarResult res = biostarUserAdapter.updateUser(ip, id, pw, before, after);
      if (!res.success()) {
        fails.add(pid + "(" + res.message() + ")");
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
