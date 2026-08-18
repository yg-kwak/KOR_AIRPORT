package AirPort.service;

import AirPort.adapter.biostar.BiostarUserDetail;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.model.ImportForm;
import AirPort.model.ImportResult;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BiostarX 사용자 1명을 우리 DB 에 <b>장비 기준으로</b> 맞춘다 — 가져오기의 반영 단계. (설정관리 → BiostarX 가져오기)
 *
 * <p>대상 선별·범위 판정은 {@link PersonImportBiostarService} 가 하고, 여기서는 한 사람만 본다.
 *
 * <p><b>장비가 원천이다.</b> 카드·출입권한은 우리 쪽에만 있던 것을 <b>지우고</b> 장비 값으로 맞춘다. 얼굴만 규칙이 다르다 — 사진은 바이너리라 같은 사람이라도
 * 값이 달라 비교가 무의미하므로 <b>있고 없음</b>만 본다:
 *
 * <ul>
 *   <li>장비에 없다 → 우리도 지운다(장비에서 지운 얼굴이 우리에만 남으면 안 된다).
 *   <li>둘 다 있다 → 그대로 둔다(비교하지 않는다 — 매번 덮어쓰면 바뀐 게 없어도 바뀐 것처럼 보인다).
 *   <li>장비에만 있다 → 가져온다.
 * </ul>
 *
 * <p>{@code dryRun} 이면 무엇이 바뀌는지만 계산하고 DB 를 건드리지 않는다. 미리보기와 실행이 <b>같은 코드</b>를 타야 "미리보기엔 없던 일"이 생기지
 * 않는다.
 */
@Service
public class PersonImportSyncService {

  /** 정규인원 — 이 가져오기가 만드는 인원 구분. */
  static final String PERSON_TYPE_REGULAR = "PT01";

  /** 인원상태 기본값 — 신규. 장비에는 이 개념이 없다. 갱신 때는 건드리지 않는다. */
  private static final String STATUS_NEW = "01";

  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final TbAcGroupMapper acGroupRefMapper;
  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;

  public PersonImportSyncService(
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      TbAcGroupMapper acGroupRefMapper,
      TbCardMapper cardMapper,
      TbCommonMapper commonMapper) {
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.acGroupRefMapper = acGroupRefMapper;
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
  }

  /**
   * 한 사람을 장비 기준으로 맞춘다. 집계와 사람별 변경 내역을 {@code r} 에 쌓는다.
   *
   * <p><b>트랜잭션 경계가 여기다</b> — 한 사람의 인원·카드·출입권한·얼굴은 함께 반영되거나 함께 되돌아간다. 가져오기 전체를 한 트랜잭션에 묶지 않는 이유는
   * 호출자({@link PersonImportBiostarService#importUsers}) 주석에 있다: 인원 수만큼의 장비 왕복이 끝날 때까지 DB 커넥션을 붙잡게
   * 된다.
   *
   * <p>{@code dryRun} 이면 아무것도 쓰지 않으므로 트랜잭션은 열려도 비어 있다.
   *
   * @param existing 우리 DB 의 현재 값 — 없으면 신규, 삭제된 행이면 되살린다
   */
  @Transactional
  public void apply(
      BiostarUserDetail d,
      String companyCode,
      TbPerson existing,
      ImportForm opt,
      boolean dryRun,
      ImportResult r) {
    boolean isNew = existing == null || "Y".equals(existing.getDelYn());
    List<String> what = new ArrayList<>();

    if (isNew) {
      what.add("인원 등록");
      if (!dryRun) {
        savePerson(d, companyCode, existing != null);
      }
    } else if (personChanged(d, companyCode, existing)) {
      what.add("인원정보 갱신");
      if (!dryRun) {
        personMapper.updateFromBiostar(personRow(d, companyCode));
      }
    }

    if (opt.isCards()) {
      syncCards(d, isNew, dryRun, what, r);
    }
    if (opt.isAcGroups()) {
      syncAcGroups(d, isNew, dryRun, what, r);
    }
    if (opt.isFace()) {
      syncFace(d, isNew, dryRun, what, r);
    }

    // 사람 단위로 딱 한 갈래에만 넣는다. 항목별 분기에서 세면 카드만 바뀐 사람이
    // 신규도 갱신도 변경없음도 아닌 채로 집계에서 사라진다.
    tally(d.userId(), isNew, what, r);
  }

