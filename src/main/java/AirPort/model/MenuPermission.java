package AirPort.model;

/**
 * 화면(메뉴) 단위 권한 판정 결과. 등록/수정은 canCreate 하나로 판정한다(정책).
 *
 * <p>화면(Thymeleaf ${perm.canCreate})과 서버 검증(MenuAuthService) 양쪽에서 사용.
 */
public class MenuPermission {

  public static final MenuPermission NONE = new MenuPermission(false, false, false);
  public static final MenuPermission ALL = new MenuPermission(true, true, true);

  private final boolean canRead;
  private final boolean canCreate; // 등록+수정
  private final boolean canDelete;

  public MenuPermission(boolean canRead, boolean canCreate, boolean canDelete) {
    this.canRead = canRead;
    this.canCreate = canCreate;
    this.canDelete = canDelete;
  }

  public boolean isCanRead() {
    return canRead;
  }

  public boolean isCanCreate() {
    return canCreate;
  }

  public boolean isCanDelete() {
    return canDelete;
  }
}
