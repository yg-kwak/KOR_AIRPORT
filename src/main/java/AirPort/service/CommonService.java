package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCommonMapper;
import AirPort.model.CommonSearchParam;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통 코드 CRUD. 골든 샘플 — 신규 CRUD 화면은 이 패턴을 따른다. (docs/backend.md)
 *
 * <p>쓰기 메서드는 메뉴 권한(tb_menu_auth_detail)을 서버에서 재검증한다 — 화면 버튼 숨김은 1차 방어일 뿐.
 */
@Service
public class CommonService {

  private final TbCommonMapper commonMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public CommonService(
      TbCommonMapper commonMapper, AuditService auditService, MenuAuthService menuAuthService) {
    this.commonMapper = commonMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  public PageResult<TbCommon> list(CommonSearchParam param) {
    long total = commonMapper.selectCount(param);
    return new PageResult<>(
        commonMapper.selectList(param), total, param.getPage(), param.getSize());
  }

  /** 엑셀 다운로드용 전체 목록(동일 검색/정렬, 페이징 없음). 목적(purpose)은 감사 remark 로 기록. */
  public java.util.List<TbCommon> listAllForExcel(
      CommonSearchParam param, TbLoginUser actor, Integer menuId, String purpose) {
    menuAuthService.requireRead(actor, menuId);
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "다운로드 목적을 입력해주세요.");
    }
    java.util.List<TbCommon> rows = commonMapper.selectListAll(param);
    auditService.log(
        actor, AuditService.DOWNLOAD, menuId, "공통코드 엑셀 다운로드 (" + rows.size() + "건)", purpose);
    return rows;
  }

  public TbCommon get(String cmmId, String codeId) {
    TbCommon row = commonMapper.selectOne(cmmId, codeId);
    if (row == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return row;
  }

  @Transactional
  public void create(TbCommon row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (commonMapper.selectOne(row.getCmmId(), row.getCodeId()) != null) {
      throw new BusinessException(ErrorCode.DUPLICATE);
    }
    commonMapper.insert(row);
    auditService.log(
        actor, AuditService.CREATE, menuId, "공통코드 등록: " + row.getCmmId() + "/" + row.getCodeId());
  }

  @Transactional
  public void update(TbCommon row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    if (commonMapper.update(row) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(
        actor, AuditService.UPDATE, menuId, "공통코드 수정: " + row.getCmmId() + "/" + row.getCodeId());
  }

  @Transactional
  public void delete(String cmmId, String codeId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    if (commonMapper.delete(cmmId, codeId) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(actor, AuditService.DELETE, menuId, "공통코드 삭제: " + cmmId + "/" + codeId);
  }
}