  /** 신규 / 갱신 / 변경없음 중 하나로 분류한다 — 화면이 이 목록으로 대상자를 찾는다. */
  private static void tally(String userId, boolean isNew, List<String> what, ImportResult r) {
    if (isNew) {
      r.setImported(r.getImported() + 1);
      r.getNewUserIds().add(userId);
    } else if (!what.isEmpty()) {
      r.setUpdated(r.getUpdated() + 1);
      r.getUpdatedUserIds().add(userId);
    } else {
      r.setUnchanged(r.getUnchanged() + 1);
      r.getUnchangedUserIds().add(userId);
      return; // 바뀐 게 없으면 남길 내역도 없다
    }
    r.getDetails().put(userId, String.join(", ", what));
  }

  // ── 카드 ────────────────────────────────────────────────

  /** 장비 카드 목록으로 맞춘다. 우리에만 있던 카드는 회수한다(삭제가 아니라 미배정 — 다른 사람이 다시 쓴다). */
  private void syncCards(
      BiostarUserDetail d, boolean isNew, boolean dryRun, List<String> what, ImportResult r) {
    Set<String> want = new LinkedHashSet<>(d.cardNos());
    Set<String> have = isNew ? Set.of() : currentCardNos(d.userId());
    if (want.equals(have)) {
      return;
    }
    Set<String> removed = new LinkedHashSet<>(have);
    removed.removeAll(want);
    what.add(cardChangeText(want, removed));
    if (!dryRun) {
      cardMapper.releaseByPerson(d.userId()); // 먼저 전부 떼고 장비 것만 다시 붙인다
      want.forEach(no -> assignCard(no, d.userId()));
    }
    r.setCards(r.getCards() + want.size());
  }

  private static String cardChangeText(Set<String> want, Set<String> removed) {
    String text = "카드 " + want.size() + "장으로 맞춤";
    return removed.isEmpty() ? text : text + "(회수 " + String.join("/", removed) + ")";
  }

  private Set<String> currentCardNos(String personId) {
    Set<String> out = new LinkedHashSet<>();
    for (TbCard c : cardMapper.selectByPerson(personId)) {
      if (c.getBiostarCardValue() != null) {
        out.add(c.getBiostarCardValue());
      }
    }
    return out;
  }

  /** 카드번호로 우리 카드를 찾아 배정한다. 없으면 만든다 — 장비에는 이미 있으므로 연동 ID 도 채운다. */
  private void assignCard(String cardNo, String personId) {
    TbCard card = cardMapper.selectByCardNo(cardNo);
    if (card == null) {
      TbCard row = new TbCard();
      row.setBiostarCardValue(cardNo);
      row.setBiostarCardId(cardNo);
      row.setCardName(cardNo); // 명칭은 운영에서 다시 붙인다 — 비워 두면 신청서 칸이 빈다
      row.setCardType(CardService.CARD_TYPE_PERSON);
      row.setPassType(PERSON_TYPE_REGULAR);
      row.setCardStatus("CS01"); // 정상
      row.setPersonId(personId);
      cardMapper.insert(row);
    } else {
      cardMapper.assignPerson(card.getCardId(), personId);
    }
  }

  // ── 출입권한 ─────────────────────────────────────────────

  /** 우리와 매핑된(biostar_ac_id) 출입그룹만 대상이다. 매핑 밖의 장비 그룹은 우리 쪽에 표현할 수단이 없다. */
  private void syncAcGroups(
      BiostarUserDetail d, boolean isNew, boolean dryRun, List<String> what, ImportResult r) {
    Set<Integer> want =
        d.accessGroupIds().isEmpty()
            ? Set.of()
            : new LinkedHashSet<>(acGroupRefMapper.selectIdsByBiostarAcIds(d.accessGroupIds()));
    Set<Integer> have =
        isNew ? Set.of() : new LinkedHashSet<>(acGroupMapper.selectAcGroupIds(d.userId()));
    if (want.equals(have)) {
      return;
    }
    what.add("출입권한 " + have.size() + " → " + want.size() + "개");
    if (!dryRun) {
      acGroupMapper.deleteByPerson(d.userId());
      if (!want.isEmpty()) {
        acGroupMapper.insertBatch(d.userId(), new ArrayList<>(want));
      }
    }
    r.setAcGroups(r.getAcGroups() + want.size());
  }

