package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.TestKeys;
import AirPort.adapter.biostar.BiostarUserRequest;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.model.PersonForm;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 정규인원 엑셀 [기존 인원 갱신] — <b>엑셀에 값이 있는 열만</b> 바꾸고 빈 칸은 저장된 값 그대로 둔다.
 *
 * <p>"지운다"로 읽으면 연락처를 비워 둔 명단 한 장이 전 직원의 연락처를 날린다. 사진·출입그룹·카드는 엑셀에 없으니 손대지 않는다(전용 UPDATE).
 */
class PersonImportUpdateTest {

  @BeforeAll
  static void initKey() {
    TestKeys.init(); // 저장된 성명·생년월일·연락처는 ARIA 암호문이다
  }

  private final TbPersonMapper personMapper = mock(TbPersonMapper.class);
  private final TbPersonPhotoMapper photoMapper = mock(TbPersonPhotoMapper.class);
  private final TbPersonAcGroupMapper acGroupMapper = mock(TbPersonAcGroupMapper.class);
  private final PersonBiostarService personBiostar = mock(PersonBiostarService.class);
  private final PersonService personService = mock(PersonService.class);
  private final AuditService auditService = mock(AuditService.class);
  private final PersonImportUpdateService svc =
      new PersonImportUpdateService(
          personService, personMapper, photoMapper, acGroupMapper, personBiostar, auditService);

  private static TbPerson stored() {
    TbPerson p = new TbPerson();
    p.setPersonId("400001");
    p.setPersonName(ARIAUtil.ariaEncrypt("홍길동"));
    p.setBirthDate(ARIAUtil.ariaEncrypt("1990-01-01"));
    p.setPersonPhone(ARIAUtil.ariaEncrypt("010-1111-2222"));
    p.setCompanyCode("C001");
    p.setTitleCode("UT01");
    p.setStatusCode("01");
    p.setAccessStartDt("2026-01-01T09:00:00");
    p.setAccessEndDt("2026-12-31T18:00:00");
    p.setMainTask("출입관리");
    p.setRemark("메모");
    p.setDelYn("N");
    return p;
  }

  @Test
  void 엑셀에_적은_열만_바뀌고_빈_칸은_저장된_값_그대로다() {
    when(personMapper.selectById("400001")).thenReturn(stored());
    when(personService.toRow(any())).thenReturn(new TbPerson());
    when(personBiostar.requestOf(any(), any(), any(), any()))
        .thenReturn(mock(BiostarUserRequest.class));
    when(personBiostar.syncRequests(anyString(), any(), any())).thenReturn(null);

    PersonForm excel = new PersonForm(); // ID·성명·생년월일만 적은 행
    excel.setPersonId("400001");
    excel.setPersonName("홍길순");
    excel.setBirthDate("1991-02-02");
    svc.update(excel, null, 201);

    ArgumentCaptor<PersonForm> merged = ArgumentCaptor.forClass(PersonForm.class);
    verify(personService).validate(merged.capture(), any());
    PersonForm m = merged.getValue();
    assertEquals("홍길순", m.getPersonName());
    assertEquals("1991-02-02", m.getBirthDate());
    assertEquals("010-1111-2222", m.getPersonPhone(), "비운 연락처는 그대로");
    assertEquals("C001", m.getCompanyCode());
    assertEquals("UT01", m.getTitleCode());
    assertEquals("01", m.getStatusCode(), "비운 상태에 기본값(신규)을 넣으면 안 된다");
    assertEquals("2026-01-01T09:00:00", m.getAccessStartDt(), "비운 기간에 오늘~2037 을 넣으면 안 된다");
    assertEquals("출입관리", m.getMainTask());
    assertEquals("메모", m.getRemark());
    verify(personMapper).updateBasics(any()); // 엑셀 열만 바꾸는 UPDATE — 사진·권한·근거문서는 손대지 않는다
    verify(personMapper, never()).update(any());
    verify(photoMapper, never()).deleteByPerson(anyString());
    verify(auditService).log(any(), eq(AuditService.UPDATE), eq(201), anyString());
  }

  @Test
  void 없거나_빈_인원ID_는_거절하고_삭제된_인원도_갱신하지_않는다() {
    when(personMapper.selectById("X")).thenReturn(null);
    PersonForm excel = new PersonForm();
    excel.setPersonId("X");
    assertThrows(BusinessException.class, () -> svc.update(excel, null, 201));

    PersonForm noId = new PersonForm(); // 갱신 모드에서 ID 를 비우면 채번하지 않는다 — 대상이 없다
    assertThrows(BusinessException.class, () -> svc.update(noId, null, 201));

    TbPerson dead = stored();
    dead.setDelYn("Y");
    when(personMapper.selectById("400001")).thenReturn(dead);
    excel.setPersonId("400001");
    assertThrows(BusinessException.class, () -> svc.update(excel, null, 201));
    verify(personMapper, never()).updateBasics(any());
  }

  @Test
  void BiostarX_동기화가_실패하면_갱신을_취소한다() {
    when(personMapper.selectById("400001")).thenReturn(stored());
    when(personService.toRow(any())).thenReturn(new TbPerson());
    when(personBiostar.requestOf(any(), any(), any(), any()))
        .thenReturn(mock(BiostarUserRequest.class));
    when(personBiostar.syncRequests(anyString(), any(), any())).thenReturn("연결 실패");

    PersonForm excel = new PersonForm();
    excel.setPersonId("400001");
    excel.setPersonPhone("010-9999-8888");
    BusinessException ex =
        assertThrows(BusinessException.class, () -> svc.update(excel, null, 201));
    assertTrue(ex.getMessage().contains("연결 실패"), ex.getMessage());
    verify(auditService, never()).log(any(), anyString(), any(), anyString());
  }

  @Test
  void 비활성_상태로_바꾸면_얼굴을_지우고_장비에도_얼굴_없이_보낸다() {
    when(personMapper.selectById("400001")).thenReturn(stored());
    when(personService.toRow(any())).thenReturn(new TbPerson());
    when(photoMapper.selectPhoto("400001")).thenReturn("PHOTO");
    when(personBiostar.isDisabled("03")).thenReturn(true);
    when(personBiostar.requestOf(any(), any(), any(), any()))
        .thenReturn(mock(BiostarUserRequest.class));
    when(personBiostar.syncRequests(anyString(), any(), any())).thenReturn(null);

    PersonForm excel = new PersonForm();
    excel.setPersonId("400001");
    excel.setStatusCode("03");
    svc.update(excel, null, 201);

    verify(photoMapper).deleteByPerson("400001");
    ArgumentCaptor<String> photo = ArgumentCaptor.forClass(String.class);
    verify(personBiostar, org.mockito.Mockito.times(2))
        .requestOf(any(), photo.capture(), any(), any());
    assertEquals("PHOTO", photo.getAllValues().get(0), "변경 전에는 얼굴이 있고");
    assertNull(photo.getAllValues().get(1), "변경 후에는 없다");
    List<String> all = photo.getAllValues();
    assertEquals(2, all.size());
  }
}
