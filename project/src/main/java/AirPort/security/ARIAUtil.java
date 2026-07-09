package AirPort.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ARIA-256 문자열 암복호화 유틸. (전자정부 ARIA 엔진 사용)
 *
 * <p>키는 하드코딩하지 않고 {@code app.crypto.aria-key} 프로퍼티(운영은 환경변수/Jasypt)에서 주입한다. (docs/security.md) 저장
 * 형식은 hex 대문자. 암복호화 대상 컬럼은 docs/database.md 의 {@code Enc=Y}.
 */
@Component
public class ARIAUtil {

  private static final int KEY_SIZE = 256;
  private static String key;

  @Value("${app.crypto.aria-key}")
  public void setKey(String ariaKey) {
    ARIAUtil.key = ariaKey;
  }

  /** 평문 → ARIA 암호문(hex 대문자). null/빈문자는 빈문자 반환. */
  public static String ariaEncrypt(String plain) {
    if (plain == null || plain.isEmpty()) {
      return "";
    }
    try {
      ARIAEngine engine = new ARIAEngine(KEY_SIZE, key);
      byte[] p = plain.getBytes("UTF-8");
      int len = p.length;
      if ((len % 16) != 0) {
        len = (len / 16 + 1) * 16;
      }
      byte[] c = new byte[len];
      System.arraycopy(p, 0, c, 0, p.length);
      engine.encrypt(p, c, p.length);
      return ARIAEngine.byteArrayToHex(c).toUpperCase();
    } catch (Exception e) {
      throw new IllegalStateException("ARIA 암호화 실패", e);
    }
  }

  /** ARIA 암호문(hex) → 평문. null/빈문자는 빈문자 반환. */
  public static String ariaDecrypt(String hex) {
    if (hex == null || hex.isEmpty()) {
      return "";
    }
    try {
      ARIAEngine engine = new ARIAEngine(KEY_SIZE, key);
      byte[] c = ARIAEngine.hexToByteArray(hex);
      byte[] p = new byte[c.length];
      engine.decrypt(c, p, p.length);
      return new String(p, "UTF-8").trim();
    } catch (Exception e) {
      throw new IllegalStateException("ARIA 복호화 실패", e);
    }
  }
}
