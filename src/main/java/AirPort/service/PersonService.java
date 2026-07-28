package AirPort.service;

import AirPort.adapter.BiostarResult;
import AirPort.adapter.BiostarUserAdapter;
import AirPort.adapter.BiostarUserCard;
import AirPort.adapter.BiostarUserRequest;
import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.PersonForm;
import AirPort.model.PersonSearchParam;
import AirPort.model.TbCommon;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
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
 * <p>성명·생년월일·연락처는 ARIA 암호화 저장. 얼굴·출입권한·카드도 함께 저장한다. BiostarX 사용자는 존재 확인 후 upsert.
 * <b>등록·수정 모두 BiostarX 동기화가 성공해야 커밋</b>(실패=설정/기관그룹 없음·장비오류면 롤백+사유 예외). 장비-DB 정합성이 최우선(유령 인원 방지).
 */
@Service
public class PersonService {

  /** 정규 발급유형 — 이 화면이 다루는 인원 구분. tb_common(cmm_id='PT') */
  private static final String PERSON_TYPE_REGULAR = "PT01";

  private static final String PS = "PS";
  private static final String UT = "UT";

  private final TbPersonMapper personMapper;
  private final TbCardMapper cardMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final TbCompanyMapper companyMapper;
  private final TbCommonMapper commonMapper;
  private final TbSystemMapper systemMapper;
  private final BiostarUserAdapter biostarUserAdapter;
  private final PersonFileService personFileService;
  private final CardService cardService;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public PersonService(
      TbPersonMapper personMapper,
      TbCardMapper cardMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      TbCompanyMapper companyMapper,
      TbCommonMapper commonMapper,
      TbSystemMapper systemMapper,
      BiostarUserAdapter biostarUserAdapter,
      PersonFileService personFileService,
      CardService cardService,
      AuditService auditService,
      MenuAuthService menuAuthService) {
    this.personMapper = personMapper;
    this.cardMapper = cardMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.companyMapper = companyMapper;
    this.commonMapper = commonMapper;
    this.systemMapper = systemMapper;
    this.biostarUserAdapter = biostarUserAdapter;
    this.personFileService = personFileService;
    this.cardService = cardService;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  /** 목록 조회(정규 PT01 고정) — 성명 복호화(표시용) + 검색조건·건수 감사(READ). */
  public PageResult<TbPerson> list(PersonSearchParam param, TbLoginUser actor, Integer menuId) {
    param.setPersonType(PERSON_TYPE_REGULAR);
    // 성명(ARIA 암호문)은 완전일치로만 검색 — keyword 를 trim 후 암호화해 넘긴다(VisitService 와 동일)
    param.setKeywordEnc(param.getKeyword() == null ? null : encryptOrNull(param.getKeyword().trim()));
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
    if (personMapper.selectById(form.getPersonId()) != null) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 존재하는 인원ID 입니다.");
    }
    TbPerson row = toRow(form);
    row.setPersonType(PERSON_TYPE_REGULAR);
    row.setUseYn(form.getUseYn());
    personMapper.insert(row);
    if (form.getFaceImage() != null && !form.getFaceImage().isBlank()) {
      photoMapper.upsert(form.getPersonId(), form.getFaceImage());
    }
    saveAcGroups(form.getPersonId(), form.getAcGroupIds());
    personFileService.apply(form);
    cardService.saveCards(form.getPersonId(), form.getCards());
    // BiostarX 동기화 실패면 등록 취소(롤백) — 장비-DB 정합성이 최우선
    String fail = syncPersonToBiostar(form, empty(form.getPersonId()));
    if (fail != null) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 동기화 실패로 등록이 취소되었습니다. 사유: " + fail);
    }
    auditService.log(actor, AuditService.CREATE, menuId, "정규인원 등록: " + form.getPersonId());
    return null;
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
    validate(form);
    TbPerson existing = personMapper.selectById(form.getPersonId());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    // 변경 전 상태(BiostarX 비교용) — 복호화된 값·기존 얼굴·기존 출입그룹
    decrypt(existing);
    BiostarUserRequest before =
        biostarRequest(
            existing,
            photoMapper.selectPhoto(form.getPersonId()),
            acGroupMapper.selectBiostarAcIds(form.getPersonId()));

    TbPerson row = toRow(form);
    personMapper.update(row);

