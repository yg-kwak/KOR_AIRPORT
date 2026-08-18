package AirPort.service;

import AirPort.adapter.BiostarImportAdapter;
import AirPort.adapter.BiostarUserDetail;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.ImportCandidateResult;
import AirPort.model.ImportForm;
import AirPort.model.ImportResult;
import AirPort.model.TbCommon;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * BiostarX 정규 사용자를 우리 DB 로 가져온다. (설정관리 → BiostarX 가져오기)
 *
 * <p><b>단방향이다.</b> 장비에는 한 글자도 쓰지 않는다 — 우리 등록 흐름은 항상 장비로 push 하므로, 그 길로 맞추면 현장에 이미 올라간 얼굴·카드·출입그룹을
 * 덮어쓴다.
 *
 * <p>가져오는 기준:
 *
 * <ul>
 *   <li><b>범위</b> — 발급구분 정규등록(`tb_common` PTD/PTD01)의 `code_tag` 사용자그룹과 <b>그 아래 모든 하위 그룹</b>에 속한
 *       인원만. 장비에는 임시·장기 사용자도 함께 있어 전체를 끌어오면 정규가 아닌 사람이 섞인다.
 *   <li><b>기관</b> — 장비 사용자그룹 ID 가 `tb_company.biostar_group_id` 에 있는 인원만. 없으면 가져올 수 없다.
 *   <li><b>선택</b> — 화면에서 <b>고른 사람만</b> 가져온다. 이미 있는 인원도 장비 기준으로 덮어쓰므로, 무엇이 바뀌는지 모르고 전체를 돌리면 되돌릴 수
 *       없다.
 *   <li><b>이미 있는 인원</b> — 건너뛰지 않고 <b>장비 기준으로 맞춘다</b>(카드·출입권한·얼굴 규칙은 {@link
 *       PersonImportSyncService}).
 * </ul>
 */
@Service
public class PersonImportBiostarService {

  private static final Logger log = LoggerFactory.getLogger(PersonImportBiostarService.class);

  /** 정규등록 발급구분 — 이 코드의 code_tag 가 가져오기 대상 사용자그룹의 뿌리다. */
  private static final String ISSUE_TYPE_REGULAR = "PTD01";

