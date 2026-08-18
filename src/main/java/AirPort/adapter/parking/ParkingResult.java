package AirPort.adapter.parking;

/** 주차 차단기(아마노) 연동 결과. */
public record ParkingResult(boolean success, String message) {
  public static ParkingResult ok() {
    return new ParkingResult(true, "OK");
  }

  public static ParkingResult fail(String message) {
    return new ParkingResult(false, message);
  }
}
