package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 방문 목록의 <b>작업번호 검색</b>과 <b>미반납 건수</b> — 둘 다 SQL 안에만 있는 규칙이라 목 테스트로는 지킬 수 없다.
 *
 * <p>어긋나도 화면은 멀쩡해 보인다 — 숫자만 조용히 틀린다. 그래서 쿼리 자체를 읽어 못 박는다.
 */
class VisitListSummaryRuleTest {

  private static final String XML = "mapper/TbVisitMapper.xml";

  @Test
  void 작업번호는_TRY_CAST_로_받는다() throws IOException {
    // visit_no 는 int 다. 검색어에 글자가 섞이면 CAST 는 SQL 오류(245)로 500 을 낸다 —
    // 사용자가 번호 칸에 이름을 치는 것은 흔한 일이라 화면이 통째로 죽는다. TRY_CAST 는 NULL 을 내 결과만 빈다
    String sql = fragment("searchWhere");

    assertTrue(sql.contains("TRY_CAST(#{keyword} AS int)"), "작업번호를 안전하게 받지 않는다:\n" + sql);
    assertTrue(
        !Pattern.compile("[^_]CAST\\(#\\{keyword\\}").matcher(sql).find(),
        "검색어를 그냥 CAST 한다 — 숫자가 아니면 500 이다:\n" + sql);
  }

  @Test
  void 미반납_건수는_목록과_같은_식으로_센다() throws IOException {
    // 미반납(VS05)은 저장되는 상태가 아니라 조회 시각으로 계산한다. 세는 곳이 식을 따로 적으면
    // 목록의 배지와 위의 숫자가 어긋난다 — 어느 쪽이 맞는지 화면으로는 알 수 없다
    String sql = select("selectUnreturnedCount");

    assertTrue(sql.contains("refid=\"statusExpr\""), "상태 판정식을 따로 적었다:\n" + sql);
    assertTrue(sql.contains("'VS05'"), "미반납이 아닌 것을 세고 있다:\n" + sql);
    assertTrue(
        sql.contains("refid=\"searchWhere\""), "검색 조건이 목록과 다르다 — 조회결과와 무관한 건수가 나온다:\n" + sql);
  }

  private String select(String id) throws IOException {
    return find("<select id=\"" + id + "\".*?</select>", id);
  }

  private String fragment(String id) throws IOException {
    return find("<sql id=\"" + id + "\".*?</sql>", id);
  }

  private String find(String regex, String id) throws IOException {
    String xml;
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(XML)) {
      if (in == null) {
        throw new IOException(XML + " 을 찾을 수 없다");
      }
      xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(xml);
    assertTrue(m.find(), id + " 을 찾지 못했다");
    return m.group();
  }
}
