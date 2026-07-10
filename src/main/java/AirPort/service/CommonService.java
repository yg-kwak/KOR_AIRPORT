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

/** 공통 코드 CRUD. 골든 샘플 — 신규 CRUD 화면은 이 패턴을 따른다. (docs/backend.md) */
@Service
public class CommonService {

  private final TbCommonMapper commonMapper;
  private final AuditService auditService;

  public CommonService(TbCommonMapper commonMapper, AuditService auditService) {
    this.commonMapper = commonMapper;
    this.auditService = auditService;
  }

  public PageResult<TbCommon> list(CommonSearchParam param) {
    long total = commonMapper.selectCount(param);
    return new PageResult<>(
        commonMapper.selectList(param), total, param.getPage(), param.getSize());
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
    if (commonMapper.selectOne(row.getCmmId(), row.getCodeId()) != null) {
      throw new BusinessException(ErrorCode.DUPLICATE);
    }
    commonMapper.insert(row);
    auditService.log(
        actor, AuditService.CREATE, menuId, "공통코드 등록: " + row.getCmmId() + "/" + row.getCodeId());
  }

  @Transactional
  public void update(TbCommon row, TbLoginUser actor, Integer menuId) {
    if (commonMapper.update(row) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(
        actor, AuditService.UPDATE, menuId, "공통코드 수정: " + row.getCmmId() + "/" + row.getCodeId());
  }

  @Transactional
  public void delete(String cmmId, String codeId, TbLoginUser actor, Integer menuId) {
    if (commonMapper.delete(cmmId, codeId) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(actor, AuditService.DELETE, menuId, "공통코드 삭제: " + cmmId + "/" + codeId);
  }
}