  // ── 얼굴 ────────────────────────────────────────────────

  /** 사진은 값을 비교하지 않는다 — 있고 없음만 맞춘다(클래스 주석 참고). */
  private void syncFace(
      BiostarUserDetail d, boolean isNew, boolean dryRun, List<String> what, ImportResult r) {
    boolean onDevice = d.photo() != null && !d.photo().isBlank();
    boolean onOurs = !isNew && photoMapper.selectPhoto(d.userId()) != null;
    if (onDevice && !onOurs) {
      what.add("얼굴 가져옴");
      if (!dryRun) {
        photoMapper.upsert(d.userId(), d.photo());
      }
      r.setFaces(r.getFaces() + 1);
    } else if (!onDevice && onOurs) {
      what.add("얼굴 삭제(장비에 없음)");
      if (!dryRun) {
        photoMapper.deleteByPerson(d.userId());
      }
      r.setFacesRemoved(r.getFacesRemoved() + 1);
    }
  }

  // ── 인원 ────────────────────────────────────────────────

  /** 장비가 원천인 값이 하나라도 다른가. 같으면 UPDATE 를 보내지 않는다(수정일시만 바뀌는 갱신을 만들지 않는다). */
  private boolean personChanged(BiostarUserDetail d, String companyCode, TbPerson cur) {
    TbPerson next = personRow(d, companyCode);
    return !Objects.equals(next.getPersonName(), cur.getPersonName())
        || !Objects.equals(next.getPersonPhone(), cur.getPersonPhone())
        || !Objects.equals(next.getCompanyCode(), cur.getCompanyCode())
        || !Objects.equals(next.getTitleCode(), cur.getTitleCode())
        || !sameMinute(next.getAccessStartDt(), cur.getAccessStartDt())
        || !sameMinute(next.getAccessEndDt(), cur.getAccessEndDt());
  }

  /**
   * 유효기간은 <b>분까지만</b> 비교한다.
   *
   * <p>조회(selectById)는 화면 표시에 맞춰 {@code varchar(16)} 으로 읽어 초가 없고(2026-01-01T09:00), 장비 값은 초까지
   * 온다(…T09:00:00). 문자열을 그대로 맞대면 <b>같은 시각인데도 늘 다르다</b>고 나와, 가져오기를 아무리 돌려도 매번 "갱신" 으로 집계된다.
   */
  public static boolean sameMinute(String a, String b) {
    return Objects.equals(toMinute(a), toMinute(b));
  }

  private static String toMinute(String v) {
    return (v == null || v.length() < 16) ? v : v.substring(0, 16);
  }

  /** 장비 값 → 우리 행(장비가 원천인 컬럼만). 성명·연락처는 ARIA 결정적 암호화라 암호문끼리 비교된다. */
  private TbPerson personRow(BiostarUserDetail d, String companyCode) {
    TbPerson p = new TbPerson();
    p.setPersonId(d.userId());
    p.setPersonName(ARIAUtil.ariaEncrypt(d.name() == null ? "" : d.name()));
    p.setPersonPhone(d.phone() == null ? null : ARIAUtil.ariaEncrypt(d.phone()));
    p.setCompanyCode(companyCode);
    p.setTitleCode(titleCodeOf(d.userTitle()));
    p.setAccessStartDt(isoDate(d.startDatetime()));
    p.setAccessEndDt(isoDate(d.expiryDatetime()));
    return p;
  }

  /** 인원 저장 — 되살리기 포함(예전에 지운 번호를 장비가 계속 쓰고 있을 수 있다). */
  private void savePerson(BiostarUserDetail d, String companyCode, boolean revive) {
    TbPerson p = personRow(d, companyCode);
    p.setPersonType(PERSON_TYPE_REGULAR);
    p.setStatusCode(STATUS_NEW);
    p.setUseYn("Y");
    if (revive) {
      personMapper.revive(p);
    } else {
      personMapper.insert(p);
    }
    // 장비에 이미 있으므로 연동 완료로 표시한다 — 다음 수정부터 update 경로를 탄다
    personMapper.updateBiostarUserId(d.userId(), d.userId());
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
}
