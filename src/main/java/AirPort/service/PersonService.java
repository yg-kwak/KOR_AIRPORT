package AirPort.service;

import AirPort.adapter.biostar.BiostarUserRequest;
import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.model.PersonForm;
import AirPort.model.PersonSearchParam;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정규인원(tb_person, person_type='PT01') 등록관리. (docs/backend.md)
 *
 * <p>성명·생년월일·연락처는 ARIA 암호화 저장. 얼굴·출입권한·카드도 함께 저장한다. BiostarX 사용자는 존재 확인 후 upsert. <b>등록·수정 모두
 * BiostarX 동기화가 성공해야 커밋</b>(실패=설정/기관그룹 없음·장비오류면 롤백+사유 예외). 장비-DB 정합성이 최우선(유령 인원 방지).
 */
@Service
public class PersonService {

  /** 정규 발급유형 — 이 화면이 다루는 인원 구분. tb_common(cmm_id='PT') */
  private static final String PERSON_TYPE_REGULAR = "PT01";

  private static final String UT = "UT";

  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final PersonBiostarService personBiostar;
  private final PersonFileService personFileService;
  private final CardService cardService;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;
  private final CodeValidationService codeValidator;

  public PersonService(
      @org.springframework.beans.factory.annotation.Value(
              "${app.person.access-end-max:2037-12-31T23:59}")
          String maxAccessEndDt,
      @org.springframework.beans.factory.annotation.Value(
              "${app.person.access-end-default:2028-05-31T23:59}")
          String defaultAccessEndDt,
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      PersonBiostarService personBiostar,
      PersonFileService personFileService,
      CardService cardService,
      AuditService auditService,
      MenuAuthService menuAuthService,
      CodeValidationService codeValidator) {
    // 상한이 장비 상한을 넘으면 BiostarX 등록이 실패한다 — 더 작은 쪽을 쓴다
    this.maxAccessEndDt =
        maxAccessEndDt.compareTo(BIOSTAR_MAX_EXPIRY) > 0 ? BIOSTAR_MAX_EXPIRY : maxAccessEndDt;
    // 기본값이 상한을 넘으면 모달을 열자마자 저장할 수 없는 값이 들어간다 — 상한으로 눌러 둔다
    this.defaultAccessEndDt =
        defaultAccessEndDt.compareTo(this.maxAccessEndDt) > 0
            ? this.maxAccessEndDt
            : defaultAccessEndDt;
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.personBiostar = personBiostar;
    this.personFileService = personFileService;
    this.cardService = cardService;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
    this.codeValidator = codeValidator;
  }

