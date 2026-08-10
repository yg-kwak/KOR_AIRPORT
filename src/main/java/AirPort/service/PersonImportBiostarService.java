package AirPort.service;

import AirPort.adapter.BiostarImportAdapter;
import AirPort.adapter.BiostarUserDetail;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.ImportResult;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *   <li><b>기관</b> — 장비 사용자그룹 ID 가 `tb_company.biostar_group_id` 에 있는 인원만. 없으면 건너뛰고 사유를 남긴다.
 *   <li><b>출입그룹</b> — 우리와 매핑된(`tb_ac_group.biostar_ac_id`) 것만 가져온다.
 *   <li><b>이미 있는 인원</b> — 건너뛴다. 덮어쓰면 우리 화면에서 채운 생년월일·신원조회 같은 값이 날아간다.
 * </ul>
 *
 * <p>카드·얼굴·출입그룹은 화면에서 <b>항목별로 골라</b> 가져온다.
 */
@Service
public class PersonImportBiostarService {

  private static final Logger log = LoggerFactory.getLogger(PersonImportBiostarService.class);

  /** 정규인원 — 이 가져오기가 만드는 인원 구분. */
  private static final String PERSON_TYPE_REGULAR = "PT01";

  /** 인원상태 기본값 — 신규. 장비에는 이 개념이 없다. */
  private static final String STATUS_NEW = "01";

  /** 정규등록 발급구분 — 이 코드의 code_tag 가 가져오기 대상 사용자그룹의 뿌리다. */
  private static final String ISSUE_TYPE_REGULAR = "PTD01";

