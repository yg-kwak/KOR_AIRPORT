package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbLoginUserMapper;
import AirPort.mapper.TbMenuAuthDetailMapper;
import AirPort.mapper.TbMenuAuthMapper;
import AirPort.mapper.TbMenuMapper;
import AirPort.model.MenuAuthForm;
import AirPort.model.MenuAuthSearchParam;
import AirPort.model.MenuNode;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.model.TbMenuAuth;
import AirPort.model.TbMenuAuthDetail;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 권한 — 판정·강제(런타임)와 권한메뉴관리 화면 CRUD 를 함께 담당한다. (docs/security.md)
 *
 * <p>화면은 버튼 숨김(1차), 서버는 require*(2차)로 이중 방어. 정책: 관리자(root_yn='Y')는 전권, 등록/수정은 create_auth 로 판정
 * (update_auth 는 create_auth 와 동일하게 저장, 미사용).
 */
@Service
public class MenuAuthService {

  private final TbMenuAuthDetailMapper detailMapper;
  private final TbMenuAuthMapper authMapper;
  private final TbMenuMapper menuMapper;
  private final TbLoginUserMapper userMapper;
  private final AuditService auditService;

  public MenuAuthService(
      TbMenuAuthDetailMapper detailMapper,
      TbMenuAuthMapper authMapper,
      TbMenuMapper menuMapper,
      TbLoginUserMapper userMapper,
      AuditService auditService) {
    this.detailMapper = detailMapper;
    this.authMapper = authMapper;
    this.menuMapper = menuMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  // ── 권한 판정·강제(런타임) ───────────────────────────────────

  /** 사용자의 해당 메뉴 권한을 조회한다. 권한 매핑이 없으면 NONE. */
  public MenuPermission permissionFor(TbLoginUser user, int menuId) {
    if (user == null) {
      return MenuPermission.NONE;
    }
    if (user.isRoot()) {
      return MenuPermission.ALL;
    }
    if (user.getAuthId() == null) {
      return MenuPermission.NONE;
    }
    TbMenuAuthDetail d = detailMapper.selectOne(user.getAuthId(), menuId);
    if (d == null) {
      return MenuPermission.NONE;
    }
    return new MenuPermission(
        "Y".equals(d.getReadAuth()), "Y".equals(d.getCreateAuth()), "Y".equals(d.getDeleteAuth()));
  }

  public void requireRead(TbLoginUser user, int menuId) {
    if (!permissionFor(user, menuId).isCanRead()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "조회 권한이 없습니다.");
    }
  }