  /** 목록 조회(정규 PT01 고정) — 성명 복호화(표시용) + 검색조건·건수 감사(READ). */
  public PageResult<TbPerson> list(PersonSearchParam param, TbLoginUser actor, Integer menuId) {
    param.setPersonType(PERSON_TYPE_REGULAR);
    // 성명(ARIA 암호문)은 완전일치로만 검색 — keyword 를 trim 후 암호화해 넘긴다(VisitService 와 동일)
    param.setKeywordEnc(
        param.getKeyword() == null ? null : encryptOrNull(param.getKeyword().trim()));
    long total = personMapper.selectCount(param);
    List<TbPerson> rows = personMapper.selectList(param);
    rows.forEach(this::decrypt);
    auditService.log(actor, AuditService.READ, menuId, "정규인원 목록 조회 (결과 " + total + "건)");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  /** 단건 조회 — 수정 모달용(출입권한 포함). */
  public TbPerson get(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbPerson row = personMapper.selectById(personId);
    if (row == null || "Y".equals(row.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    decrypt(row);
    return row;
  }

  /** 인원의 출입권한(ac_group_id) 목록. */
  public List<Integer> acGroupIds(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return acGroupMapper.selectAcGroupIds(personId);
  }

  /** 정규인원 등록 — 저장 후 BiostarX 사용자 생성이 성공해야 커밋(실패면 전체 롤백 + 사유 예외). return 은 항상 null. */
  @Transactional
  public String create(PersonForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(form);
    // 삭제된 인원ID 는 다시 쓸 수 있다. 소프트 삭제라 행이 남아 person_id(PK) 로 INSERT 가 안 되므로
    // 남은 행을 되살린다. 삭제 때 BiostarX 사용자도 지웠으므로(deleteOne) 같은 ID 를 재사용해도 충돌하지 않는다.
    TbPerson dead = personMapper.selectById(form.getPersonId());
    if (dead != null && !"Y".equals(dead.getDelYn())) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 존재하는 인원ID 입니다.");
    }
    TbPerson row = toRow(form);
    row.setPersonType(PERSON_TYPE_REGULAR);
    row.setUseYn(form.getUseYn());
    try {
      if (dead == null) {
        personMapper.insert(row);
      } else {
        // 이 정리가 들어가기 전에 삭제된 행에는 사진이 남아 있다 — 되살리기 전에 지운다
        photoMapper.deleteByPerson(form.getPersonId());
        personMapper.revive(row);
      }
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // 동시 등록 레이스(중복검사 통과 후 PK 충돌) — 친화적 메시지로 변환
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 존재하는 인원ID 입니다. 다른 ID 로 다시 시도하세요.");
    }
    savePhoto(form); // 저장은 원본 사진(카드 출력에 쓴다). 비활성 상태면 안에서 지운다
    saveAcGroups(form.getPersonId(), form.getAcGroupIds());
    personFileService.apply(form);
    cardService.saveCards(form.getPersonId(), form.getCards(), actor, menuId);
    // BiostarX 동기화 실패면 등록 취소(롤백) — 장비-DB 정합성이 최우선
    String fail =
        personBiostar.syncPersonToBiostar(form, PersonBiostarService.empty(form.getPersonId()));
    if (fail != null) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 동기화 실패로 등록이 취소되었습니다. 사유: " + fail);
    }
    auditService.log(actor, AuditService.CREATE, menuId, "정규인원 등록: " + form.getPersonId());
    return null;
  }

  /** 출입종료일 상한 — 화면(입력 max·기본값)과 서버 검증이 같은 값을 쓰도록 내려준다. */
  public String maxAccessEndDt() {
    return maxAccessEndDt;
  }

  /** 등록 모달 기본값 — 상한과 다르다(넘겨도 저장된다). */
  public String defaultAccessEndDt() {
    return defaultAccessEndDt;
  }

  /** 다음 인원ID 자동 채번 — 등록 모달의 인원ID 초기값. 사용자가 바꿀 수 있고, 중복은 저장 시 막힌다. */
  public String nextPersonId(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    return personMapper.selectNextPersonId();
  }

  /** 인원의 등록사진(BASE64) — 수정 모달에서 기존 얼굴 표시용. */
  public String photo(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return photoMapper.selectPhoto(personId);
  }

  /** 정규인원 수정 — 변경분 BiostarX 동기화(PUT). 등록과 동일하게 동기화 실패면 롤백 + 사유 예외. return 은 항상 null. */
  @Transactional
  public String update(PersonForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    TbPerson existing = personMapper.selectById(form.getPersonId());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    validate(form, existing); // 코드 검증은 저장된 값 대비(안 바꾼 항목은 통과)
    // 변경 전 상태(BiostarX 비교용) — 복호화된 값·기존 얼굴·기존 출입그룹
    decrypt(existing);
    BiostarUserRequest before =
        personBiostar.requestOf(
            existing,
            photoMapper.selectPhoto(form.getPersonId()),
            acGroupMapper.selectBiostarAcIds(form.getPersonId()));

    TbPerson row = toRow(form);
    personMapper.update(row);

    if (photoOf(form) != null) {
      savePhoto(form); // 비활성 상태면 화면이 사진을 보냈더라도 안에서 지운다
    } else {
      photoMapper.deleteByPerson(form.getPersonId()); // 화면에서 얼굴을 비웠다
    }
    saveAcGroups(form.getPersonId(), form.getAcGroupIds());
    personFileService.apply(form);
    cardService.saveCards(form.getPersonId(), form.getCards(), actor, menuId);
    // BiostarX 동기화 실패면 수정 취소(롤백) — before 대비 변경분 전송, 장비에 없으면 새로 등록
    String fail = personBiostar.syncPersonToBiostar(form, before);
    if (fail != null) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 동기화 실패로 수정이 취소되었습니다. 사유: " + fail);
    }
    auditService.log(actor, AuditService.UPDATE, menuId, "정규인원 수정: " + form.getPersonId());
    return null;
  }

