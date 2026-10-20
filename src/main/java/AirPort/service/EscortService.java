package AirPort.service;

import AirPort.adapter.biostar.BiostarAdapter;
import AirPort.adapter.biostar.BiostarDoor;
import AirPort.adapter.biostar.BiostarDoorAdapter;
import AirPort.adapter.biostar.BiostarDoorAuth;
import AirPort.adapter.biostar.BiostarDoorUserPage;
import AirPort.adapter.biostar.BiostarResult;
import AirPort.adapter.biostar.BiostarSessionException;
import AirPort.adapter.biostar.BiostarUserGroup;
import AirPort.adapter.biostar.BiostarUserGroups;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.EscortForm;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 에스코트 인원 관리(보안관리 504) — BiostarX 출입문의 에스코트 사용자({@code auth_door_control.users})를 조회·추가·제외한다.
 *
 * <p><b>이 시스템에는 저장하지 않는다.</b> 원천은 BiostarX 하나이고, 화면은 그때그때 장비에서 읽어 보여 준다 — 사본을 두면 BiostarX 에서 직접 고친
 * 내용과 어긋난다. 그래서 테이블·mapper 가 없다(감사만 tb_system_log 에 남긴다).
 */
@Service
public class EscortService {

  /** 출입문 목록 — 현장 전체를 한 번에 읽는다. */
  static final int DOOR_LIMIT = 5000;

  /** 추가할 수 있는 사용자 한 쪽의 상한 — BiostarX 화면과 같은 200. */
  static final int USER_PAGE_MAX = 200;

  /** 일정이 비어 있을 때 쓰는 BiostarX 기본 일정(Always). */
  static final String ALWAYS_SCHEDULE = "1";

  private final TbSystemMapper systemMapper;
  private final BiostarDoorAdapter doorAdapter;
  private final BiostarAdapter biostarAdapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public EscortService(
      TbSystemMapper systemMapper,
      BiostarDoorAdapter doorAdapter,
      BiostarAdapter biostarAdapter,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.systemMapper = systemMapper;
    this.doorAdapter = doorAdapter;
    this.biostarAdapter = biostarAdapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 출입문 목록 — ID·이름·입실장치. */
  public List<BiostarDoor> doors(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbSystem cfg = config();
    List<BiostarDoor> rows;
    try {
      rows = doorAdapter.searchDoors(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), DOOR_LIMIT);
    } catch (BiostarSessionException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
    }
    auditService.log(actor, AuditService.READ, menuId, "에스코트 출입문 조회 (" + rows.size() + "건)");
    return rows;
  }

  /** 출입문 하나의 에스코트 설정과 등록된 사용자. */
  public BiostarDoorAuth door(long doorId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    BiostarDoorAuth auth = load(doorId);
    auditService.log(
        actor,
        AuditService.READ,
        menuId,
        "에스코트 인원 조회: 문 " + doorId + " " + nvl(auth.doorName()) + " (" + auth.users().size() + "명)");
    return auth;
  }