  private final BiostarImportAdapter importAdapter;
  private final TbSystemMapper systemMapper;
  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final TbCompanyMapper companyMapper;
  private final TbAcGroupMapper acGroupRefMapper;
  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public PersonImportBiostarService(
      BiostarImportAdapter importAdapter,
      TbSystemMapper systemMapper,
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      TbCompanyMapper companyMapper,
      TbAcGroupMapper acGroupRefMapper,
      TbCardMapper cardMapper,
      TbCommonMapper commonMapper,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.importAdapter = importAdapter;
    this.systemMapper = systemMapper;
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.companyMapper = companyMapper;
    this.acGroupRefMapper = acGroupRefMapper;
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 미리보기 — 무엇이 들어오고 무엇이 빠지는지만 센다. DB 를 건드리지 않는다. */
  public ImportResult preview(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    ImportResult r = run(true, false, false, false, actor, menuId);
    r.setPreview(true);
    return r;
  }

  /**
   * 가져오기 실행.
   *
   * @param withCards 카드도 가져올지
   * @param withFace 얼굴도 가져올지
   * @param withAcGroups 출입권한도 가져올지
   */
  @Transactional
  public ImportResult importUsers(
      boolean withCards,
      boolean withFace,
      boolean withAcGroups,
      TbLoginUser actor,
      Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    return run(false, withCards, withFace, withAcGroups, actor, menuId);
  }

  private ImportResult run(
      boolean dryRun,
      boolean withCards,
      boolean withFace,
      boolean withAcGroups,
      TbLoginUser actor,
      Integer menuId) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null || cfg.getBiostarIp() == null || cfg.getBiostarIp().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 접속정보가 없습니다. 설정관리에서 등록하세요.");
    }
    String ip = cfg.getBiostarIp();
    String id = cfg.getBiostarId();
    String pw = cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());

    java.util.Set<Long> scope = regularGroupScope(ip, id, pw);
    List<BiostarUserDetail> users = importAdapter.searchUsers(ip, id, pw);
    ImportResult r = new ImportResult();

    for (BiostarUserDetail u : users) {
      if (u.userId() == null || u.userId().isBlank()) {
        continue;
      }
      // 정규등록 그룹 밖은 애초에 우리 대상이 아니다 — 세지도, 사유를 남기지도 않는다
      if (u.userGroupId() == null || !scope.contains(u.userGroupId().longValue())) {
        continue;
      }
      r.setTotal(r.getTotal() + 1); // 대상 그룹에 속한 인원
      String company = companyOf(u.userGroupId());
      if (company == null) {
        skip(r, u, "기관 매핑 없음(사용자그룹 " + u.userGroupId() + ")");
        continue;
      }
      TbPerson existing = personMapper.selectById(u.userId());
      if (existing != null && !"Y".equals(existing.getDelYn())) {
        skip(r, u, "이미 등록된 인원");
        continue;
      }
      r.setTarget(r.getTarget() + 1); // 선별을 통과 — 가져올 수 있는 인원
      if (dryRun) {
        continue;
      }
      // 상세는 여기서 한 번만 읽는다 — 목록 응답에는 사진·카드가 없다
      BiostarUserDetail d = importAdapter.fetchUser(ip, id, pw, u.userId());
      if (d == null) {
        skip(r, u, "상세 조회 실패");
        continue;
      }
      savePerson(d, company, existing != null);
      r.setImported(r.getImported() + 1);
      if (withFace && d.photo() != null) {
        photoMapper.upsert(d.userId(), d.photo());
        r.setFaces(r.getFaces() + 1);
      }
      if (withAcGroups) {
        r.setAcGroups(r.getAcGroups() + saveAcGroups(d));
      }
      if (withCards) {
        r.setCards(r.getCards() + saveCards(d));
      }
    }
    record(r, dryRun, withCards, withFace, withAcGroups, actor, menuId);
    return r;
  }

  /** 인원 저장 — 되살리기 포함(예전에 지운 번호를 장비가 계속 쓰고 있을 수 있다). */
  private void savePerson(BiostarUserDetail d, String companyCode, boolean revive) {
    TbPerson p = new TbPerson();
    p.setPersonId(d.userId());
    p.setPersonName(ARIAUtil.ariaEncrypt(d.name() == null ? "" : d.name()));
    p.setPersonPhone(d.phone() == null ? null : ARIAUtil.ariaEncrypt(d.phone()));
    p.setCompanyCode(companyCode);
    p.setTitleCode(titleCodeOf(d.userTitle()));
    p.setPersonType(PERSON_TYPE_REGULAR);
    p.setStatusCode(STATUS_NEW);
    p.setAccessStartDt(isoDate(d.startDatetime()));
    p.setAccessEndDt(isoDate(d.expiryDatetime()));
    p.setUseYn("Y");
    if (revive) {
      personMapper.revive(p);
    } else {
      personMapper.insert(p);
    }
    // 장비에 이미 있으므로 연동 완료로 표시한다 — 다음 수정부터 update 경로를 탄다
    personMapper.updateBiostarUserId(d.userId(), d.userId());
  }

  /** 우리와 매핑된 출입그룹만 연결한다. */
  private int saveAcGroups(BiostarUserDetail d) {
    if (d.accessGroupIds().isEmpty()) {
      return 0;
    }
    List<Integer> ids = acGroupRefMapper.selectIdsByBiostarAcIds(d.accessGroupIds());
    if (ids.isEmpty()) {
      return 0;
    }
    acGroupMapper.deleteByPerson(d.userId());
    acGroupMapper.insertBatch(d.userId(), ids);
    return ids.size();
  }

  /** 카드번호로 우리 카드를 찾아 배정한다. 없으면 만든다 — 장비에는 이미 있으므로 연동 ID 도 채운다. */
  private int saveCards(BiostarUserDetail d) {
    int n = 0;
    for (String cardNo : d.cardNos()) {
      TbCard card = cardMapper.selectByCardNo(cardNo);
      if (card == null) {
        TbCard row = new TbCard();
        row.setBiostarCardValue(cardNo);
        row.setBiostarCardId(cardNo);
        row.setCardName(cardNo); // 명칭은 운영에서 다시 붙인다 — 비워 두면 신청서 칸이 빈다
        row.setCardType(CardService.CARD_TYPE_PERSON);
        row.setPassType(PERSON_TYPE_REGULAR);
        row.setCardStatus("CS01"); // 정상
        row.setPersonId(d.userId());
        cardMapper.insert(row);
      } else {
        cardMapper.assignPerson(card.getCardId(), d.userId());
      }
      n++;
    }
    return n;
  }

  /**
   * 가져오기 대상 사용자그룹 — 정규등록(PTD01) 그룹과 그 아래 전부.
   *
   * <p>장비에는 임시·장기 사용자도 같이 있다. 뿌리를 정해 두지 않으면 정규가 아닌 사람까지 정규인원으로 들어온다.
   */
  private java.util.Set<Long> regularGroupScope(String ip, String id, String pw) {
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
    java.util.Map<Long, List<Long>> children = new java.util.HashMap<>();
    for (AirPort.adapter.BiostarUserGroup g : importAdapter.searchUserGroups(ip, id, pw)) {
      if (g.parentId() != null) {
        children.computeIfAbsent(g.parentId(), k -> new java.util.ArrayList<>()).add(g.id());
      }
    }
    java.util.Set<Long> scope = new java.util.LinkedHashSet<>();
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

  private String companyOf(Integer biostarGroupId) {
    return biostarGroupId == null ? null : companyMapper.selectCodeByBiostarGroupId(biostarGroupId);
  }

  /** 직위는 이름으로 맞춘다 — 장비의 user_title 과 우리 UT 코드명이 같을 때만. 없으면 비운다. */
  private String titleCodeOf(String userTitle) {
    if (userTitle == null || userTitle.isBlank()) {
      return null;
    }
    for (TbCommon c : commonMapper.selectCodesForPicker("UT", userTitle)) {
      if (userTitle.equals(c.getCodeName())) {
        return c.getCodeId();
      }
    }
    return null;
  }

  /** "2026-08-04T09:37:00.00Z" → "2026-08-04T09:37:00" (우리 datetime2 형식). */
  private static String isoDate(String v) {
    if (v == null || v.length() < 19) {
      return v;
    }
    return v.substring(0, 19);
  }

  private static void skip(ImportResult r, BiostarUserDetail u, String reason) {
    r.setSkipped(r.getSkipped() + 1);
    if (r.getSkippedReasons().size() < 50) {
      r.getSkippedReasons().add(u.userId() + " — " + reason);
    }
  }

  private void record(
      ImportResult r,
      boolean dryRun,
      boolean withCards,
      boolean withFace,
      boolean withAcGroups,
      TbLoginUser actor,
      Integer menuId) {
    String detail =
        "BiostarX 정규인원 가져오기"
            + (dryRun ? "(미리보기)" : "")
            + " — 대상 "
            + r.getTotal()
            + "명, "
            + (dryRun ? "가져올 수 있음 " + r.getTarget() : "가져옴 " + r.getImported())
            + "명, 건너뜀 "
            + r.getSkipped()
            + "명"
            + (dryRun
                ? ""
                : " [카드 " + r.getCards() + " 얼굴 " + r.getFaces() + " 출입권한 " + r.getAcGroups() + "]")
            + " (옵션 카드="
            + yn(withCards)
            + " 얼굴="
            + yn(withFace)
            + " 출입권한="
            + yn(withAcGroups)
            + ")";
    auditService.log(actor, dryRun ? AuditService.READ : AuditService.CREATE, menuId, detail);
    log.info(detail);
  }

  private static String yn(boolean v) {
    return v ? "Y" : "N";
  }
}
