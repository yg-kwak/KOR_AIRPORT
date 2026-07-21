package AirPort.adapter;

import java.util.List;

/** BiostarX 장치 조회 결과(성공여부 + 메시지 + 목록). */
public record BiostarDevices(boolean success, String message, List<BiostarDevice> devices) {
  public static BiostarDevices ok(List<BiostarDevice> devices) {
    return new BiostarDevices(true, "OK", devices);
  }

  public static BiostarDevices fail(String message) {
    return new BiostarDevices(false, message, List.of());
  }
}
