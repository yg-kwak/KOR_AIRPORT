package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * 카드 스캔 — 본문 없이 보내면 장비가 일반 카드와 스마트카드를 모두 읽고, 응답 모양이 둘로 갈린다.
 *
 * <p>스마트카드의 {@code card_id} 는 암호화 값이라 읽을 때마다 바뀐다 — 화면·저장은 {@code display_card_id} 로 한다. 응답 본문은 장비
 * 실측값을 줄인 것이다.
 */
class BiostarScanCardTest {

  private static final String NORMAL =
      "{\"Card\":{\"card_type\":{\"id\":\"0\",\"name\":\"\",\"type\":\"1\"},\"card_id\":\"1672337446\","
          + "\"id\":\"0\",\"display_card_id\":\"1672337446\"},"
          + "\"DeviceResponse\":{\"rows\":[{\"id\":\"543737030\",\"code\":\"0\"}],\"result\":\"true\"},"
          + "\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}";

  private static final String SMART =
      "{\"SmartCard\":{\"card_id\":{\"card_id\":\"37373121958526369380543361548\","
          + "\"card_type\":{\"id\":\"2\",\"name\":\"\",\"type\":\"2\"},\"issue_count\":\"5\",\"id\":\"0\","
          + "\"display_card_id\":\"2026001001\",\"card_type_id\":{\"id\":\"2\",\"type\":\"2\"}},"
          + "\"user_id\":{\"user_id\":\"1\",\"name\":\"Administrator\"}},"
          + "\"DeviceResponse\":{\"rows\":[{\"id\":\"543737030\",\"code\":\"0\"}],\"result\":\"true\"},"
          + "\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}";

  private final BiostarSession session = mock(BiostarSession.class);
  private final BiostarCardAdapter adapter = new BiostarCardAdapter(new ObjectMapper(), session);

  @SuppressWarnings("unchecked")
  private void answer(String body) throws Exception {
    HttpResponse<String> resp = mock(HttpResponse.class);
    when(resp.statusCode()).thenReturn(200);
    when(resp.body()).thenReturn(body);
    when(session.post(anyString(), anyString(), anyString(), anyString(), isNull()))
        .thenReturn(resp);
  }

  @Test
  void 일반_카드는_Card_의_card_id() throws Exception {
    answer(NORMAL);
    BiostarCard c = adapter.scanCard("10.0.0.1", "id", "pw", "543737030");
    assertTrue(c.success());
    assertEquals("1672337446", c.cardNo());
  }

  @Test
  void 스마트카드는_암호화된_card_id_가_아니라_display_card_id() throws Exception {
    answer(SMART);
    BiostarCard c = adapter.scanCard("10.0.0.1", "id", "pw", "543737030");
    assertTrue(c.success());
    assertEquals("2026001001", c.cardNo());
  }

  @Test
  void 본문_없이_보낸다() throws Exception {
    // 본문이 있으면 장비가 스마트카드를 읽지 않는다
    answer(NORMAL);
    adapter.scanCard("10.0.0.1", "id", "pw", "543737030");
    verify(session)
        .post(
            anyString(),
            anyString(),
            anyString(),
            eq("/api/devices/543737030/scan_card"),
            isNull());
  }

  @Test
  void 읽은_카드가_없으면_실패다() throws Exception {
    answer("{\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}");
    BiostarCard c = adapter.scanCard("10.0.0.1", "id", "pw", "543737030");
    assertFalse(c.success());
    assertNull(c.cardNo());
  }
}
