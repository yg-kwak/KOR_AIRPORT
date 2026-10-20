package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.adapter.biostar.BiostarDoorAdapter;
import AirPort.adapter.biostar.BiostarDoorAuth;
import AirPort.adapter.biostar.BiostarDoorUser;
import AirPort.adapter.biostar.BiostarResult;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.EscortForm;
import AirPort.model.TbSystem;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 에스코트 사용자 추가 — BiostarX 의 PUT 은 목록을 통째로 바꾸므로, <b>지금 등록된 사람을 지우지 않는 것</b>이 이 화면의 핵심 규칙이다. */
class EscortServiceTest {

  private final TbSystemMapper systemMapper = mock(TbSystemMapper.class);
  private final BiostarDoorAdapter doorAdapter = mock(BiostarDoorAdapter.class);
  private final AuditService auditService = mock(AuditService.class);
  private final MenuAuthService menuAuth = mock(MenuAuthService.class);
  private final EscortService service =
      new EscortService(
          systemMapper,
          doorAdapter,
          mock(AirPort.adapter.biostar.BiostarAdapter.class),
          menuAuth,
          auditService);

  EscortServiceTest() {
    TbSystem cfg = new TbSystem();
    cfg.setBiostarIp("10.0.0.1");
    cfg.setBiostarId("admin");
    when(systemMapper.selectOne()).thenReturn(cfg);
  }

  private void current(boolean enabled, String schedule, String... userIds) {
    List<BiostarDoorUser> users =
        java.util.Arrays.stream(userIds).map(id -> new BiostarDoorUser(id, "이름", null)).toList();
    when(doorAdapter.authControl(anyString(), anyString(), anyString(), eq(15L)))
        .thenReturn(new BiostarDoorAuth(15, "CS_40", enabled, schedule, "Always", users));
  }

  private static EscortForm form(String... ids) {
    EscortForm f = new EscortForm();
    f.setDoorId(15L);
    f.setUserIds(List.of(ids));
    return f;
  }

  @Test
  void 지금_등록된_사람을_남기고_고른_사람을_더해_보낸다() {
    current(true, "1", "1", "202606100001");
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.ok());

    String msg = service.update(form(" 202606110002 ", "202606100001", ""), null, 504);