  /** 등록·수정 공통(정책: create_auth 로 판정). */
  public void requireCreate(TbLoginUser user, int menuId) {
    if (!permissionFor(user, menuId).isCanCreate()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "등록/수정 권한이 없습니다.");
    }
  }

  public void requireDelete(TbLoginUser user, int menuId) {
    if (!permissionFor(user, menuId).isCanDelete()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "삭제 권한이 없습니다.");
    }
  }

  // ── 권한메뉴관리 화면 CRUD ──────────────────────────────────

  /** 권한 목록 — 검색조건·건수 감사(READ). */
  public PageResult<TbMenuAuth> list(MenuAuthSearchParam param, TbLoginUser actor, Integer menuId) {
    long total = authMapper.selectCount(param);
    auditService.log(
        actor, AuditService.READ, menuId, "권한 목록 조회 (" + searchSummary(param, total) + ")");
    return new PageResult<>(authMapper.selectList(param), total, param.getPage(), param.getSize());
  }

  private String searchSummary(MenuAuthSearchParam param, long total) {
    String kw =
        (param.getKeyword() != null && !param.getKeyword().isBlank())
            ? "검색어=" + param.getKeyword()
            : "검색어=없음";
    return kw
        + ", 정렬="
        + (param.getSort() == null ? "기본" : param.getSort())
        + ' '
        + param.getDir()
        + ", 페이지="
        + param.getPage()
        + ", 결과 "
        + total
        + "건";
  }

  /** 엑셀 다운로드용 전체 목록. 목적(purpose)은 감사 remark. */
  public List<TbMenuAuth> listAllForExcel(
      MenuAuthSearchParam param, TbLoginUser actor, Integer menuId, String purpose) {
    requireRead(actor, menuId);
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "다운로드 목적을 입력해주세요.");
    }
    List<TbMenuAuth> rows = authMapper.selectListAll(param);
    auditService.log(
        actor, AuditService.DOWNLOAD, menuId, "권한 엑셀 다운로드 (" + rows.size() + "건)", purpose);
    return rows;
  }

  /** 권한 선택 트리가 될 전체 메뉴 트리(계층 구조, 권한 필터 없음). */
  public List<MenuNode> menuTree(TbLoginUser actor, Integer menuId) {
    requireRead(actor, menuId);
    return MenuNode.buildTree(menuMapper.selectUseList());
  }

  /** 권한의 메뉴권한 목록(편집 로드용). */
  public List<TbMenuAuthDetail> detail(int authId, TbLoginUser actor, Integer menuId) {
    requireRead(actor, menuId);
    return detailMapper.selectByAuthId(authId);
  }

  @Transactional
  public void create(MenuAuthForm form, TbLoginUser actor, Integer menuId) {
    requireCreate(actor, menuId);
    validate(form);
    TbMenuAuth auth = new TbMenuAuth();
    auth.setAuthName(form.getAuthName().trim());
    authMapper.insert(auth); // authId 생성됨
    saveDetails(auth.getAuthId(), form.getDetails());
    auditService.log(actor, AuditService.CREATE, menuId, "권한 등록: " + auth.getAuthName());
  }

  @Transactional
  public void update(MenuAuthForm form, TbLoginUser actor, Integer menuId) {
    requireCreate(actor, menuId);
    validate(form);
    if (form.getAuthId() == null || authMapper.selectById(form.getAuthId()) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    TbMenuAuth auth = new TbMenuAuth();
    auth.setAuthId(form.getAuthId());
    auth.setAuthName(form.getAuthName().trim());
    authMapper.update(auth);
    saveDetails(form.getAuthId(), form.getDetails());
    auditService.log(actor, AuditService.UPDATE, menuId, "권한 수정: " + auth.getAuthName());
  }

  @Transactional
  public void delete(int authId, TbLoginUser actor, Integer menuId) {
    requireDelete(actor, menuId);
    if (userMapper.countByAuthId(authId) > 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이 권한을 사용 중인 사용자가 있어 삭제할 수 없습니다.");
    }
    detailMapper.deleteByAuthId(authId);
    if (authMapper.delete(authId) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(actor, AuditService.DELETE, menuId, "권한 삭제: authId=" + authId);
  }

  /** 메뉴권한 저장 — 기존 상세를 지우고 플래그가 하나라도 Y 인 행만 다시 넣는다. update_auth=create_auth. */
  private void saveDetails(int authId, List<TbMenuAuthDetail> details) {
    detailMapper.deleteByAuthId(authId);
    if (details == null) {
      return;
    }
    for (TbMenuAuthDetail d : details) {
      if (d.getMenuId() == null) {
        continue;
      }
      boolean any =
          "Y".equals(d.getReadAuth())
              || "Y".equals(d.getCreateAuth())
              || "Y".equals(d.getDeleteAuth());
      if (!any) {
        continue; // 모두 미체크면 저장하지 않음(NONE 과 동일)
      }
      d.setAuthId(authId);
      d.setUpdateAuth(d.getCreateAuth()); // 정책: 수정=등록권한
      detailMapper.insert(d);
    }
  }

  private void validate(MenuAuthForm form) {
    if (form.getAuthName() == null || form.getAuthName().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "권한명은 필수입니다.");
    }
  }
}