    if (form.getFaceImage() != null && !form.getFaceImage().isBlank()) {
      photoMapper.upsert(form.getPersonId(), form.getFaceImage());
    } else {
      photoMapper.deleteByPerson(form.getPersonId()); // 얼굴 삭제
    }
    saveAcGroups(form.getPersonId(), form.getAcGroupIds());
    personFileService.apply(form);
    cardService.saveCards(form.getPersonId(), form.getCards());
    // BiostarX 동기화 실패면 수정 취소(롤백) — before 대비 변경분 전송, 장비에 없으면 새로 등록
    String fail = syncPersonToBiostar(form, before);
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
   * @return BiostarX 연동 경고(성공이면 null)
   */
  @Transactional
  public String delete(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    return deleteOne(personId, actor, menuId);
  }

  /**
   * 선택 인원 일괄 삭제 — 건별로 소프트 삭제 + BiostarX 사용자 삭제.
   *
   * @return 실패한 건들의 경고(모두 성공이면 null)
   */
  @Transactional
  public String deleteMany(List<String> personIds, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    if (personIds == null || personIds.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "삭제할 인원을 선택하세요.");
    }
    List<String> warns = new java.util.ArrayList<>();
    for (String personId : personIds) {
      String warn = deleteOne(personId, actor, menuId);
      if (warn != null) {
        warns.add(personId + "(" + warn + ")");
      }
    }
    return warns.isEmpty() ? null : String.join(", ", warns);
  }

  private String deleteOne(String personId, TbLoginUser actor, Integer menuId) {
    TbPerson existing = personMapper.selectById(personId);
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    personMapper.softDelete(personId);
    auditService.log(actor, AuditService.DELETE, menuId, "정규인원 삭제: " + personId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다.";
    }
    BiostarResult res =
        biostarUserAdapter.deleteUser(
            cfg.getBiostarIp(),
            cfg.getBiostarId(),
            pw(cfg),
            personId,
            companyGroupId(existing.getCompanyCode()));
    return res.success() ? null : res.message();
  }

  /** 등록/수정 요청(폼) → BiostarX 전송 값. */
  private BiostarUserRequest biostarRequest(PersonForm f, List<Integer> acIds) {
    return biostarRequest(
        f.getPersonId(), f.getPersonName(), f.getPersonPhone(), f.getFaceImage(),
        f.getCompanyCode(), f.getStatusCode(), f.getAccessStartDt(), f.getAccessEndDt(),
        f.getTitleCode(), acIds, f.getFaceTemplate9(), f.getFaceTemplate5(),
        CardService.toBiostarCards(f.getCards()));
  }

  /** 저장된 인원(수정 전 상태) → BiostarX 전송 값. 얼굴 템플릿은 보관하지 않으므로 없음. */
  private BiostarUserRequest biostarRequest(TbPerson p, String faceImage, List<Integer> acIds) {
    return biostarRequest(
        p.getPersonId(), p.getPersonName(), p.getPersonPhone(), faceImage,
        p.getCompanyCode(), p.getStatusCode(), p.getAccessStartDt(), p.getAccessEndDt(),
        p.getTitleCode(), acIds, null, null,
        CardService.toBiostarCardsOf(cardMapper.selectByPerson(p.getPersonId())));
  }

  /** BiostarX 전송 값 구성(코드 → 실제 값 변환 포함). */
  private BiostarUserRequest biostarRequest(
      String personId,
      String name,
      String phone,
      String faceImage,
      String companyCode,
      String statusCode,
      String startDt,
      String endDt,
      String titleCode,
      List<Integer> acIds,
      String t9,
      String t5,
      List<BiostarUserCard> cards) {
    return new BiostarUserRequest(
        personId,
        name,
        phone,
        faceImage,
        companyGroupId(companyCode),
        codeTag(PS, statusCode),
        biostarDateTime(startDt, "00:00"),
        biostarDateTime(endDt, "23:59"),
        codeName(UT, titleCode),
        acIds,
        faceImage,
        t9,
        t5,
        cards);
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

  private void saveAcGroups(String personId, List<Integer> acGroupIds) {
    acGroupMapper.deleteByPerson(personId);
    if (acGroupIds != null && !acGroupIds.isEmpty()) {
      acGroupMapper.insertBatch(personId, acGroupIds);
    }
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  /** BiostarX 사용자 생성. 성공 시 biostar_user_id(=인원ID) 반영. 실패는 경고 문자열 반환. */
  /**
   * BiostarX 사용자 동기화(등록·수정 공통) — 실패 사유 문자열, 성공이면 null. 설정 없음/소속 기관 그룹 없음이면 장비 호출 전에 막는다.
   * 반환이 null 이 아니면 호출자가 트랜잭션을 롤백해야 한다(장비-DB 정합성 유지).
   */
  private String syncPersonToBiostar(PersonForm form, BiostarUserRequest before) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다. 설정관리에서 먼저 등록하세요.";
    }
    // 소속 기관에 BiostarX 사용자그룹이 없으면 사용자를 만들 수 없다 — 유령 인원 방지 위해 막는다
    if (companyGroupId(form.getCompanyCode()) == null) {
      return "소속 기관에 BiostarX 사용자그룹이 없습니다. 기관등록관리에서 해당 기관을 저장(동기화)해 그룹을 만든 뒤 다시 시도하세요.";
    }
    BiostarUserRequest after =
        biostarRequest(form, acGroupMapper.selectBiostarAcIds(form.getPersonId()));
    BiostarResult res = syncUser(cfg, before, after);
    if (!res.success()) {
      return res.message();
    }
    personMapper.updateBiostarUserId(form.getPersonId(), form.getPersonId());
    return null;
  }

  /**
   * BiostarX 사용자 동기화 — {@code GET /api/users/{인원ID}} 로 존재를 확인해 있으면 수정, 없으면 등록한다.
   *
   * <p>등록/수정 어느 쪽에서 들어와도 결과가 같아진다(우리 DB 와 BiostarX 가 어긋나 있어도 한 번에 맞춰진다).
   */
  private BiostarResult syncUser(TbSystem cfg, BiostarUserRequest before, BiostarUserRequest after) {
    String ip = cfg.getBiostarIp();
    String id = cfg.getBiostarId();
    boolean exists = biostarUserAdapter.userExists(ip, id, pw(cfg), after.userId());
    return exists
        ? biostarUserAdapter.updateUser(ip, id, pw(cfg), before, after)
        : biostarUserAdapter.createUser(ip, id, pw(cfg), after);
  }

  /** 비교 기준이 없을 때 쓰는 빈 요청 — 모든 항목이 '변경됨'이 되어 전 항목이 전송된다. */
  private static BiostarUserRequest empty(String userId) {
    return new BiostarUserRequest(
        userId, null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  /** 기관의 BiostarX 사용자그룹 ID. */
  private Integer companyGroupId(String companyCode) {
    if (companyCode == null || companyCode.isBlank()) {
      return null;
    }
    TbCompany company = companyMapper.selectById(companyCode);
    return company == null ? null : company.getBiostarGroupId();
  }

  /** 공통코드의 code_tag(예: PS 상태 → disabled 값). 없으면 null. */
  private String codeTag(String cmmId, String codeId) {
    TbCommon code = code(cmmId, codeId);
    return code == null ? null : code.getCodeTag();
  }

  /** 공통코드의 code_name(예: UT 직위 → user_title). 없으면 null. */
  private String codeName(String cmmId, String codeId) {
    TbCommon code = code(cmmId, codeId);
    return code == null ? null : code.getCodeName();
  }

  private TbCommon code(String cmmId, String codeId) {
    return (codeId == null || codeId.isBlank()) ? null : commonMapper.selectOne(cmmId, codeId);
  }

  /**
   * 화면 값("YYYY-MM-DDTHH:mm" 또는 날짜만) → BiostarX 일시 형식(예: 2037-12-31T23:59:00.00Z).
   *
   * @param defaultTime 날짜만 들어온 경우 채울 시각("00:00"/"23:59")
   */
  private static String biostarDateTime(String value, String defaultTime) {
    String v = withSeconds(value, defaultTime);
    return v == null ? null : v + ".00Z";
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

  /** BiostarX 유효기간 상한 — expiry_datetime 은 2037-12-31T23:59 를 넘을 수 없다. */
  private static final String MAX_ACCESS_END_DT = "2037-12-31T23:59";

  /** 직위(user_title) 허용 문자 — 한글·영문·숫자·공백만(특수문자 금지). */
  private static final Pattern TITLE_ALLOWED = Pattern.compile("^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ\\s]+$");

  /** 인원ID 허용 문자 — 영문·숫자만. BiostarX 사용자ID 와 같은 키라 공백·특수문자를 막는다. */
  private static final Pattern PERSON_ID_ALLOWED = Pattern.compile("^[0-9A-Za-z]+$");

  /** 필수값·형식 검증 — 등록·수정 공통(인원 데이터의 최소 요건). */
  private void validate(PersonForm form) {
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
    if (form.getAccessEndDt().compareTo(MAX_ACCESS_END_DT) > 0) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "출입종료일은 " + MAX_ACCESS_END_DT.replace('T', ' ') + " 를 초과할 수 없습니다.");
    }
    if (form.getAccessStartDt().compareTo(form.getAccessEndDt()) > 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "출입시작일은 출입종료일보다 늦을 수 없습니다.");
    }
    String title = codeName(UT, form.getTitleCode());
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
