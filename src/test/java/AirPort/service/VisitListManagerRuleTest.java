package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.common.Affiliations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 방문 목록의 <b>첫 인솔자</b> — 인솔자가 여럿이어도 "첫 줄에 적은 사람"만 보여준다.
 *
 * <p>"첫 번째" 는 {@code tb_visit_manager.seq} 순서다. 그 정렬이 빠지면 DB 가 고르는 아무 행이 나온다 — 틀린 것이 아니라 <b>조회할 때마다
 * 달라진다.</b> 같은 방문을 두 번 보는 사이에 인솔자가 바뀌어 있으면 화면을 믿을 수 없게 된다.
 *
 * <p>조건이 SQL 안에만 있어 목 기반 테스트로는 지킬 수 없다 — 정렬이 사라져도 <b>테스트는 그대로 초록</b>이다.
 */
class VisitListManagerRuleTest {

  private static final String XML = "mapper/TbVisitMapper.xml";

  @Test
  void 첫_인솔자는_seq_순서로_고른다() throws IOException {
    String sql = fragment("joins");

    assertTrue(sql.contains("tb_visit_manager"), "목록이 인솔자를 잇지 않는다:\n" + sql);
    assertTrue(sql.contains("TOP 1"), "인솔자를 한 명으로 좁히지 않아 방문이 여러 줄로 불어난다:\n" + sql);
    assertTrue(
        sql.contains("ORDER BY vm.seq"), "seq 정렬이 없다 — DB 가 고르는 아무 인솔자가 나와 조회할 때마다 달라진다:\n" + sql);
  }

  @Test
  void 소속_판정을_SQL_로_옮겨_적지_않는다() throws IOException {
    // 규칙(자유입력 우선, 없으면 기관명)은 Affiliations 한 곳에만 둔다. SQL 에 다시 적는 순간 규칙이 둘이 되고,
    // 한쪽만 고쳐지면 같은 사람이 화면마다 다른 소속으로 보인다
    String sql = fragment("joins");

    assertTrue(sql.contains("pm.affiliation") && sql.contains("cm.company_name"), sql);
    assertTrue(!sql.contains("ISNULL(NULLIF"), "소속 판정이 SQL 로 새어 나갔다:\n" + sql);
  }

  @Test
  void 소속은_자유입력이_먼저고_없으면_기관명이다() {
    assertEquals("㈜대한기술", Affiliations.of("㈜대한기술", "청주공항공사"));
    assertEquals("청주공항공사", Affiliations.of(null, "청주공항공사"));
    assertEquals("청주공항공사", Affiliations.of("   ", "청주공항공사"));
    assertEquals(null, Affiliations.of(null, null));
  }

  private String fragment(String id) throws IOException {
    String xml;
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(XML)) {
      if (in == null) {
        throw new IOException(XML + " 을 찾을 수 없다");
      }
      xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher m = Pattern.compile("<sql id=\"" + id + "\".*?</sql>", Pattern.DOTALL).matcher(xml);
    assertTrue(m.find(), id + " 조각을 찾지 못했다");
    return m.group();
  }
}
