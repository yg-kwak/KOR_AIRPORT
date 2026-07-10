package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbMenuAuthDetailMapper;
import AirPort.model.MenuPermission;
import AirPort.model.TbLoginUser;
import AirPort.model.TbMenuAuthDetail;
import org.springframework.stereotype.Service;

/**
 * 메뉴 권한 판정·강제 (tb_menu_auth_detail). 화면은 버튼 숨김(1차), 서버는 require*(2차)로 이중 방어. (docs/security.md)
 *
 * <p>정책: 관리자(root_yn='Y')는 전권. 등록/수정은 create_auth 하나로 판정.
 */
@Service
public class MenuAuthService {

  private final TbMenuAuthDetailMapper detailMapper;

  public MenuAuthService(TbMenuAuthDetailMapper detailMapper) {
    this.detailMapper = detailMapper;
  }

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
}
