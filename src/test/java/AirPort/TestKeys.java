package AirPort;

import AirPort.security.ARIAUtil;

/**
 * 테스트용 암호화 키 준비.
 *
 * <p>{@link ARIAUtil} 은 프로퍼티로 주입받은 <b>정적 키</b>를 쓴다. 어떤 테스트가 먼저 키를 넣어 주면 뒤따르는 테스트도 우연히 통과해서, 실행 순서가
 * 바뀌는 순간 "ARIA 암호화 실패"로 깨진다. 키가 필요한 테스트는 {@code @BeforeAll} 에서 이 메서드를 불러 <b>스스로</b> 준비한다.
 */
final class TestKeys {

  private static final String ARIA_KEY = "0123456789abcdef0123456789abcdef"; // 테스트 전용 더미

  private TestKeys() {}

  /** 여러 번 불러도 안전하다. */
  static void init() {
    new ARIAUtil().setKey(ARIA_KEY);
  }
}
