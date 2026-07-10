package AirPort.model;

import lombok.Data;

/**
 * 권한별 메뉴 CRUD 권한 (tb_menu_auth_detail). docs/database.md
 *
 * <p>정책: 등록/수정은 create_auth 하나로 판정한다(update_auth 는 스키마 유지, 미사용).
 */
@Data
public class TbMenuAuthDetail {
  private Integer authId;
  private Integer menuId;
  private String readAuth;
  private String createAuth;
  private String updateAuth; // 미사용(등록/수정은 createAuth 로 판정)
  private String deleteAuth;
}