  private final BiostarImportAdapter importAdapter;
  private final PersonImportSyncService sync;
  private final TbSystemMapper systemMapper;
  private final TbPersonMapper personMapper;
  private final TbCompanyMapper companyMapper;
  private final TbCommonMapper commonMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public PersonImportBiostarService(
      BiostarImportAdapter importAdapter,
      PersonImportSyncService sync,
      TbSystemMapper systemMapper,
      TbPersonMapper personMapper,
      TbCompanyMapper companyMapper,
      TbCommonMapper commonMapper,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.importAdapter = importAdapter;
    this.sync = sync;
    this.systemMapper = systemMapper;
    this.personMapper = personMapper;
    this.companyMapper = companyMapper;
    this.commonMapper = commonMapper;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** BiostarX 접속정보 — 없으면 아무것도 할 수 없다. */
  private record Conn(String ip, String id, String pw) {}

  private Conn conn() {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null || cfg.getBiostarIp() == null || cfg.getBiostarIp().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 접속정보가 없습니다. 설정관리에서 등록하세요.");
    }
    return new Conn(
        cfg.getBiostarIp(),
        cfg.getBiostarId(),
        cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw()));
  }

  /**
   * 선택 목록 — 대상 그룹에 속한 장비 사용자를 우리 등록 여부와 함께 준다.
   *
   * <p>여기서는 사용자 <b>상세를 읽지 않는다</b>. 카드·출입그룹은 1명당 1회 왕복이라, 목록을 그리자고 인원 수만큼 호출하면 화면이 열리지 않는다. 무엇이
   * 달라지는지는 고른 뒤 미리보기가 알려 준다.
   */
  public List<ImportCandidateResult> candidates(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    Conn c = conn();
    Set<Long> scope = regularGroupScope(c);

    // 대상만 먼저 추린 뒤 DB 는 두 번만 친다 — 인원마다 조회하면 수천 명일 때 질의도 수천 번이다
    List<BiostarUserDetail> targets = new ArrayList<>();
    for (BiostarUserDetail u : importAdapter.searchUsers(c.ip(), c.id(), c.pw())) {
      if (u.userId() == null || u.userId().isBlank()) {
        continue;
      }
      // 정규등록 그룹 밖은 애초에 우리 대상이 아니다 — 목록에 올리지 않는다
      if (u.userGroupId() != null && scope.contains(u.userGroupId().longValue())) {
        targets.add(u);
      }
    }
    Map<Integer, TbCompany> byGroup = companiesByBiostarGroup();
    Set<String> registered = existingPersonIds(targets);

    List<ImportCandidateResult> out = new ArrayList<>();
    for (BiostarUserDetail u : targets) {
      ImportCandidateResult row = new ImportCandidateResult();
      row.setUserId(u.userId());
      row.setUserName(u.name());
      TbCompany company = byGroup.get(u.userGroupId());
      row.setCompanyName(company == null ? null : company.getCompanyName());
      row.setImportable(company != null);
      if (company == null) {
        row.setReason("기관 매핑 없음(사용자그룹 " + u.userGroupId() + ") — 기관등록관리에서 연결하세요.");
      }
      row.setRegistered(registered.contains(u.userId()));
      out.add(row);
    }
    auditService.log(actor, AuditService.READ, menuId, "BiostarX 가져오기 대상 조회 (" + out.size() + "명)");
    return out;
  }

  /** BiostarX 사용자그룹 → 기관. 매핑은 기관 수만큼(보통 수십 개)이라 한 번에 읽는다. */
  private Map<Integer, TbCompany> companiesByBiostarGroup() {
    Map<Integer, TbCompany> out = new HashMap<>();
    for (TbCompany co : companyMapper.selectBiostarGroupMappings()) {
      out.putIfAbsent(co.getBiostarGroupId(), co); // 한 그룹에 기관이 둘이면 앞선 것(코드 오름차순)
    }
    return out;
  }

  /** 이미 우리 DB 에 살아 있는 인원ID — 목록에는 '등록됨/신규' 만 필요하다. */
  private Set<String> existingPersonIds(List<BiostarUserDetail> targets) {
    if (targets.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        personMapper.selectExistingIds(targets.stream().map(BiostarUserDetail::userId).toList()));
  }

  /** 미리보기 — 고른 사람에게 무엇이 일어나는지만 계산한다. DB 를 건드리지 않는다. */
  public ImportResult preview(ImportForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    ImportResult r = run(true, form, actor, menuId);
    r.setPreview(true);
    return r;
  }

  /**
   * 가져오기 실행 — 고른 사람을 장비 기준으로 맞춘다.
   *
   * <p><b>트랜잭션은 사람 단위다</b>({@link PersonImportSyncService#apply}). 전체를 한 트랜잭션에 묶으면 인원 수만큼의 장비 왕복이
   * 끝날 때까지 DB 커넥션을 붙잡아, 수백 명을 고른 순간 풀이 말라 다른 화면까지 멈춘다(deployment.md).
   *
   * <p>그래서 중간에 실패해도 <b>앞서 반영된 사람은 남는다</b>. 이 가져오기는 멱등하므로(장비와 같으면 손대지 않는다) 사유를 고치고 다시 돌리면 남은 사람만 이어서
   * 맞춰진다.
   */
  public ImportResult importUsers(ImportForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    return run(false, form, actor, menuId);
  }

  private ImportResult run(boolean dryRun, ImportForm form, TbLoginUser actor, Integer menuId) {
    List<String> wanted = form.getUserIds() == null ? List.of() : form.getUserIds();
    if (wanted.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "가져올 사용자를 선택하세요.");
    }
    Conn c = conn();
    Set<Long> scope = regularGroupScope(c);
    Map<String, BiostarUserDetail> byId = new LinkedHashMap<>();
    for (BiostarUserDetail u : importAdapter.searchUsers(c.ip(), c.id(), c.pw())) {
      if (u.userId() != null) {
        byId.put(u.userId(), u);
      }
    }

    ImportResult r = new ImportResult();
    r.setTotal(wanted.size());
    for (String userId : wanted) {
      BiostarUserDetail u = byId.get(userId);
      if (u == null || u.userGroupId() == null || !scope.contains(u.userGroupId().longValue())) {
        skip(r, userId, "장비의 정규등록 대상이 아님");
        continue;
      }
      String company = companyMapper.selectCodeByBiostarGroupId(u.userGroupId());
      if (company == null) {
        skip(r, userId, "기관 매핑 없음(사용자그룹 " + u.userGroupId() + ")");
        continue;
      }
      // 상세는 여기서 한 번만 읽는다 — 목록 응답에는 사진·카드가 없다
      BiostarUserDetail d = importAdapter.fetchUser(c.ip(), c.id(), c.pw(), userId);
      if (d == null) {
        skip(r, userId, "상세 조회 실패");
        continue;
      }
      r.setTarget(r.getTarget() + 1);
      try {
        sync.apply(d, company, personMapper.selectById(userId), form, dryRun, r);
      } catch (RuntimeException e) {
        // 사람 단위 트랜잭션이라 이 사람만 되돌아간다 — 한 명 때문에 나머지를 멈추지 않는다
        log.warn("BiostarX 가져오기 실패({}): {}", userId, e.toString());
        skip(r, userId, "반영 실패 — " + e.getMessage());
      }
    }
    record(r, dryRun, form, actor, menuId);
    return r;
  }

  /**
   * 가져오기 대상 사용자그룹 — 정규등록(PTD01) 그룹과 그 아래 전부.
   *
   * <p>장비에는 임시·장기 사용자도 같이 있다. 뿌리를 정해 두지 않으면 정규가 아닌 사람까지 정규인원으로 들어온다.
   */
  private Set<Long> regularGroupScope(Conn c) {
    TbCommon ptd = commonMapper.selectOne("PTD", ISSUE_TYPE_REGULAR);
    if (ptd == null || ptd.getCodeTag() == null || ptd.getCodeTag().isBlank()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "발급구분(PTD01 정규등록)에 BiostarX 사용자그룹 ID가 없습니다. 공통코드관리에서 등록하세요.");
    }
    long root;
    try {
      root = Long.parseLong(ptd.getCodeTag().trim());
    } catch (NumberFormatException e) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "발급구분(PTD01)의 사용자그룹 ID가 숫자가 아닙니다: " + ptd.getCodeTag());
    }
    // 부모→자식 한 단계씩 내려가며 넓힌다(깊이 제한 없음)
    Map<Long, List<Long>> children = new HashMap<>();
    for (AirPort.adapter.BiostarUserGroup g :
        importAdapter.searchUserGroups(c.ip(), c.id(), c.pw())) {
      if (g.parentId() != null) {
        children.computeIfAbsent(g.parentId(), k -> new ArrayList<>()).add(g.id());
      }
    }
    Set<Long> scope = new java.util.LinkedHashSet<>();
    java.util.Deque<Long> queue = new java.util.ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Long g = queue.poll();
      if (!scope.add(g)) {
        continue; // 순환 방어
      }
      queue.addAll(children.getOrDefault(g, List.of()));
    }
    log.info("BiostarX 가져오기 대상 그룹 — 정규등록 {} 아래 {}개", root, scope.size());
    return scope;
  }

  private static void skip(ImportResult r, String userId, String reason) {
    r.setSkipped(r.getSkipped() + 1);
    r.getDetails().put(userId, "건너뜀 — " + reason);
  }

  private void record(
      ImportResult r, boolean dryRun, ImportForm form, TbLoginUser actor, Integer menuId) {
    String detail =
        "BiostarX 정규인원 가져오기"
            + (dryRun ? "(미리보기)" : "")
            + " — 선택 "
            + r.getTotal()
            + "명, 신규 "
            + r.getImported()
            + " 갱신 "
            + r.getUpdated()
            + " 변경없음 "
            + r.getUnchanged()
            + " 건너뜀 "
            + r.getSkipped()
            + " [카드 "
            + r.getCards()
            + " 얼굴 +"
            + r.getFaces()
            + "/-"
            + r.getFacesRemoved()
            + " 출입권한 "
            + r.getAcGroups()
            + "] (옵션 카드="
            + yn(form.isCards())
            + " 얼굴="
            + yn(form.isFace())
            + " 출입권한="
            + yn(form.isAcGroups())
            + ")";
    auditService.log(actor, dryRun ? AuditService.READ : AuditService.CREATE, menuId, detail);
    log.info(detail);
  }

  private static String yn(boolean v) {
    return v ? "Y" : "N";
  }
}
