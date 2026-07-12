package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbLoginUserMapper;
import AirPort.mapper.TbMenuAuthMapper;
import AirPort.mapper.TbMenuMapper;
import AirPort.model.LoginUserSearchParam;
import AirPort.model.TbLoginUser;
import AirPort.model.TbMenu;
import AirPort.security.ARIAUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자(tb_login_user) 관리 CRUD. 골든 샘플(CommonService) 패턴을 따른다. (docs/backend.md)
 *
 * <p>성명(user_name)·비밀번호(password)는 ARIA 암호화 저장(docs/security.md). 화면에는 성명만 복호화해 노출하고 비밀번호는 절대 내리지
 * 않는다. 쓰기는 메뉴 권한(tb_menu_auth_detail)을 서버에서 재검증한다.
 */
@Service
public class LoginUserService {

  private final TbLoginUserMapper userMapper;
  private final TbMenuAuthMapper menuAuthMapper;
  private final TbMenuMapper menuMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public LoginUserService(
      TbLoginUserMapper userMapper,
      TbMenuAuthMapper menuAuthMapper,
      TbMenuMapper menuMapper,
      AuditService auditService,
      MenuAuthService menuAuthService) {
    this.userMapper = userMapper;
    this.menuAuthMapper = menuAuthMapper;
    this.menuMapper = menuMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  /** 목록 조회 — 성명 복호화(표시용) + 검색조건·결과 건수 감사(READ). */
  public PageResult<TbLoginUser> list(
      LoginUserSearchParam param, TbLoginUser actor, Integer menuId) {
    long total = userMapper.selectCount(param);
    List<TbLoginUser> rows = userMapper.selectList(param);
    rows.forEach(r -> r.setUserName(ARIAUtil.ariaDecrypt(r.getUserName())));
    auditService.log(
        actor, AuditService.READ, menuId, "사용자 목록 조회 (" + searchSummary(param, total) + ")");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  private String searchSummary(LoginUserSearchParam param, long total) {
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
  public List<TbLoginUser> listAllForExcel(
      LoginUserSearchParam param, TbLoginUser actor, Integer menuId, String purpose) {
    menuAuthService.requireRead(actor, menuId);
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "다운로드 목적을 입력해주세요.");
    }
    List<TbLoginUser> rows = userMapper.selectListAll(param);
    rows.forEach(r -> r.setUserName(ARIAUtil.ariaDecrypt(r.getUserName())));
    auditService.log(
        actor, AuditService.DOWNLOAD, menuId, "사용자 엑셀 다운로드 (" + rows.size() + "건)", purpose);
    return rows;
  }

  /** 등록/수정 화면 참조 데이터 — 권한/시작메뉴/근무지역 select 옵션. */
  public Map<String, Object> refs(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    // 시작메뉴는 실제 화면(URL 있는 메뉴)만 후보로.
    List<TbMenu> menus =
        menuMapper.selectUseList().stream().filter(m -> m.getMenuUrl() != null).toList();
    Map<String, Object> refs = new LinkedHashMap<>();
    refs.put("auths", menuAuthMapper.selectList());
    refs.put("menus", menus);
    refs.put("locations", userMapper.selectLocationOptions());
    return refs;
  }

  @Transactional
  public void create(TbLoginUser row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(row, true);
    guardRoot(row, actor);
    if (userMapper.selectById(row.getUserId()) != null) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 존재하는 사용자ID 입니다.");
    }
    row.setUserName(ARIAUtil.ariaEncrypt(row.getUserName()));
    row.setPassword(ARIAUtil.ariaEncrypt(row.getPassword()));
    userMapper.insert(row);
    auditService.log(actor, AuditService.CREATE, menuId, "사용자 등록: " + row.getUserId());
  }

  @Transactional
  public void update(TbLoginUser row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    validate(row, false);
    guardRoot(row, actor);
    if (userMapper.selectById(row.getUserId()) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    row.setUserName(ARIAUtil.ariaEncrypt(row.getUserName()));
    // 비밀번호는 입력했을 때만 암호화해 갱신, 비우면 유지(mapper 의 <if> 로 컬럼 자체를 건드리지 않음)
    if (row.getPassword() != null && !row.getPassword().isBlank()) {
      row.setPassword(ARIAUtil.ariaEncrypt(row.getPassword()));
    } else {
      row.setPassword(null);
    }
    userMapper.update(row);
    auditService.log(actor, AuditService.UPDATE, menuId, "사용자 수정: " + row.getUserId());
  }

  @Transactional
  public void delete(String userId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    if (actor != null && actor.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "본인 계정은 삭제할 수 없습니다.");
    }
    if (userMapper.delete(userId) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(actor, AuditService.DELETE, menuId, "사용자 삭제: " + userId);
  }

  private void validate(TbLoginUser row, boolean isCreate) {
    if (row.getUserId() == null || row.getUserId().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자ID는 필수입니다.");
    }
    if (row.getUserName() == null || row.getUserName().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "성명은 필수입니다.");
    }
    if (isCreate && (row.getPassword() == null || row.getPassword().isBlank())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호는 필수입니다.");
    }
  }

  /** 관리자여부(root_yn='Y') 부여는 관리자(root)만 가능 — 권한 상승 방지. */
  private void guardRoot(TbLoginUser row, TbLoginUser actor) {
    if ("Y".equals(row.getRootYn()) && (actor == null || !actor.isRoot())) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한 부여는 관리자만 가능합니다.");
    }
  }
}
