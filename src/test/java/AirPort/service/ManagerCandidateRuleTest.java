package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 인솔자 후보에 <b>비활성 상태(정지·퇴사·회수·분실)를 올리지 않는다</b> — 이 규칙을 지킨다.
 *
 * <p>출입이 막힌 사람을 인솔자로 세울 수는 없다. 그런데 조건이 SQL 안에만 있어 서비스 단위 테스트로는 지킬 수 없다 — 매퍼가 목이라 조건이 빠져도 <b>테스트는
 * 그대로 초록</b>이고, 화면에서는 그 사람이 후보로 뜬 뒤 저장까지 된다.
 *
 * <p>판정은 코드값을 박지 않고 공통코드가 원천이다(`tb_common(PS).code_tag='true'`). 현장에서 상태를 더 만들어도 태그만 맞추면 자동으로 걸린다.
 * 그래서 <b>코드값이 아니라 code_tag 로 거르는지</b>를 본다.
 */
class ManagerCandidateRuleTest {

  private static final String XML = "mapper/TbPersonMapper.xml";

  @Test
  void 인솔자_후보는_비활성_상태를_공통코드_기준으로_뺀다() throws IOException {
    String sql = select("selectRegular");

    assertTrue(sql.contains("NOT EXISTS"), "비활성 상태를 빼는 조건이 없다 — 정지·퇴사한 사람이 인솔자 후보로 뜬다:\n" + sql);
    assertTrue(
        sql.contains("cmm_id = 'PS'") && sql.contains("code_tag = 'true'"),
        "공통코드(PS.code_tag)가 아니라 다른 기준으로 거르고 있다 — 현장에서 상태를 추가하면 조용히 새어 나온다:\n" + sql);
    // 상태 코드값을 SQL 에 박으면 상태가 늘거나 바뀔 때 여기만 뒤처진다
    assertTrue(
        !sql.contains("status_code = '02'") && !sql.contains("status_code IN ("),
        "상태 코드값이 SQL 에 박혀 있다 — 공통코드로 판정해야 한다:\n" + sql);
  }

  private String select(String id) throws IOException {
    String xml;
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(XML)) {
      if (in == null) {
        throw new IOException(XML + " 을 찾을 수 없다");
      }
      xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher m =
        Pattern.compile("<select id=\"" + id + "\".*?</select>", Pattern.DOTALL).matcher(xml);
    assertTrue(m.find(), id + " 쿼리를 찾지 못했다");
    return m.group();
  }
}
