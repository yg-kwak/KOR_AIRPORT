package AirPort.service;

import AirPort.adapter.BiostarAdapter;
import AirPort.adapter.BiostarGroupResult;
import AirPort.adapter.BiostarUserGroup;
import AirPort.adapter.BiostarUserGroups;
import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.CompanySearchParam;
import AirPort.model.TbCommon;
import AirPort.model.ExcelImportResult;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관(tb_company) 등록관리 CRUD. 골든 샘플(LoginUserService) 패턴을 따른다. (docs/backend.md)
 *
 * <p>PK=company_code(업무코드) 중복 불가. 대표자(ceo_name)는 ARIA 암호화 저장·복호화 표시(security.md). 삭제는 소프트
 * 삭제(del_yn='Y')이며 삭제 로그에 기관코드를 스냅샷으로 남긴다.
 */
@Service
public class CompanyService {

  /** 정규발급 구분 — 이 코드의 code_tag 가 BiostarX 부모 사용자그룹 ID. (tb_common cmm_id='PTD') */
  private static final String PTD = "PTD";

  private static final String PTD_REGULAR = "PTD01";

  private final TbCompanyMapper companyMapper;
  private final TbCommonMapper commonMapper;
  private final TbSystemMapper systemMapper;
  private final BiostarAdapter biostarAdapter;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  /** 자기 자신(프록시) — 엑셀 일괄등록에서 create 를 행마다 독립 트랜잭션으로 부르기 위해(자가호출은 @Transactional 무시됨). */
  private final CompanyService self;

  public CompanyService(
      TbCompanyMapper companyMapper,
      TbCommonMapper commonMapper,
      TbSystemMapper systemMapper,
      BiostarAdapter biostarAdapter,
      AuditService auditService,
      MenuAuthService menuAuthService,
      @org.springframework.context.annotation.Lazy CompanyService self) {
    this.companyMapper = companyMapper;
    this.commonMapper = commonMapper;
    this.systemMapper = systemMapper;
    this.biostarAdapter = biostarAdapter;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
    this.self = self;
  }

  // ── BiostarX 사용자그룹(=기관) 연동 ───────────────────────────────────────