    verify(doorAdapter)
        .updateAuthUsers(
            "10.0.0.1",
            "admin",
            "",
            15L,
            "1",
            List.of("1", "202606100001", "202606110002")); // 기존 둘 + 새로 하나, 이미 있던 사람은 한 번만
    assertEquals("1명 추가했습니다.", msg);
    verify(auditService)
        .log(
            eq(null),
            eq(AuditService.UPDATE),
            eq(504),
            org.mockito.ArgumentMatchers.contains("추가 1명 · 제외 0명 → 반영 후 3명"));
  }

  @Test
  void 일정이_비어_있으면_기본_일정으로_보낸다() {
    current(true, null);
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.ok());
    service.update(form("7"), null, 504);
    verify(doorAdapter).updateAuthUsers(any(), any(), any(), eq(15L), eq("1"), eq(List.of("7")));
  }

  @Test
  void 에스코트가_꺼진_문에는_보내지_않는다() {
    current(false, "1");
    BusinessException e =
        assertThrows(BusinessException.class, () -> service.update(form("7"), null, 504));
    assertTrue(e.getMessage().contains("꺼져"), e.getMessage());
    verify(doorAdapter, never()).updateAuthUsers(any(), any(), any(), anyLong(), any(), any());
  }

  @Test
  void 고른_사람이_모두_이미_있거나_아무도_없으면_보내지_않는다() {
    current(true, "1", "1");
    assertThrows(BusinessException.class, () -> service.update(form("1"), null, 504));
    assertThrows(BusinessException.class, () -> service.update(form(" ", ""), null, 504));
    verify(doorAdapter, never()).updateAuthUsers(any(), any(), any(), anyLong(), any(), any());
  }

  private static EscortForm change(List<String> add, List<String> remove) {
    EscortForm f = new EscortForm();
    f.setDoorId(15L);
    f.setUserIds(add);
    f.setRemoveIds(remove);
    return f;
  }

  @Test
  void 체크를_푼_등록자는_빼고_나머지는_지킨다() {
    current(true, "1", "1", "202606100001", "202606110001");
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.ok());

    String msg =
        service.update(change(List.of("202606110009"), List.of("202606100001")), null, 504);

    verify(doorAdapter)
        .updateAuthUsers(
            any(),
            any(),
            any(),
            eq(15L),
            eq("1"),
            eq(List.of("1", "202606110001", "202606110009")));
    verify(menuAuth).requireDelete(null, 504); // 빼는 사람이 있으면 삭제 권한
    assertEquals("1명 추가 · 1명 제외했습니다.", msg);
  }

  @Test
  void 빼기만_해도_되고_추가만이면_삭제_권한을_묻지_않는다() {
    current(true, "1", "1", "202606100001");
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.ok());
    assertEquals(
        "2명 제외했습니다.", service.update(change(List.of(), List.of("1", "202606100001")), null, 504));
    verify(doorAdapter).updateAuthUsers(any(), any(), any(), eq(15L), eq("1"), eq(List.of()));

    current(true, "1", "1");
    service.update(change(List.of("7"), null), null, 504);
    verify(menuAuth, org.mockito.Mockito.times(1)).requireDelete(null, 504); // 앞의 제외 한 번뿐
  }

  @Test
  void 이미_빠진_사람을_빼거나_같은_사람을_넣고_빼면_바뀐_것이_없다() {
    current(true, "1", "1");
    // 화면을 연 뒤 BiostarX 에서 이미 빠진 사람 — 셀 것이 없다
    assertThrows(
        BusinessException.class, () -> service.update(change(List.of(), List.of("99")), null, 504));
    // 넣으면서 빼면 넣는 쪽을 따른다 — 이미 있는 사람이라 바뀐 것이 없다
    assertThrows(
        BusinessException.class,
        () -> service.update(change(List.of("1"), List.of("1")), null, 504));
    verify(doorAdapter, never()).updateAuthUsers(any(), any(), any(), anyLong(), any(), any());
  }

  @Test
  void 출입문_하나에_300명을_넘기면_보내지_않는다() {
    current(true, "1", "a", "b");
    List<String> many = java.util.stream.IntStream.range(0, 299).mapToObj(i -> "u" + i).toList();
    BusinessException e =
        assertThrows(BusinessException.class, () -> service.update(change(many, null), null, 504));
    assertTrue(
        e.getMessage().contains("300명까지") && e.getMessage().contains("301명"), e.getMessage());
    verify(doorAdapter, never()).updateAuthUsers(any(), any(), any(), anyLong(), any(), any());

    // 빼면서 넣어 300명이 되면 된다
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.ok());
    service.update(change(many, List.of("a")), null, 504);
  }

  @Test
  void 수백_명을_바꿔도_감사_문구는_짧다() {
    current(true, "1", "a");
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.ok());
    List<String> many =
        java.util.stream.IntStream.range(0, 288).mapToObj(i -> "2026061100" + i).toList();
    service.update(change(many, null), null, 504);
    org.mockito.ArgumentCaptor<String> detail = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(auditService).log(eq(null), eq(AuditService.UPDATE), eq(504), detail.capture());
    // 사람마다 ID 를 늘어놓으면 1000자 칸을 넘어 저장 자체가 실패했다(현장 오류)
    assertEquals("에스코트 인원 변경: 문 15 CS_40 — 추가 288명 · 제외 0명 → 반영 후 289명", detail.getValue());
  }

  @Test
  void 장비가_거절하면_실패를_감사에_남기고_알린다() {
    current(true, "1", "1");
    when(doorAdapter.updateAuthUsers(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(BiostarResult.fail("Invalid user (code 30)"));
    BusinessException e =
        assertThrows(BusinessException.class, () -> service.update(form("9"), null, 504));
    assertTrue(e.getMessage().contains("Invalid user"), e.getMessage());
    verify(auditService)
        .logAlways(
            eq(null),
            eq(AuditService.UPDATE),
            eq(504),
            org.mockito.ArgumentMatchers.contains("실패"));
  }

  @Test
  void 추가할_수_있는_사용자는_한_쪽_200명까지로_묻는다() {
    when(doorAdapter.accessibleUsers(
            any(), any(), any(), anyLong(), anyInt(), anyInt(), any(), any()))
        .thenReturn(new AirPort.adapter.biostar.BiostarDoorUserPage(0, false, List.of()));
    service.accessibleUsers(15, 3, 5000, "  김 ", 1009L, null, 504);
    verify(doorAdapter).accessibleUsers("10.0.0.1", "admin", "", 15L, 400, 200, "김", 1009L);
  }
}
