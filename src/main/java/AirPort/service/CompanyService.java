package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCompanyMapper;
import AirPort.model.CompanySearchParam;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
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

  private final TbCompanyMapper companyMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public CompanyService(
      TbCompanyMapper companyMapper, AuditService auditService, MenuAuthService menuAuthService) {
    this.companyMapper = companyMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
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

  @Transactional
  public void create(TbCompany row, TbLoginUser actor, Integer menuId) {
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
      companyMapper.insert(row);
    }
    auditService.log(actor, AuditService.CREATE, menuId, "기관 등록: " + row.getCompanyCode());
  }

  @Transactional
  public void update(TbCompany row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    validate(row);
    TbCompany existing = companyMapper.selectById(row.getCompanyCode());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    encryptCeo(row);
    companyMapper.update(row);
    auditService.log(actor, AuditService.UPDATE, menuId, "기관 수정: " + row.getCompanyCode());
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
