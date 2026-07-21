package AirPort.adapter;

import java.util.List;

/** BiostarX 사용자 그룹 조회 결과(성공여부 + 메시지 + 목록). */
public record BiostarUserGroups(boolean success, String message, List<BiostarUserGroup> groups) {
  public static BiostarUserGroups ok(List<BiostarUserGroup> groups) {
    return new BiostarUserGroups(true, "OK", groups);
  }

  public static BiostarUserGroups fail(String message) {
    return new BiostarUserGroups(false, message, List.of());
  }
}