  /**
   * 정규인원 삭제 — 우리 DB 는 소프트 삭제(del_yn='Y'), BiostarX 사용자도 삭제한다.
   *
   * @return 항상 null(성공). BiostarX 삭제 실패면 예외로 롤백된다(장비에 유령 사용자 방지).
   */
  @Transactional
  public String delete(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    deleteOne(personId, actor, menuId);
    return null;
  }

  /** 선택 인원 일괄 삭제 — 한 건이라도 BiostarX 삭제 실패면 전체 롤백(실패 인원ID 안내 후 재시도 유도). */
  @Transactional
  public String deleteMany(List<String> personIds, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    if (personIds == null || personIds.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "삭제할 인원을 선택하세요.");
    }
    for (String personId : personIds) {
      deleteOne(personId, actor, menuId);
    }
    return null;
  }

  /** 1명 삭제 — BiostarX 사용자 삭제가 성공해야 DB 소프트삭제를 커밋한다(실패=예외 → 롤백 + 실패 감사). */
  private void deleteOne(String personId, TbLoginUser actor, Integer menuId) {
    TbPerson existing = personMapper.selectById(personId);
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    String fail = personBiostar.deleteUser(personId, existing.getCompanyCode());
    if (fail != null) {
      auditService.logAlways(
          actor, AuditService.DELETE, menuId, "정규인원 삭제 실패(" + personId + "): " + fail);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "BiostarX 사용자 삭제 실패로 삭제가 취소되었습니다(" + personId + "). 사유: " + fail + " — 다시 시도하세요.");
    }
    // 카드 회수가 빠지면 사라진 인원에 카드가 물린 채 '발급중'으로 남아 다른 인원에게 발급할 수 없다
    int released = cardService.releasePersonCards(personId);
    acGroupMapper.deleteByPerson(personId); // 출입권한도 함께 정리
    // 얼굴은 생체정보다. 소프트 삭제로 남길 이유가 없고, 인원ID 를 재사용하면
    // 다음 사람에게 그대로 붙는다(수정 모달·카드 출력에 다른 사람 얼굴이 나온다).
    photoMapper.deleteByPerson(personId);
    personMapper.softDelete(personId);
    auditService.log(
        actor,
        AuditService.DELETE,
        menuId,
        "정규인원 삭제: " + personId + (released > 0 ? " (카드 " + released + "장 회수)" : ""));
  }

  /** 폼 → 저장 행. 성명·생년월일·연락처는 ARIA 암호화, 출입기간은 초까지 채운다. (등록/수정 공통) */
  private TbPerson toRow(PersonForm form) {
    TbPerson row = new TbPerson();
    row.setPersonId(form.getPersonId());
    row.setPersonName(ARIAUtil.ariaEncrypt(form.getPersonName()));
    row.setBirthDate(encryptOrNull(form.getBirthDate()));
    row.setPersonPhone(encryptOrNull(form.getPersonPhone()));
    row.setCompanyCode(form.getCompanyCode());
    row.setTitleCode(form.getTitleCode());
    row.setStatusCode(form.getStatusCode());
    row.setMainTask(form.getMainTask());
    row.setIdCheckDt(form.getIdCheckDt());
    row.setIdCheckFile(form.getIdCheckFile());
    row.setSecurityEduDt(form.getSecurityEduDt());
    row.setSecurityEduScore(form.getSecurityEduScore());
    row.setFinalApproveDt(form.getFinalApproveDt());
    row.setApproveFile(form.getApproveFile());
    row.setAccessStartDt(withSeconds(form.getAccessStartDt(), "00:00"));
    row.setAccessEndDt(withSeconds(form.getAccessEndDt(), "23:59"));
    row.setRemark(form.getRemark());
    return row;
  }

  /** 화면이 보낸 사진 — 원본(facePhoto) 우선, 없으면 정규화 얼굴(장치 촬영은 원본이 없다). */
  private static String photoOf(PersonForm form) {
    String photo = form.getFacePhoto();
    if (photo != null && !photo.isBlank()) {
      return photo;
    }
    return (form.getFaceImage() != null && !form.getFaceImage().isBlank())
        ? form.getFaceImage()
        : null;
  }

  /**
   * 등록사진 저장 — <b>비활성 상태(정지·퇴사·회수·분실)면 지운다.</b>
   *
   * <p>출입을 막아 놓고 생체정보만 남겨 두면 상태를 되돌리는 순간 예전 얼굴로 문이 열린다. 사람이 떠났거나 카드를 잃은 상태에서 얼굴을 보관할 이유도 없다 (개인정보
   * 최소화). 장비 쪽 얼굴은 {@link PersonBiostarService} 가 같은 판정으로 함께 지운다 — <b>한쪽만 지우면 다음 저장에서 되살아난다.</b>
   *
   * <p>되돌릴 수 없으므로 화면이 저장 전에 알린다(`PAGE_DISABLED_STATUS`).
   */
  private void savePhoto(PersonForm form) {
    if (personBiostar.isDisabled(form.getStatusCode())) {
      photoMapper.deleteByPerson(form.getPersonId());
      return;
    }
    String photo = photoOf(form);
    if (photo != null) {
      photoMapper.upsert(form.getPersonId(), photo);
    }
  }

  /** 화면이 "저장하면 얼굴이 지워진다"고 알리려면 어떤 상태가 비활성인지 알아야 한다. */
  public List<String> disabledStatusCodes(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return personBiostar.disabledStatusCodes();
  }

  private void saveAcGroups(String personId, List<Integer> acGroupIds) {
    acGroupMapper.deleteByPerson(personId);
    if (acGroupIds != null && !acGroupIds.isEmpty()) {
      acGroupMapper.insertBatch(personId, acGroupIds);
    }
  }

  /** "YYYY-MM-DDTHH:mm"(또는 날짜만) → 초까지 채운 ISO 문자열. DB(datetime2) 저장·BiostarX 변환 공통. */
  private static String withSeconds(String value, String defaultTime) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim();
    if (!v.contains("T")) {
      v = v + "T" + defaultTime; // 날짜만 오면 기본 시각 보정
    }
    return v.length() == 16 ? v + ":00" : v; // "YYYY-MM-DDTHH:mm" → 초 보정
  }

  // ── 공통 ────────────────────────────────────────────────────────────────

  /** BiostarX 유효기간의 기술적 상한 — expiry_datetime 은 이 값을 넘을 수 없다. 아래 운영 상한은 항상 이 안쪽이어야 한다. */
  private static final String BIOSTAR_MAX_EXPIRY = "2037-12-31T23:59";

  /**
   * 출입종료일 <b>상한</b> — 저장 시 검증 기준. 넘기면 거부한다({@code app.person.access-end-max}).
   *
   * <p>기본값과 다르다. 기본값은 계약 기간이라 자주 바뀌고 넘겨서 저장해도 되지만, 이 값은 <b>장비가 받아 주는 한계</b>라 넘기면 BiostarX 등록 자체가
   * 실패한다. 한 값으로 두면 계약 기간을 넘긴 인원을 아예 저장할 수 없다.
   */
  private final String maxAccessEndDt;

  /**
   * 출입종료일 <b>기본값</b> — 등록 모달을 열었을 때 채워지는 값({@code app.person.access-end-default}).
   *
   * <p>계약 기간이라 그때그때 바뀐다. 넘겨서 입력해도 <b>저장은 된다</b> — 계약이 연장될 수 있어 막지 않는다.
   */
  private final String defaultAccessEndDt;

  /** 직위(user_title) 허용 문자 — 한글·영문·숫자·공백만(특수문자 금지). */
  private static final Pattern TITLE_ALLOWED = Pattern.compile("^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$");

  /** 인원ID 허용 문자 — 영문·숫자만. BiostarX 사용자ID 와 같은 키라 공백·특수문자를 막는다. */
  private static final Pattern PERSON_ID_ALLOWED = Pattern.compile("^[0-9A-Za-z]+$");

  /** 필수값·형식 검증 — 등록·수정 공통(인원 데이터의 최소 요건). */
  private void validate(PersonForm form) {
    validate(form, null);
  }

  /** prev(저장된 인원)가 있으면 코드 검증은 값이 바뀐 항목만 — 코드가 정리돼도 기존 행 수정이 막히지 않게 한다. */
  private void validate(PersonForm form, TbPerson prev) {
    require(form.getPersonId(), "인원ID");
    require(form.getPersonName(), "성명");
    require(form.getCompanyCode(), "기관");
    require(form.getStatusCode(), "상태");
    require(form.getAccessStartDt(), "출입시작일");
    require(form.getAccessEndDt(), "출입종료일");

    if (!PERSON_ID_ALLOWED.matcher(form.getPersonId()).matches()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "인원ID 는 영문·숫자만 사용할 수 있습니다.");
    }
    String birth = form.getBirthDate();
    if (birth != null && !birth.isBlank() && !isIsoDate(birth)) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "생년월일은 YYYY-MM-DD 형식으로 입력하세요. 예: 1990-01-01");
    }

    // 날짜는 "YYYY-MM-DD" 형식이라 문자열 비교로 대소 판정이 가능하다
    // 상한만 막는다 — 기본값(계약 기간)을 넘기는 것은 정상이다(계약은 연장된다)
    if (form.getAccessEndDt().compareTo(maxAccessEndDt) > 0) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "출입종료일은 "
              + maxAccessEndDt.replace('T', ' ')
              + " 까지만 지정할 수 있습니다. BiostarX 가 받을 수 있는 마지막 날짜입니다.");
    }
    if (form.getAccessStartDt().compareTo(form.getAccessEndDt()) > 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "출입시작일은 출입종료일보다 늦을 수 없습니다.");
    }
    // 엑셀 일괄등록은 코드ID 를 직접 적으므로 없는 코드가 그대로 저장되지 않게 막는다
    codeValidator.validate(
        UT, form.getTitleCode(), "직위", prev == null ? null : prev.getTitleCode());
    codeValidator.validate(
        "PS", form.getStatusCode(), "상태", prev == null ? null : prev.getStatusCode());
    String title = personBiostar.codeName(UT, form.getTitleCode());
    if (title != null && !title.isBlank() && !TITLE_ALLOWED.matcher(title).matches()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "직위에 특수문자를 사용할 수 없습니다: " + title);
    }
  }

  /** "YYYY-MM-DD" 형식이면서 실제로 존재하는 날짜인지(2월 30일 같은 값 차단). */
  private static boolean isIsoDate(String value) {
    try {
      LocalDate.parse(value); // ISO_LOCAL_DATE = 화면 안내(1990-01-01)와 같은 형식
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, label + "은(는) 필수입니다.");
    }
  }

  private static String encryptOrNull(String plain) {
    return (plain == null || plain.isBlank()) ? null : ARIAUtil.ariaEncrypt(plain);
  }

  /** 표시용 복호화 — 성명/생년월일/연락처. */
  private void decrypt(TbPerson row) {
    row.setPersonName(decryptOrNull(row.getPersonName()));
    row.setBirthDate(decryptOrNull(row.getBirthDate()));
    row.setPersonPhone(decryptOrNull(row.getPersonPhone()));
  }

  private static String decryptOrNull(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }
}
