package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.TestKeys;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbVisit;
import AirPort.model.VisitForm;
import AirPort.model.VisitManagerForm;
import AirPort.model.VisitorForm;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 키오스크 [등록 수정] — 무인증 화면에서 남의 신청을 보거나 고칠 수 없어야 한다.
 *
 * <p>인솔자 인원ID 하나는 명단을 훑어 맞힐 수 있다. 그래서 <b>성명(암호문)까지 둘 다</b> 맞는 신청만, 그것도 <b>임시·신청 상태</b>만 연다. 이 판정은
 * 목록·상세·수정 세 요청이 모두 같은 SQL 을 지난다 — 목록만 거르면 방문번호를 바꿔 넣는 것으로 뚫린다.
 */
class KioskEditRuleTest {

  private static final String XML = "mapper/TbVisitMapper.xml";

  @BeforeAll
  static void initKey() {
    TestKeys.init(); // 성명은 ARIA 암호문끼리 비교한다
  }

  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final VisitRosterService roster = mock(VisitRosterService.class);
  private final VisitService visitService = mock(VisitService.class);
  private final KioskVisitService svc =
      new KioskVisitService(
          visitService,
          roster,
          visitMapper,
          mock(AcGroupService.class),
          mock(TbCommonMapper.class),
          personMapper,
          mock(TbCarMapper.class),
          mock(AuditService.class));

  @Test
  void 인솔자_판정은_인원ID와_성명_암호문_그리고_임시_신청_상태를_모두_본다() throws IOException {
    String sql = select("selectAppliedByManager");

    assertTrue(sql.contains("vm.person_id = #{managerId}"), "인원ID 조건이 없다:\n" + sql);
    assertTrue(
        sql.contains("pm.person_name = #{managerNameEnc}"),
        "성명 조건이 없다 — 인원ID 만으로 남의 신청이 열린다:\n" + sql);
    assertTrue(sql.contains("v.visit_type = #{visitType}"), "임시 방문으로 좁히지 않는다:\n" + sql);
    assertTrue(
        sql.contains("v.status_code = #{statusCode}"),
        "신청 상태로 좁히지 않는다 — 카드가 붙은 뒤에도 방문객이 고칠 수 있다:\n" + sql);
    assertTrue(sql.contains("v.del_yn = 'N'"), sql);
    assertTrue(sql.contains("v.visit_no = #{visitNo}"), "방문번호로 다시 좁히는 분기가 없다(상세·수정용):\n" + sql);
  }

  @Test
  void 내_신청이_아니면_상세도_수정도_거부하고_아무것도_쓰지_않는다() {
    when(visitMapper.selectAppliedByManager(
            anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(List.of());

    BusinessException d = assertThrows(BusinessException.class, () -> svc.detail(7, "REG1", "홍길동"));
    assertEquals(ErrorCode.NOT_FOUND, d.getErrorCode());
    verify(visitService, never()).detailOf(anyInt());

    VisitForm form = form(7);
    BusinessException u =
        assertThrows(BusinessException.class, () -> svc.update(form, "REG1", "홍길동"));
    assertEquals(ErrorCode.NOT_FOUND, u.getErrorCode());
    verify(visitMapper, never()).update(any());
    verify(visitMapper, never()).deletePersons(anyInt());
  }

  @Test
  void 판정은_상태와_유형을_서버_상수로_넘기고_성명은_암호문으로_넘긴다() {
    when(visitMapper.selectAppliedByManager(
            anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(List.of());

    svc.applied(" REG1 ", " 홍길동 ");

    ArgumentCaptor<String> enc = ArgumentCaptor.forClass(String.class);
    verify(visitMapper)
        .selectAppliedByManager(
            eq("REG1"),
            enc.capture(),
            isNull(),
            eq(VisitService.VISIT_TYPE),
            eq(VisitService.DEFAULT_STATUS));
    assertEquals(VisitService.encryptOrNull("홍길동"), enc.getValue());
    assertTrue(!"홍길동".equals(enc.getValue()), "성명이 평문으로 나갔다");
  }

  @Test
  void 명단_밖_personId_는_신규로_바꿔_남의_인원을_덮어쓰지_못하게_한다() {
    TbVisit mine = new TbVisit();
    mine.setVisitNo(7);
    when(visitMapper.selectAppliedByManager(
            anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(List.of(mine));
    when(visitService.toRow(any())).thenReturn(new TbVisit());
    when(visitMapper.selectPersonIds(7)).thenReturn(List.of("IS000001"));
    when(roster.upsertVisitor(any(), any())).thenReturn("IS000009");

    VisitForm form = form(7);
    VisitorForm keep = visitor("IS000001", "김방문");
    VisitorForm foreign = visitor("REG9999", "정규인원"); // 이 방문의 명단이 아닌 ID
    form.setVisitors(List.of(keep, foreign));

    svc.update(form, "REG1", "홍길동");

    assertEquals("IS000001", keep.getPersonId(), "명단에 있는 사람은 그대로 갱신한다");
    assertNull(foreign.getPersonId(), "명단 밖 ID 는 신규로 취급해야 남의 인원을 덮어쓰지 못한다");
    verify(personMapper, never()).softDelete("REG9999");
    verify(personMapper, never()).softDelete("IS000001");
  }

  @Test
  void 수정해도_임시_신청_상태는_서버가_지킨다() {
    TbVisit mine = new TbVisit();
    mine.setVisitNo(7);
    when(visitMapper.selectAppliedByManager(
            anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(List.of(mine));
    when(visitService.toRow(any())).thenReturn(new TbVisit());
    when(roster.upsertVisitor(any(), any())).thenReturn("IS000001");

    VisitForm form = form(7);
    form.setStatusCode("VS03"); // 화면이 무엇을 보내든
    form.setVisitType("PT01");
    svc.update(form, "REG1", "홍길동");

    ArgumentCaptor<TbVisit> row = ArgumentCaptor.forClass(TbVisit.class);
    verify(visitMapper).update(row.capture());
    assertEquals(7, row.getValue().getVisitNo());
    assertEquals(VisitService.VISIT_TYPE, row.getValue().getVisitType());
    assertEquals(VisitService.DEFAULT_STATUS, row.getValue().getStatusCode());
  }

  private static VisitForm form(int visitNo) {
    VisitForm f = new VisitForm();
    f.setVisitNo(visitNo);
    f.setVisitKind(AirPort.common.VisitKinds.PERSON);
    f.setWorkStartDt("2026-09-15T09:00");
    f.setWorkEndDt("2026-09-15T18:00");
    f.setWorkPurpose("점검");
    VisitManagerForm m = new VisitManagerForm();
    m.setPersonId("REG1");
    m.setPhone("010-0000-0000");
    f.setManagers(List.of(m));
    f.setAcGroupIds(List.of(1));
    f.setVisitors(List.of(visitor(null, "김방문")));
    return f;
  }

  private static VisitorForm visitor(String personId, String name) {
    VisitorForm v = new VisitorForm();
    v.setPersonId(personId);
    v.setPersonName(name);
    v.setBirthDate("1990-01-01");
    v.setAffiliation("㈜시험");
    return v;
  }

  private String select(String id) throws IOException {
    String xml;
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(XML)) {
      if (in == null) {
        throw new IOException(XML + " 을 찾을 수 없다");
      }
      xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher m =
        Pattern.compile("<select id=\"" + id + "\".*?</select>", Pattern.DOTALL).matcher(xml);
    assertTrue(m.find(), id + " 를 찾지 못했다");
    return m.group();
  }
}
