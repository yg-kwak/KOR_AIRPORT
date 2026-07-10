package AirPort.adapter;

/** BiostarX 연동 결과. */
public record BiostarResult(boolean success, String message) {
  public static BiostarResult ok() {
    return new BiostarResult(true, "OK");
  }

  public static BiostarResult fail(String message) {
    return new BiostarResult(false, message);
  }
}
