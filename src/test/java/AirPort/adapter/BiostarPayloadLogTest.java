package AirPort.adapter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 실패한 BiostarX 요청 본문을 로그에 남길 때의 가림 규칙.
 *
 * <p>본문이 없으면 "not defined" 같은 응답만 보고는 <b>무엇이</b> 잘못됐는지 알 수 없다. 그렇다고 그대로 남기면 성명·사진이 평문으로 쌓인다 — 진단에
 * 필요한 구조(그룹·출입그룹·카드·유효기간)는 남기고 개인정보만 가린다.
 */
class BiostarPayloadLogTest {

  private static final String USER_BODY =
      "{\"User\":{\"name\":\"박상준\",\"photo\":\"iVBORw0KGgoAAAANSUhEUg\","
          + "\"phone\":\"010-1234-5678\",\"user_id\":\"IS000046\","
          + "\"user_group_id\":{\"id\":\"1004\"},"
          + "\"start_datetime\":\"2026-08-07T10:46:00.00Z\","
          + "\"access_groups\":[{\"id\":1},{\"id\":3}],"
          + "\"cards\":[{\"card_id\":\"1111114\"}]}}";

  @Test
  void 개인정보는_가린다() {
    String masked = BiostarSession.maskBody(USER_BODY);

    assertFalse(masked.contains("박상준"), masked);
    assertFalse(masked.contains("010-1234-5678"), masked);
    assertFalse(masked.contains("iVBORw0KGgo"), masked); // 얼굴 사진(BASE64)
  }

  @Test
  void 원인을_찾는_데_필요한_값은_남긴다() {
    String masked = BiostarSession.maskBody(USER_BODY);

    // 이번 오류(code 65717 not defined)의 범인이 바로 이 그룹 ID 였다
    assertTrue(masked.contains("\"user_group_id\":{\"id\":\"1004\"}"), masked);
    assertTrue(masked.contains("\"access_groups\":[{\"id\":1},{\"id\":3}]"), masked);
    assertTrue(masked.contains("\"cards\":[{\"card_id\":\"1111114\"}]"), masked);
    assertTrue(masked.contains("IS000046"), masked); // 어느 방문객인지
    assertTrue(masked.contains("start_datetime"), masked);
  }

  @Test
  void 본문이_없으면_그렇게_적는다() {
    assertTrue(BiostarSession.maskBody(null).contains("본문 없음"));
    assertTrue(BiostarSession.maskBody("  ").contains("본문 없음"));
  }

  @Test
  void 너무_길면_잘라_로그를_지키지_않게_한다() {
    String huge = "{\"a\":\"" + "x".repeat(5000) + "\"}";

    String masked = BiostarSession.maskBody(huge);

    assertTrue(masked.length() < 1600, "길이=" + masked.length());
    assertTrue(masked.endsWith("…"), masked.substring(masked.length() - 20));
  }
}
