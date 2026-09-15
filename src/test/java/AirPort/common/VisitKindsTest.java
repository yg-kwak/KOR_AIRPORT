package AirPort.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 방문구분(인원/차량/인원+차량) — 고른 쪽은 있어야 하고, 고르지 않은 쪽은 없어야 한다.
 *
 * <p>화면은 고르지 않은 칸을 감추고 빈 값을 보내지만 최종 판정은 서버다. 감춘 칸의 값을 조용히 버리면 "인원"으로 바꿔 저장한 순간 차량과 그 카드가 이유 없이
 * 사라지므로, 어긋나면 거절한다. 임시·장기·키오스크 세 화면이 이 한 곳을 지난다.
 */
class VisitKindsTest {

  @Test
  void 고른_쪽은_있어야_한다() {
    assertThrows(
        BusinessException.class,
        () -> VisitKinds.check(VisitKinds.PERSON, false, false, false, false));
    assertThrows(
        BusinessException.class,
        () -> VisitKinds.check(VisitKinds.CAR, false, false, false, false));
    assertThrows(
        BusinessException.class, () -> VisitKinds.check(VisitKinds.BOTH, true, false, true, false));
    assertThrows(
        BusinessException.class, () -> VisitKinds.check(VisitKinds.BOTH, false, true, false, true));
    assertDoesNotThrow(() -> VisitKinds.check(VisitKinds.PERSON, true, false, true, false));
    assertDoesNotThrow(() -> VisitKinds.check(VisitKinds.CAR, false, true, false, true));
    assertDoesNotThrow(() -> VisitKinds.check(VisitKinds.BOTH, true, true, true, true));
  }

  @Test
  void 고르지_않은_쪽은_없어야_한다() {
    // 인원인데 차량이 딸려 오면 거절 — 감춰진 칸의 값이 조용히 지워지는 것보다 낫다
    BusinessException car =
        assertThrows(
            BusinessException.class,
            () -> VisitKinds.check(VisitKinds.PERSON, true, true, true, false));
    assertTrue(car.getMessage().contains("차량"), car.getMessage());
    assertThrows(
        BusinessException.class,
        () -> VisitKinds.check(VisitKinds.PERSON, true, false, true, true));
    BusinessException vis =
        assertThrows(
            BusinessException.class,
            () -> VisitKinds.check(VisitKinds.CAR, true, true, false, true));
    assertTrue(vis.getMessage().contains("방문객"), vis.getMessage());
    assertThrows(
        BusinessException.class, () -> VisitKinds.check(VisitKinds.CAR, false, true, true, true));
  }

  @Test
  void 값이_없거나_모르는_값이면_거절한다() {
    assertThrows(BusinessException.class, () -> VisitKinds.check(null, true, true, true, true));
    assertThrows(BusinessException.class, () -> VisitKinds.check("VK01", true, true, true, true));
  }

  @Test
  void 옛_방문은_명단으로_되짚고_둘_다_없으면_모든_칸을_연다() {
    assertEquals(VisitKinds.PERSON, VisitKinds.infer(true, false));
    assertEquals(VisitKinds.CAR, VisitKinds.infer(false, true));
    assertEquals(VisitKinds.BOTH, VisitKinds.infer(true, true));
    assertEquals(VisitKinds.BOTH, VisitKinds.infer(false, false));
  }

  @Test
  void 컬럼은_조회_등록_수정_세_SQL_모두에_있다() throws IOException {
    // 어느 하나에서 빠지면 저장은 되는데 다시 열었을 때 구분이 사라지거나(조회), 고른 값이 남지 않는다(수정)
    String xml;
    try (InputStream in =
        getClass().getClassLoader().getResourceAsStream("mapper/TbVisitMapper.xml")) {
      xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(block(xml, "<sql id=\"cols\"", "</sql>").contains("v.visit_kind"), "조회 컬럼에 없다");
    assertTrue(
        block(xml, "<insert id=\"insert\"", "</insert>").contains("#{visitKind}"), "INSERT 에 없다");
    assertTrue(
        block(xml, "<update id=\"update\"", "</update>").contains("visit_kind = #{visitKind}"),
        "UPDATE 에 없다");
  }

  private static String block(String xml, String open, String close) {
    Matcher m =
        Pattern.compile(Pattern.quote(open) + ".*?" + Pattern.quote(close), Pattern.DOTALL)
            .matcher(xml);
    assertTrue(m.find(), open + " 을 찾지 못했다");
    return m.group();
  }
}