  /** 사용자 그룹 목록 — 추가할 사용자를 그룹으로 거르는 데 쓴다(조회 조건용 목록이라 감사하지 않는다 — 출입권한관리의 출입그룹 목록과 같다). */
  public List<BiostarUserGroup> userGroups(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbSystem cfg = config();
    BiostarUserGroups res =
        biostarAdapter.searchUserGroups(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg));
    if (!res.success()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, res.message());
    }
    return res.groups();
  }

  /**
   * 그 문에 추가할 수 있는 사용자 — BiostarX 가 걸러 준다(검색어는 BiostarX 의 search_text).
   *
   * @param userGroupId 사용자 그룹으로 거른다(그 그룹에 바로 속한 사람만). null 이면 전체
   */
  public BiostarDoorUserPage accessibleUsers(
      long doorId,
      int page,
      int size,
      String keyword,
      Long userGroupId,
      TbLoginUser actor,
      Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    int limit = Math.max(1, Math.min(USER_PAGE_MAX, size));
    int offset = Math.max(0, page - 1) * limit;
    String text = keyword == null ? "" : keyword.trim();
    TbSystem cfg = config();
    BiostarDoorUserPage res;
    try {
      res =
          doorAdapter.accessibleUsers(
              cfg.getBiostarIp(),
              cfg.getBiostarId(),
              pw(cfg),
              doorId,
              offset,
              limit,
              text,
              userGroupId);
    } catch (BiostarSessionException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
    }
    auditService.log(
        actor,
        AuditService.READ,
        menuId,
        "에스코트 추가 대상 조회: 문 "
            + doorId
            // 검색어는 남기지 않는다 — 대개 사람 이름이다(성명을 검색하는 정규인원·방문 목록과 같다)
            + (text.isEmpty() ? "" : " 검색어 있음")
            + (userGroupId == null ? "" : " 사용자그룹=" + userGroupId)
            + " ("
            + (res.total() < 0 ? page + "쪽, 다음 쪽 있음" : res.total() + "건")
            + ")");
    return res;
  }

  /**
   * 에스코트 사용자 반영 — 고른 사람을 더하고({@code userIds}), 체크를 푼 등록자를 뺀다({@code removeIds}).
   *
   * <p>BiostarX 의 PUT 은 사용자 목록을 <b>통째로 바꾼다</b>. 화면이 들고 있던 목록으로 보내면, 화면을 연 뒤 BiostarX 에서 누가 넣은 사람이
   * 빠진다. 그래서 보내기 직전에 장비에서 지금 목록을 다시 읽고 <b>바뀐 부분만</b> 적용한다(지금 − 뺄 사람 + 더할 사람). 일정도 지금 걸린 것을 그대로 쓴다.
   *
   * <p>권한: 반영은 등록 권한, 빼는 사람이 있으면 삭제 권한도 필요하다.
   *
   * @return 화면에 띄울 결과 문구
   */
  public String update(EscortForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (form == null || form.getDoorId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "출입문을 선택하세요.");
    }
    List<String> picked = cleanIds(form.getUserIds());
    List<String> dropped = cleanIds(form.getRemoveIds());
    if (picked.isEmpty() && dropped.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "추가하거나 뺄 사용자를 선택하세요.");
    }
    long doorId = form.getDoorId();
    BiostarDoorAuth auth = load(doorId);
    if (!auth.enabled()) {
      // 보내는 본문이 enabled=true 라 꺼진 문에 보내면 에스코트가 켜진다 — 바꾸지 않는다
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "이 출입문은 에스코트(auth_door_control)가 꺼져 있어 사용자를 바꿀 수 없습니다.");
    }
    Set<String> merged = new LinkedHashSet<>();
    auth.users().forEach(u -> merged.add(u.userId()));
    List<String> removed = new ArrayList<>();
    for (String id : dropped) {
      if (!picked.contains(id) && merged.remove(id)) {
        removed.add(id); // 지금 등록돼 있는 사람만 — 이미 빠진 사람은 셀 것이 없다
      }
    }
    List<String> added = new ArrayList<>();
    for (String id : picked) {
      if (merged.add(id)) {
        added.add(id);
      }
    }
    if (added.isEmpty() && removed.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "바뀐 내용이 없습니다. (이미 반영된 상태입니다)");
    }
    if (!removed.isEmpty()) {
      menuAuthService.requireDelete(actor, menuId);
    }
    String schedule = auth.scheduleId() == null ? ALWAYS_SCHEDULE : auth.scheduleId();
    TbSystem cfg = config();
    BiostarResult res =
        doorAdapter.updateAuthUsers(
            cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), doorId, schedule, List.copyOf(merged));
    String what =
        "에스코트 인원 변경: 문 "
            + doorId
            + " "
            + nvl(auth.doorName())
            + " — "
            + change("추가", added)
            + (added.isEmpty() || removed.isEmpty() ? "" : ", ")
            + change("제외", removed);
    if (!res.success()) {
      auditService.logAlways(actor, AuditService.UPDATE, menuId, what + " 실패 — " + res.message());
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 반영 실패: " + res.message());
    }
    auditService.log(actor, AuditService.UPDATE, menuId, what);
    List<String> parts = new ArrayList<>();
    if (!added.isEmpty()) {
      parts.add(added.size() + "명 추가");
    }
    if (!removed.isEmpty()) {
      parts.add(removed.size() + "명 제외");
    }
    return String.join(" · ", parts) + "했습니다.";
  }

  private static String change(String label, List<String> ids) {
    return ids.isEmpty() ? "" : label + " " + ids.size() + "명 (" + String.join(", ", ids) + ")";
  }

  /** 사용자ID — 앞뒤 공백을 지우고 빈 값·중복을 뺀다(고른 순서는 지킨다). */
  static List<String> cleanIds(List<String> ids) {
    Set<String> out = new LinkedHashSet<>();
    if (ids != null) {
      for (String id : ids) {
        if (id != null && !id.isBlank()) {
          out.add(id.trim());
        }
      }
    }
    return List.copyOf(out);
  }

  private BiostarDoorAuth load(long doorId) {
    TbSystem cfg = config();
    try {
      return doorAdapter.authControl(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), doorId);
    } catch (BiostarSessionException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
    }
  }

  private TbSystem config() {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null || cfg.getBiostarIp() == null || cfg.getBiostarIp().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 접속정보가 없습니다. 설정관리에서 등록하세요.");
    }
    return cfg;
  }

  private static String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }
}
