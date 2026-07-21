package AirPort.adapter;

/**
 * BiostarX 사용자 그룹 생성/수정 결과. 성공 시 {@code id}(생성된 그룹 ID, 수정은 null 가능), 실패 시 BiostarX 가 준 메시지를 담는다(예:
 * "User group name is duplicated." code=65646).
 */
public record BiostarGroupResult(boolean success, String message, Long id) {
  public static BiostarGroupResult ok(Long id) {
    return new BiostarGroupResult(true, "OK", id);
  }

  public static BiostarGroupResult fail(String message) {
    return new BiostarGroupResult(false, message, null);
  }
}
