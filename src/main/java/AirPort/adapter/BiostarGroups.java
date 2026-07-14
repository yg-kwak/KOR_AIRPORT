package AirPort.adapter;

import java.util.List;

/** BiostarX 출입그룹 조회 결과(성공여부 + 메시지 + 목록). */
public record BiostarGroups(boolean success, String message, List<BiostarGroup> groups) {
  public static BiostarGroups ok(List<BiostarGroup> groups) {
    return new BiostarGroups(true, "OK", groups);
  }

  public static BiostarGroups fail(String message) {
    return new BiostarGroups(false, message, List.of());
  }
}
