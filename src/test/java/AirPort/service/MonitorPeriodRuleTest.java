package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 실시간 이벤트의 <b>허가구역과 허가기간은 같은 방문에서 나온다</b> — 이 규칙을 지킨다.
 *
 * <p>규칙이 SQL 안에만 있어 단위 테스트로는 지킬 수 없다. 두 매퍼를 각각 목으로 세우므로 한쪽 쿼리가 다른 방문을 고르도록 바뀌어도 <b>테스트는 그대로
 * 초록</b>이고, 화면에는 구역과 기간이 서로 다른 방문의 값으로 섞여 나온다. 둘 다 그럴듯한 값이라 눈으로도 알아채기 어렵다.
 *
 * <p>그래서 mapper XML 자체를 읽어 <b>두 쿼리가 같은 조각을 쓰는지</b>만 본다. 조각 하나가 원천이면 기준이 갈릴 수 없다.
 */
class MonitorPeriodRuleTest {

  private static final String XML = "mapper/TbVisitMapper.xml";

  /** 방문 선택 기준이 담긴 조각. 여기를 고치면 두 쿼리가 함께 따라온다. */
  private static final String FRAGMENT = "latestVisitOfPerson";

  @Test
  void 허가구역과_허가기간은_같은_방문_조각을_쓴다() throws IOException {
    String xml = read();

    assertTrue(
        xml.contains("<sql id=\"" + FRAGMENT + "\">"),
        "방문 선택 기준 조각(" + FRAGMENT + ")이 없다. 기준이 쿼리마다 흩어지면 구역과 기간이 다른 방문에서 나온다.");
    assertIncludesFragment(xml, "selectAcGroupNamesByPerson");
    assertIncludesFragment(xml, "selectLatestVisitByPerson");
  }

  /** 그 select 안에 조각 include 가 있는지 — 자기 손으로 방문을 고르면 기준이 갈린다. */
  private void assertIncludesFragment(String xml, String selectId) {
    Matcher m =
        Pattern.compile("<select id=\"" + selectId + "\".*?</select>", Pattern.DOTALL).matcher(xml);
    assertTrue(m.find(), selectId + " 쿼리를 찾지 못했다");
    assertTrue(
        m.group().contains("<include refid=\"" + FRAGMENT + "\"/>"),
        selectId + " 가 " + FRAGMENT + " 조각을 쓰지 않는다 — 허가구역과 허가기간이 서로 다른 방문에서 나올 수 있다");
  }

  private String read() throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(XML)) {
      if (in == null) {
        throw new IOException(XML + " 을 찾을 수 없다");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
