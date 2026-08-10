package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.adapter.BiostarImportAdapter;
import AirPort.adapter.BiostarUserDetail;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.ImportResult;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.service.AuditService;
import AirPort.service.MenuAuthService;
import AirPort.service.PersonImportBiostarService;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * BiostarX 정규인원 가져오기 검증 — <b>단방향</b>이 핵심이다. 장비에 쓰는 순간 현장에 이미 올라간 얼굴·카드·출입그룹을 덮어쓴다.
 *
 * <p>그 다음은 대상 선별이다: 기관이 매핑된 인원만, 이미 있는 인원은 건너뛰고, 출입그룹은 매핑된 것만.
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
    return new PersonImportBiostarService(
        importAdapter,
        systemMapper,
        personMapper,
        photoMapper,
        acGroupMapper,
        companyMapper,
        acGroupRefMapper,
        cardMapper,
        commonMapper,
        menuAuthService,
        auditService);
  }

  private void configured() {
    TbSystem cfg = new TbSystem();
    cfg.setBiostarIp("10.0.0.1");
    cfg.setBiostarId("admin");
    when(systemMapper.selectOne()).thenReturn(cfg);
  }

  private static BiostarUserDetail user(String id, Integer groupId) {
    return new BiostarUserDetail(
        id, "홍길동", null, null, groupId, null, null, null, List.of(), List.of());
  }

  private static BiostarUserDetail full(String id, Integer groupId) {
    return new BiostarUserDetail(
        id,
        "홍길동",
        "01011112222",
        "대리",
        groupId,
        "2026-08-04T09:37:00.00Z",
        "2037-12-31T23:59:00.00Z",
        "BASE64PHOTO",
        List.of("1111114"),
        List.of(1, 2, 99));
  }

  @Test
  void 기관이_매핑되지_않은_인원은_가져오지_않는다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("1", 1)));
    when(companyMapper.selectCodeByBiostarGroupId(1)).thenReturn(null);

    ImportResult r = service().importUsers(true, true, true, null, 101);

    assertEquals(0, r.getTarget());
    assertEquals(1, r.getSkipped());
    assertTrue(r.getSkippedReasons().get(0).contains("기관 매핑 없음"), r.getSkippedReasons().get(0));
    verify(personMapper, never()).insert(any());
  }

  @Test
  void 이미_등록된_인원은_건너뛴다() {
    // 덮어쓰면 우리 화면에서 채운 생년월일·신원조회 같은 값이 날아간다
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("400001", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn("C004");
    TbPerson existing = new TbPerson();
    existing.setPersonId("400001");
    existing.setDelYn("N");
    when(personMapper.selectById("400001")).thenReturn(existing);

    ImportResult r = service().importUsers(true, true, true, null, 101);

    assertEquals(1, r.getTarget());
    assertEquals(0, r.getImported());
    assertTrue(r.getSkippedReasons().get(0).contains("이미 등록된"), r.getSkippedReasons().get(0));
  }

  @Test
  void 매핑된_출입그룹만_가져온다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("400002", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn("C004");
    when(importAdapter.fetchUser(any(), any(), any(), any())).thenReturn(full("400002", 1009));
    // 장비는 1,2,99 를 주지만 우리와 매핑된 것은 두 개뿐
    when(acGroupRefMapper.selectIdsByBiostarAcIds(List.of(1, 2, 99))).thenReturn(List.of(3, 4));

    ImportResult r = service().importUsers(false, false, true, null, 101);

    assertEquals(2, r.getAcGroups());
    verify(acGroupMapper).insertBatch("400002", List.of(3, 4));
  }

  @Test
  void 체크하지_않은_항목은_가져오지_않는다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("400002", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn("C004");
    when(importAdapter.fetchUser(any(), any(), any(), any())).thenReturn(full("400002", 1009));

    ImportResult r = service().importUsers(false, false, false, null, 101);

    assertEquals(1, r.getImported()); // 인원은 들어온다
    assertEquals(0, r.getCards());
    assertEquals(0, r.getFaces());
    assertEquals(0, r.getAcGroups());
    verify(photoMapper, never()).upsert(anyString(), anyString());
  }

  @Test
  void 미리보기는_DB_를_건드리지_않는다() {
    configured();
    when(importAdapter.searchUsers(any(), any(), any())).thenReturn(List.of(user("400002", 1009)));
    when(companyMapper.selectCodeByBiostarGroupId(1009)).thenReturn("C004");

    ImportResult r = service().preview(null, 101);

    assertTrue(r.isPreview());
    assertEquals(1, r.getTarget());
    verify(personMapper, never()).insert(any());
    verify(importAdapter, never()).fetchUser(any(), any(), any(), any());
  }

  @Test
  void 접속정보가_없으면_거부한다() {
    when(systemMapper.selectOne()).thenReturn(null);

    assertThrows(BusinessException.class, () -> service().importUsers(true, true, true, null, 101));
  }
}