  /** PTD01(정규발급)의 code_tag = BiostarX 부모 사용자그룹 ID. 미설정이면 null. */
  private Long parentGroupId() {
    TbCommon ptd = commonMapper.selectOne(PTD, PTD_REGULAR);
    if (ptd == null || ptd.getCodeTag() == null || ptd.getCodeTag().isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(ptd.getCodeTag().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 기관 등록 모달용 — PTD01 하위(부모=code_tag)의 BiostarX 사용자그룹만 조회한다.
   *
   * @return 조회 실패 시 success=false + 메시지(화면에서 안내)
   */
  public BiostarUserGroups biostarUserGroups(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarUserGroups.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    Long parent = parentGroupId();
    if (parent == null) {
      return BiostarUserGroups.fail("공통코드 PTD/PTD01 의 code_tag(BiostarX 사용자그룹 ID)가 없습니다.");
    }
    BiostarUserGroups all =
        biostarAdapter.searchUserGroups(cfg.getBiostarIp(), cfg.getBiostarId(), biostarPw(cfg));
    if (!all.success()) {
      return all;
    }
    List<BiostarUserGroup> children =
        all.groups().stream().filter(g -> parent.equals(g.parentId())).toList();
    return BiostarUserGroups.ok(children);
  }

  private String biostarPw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  /**
   * 기관명으로 BiostarX 사용자그룹 생성 후 연동 ID 저장. 실패해도 기관 등록은 유지하고 경고 메시지를 돌려준다(정책).
   *
   * @return 실패 사유(성공이면 null)
   */
  private String createBiostarGroup(TbCompany row) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다.";
    }
    Long parent = parentGroupId();
    if (parent == null) {
      return "공통코드 PTD/PTD01 의 code_tag 가 없습니다.";
    }
    BiostarGroupResult res =
        biostarAdapter.createUserGroup(
            cfg.getBiostarIp(), cfg.getBiostarId(), biostarPw(cfg), parent, row.getCompanyName());
    if (!res.success()) {
      return res.message();
    }
    if (res.id() == null) {
      return "그룹은 생성됐으나 ID를 확인하지 못했습니다. 모달에서 선택해 연결하세요.";
    }
    companyMapper.updateBiostarGroupId(row.getCompanyCode(), res.id().intValue());
    return null;
  }

  /** 기관명 변경 시 BiostarX 사용자그룹 이름도 수정. 실패는 경고만. */
  private String renameBiostarGroup(TbCompany row) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return "BiostarX 설정이 없습니다.";
    }
    BiostarGroupResult res =
        biostarAdapter.updateUserGroupName(
            cfg.getBiostarIp(),
            cfg.getBiostarId(),
            biostarPw(cfg),
            row.getBiostarGroupId(),
            row.getCompanyName());
    return res.success() ? null : res.message();
  }

  /** 기관 선택 팝업용 조회 — 로그인 사용자 공용(특정 메뉴 권한 불요). 다른 화면이 tb_company 를 참조할 때 사용. */
  public List<TbCompany> pickerCompanies() {
    return companyMapper.selectOptions();
  }

  /** 다음 기관코드 자동 채번 — 등록 모달의 기관코드 초기값. 사용자가 바꿀 수 있고, 중복은 저장 시 막힌다. */
  public String nextCompanyCode(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    return companyMapper.selectNextCompanyCode();
  }

  /** 목록 조회 — 대표자 복호화(표시용) + 검색조건·결과 건수 감사(READ). */
  public PageResult<TbCompany> list(CompanySearchParam param, TbLoginUser actor, Integer menuId) {
    long total = companyMapper.selectCount(param);
    List<TbCompany> rows = companyMapper.selectList(param);
    rows.forEach(this::decryptCeo);
    auditService.log(actor, AuditService.READ, menuId, "기관 목록 조회 (" + searchSummary(param, total) + ")");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  private String searchSummary(CompanySearchParam param, long total) {
    StringBuilder sb = new StringBuilder();
    if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
      sb.append("검색어=").append(param.getSearchType()).append(':').append(param.getKeyword());
    } else {
      sb.append("검색어=없음");
    }
    if (param.getUseYn() != null && !param.getUseYn().isEmpty()) {
      sb.append(", 사용여부=").append(param.getUseYn());
    }
    sb.append(", 정렬=")
        .append(param.getSort() == null ? "기본" : param.getSort())
        .append(' ')
        .append(param.getDir())
        .append(", 페이지=")
        .append(param.getPage())
        .append(", 결과 ")
        .append(total)
        .append("건");
    return sb.toString();
  }

  /** 엑셀 다운로드용 전체 목록(동일 검색/정렬). 목적(purpose)은 감사 remark 로 기록. */
  public List<TbCompany> listAllForExcel(
      CompanySearchParam param, TbLoginUser actor, Integer menuId, String purpose) {
    menuAuthService.requireRead(actor, menuId);
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "다운로드 목적을 입력해주세요.");
    }
    List<TbCompany> rows = companyMapper.selectListAll(param);
    rows.forEach(this::decryptCeo);
    auditService.log(
        actor, AuditService.DOWNLOAD, menuId, "기관 엑셀 다운로드 (" + rows.size() + "건)", purpose);
    return rows;
  }

  /** 엑셀 일괄 등록의 열 순서(양식 헤더와 일치): 기관코드 · 기관명 · 기관구분코드 · 대표자 · 연락처. */
  public static final String[] IMPORT_HEADERS = {"기관코드*", "기관명*", "기관구분코드", "대표자", "연락처"};

  /** 양식 2행에 넣는 예시 행 — 그대로 두거나 지우면 등록에서 건너뛴다(사용자가 덮어쓰면 정상 등록). */
  public static final String[] EXAMPLE_ROW = {"C001", "예시기관", "", "홍길동", "02-1234-5678"};

  /**
   * 엑셀 일괄 등록 — 행마다 {@link #create}를 호출한다(행 단위 독립 트랜잭션이라 한 행 실패가 나머지를 막지 않는다).
   *
   * <p>기관코드·기관명은 필수. BiostarX 그룹은 각 행에서 create 규칙대로 생성된다(연동 실패해도 저장은 유지). 결과는 성공/실패
   * 건수와 행별 사유로 돌려준다.
   */
  public ExcelImportResult importExcel(
      java.io.InputStream in, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    ExcelImportResult result = new ExcelImportResult();
    List<String[]> rows;
    try {
      rows = AirPort.util.ExcelUtil.read(in, IMPORT_HEADERS.length);
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀을 읽을 수 없습니다. 양식 파일을 확인하세요.");
    }
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "등록할 데이터가 없습니다. 2행부터 입력하세요.");
    }
    int line = 1; // 헤더가 1행 → 데이터는 2행부터
    for (String[] r : rows) {
      line++;
      if (java.util.Arrays.equals(r, EXAMPLE_ROW)) {
        continue; // 안내용 예시 행 — 건너뛴다
      }
      try {
        TbCompany row = new TbCompany();
        row.setCompanyCode(blankToNull(r[0]));
        row.setCompanyName(blankToNull(r[1]));
        row.setCompanyType(blankToNull(r[2]));
        row.setCeoName(blankToNull(r[3]));
        row.setTel(blankToNull(r[4]));
        self.create(row, actor, menuId); // 프록시 경유 — 행마다 독립 트랜잭션
        result.addSuccess();
      } catch (BusinessException e) {
        result.addError(line, e.getMessage());
      } catch (Exception e) {
        result.addError(line, "처리 실패");
      }
    }
    auditService.log(
        actor,
        AuditService.CREATE,
        menuId,
        "기관 엑셀 일괄등록 (성공 " + result.getSuccess() + " / 실패 " + result.getFail() + ")");
    return result;
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }

  /**
   * 기관 등록. BiostarX 사용자그룹은 모달에서 선택했으면 그 ID 로 연결하고, 미선택이면 PTD01 하위에 기관명으로 새로 생성한다.
   *
   * @return BiostarX 연동 경고(성공이면 null) — 연동 실패해도 기관 등록은 유지한다(정책)
   */
  @Transactional
  public String create(TbCompany row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(row);
    // company_code 는 업무 PK — 활성 행이 있으면 중복. 소프트 삭제(del_yn='Y') 행이면 되살려 재등록.
    TbCompany existing = companyMapper.selectById(row.getCompanyCode());
    if (existing != null && !"Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 존재하는 기관코드입니다.");
    }
    encryptCeo(row);
    if (existing != null) {
      companyMapper.reactivate(row); // 삭제된 코드 재등록 → 되살리기
    } else {
      try {
        companyMapper.insert(row);
      } catch (org.springframework.dao.DataIntegrityViolationException e) {
        // 동시 등록 레이스(자동 채번 겹침 등) — 친화적 메시지로 변환
        throw new BusinessException(ErrorCode.DUPLICATE, "이미 존재하는 기관코드입니다. 다른 코드로 다시 시도하세요.");
      }
    }
    auditService.log(actor, AuditService.CREATE, menuId, "기관 등록: " + row.getCompanyCode());
    return row.getBiostarGroupId() == null ? createBiostarGroup(row) : null;
  }

  /**
   * 기관 수정. 기관명이 바뀌고 연동된 사용자그룹이 있으면 BiostarX 그룹명도 함께 수정한다.
   *
   * @return BiostarX 연동 경고(성공이면 null)
   */
  @Transactional
  public String update(TbCompany row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    validate(row);
    TbCompany existing = companyMapper.selectById(row.getCompanyCode());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    boolean nameChanged = !row.getCompanyName().equals(existing.getCompanyName());
    encryptCeo(row);
    companyMapper.update(row);
    auditService.log(actor, AuditService.UPDATE, menuId, "기관 수정: " + row.getCompanyCode());
    if (row.getBiostarGroupId() == null) {
      // 그룹 미연동 안내 — 이 상태로는 소속 인원 등록이 차단되므로 조치 방법을 알려준다(자동 생성은 하지 않음, 정책 유지)
      return "이 기관은 BiostarX 사용자그룹이 연결되어 있지 않아 소속 인원을 등록할 수 없습니다."
          + " 수정 모달에서 BiostarX 그룹을 선택해 다시 저장하세요.";
    }
    return nameChanged ? renameBiostarGroup(row) : null;
  }

  /** 소프트 삭제 — 기관코드를 감사 스냅샷으로 남긴다. */
  @Transactional
  public void delete(String companyCode, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbCompany company = companyMapper.selectById(companyCode);
    if (company == null || "Y".equals(company.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    companyMapper.softDelete(companyCode);
    auditService.log(actor, AuditService.DELETE, menuId, "기관 삭제: " + companyCode);
  }

  private void validate(TbCompany row) {
    if (row.getCompanyCode() == null || row.getCompanyCode().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "기관코드는 필수입니다.");
    }
    if (row.getCompanyName() == null || row.getCompanyName().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "기관명은 필수입니다.");
    }
  }

  /** 대표자(ceo_name) — 저장 직전 ARIA 암호화(빈 값은 null). */
  private void encryptCeo(TbCompany row) {
    row.setCeoName(
        (row.getCeoName() == null || row.getCeoName().isBlank())
            ? null
            : ARIAUtil.ariaEncrypt(row.getCeoName()));
  }

  /** 대표자(ceo_name) — 표시용 복호화(null/빈 값은 그대로). */
  private void decryptCeo(TbCompany row) {
    if (row.getCeoName() != null && !row.getCeoName().isBlank()) {
      row.setCeoName(ARIAUtil.ariaDecrypt(row.getCeoName()));
    }
  }
}
