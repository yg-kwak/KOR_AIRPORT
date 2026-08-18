package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.adapter.biostar.BiostarImportAdapter;
import AirPort.adapter.biostar.BiostarUserDetail;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.ImportCandidateResult;
import AirPort.model.ImportForm;
import AirPort.model.ImportResult;
import AirPort.model.TbCommon;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.service.AuditService;
import AirPort.service.MenuAuthService;
import AirPort.service.PersonImportBiostarService;
import AirPort.service.PersonImportSyncService;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * BiostarX 정규인원 가져오기 검증 — <b>단방향</b>이 핵심이다. 장비에 쓰는 순간 현장에 이미 올라간 얼굴·카드·출입그룹을 덮어쓴다.
 *
 * <p>그 다음은 대상 선별(정규등록 그룹 아래 + 기관 매핑 + 화면에서 고른 사람만)과, 이미 있는 인원을 <b>장비 기준으로 맞추는</b> 규칙이다.
 */
class PersonImportBiostarTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init();
  }

  private final BiostarImportAdapter importAdapter = mock(BiostarImportAdapter.class);
  private final TbSystemMapper systemMapper = mock(TbSystemMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbPersonPhotoMapper photoMapper = mock(TbPersonPhotoMapper.class);
  private final TbPersonAcGroupMapper acGroupMapper = mock(TbPersonAcGroupMapper.class);
  private final TbCompanyMapper companyMapper = mock(TbCompanyMapper.class);
  private final TbAcGroupMapper acGroupRefMapper = mock(TbAcGroupMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final MenuAuthService menuAuthService = mock(MenuAuthService.class);
  private final AuditService auditService = mock(AuditService.class);

  private PersonImportBiostarService service() {
    PersonImportSyncService sync =
        new PersonImportSyncService(
            personMapper, photoMapper, acGroupMapper, acGroupRefMapper, cardMapper, commonMapper);
    return new PersonImportBiostarService(
        importAdapter,
        sync,
        systemMapper,
        personMapper,
        companyMapper,
        commonMapper,
        menuAuthService,
        auditService);
  }

  /** 화면에서 고른 사용자 + 항목 전체 선택. */
  private static ImportForm pick(String... userIds) {
    ImportForm f = new ImportForm();
    f.setUserIds(List.of(userIds));
    f.setCards(true);
    f.setFace(true);
    f.setAcGroups(true);
    return f;
  }

  /** 접속정보 + 가져오기 범위(정규등록 1003 아래 1009) 준비. */
  private void configured() {
    TbSystem cfg = new TbSystem();
    cfg.setBiostarIp("10.0.0.1");
    cfg.setBiostarId("admin");
    when(systemMapper.selectOne()).thenReturn(cfg);
    TbCommon ptd = new TbCommon();
    ptd.setCodeTag("1003");
    when(commonMapper.selectOne("PTD", "PTD01")).thenReturn(ptd);
    when(importAdapter.searchUserGroups(any(), any(), any()))
        .thenReturn(
            List.of(
                new AirPort.adapter.biostar.BiostarUserGroup(1003L, "정규", 1L),
                new AirPort.adapter.biostar.BiostarUserGroup(1009L, "슈프리마", 1003L),
                new AirPort.adapter.biostar.BiostarUserGroup(1004L, "임시", 1L),
                new AirPort.adapter.biostar.BiostarUserGroup(2001L, "임시하위", 1004L)));
  }

  private static BiostarUserDetail user(String id, Integer groupId) {
    return new BiostarUserDetail(
        id, "홍길동", null, null, groupId, null, null, null, List.of(), List.of());
  }

  private static BiostarUserDetail full(String id, Integer groupId) {
    return detail(id, groupId, "BASE64PHOTO", List.of("1111114"), List.of(1, 2, 99));
  }

  private static BiostarUserDetail detail(
      String id, Integer groupId, String photo, List<String> cards, List<Integer> acs) {
    return new BiostarUserDetail(
        id,
        "홍길동",
        "01011112222",
        "대리",
        groupId,
        "2026-08-04T09:37:00.00Z",
        "2037-12-31T23:59:00.00Z",
        photo,
        cards,
        acs);
  }

  /**
   * 이미 우리 DB 에 있는 인원(장비 값과 같은 상태).
   *
   * <p>유효기간을 <b>분까지만</b> 채우는 것이 중요하다 — 실제 {@code selectById} 가 화면 표시에 맞춰 {@code varchar(16)} 으로 읽어
   * 오기 때문이다. 장비는 초까지 주므로, 문자열을 그대로 맞대면 같은 시각인데도 늘 다르다고 나온다.
   */
  private TbPerson registered(String id) {
    TbPerson p = new TbPerson();
    p.setPersonId(id);
    p.setDelYn("N");
    p.setPersonName(AirPort.security.ARIAUtil.ariaEncrypt("홍길동"));
    p.setPersonPhone(AirPort.security.ARIAUtil.ariaEncrypt("01011112222"));
    p.setCompanyCode("C004");
    p.setAccessStartDt("2026-08-04T09:37"); // 초 없음 — mapper 가 읽어 오는 그대로
    p.setAccessEndDt("2037-12-31T23:59");
    when(personMapper.selectById(id)).thenReturn(p);
    return p;
  }

  private void onDevice(BiostarUserDetail d) {
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(d));
    when(importAdapter.fetchUser(any(), any(), any(), any())).thenReturn(d);
    when(companyMapper.selectCodeByBiostarGroupId(d.userGroupId())).thenReturn("C004");
  }

  // ── 대상 선별 ────────────────────────────────────────────

  @Test
  void 기관이_매핑되지_않은_인원은_가져오지_않는다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("1", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn(null);

    ImportResult r = service().importUsers(pick("1"), null, 101);

    assertEquals(0, r.getTarget());
    assertEquals(1, r.getSkipped());
    assertTrue(r.getDetails().get("1").contains("기관 매핑 없음"), r.getDetails().toString());
    verify(personMapper, never()).insert(any());
  }

  @Test
  void 정규등록_그룹_밖의_사용자는_가져오지_않는다() {
    // 장비에는 임시·장기 사용자도 같이 있다 — 골랐더라도 정규가 아니면 넣지 않는다
    configured();
    when(importAdapter.searchUsers(any(), any(), any()))
        .thenReturn(List.of(user("IS000001", 2001), user("400001", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn("C004");

    ImportResult r = service().preview(pick("IS000001"), null, 101);

    assertEquals(1, r.getSkipped());
    assertTrue(r.getDetails().get("IS000001").contains("정규등록 대상이 아님"), r.getDetails().toString());
  }

  @Test
  void 아무도_고르지_않으면_거부한다() {
    // 이 가져오기는 덮어쓰기라, 대상 없이 실행되면 무엇이 바뀌었는지 알 수 없다
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().importUsers(pick(), null, 101));
    assertTrue(ex.getMessage().contains("선택"), ex.getMessage());
  }

  /** 기관 ↔ BiostarX 사용자그룹 매핑 1건. */
  private static AirPort.model.TbCompany company(int groupId, String code, String name) {
    AirPort.model.TbCompany c = new AirPort.model.TbCompany();
    c.setBiostarGroupId(groupId);
    c.setCompanyCode(code);
    c.setCompanyName(name);
    return c;
  }

  @Test
  void 선택_목록은_등록_여부와_가져오기_가능_여부를_알려준다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any()))
        .thenReturn(List.of(user("400001", 1009), user("400002", 1009), user("IS1", 2001)));
    when(companyMapper.selectBiostarGroupMappings())
        .thenReturn(List.of(company(1009, "C004", "슈프리마")));
    when(personMapper.selectExistingIds(List.of("400001", "400002"))).thenReturn(List.of("400001"));

    List<ImportCandidateResult> list = service().candidates(null, 101);

    assertEquals(2, list.size()); // 정규등록 그룹 밖(IS1)은 목록에 올리지 않는다
    assertTrue(list.get(0).isRegistered()); // 이미 있음 → 갱신 대상
    assertEquals("슈프리마", list.get(0).getCompanyName());
    assertFalse(list.get(1).isRegistered()); // 없음 → 신규
    assertTrue(list.get(0).isImportable());
    // 목록을 그리자고 인원 수만큼 상세를 읽지 않는다
    verify(importAdapter, never()).fetchUser(any(), any(), any(), any());
  }

  @Test
  void 선택_목록은_인원_수만큼_DB_를_치지_않는다() {
    // 수천 명이면 1명당 2질의가 수천 질의가 된다 — 기관 매핑과 등록 여부를 각각 한 번에 읽는다
    configured();
    when(importAdapter.searchUsers(any(), any(), any()))
        .thenReturn(List.of(user("400001", 1009), user("400002", 1009), user("400003", 1009)));
    when(companyMapper.selectBiostarGroupMappings())
        .thenReturn(List.of(company(1009, "C004", "슈프리마")));

    service().candidates(null, 101);

    verify(companyMapper, never()).selectCodeByBiostarGroupId(any());
    verify(personMapper, never()).selectById(anyString());
    verify(companyMapper, org.mockito.Mockito.times(1)).selectBiostarGroupMappings();
    verify(personMapper, org.mockito.Mockito.times(1)).selectExistingIds(any());
  }

  @Test
  void 기관_매핑이_없는_사용자는_목록에_사유와_함께_남는다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("400009", 1009)));
    when(companyMapper.selectBiostarGroupMappings()).thenReturn(List.of()); // 매핑 없음

    List<ImportCandidateResult> list = service().candidates(null, 101);

    assertFalse(list.get(0).isImportable());
    assertTrue(list.get(0).getReason().contains("기관 매핑 없음"), list.get(0).getReason());
  }

  // ── 신규 ────────────────────────────────────────────────

  @Test
  void 매핑된_출입그룹만_가져온다() {
    configured();
    onDevice(full("400002", 1009));
    // 장비는 1,2,99 를 주지만 우리와 매핑된 것은 두 개뿐
    when(acGroupRefMapper.selectIdsByBiostarAcIds(List.of(1, 2, 99))).thenReturn(List.of(3, 4));

    ImportResult r = service().importUsers(pick("400002"), null, 101);

    assertEquals(2, r.getAcGroups());
    verify(acGroupMapper).insertBatch("400002", List.of(3, 4));
  }

  @Test
  void 체크하지_않은_항목은_가져오지_않는다() {
    configured();
    onDevice(full("400002", 1009));
    ImportForm form = pick("400002");
    form.setCards(false);
    form.setFace(false);
    form.setAcGroups(false);

    ImportResult r = service().importUsers(form, null, 101);

    assertEquals(1, r.getImported()); // 인원은 들어온다
    assertEquals(0, r.getCards());
    assertEquals(0, r.getFaces());
    assertEquals(0, r.getAcGroups());
    verify(photoMapper, never()).upsert(anyString(), anyString());
  }

  // ── 이미 있는 인원(장비 기준으로 맞추기) ─────────────────────

  @Test
  void 이미_있는_인원의_카드를_장비_기준으로_맞춘다() {
    // 우리에만 있던 카드는 회수한다 — 장비에서 뗀 카드가 우리에 남으면 발급 현황이 어긋난다
    configured();
    onDevice(detail("400001", 1009, null, List.of("1111114"), List.of()));
    registered("400001");
    AirPort.model.TbCard old = new AirPort.model.TbCard();
    old.setCardId(7);
    old.setBiostarCardValue("9999999"); // 장비에는 없는 카드
    when(cardMapper.selectByPerson("400001")).thenReturn(List.of(old));

    ImportResult r = service().importUsers(pick("400001"), null, 101);

    verify(cardMapper).releaseByPerson("400001"); // 전부 떼고
    verify(cardMapper).insert(any()); // 장비 카드만 다시 붙인다
    assertEquals(1, r.getCards());
    // 사람별 내용은 그 사람 행(비고 열)에 붙는다 — 결과 상자에는 숫자만 둔다
    assertTrue(r.getDetails().get("400001").contains("회수 9999999"), r.getDetails().toString());
  }

  @Test
  void 장비와_같으면_아무것도_바꾸지_않는다() {
    configured();
    onDevice(detail("400001", 1009, null, List.of("1111114"), List.of(1)));
    registered("400001");
    AirPort.model.TbCard same = new AirPort.model.TbCard();
    same.setCardId(7);
    same.setBiostarCardValue("1111114");
    when(cardMapper.selectByPerson("400001")).thenReturn(List.of(same));
    when(acGroupRefMapper.selectIdsByBiostarAcIds(List.of(1))).thenReturn(List.of(3));
    when(acGroupMapper.selectAcGroupIds("400001")).thenReturn(List.of(3));

    ImportResult r = service().importUsers(pick("400001"), null, 101);

    assertEquals(1, r.getUnchanged());
    assertEquals(0, r.getUpdated());
    verify(cardMapper, never()).releaseByPerson(anyString());
    verify(acGroupMapper, never()).deleteByPerson(anyString());
    verify(personMapper, never()).updateFromBiostar(any());
  }

  @Test
  void 유효기간은_분까지만_비교한다() {
    // 조회는 varchar(16)이라 초가 없고 장비는 초까지 준다. 그대로 맞대면 아무리 가져와도 매번 '갱신'이 된다.
    assertTrue(PersonImportSyncService.sameMinute("2026-08-04T09:37", "2026-08-04T09:37:00"));
    assertFalse(PersonImportSyncService.sameMinute("2026-08-04T09:37", "2026-08-04T09:38:00"));
    assertTrue(PersonImportSyncService.sameMinute(null, null));
    assertFalse(PersonImportSyncService.sameMinute(null, "2026-08-04T09:37:00"));
  }

  @Test
  void 우리_화면에서만_채운_값은_갱신이_덮어쓰지_않는다() {
    // 생년월일·신원조회·인원상태는 장비에 없다 — update 를 쓰면 함께 비워진다
    configured();
    onDevice(detail("400001", 1009, null, List.of(), List.of()));
    TbPerson cur = registered("400001");
    cur.setCompanyCode("C999"); // 기관이 달라져 갱신 대상이 된다

    ImportResult r = service().importUsers(pick("400001"), null, 101);

    assertEquals(1, r.getUpdated());
    verify(personMapper).updateFromBiostar(any());
    verify(personMapper, never()).update(any()); // 전체 덮어쓰기 경로를 타지 않는다
    verify(personMapper, never()).insert(any());
  }

  @Test
  void 카드만_바뀐_사람도_갱신으로_집계된다() {
    // 항목별 분기에서 세면 카드만 바뀐 사람이 신규도 갱신도 변경없음도 아닌 채로 집계에서 사라진다
    configured();
    onDevice(detail("400001", 1009, null, List.of("1111114"), List.of()));
    registered("400001"); // 인원정보는 장비와 같다

    ImportResult r = service().preview(pick("400001"), null, 101);

    assertEquals(1, r.getUpdated());
    assertEquals(0, r.getUnchanged());
    assertEquals(List.of("400001"), r.getUpdatedUserIds());
  }

  @Test
  void 미리보기는_신규와_갱신을_사람별로_갈라_준다() {
    // 건수만으로는 "갱신 3명" 이 누구인지 알 수 없다 — 화면이 이 목록으로 대상자를 찾는다
    configured();
    when(importAdapter.searchUsers(any(), any(), any()))
        .thenReturn(List.of(user("400001", 1009), user("400009", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn("C004");
    when(importAdapter.fetchUser(any(), any(), any(), any()))
        .thenAnswer(inv -> detail(inv.getArgument(3), 1009, null, List.of(), List.of()));
    registered("400001"); // 이미 있고 장비와 같다 → 변경없음
    // 400009 는 우리 DB 에 없다 → 신규

    ImportResult r = service().preview(pick("400001", "400009"), null, 101);

    assertEquals(List.of("400009"), r.getNewUserIds());
    assertEquals(List.of("400001"), r.getUnchangedUserIds());
    assertTrue(r.getUpdatedUserIds().isEmpty(), r.getUpdatedUserIds().toString());
  }

  // ── 얼굴 규칙 ────────────────────────────────────────────

  @Test
  void 장비에_얼굴이_없으면_우리_등록사진도_지운다() {
    configured();
    onDevice(detail("400001", 1009, null, List.of(), List.of()));
    registered("400001");
    when(photoMapper.selectPhoto("400001")).thenReturn("OURPHOTO");

    ImportResult r = service().importUsers(pick("400001"), null, 101);

    verify(photoMapper).deleteByPerson("400001");
    assertEquals(1, r.getFacesRemoved());
  }

  @Test
  void 양쪽에_얼굴이_있으면_비교하지_않고_그대로_둔다() {
    // 사진은 바이너리라 같은 사람이라도 값이 달라 비교가 무의미하다
    configured();
    onDevice(detail("400001", 1009, "BASE64PHOTO", List.of(), List.of()));
    registered("400001");
    when(photoMapper.selectPhoto("400001")).thenReturn("OURPHOTO");

    ImportResult r = service().importUsers(pick("400001"), null, 101);

    verify(photoMapper, never()).upsert(anyString(), anyString());
    verify(photoMapper, never()).deleteByPerson(anyString());
    assertEquals(0, r.getFaces());
    assertEquals(0, r.getFacesRemoved());
  }

  @Test
  void 장비에만_얼굴이_있으면_가져온다() {
    configured();
    onDevice(detail("400001", 1009, "BASE64PHOTO", List.of(), List.of()));
    registered("400001");
    when(photoMapper.selectPhoto("400001")).thenReturn(null);

    ImportResult r = service().importUsers(pick("400001"), null, 101);

    verify(photoMapper).upsert("400001", "BASE64PHOTO");
    assertEquals(1, r.getFaces());
  }

  // ── 미리보기·설정 ─────────────────────────────────────────

  @Test
  void 미리보기는_DB_를_건드리지_않는다() {
    configured();
    onDevice(full("400002", 1009));

    ImportResult r = service().preview(pick("400002"), null, 101);

    assertTrue(r.isPreview());
    assertEquals(1, r.getTarget());
    assertEquals(1, r.getImported()); // 무엇이 일어날지는 센다
    verify(personMapper, never()).insert(any());
    verify(photoMapper, never()).upsert(anyString(), anyString());
    verify(cardMapper, never()).releaseByPerson(anyString());
    assertFalse(r.getDetails().isEmpty()); // 무엇이 바뀌는지 알려 준다
  }

  @Test
  void 정규등록_그룹이_지정되지_않으면_거부한다() {
    TbSystem cfg = new TbSystem();
    cfg.setBiostarIp("10.0.0.1");
    when(systemMapper.selectOne()).thenReturn(cfg);
    when(commonMapper.selectOne("PTD", "PTD01")).thenReturn(null);

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service().preview(pick("400001"), null, 101));
    assertTrue(ex.getMessage().contains("PTD01"), ex.getMessage());
  }

  @Test
  void 접속정보가_없으면_거부한다() {
    when(systemMapper.selectOne()).thenReturn(null);

    assertThrows(BusinessException.class, () -> service().importUsers(pick("400001"), null, 101));
  }
}
