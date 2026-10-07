package AirPort.adapter.biostar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * BiostarX 사용자 존재 확인 — <b>'없음'은 BiostarX 가 분명히 그렇게 답할 때만</b>이다.
 *
 * <p>삭제·퇴실은 '없음'이면 장비를 건너뛰고 DB 만 정리한다. 권한 없음·서버 오류까지 '없음'으로 읽으면 장비에 사용자가 남아 그 카드로 문이 계속 열린다 — 판단할 수
 * 없으면 실패로 올려야 한다. 응답 모양은 장비 실측값이다(있음: HTTP 200 + code 0 / 없음: HTTP 400 + code 201).
 */
class BiostarUserExistsTest {

  private final BiostarSession session = mock(BiostarSession.class);
  private final BiostarUserAdapter adapter = new BiostarUserAdapter(new ObjectMapper(), session);

  @SuppressWarnings("unchecked")
  private void answer(int status, String body) throws Exception {
    HttpResponse<String> resp = mock(HttpResponse.class);
    when(resp.statusCode()).thenReturn(status);
    when(resp.body()).thenReturn(body);
    when(session.get(anyString(), anyString(), anyString(), anyString())).thenReturn(resp);
  }

  @Test
  void 있으면_true() throws Exception {
    answer(
        200,
        "{\"User\":{\"user_id\":\"400001\"},\"Response\":{\"code\":\"0\",\"message\":\"Success\"}}");
    assertTrue(adapter.userExists("10.0.0.1", "id", "pw", "400001"));
  }

  @Test
  void BiostarX_가_없다고_답하면_false() throws Exception {
    answer(400, "{\"Response\":{\"code\":\"201\",\"message\":\"User can not be found with id\"}}");
    assertFalse(adapter.userExists("10.0.0.1", "id", "pw", "ZZNONE"));
  }

  @Test
  void 권한_없음이나_서버_오류는_없음이_아니라_실패다() throws Exception {
    answer(403, "{\"Response\":{\"code\":\"20\",\"message\":\"Permission denied\"}}");
    assertThrows(
        BiostarSessionException.class, () -> adapter.userExists("10.0.0.1", "id", "pw", "400001"));

    answer(500, "<html>Internal Server Error</html>");
    assertThrows(
        BiostarSessionException.class, () -> adapter.userExists("10.0.0.1", "id", "pw", "400001"));
  }
}
