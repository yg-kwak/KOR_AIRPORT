package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbSystemLogMapper;
import AirPort.model.MenuNode;
import AirPort.model.SystemLogSearchParam;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystemLog;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 감사추적(tb_system_log) 조회. 입력/수정/삭제 없이 조회 전용이다. (docs/security.md)
 *
 * <p>이 화면 자체의 목록 조회·엑셀 다운로드도 감사 이력을 남긴다(누가 감사로그를 열람/추출했는지).
 */
@Service
public class SystemLogService {

  private final TbSystemLogMapper logMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;
  private final MenuService menuService;

  public SystemLogService(
      TbSystemLogMapper logMapper,
      AuditService auditService,
      MenuAuthService menuAuthService,
      MenuService menuService) {
    this.logMapper = logMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
    this.menuService = menuService;
  }

  /** 목록 조회 — 검색조건·건수 감사(READ). */
  public PageResult<TbSystemLog> list(
      SystemLogSearchParam param, TbLoginUser actor, Integer menuId) {
    long total = logMapper.selectCount(param);
    auditService.log(
        actor, AuditService.READ, menuId, "감사추적 조회 (" + searchSummary(param, total) + ")");
    return new PageResult<>(logMapper.selectList(param), total, param.getPage(), param.getSize());
  }

  private String searchSummary(SystemLogSearchParam param, long total) {
    StringBuilder sb = new StringBuilder();
    if (param.getStartDate() != null && !param.getStartDate().isBlank()) {
      sb.append("기간=").append(param.getStartDate()).append('~').append(param.getEndDate());
    } else {
      sb.append("기간=전체");
    }
    if (param.getActionType() != null && !param.getActionType().isEmpty()) {
      sb.append(", 유형=").append(param.getActionType());
    }
    if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
      sb.append(", 검색어=").append(param.getSearchType()).append(':').append(param.getKeyword());
    }
    sb.append(", 결과 ").append(total).append("건");
    return sb.toString();
  }

  /** 엑셀 다운로드용 전체(동일 검색/정렬). 목적(purpose)은 감사 remark. */
  public List<TbSystemLog> listAllForExcel(
      SystemLogSearchParam param, TbLoginUser actor, Integer menuId, String purpose) {
    menuAuthService.requireRead(actor, menuId);
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "다운로드 목적을 입력해주세요.");
    }
    List<TbSystemLog> rows = logMapper.selectListAll(param);
    auditService.log(
        actor, AuditService.DOWNLOAD, menuId, "감사추적 엑셀 다운로드 (" + rows.size() + "건)", purpose);
    return rows;
  }

  /** 유형 필터 옵션(tb_common AT). */
  public List<TbCommon> actionTypes(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return logMapper.selectActionTypes();
  }

  /** 메뉴 필터 옵션 — 로그인 사용자가 read 권한을 가진 메뉴만. */
  public List<MenuNode> menuOptions(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return menuService.readableLeaves(actor);
  }
}
