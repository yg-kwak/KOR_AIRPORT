package AirPort.adapter;

/** BiostarX 로그인 실패(세션 미발급) 등 세션 획득 오류. */
public class BiostarSessionException extends RuntimeException {
  public BiostarSessionException(String message) {
    super(message);
  }
}
