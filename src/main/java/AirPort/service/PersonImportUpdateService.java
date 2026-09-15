package AirPort.service;

import AirPort.adapter.biostar.BiostarUserRequest;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.model.PersonForm;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정규인원 엑셀 <b>갱신</b> — [기존 인원 갱신] 을 켜고 올린 엑셀의 행이 이미 있는 인원ID 면 여기로 온다.
 *
 * <p>{@link PersonService#update} 를 그대로 쓰지 않는 이유: 그쪽은 화면 폼 전체를 받아 <b>사진·출입그룹·카드·근거문서까지 폼대로 다시
 * 놓는다</b> (폼에 없으면 지운다). 엑셀에는 그 칸이 없으므로 그 길로 보내면 얼굴과 권한이 사라진다. 여기서는 <b>엑셀의 11개 열만</b> 바꾸고 나머지는 손대지
 * 않는다.
 *
 * <p><b>빈 칸은 그대로 둔다</b> — ID·성명·생년월일만 적고 나머지를 비우면 그 둘만 바뀐다. "지운다"로 읽으면 연락처 칸을 비워 둔 명단 한 장이 전 직원의
 * 연락처를 날린다. 값을 지우려면 화면에서 한다.
 *
 * <p>BiostarX 는 화면 수정과 같은 길(변경 전·후 비교 전송)로 맞춘다 — 성명·연락처·상태·기간·직위가 장비에 나가는 값이다. 상태가 비활성으로 바뀌면 화면 수정과
 * 같이 얼굴을 지운다.
 */
@Service
public class PersonImportUpdateService {

  private final PersonService personService;
  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final PersonBiostarService personBiostar;
  private final AuditService auditService;

  public PersonImportUpdateService(
      PersonService personService,
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      PersonBiostarService personBiostar,
      AuditService auditService) {
    this.personService = personService;
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.personBiostar = personBiostar;
    this.auditService = auditService;
  }

  /** 엑셀 한 행으로 기존 인원을 갱신한다 — 행 단위 트랜잭션(호출자가 행마다 부른다). 없는 인원ID 면 거절. */
  @Transactional
  public void update(PersonForm excel, TbLoginUser actor, Integer menuId) {
    TbPerson existing = personMapper.selectById(excel.getPersonId());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "없는 인원ID 입니다 — 갱신할 인원이 없습니다.");
    }
    TbPerson plain = decrypted(existing);
    String photo = photoMapper.selectPhoto(plain.getPersonId());
    List<Integer> acIds = acGroupMapper.selectBiostarAcIds(plain.getPersonId());
    List<String> acNames = acGroupMapper.selectAcGroupNames(plain.getPersonId());
    BiostarUserRequest before = personBiostar.requestOf(plain, photo, acIds, acNames);

    PersonForm merged = merge(plain, excel); // 빈 칸은 저장된 값
    personService.validate(merged, existing); // 화면 수정과 같은 검증(코드는 바뀐 항목만)

    personMapper.updateBasics(personService.toRow(merged));
    // 비활성으로 바뀌면 얼굴을 지운다 — 출입을 막아 놓고 생체정보만 남기지 않는다(화면 수정과 같은 규칙)
    boolean disabled = personBiostar.isDisabled(merged.getStatusCode());
    if (disabled) {
      photoMapper.deleteByPerson(merged.getPersonId());
    }
    TbPerson afterPlain = applied(plain, merged);
    BiostarUserRequest after =
        personBiostar.requestOf(afterPlain, disabled ? null : photo, acIds, acNames);
    String fail = personBiostar.syncRequests(merged.getCompanyCode(), before, after);
    if (fail != null) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 동기화 실패로 갱신이 취소되었습니다. 사유: " + fail);
    }
    auditService.log(actor, AuditService.UPDATE, menuId, "정규인원 엑셀 갱신: " + merged.getPersonId());
  }

  /** 엑셀에 값이 있는 열만 덮는다 — 나머지 열은 저장된 값 그대로. */
  private static PersonForm merge(TbPerson plain, PersonForm excel) {
    PersonForm f = new PersonForm();
    f.setPersonId(plain.getPersonId());
    f.setCompanyCode(pick(excel.getCompanyCode(), plain.getCompanyCode()));
    f.setPersonName(pick(excel.getPersonName(), plain.getPersonName()));
    f.setBirthDate(pick(excel.getBirthDate(), plain.getBirthDate()));
    f.setPersonPhone(pick(excel.getPersonPhone(), plain.getPersonPhone()));
    f.setTitleCode(pick(excel.getTitleCode(), plain.getTitleCode()));
    f.setStatusCode(pick(excel.getStatusCode(), plain.getStatusCode()));
    f.setAccessStartDt(pick(excel.getAccessStartDt(), plain.getAccessStartDt()));
    f.setAccessEndDt(pick(excel.getAccessEndDt(), plain.getAccessEndDt()));
    f.setMainTask(pick(excel.getMainTask(), plain.getMainTask()));
    f.setRemark(pick(excel.getRemark(), plain.getRemark()));
    return f;
  }

  /** 갱신 뒤의 평문 행 — BiostarX 변경 후 값을 만들 때 쓴다(엑셀 열 밖은 저장된 값). */
  private static TbPerson applied(TbPerson plain, PersonForm merged) {
    TbPerson p = new TbPerson();
    p.setPersonId(plain.getPersonId());
    p.setPersonName(merged.getPersonName());
    p.setPersonPhone(merged.getPersonPhone());
    p.setCompanyCode(merged.getCompanyCode());
    p.setStatusCode(merged.getStatusCode());
    p.setTitleCode(merged.getTitleCode());
    p.setAccessStartDt(PersonService.withSeconds(merged.getAccessStartDt(), "00:00"));
    p.setAccessEndDt(PersonService.withSeconds(merged.getAccessEndDt(), "23:59"));
    return p;
  }

  private static String pick(String excel, String stored) {
    return (excel == null || excel.isBlank()) ? stored : excel.trim();
  }

  private static TbPerson decrypted(TbPerson row) {
    TbPerson p = new TbPerson();
    p.setPersonId(row.getPersonId());
    p.setPersonName(dec(row.getPersonName()));
    p.setBirthDate(dec(row.getBirthDate()));
    p.setPersonPhone(dec(row.getPersonPhone()));
    p.setCompanyCode(row.getCompanyCode());
    p.setTitleCode(row.getTitleCode());
    p.setStatusCode(row.getStatusCode());
    p.setAccessStartDt(row.getAccessStartDt());
    p.setAccessEndDt(row.getAccessEndDt());
    p.setMainTask(row.getMainTask());
    p.setRemark(row.getRemark());
    return p;
  }

  private static String dec(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }
}
