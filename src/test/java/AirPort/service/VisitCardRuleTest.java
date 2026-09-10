package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import AirPort.adapter.biostar.BiostarCard;
import AirPort.common.exception.BusinessException;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 방문 카드 후보는 <b>고른 구역에 맞는 카드만</b>이다 — {@code 임시234-0001}·{@code 상주234-0001}·{@code 대여-0001}.
 *
 * <p>구역이 맞지 않는 카드를 쥐어 주면 화면에서는 아무 문제가 없고 <b>문 앞에서야</b> 안 열리는 것을 알게 된다. 그래서 이 규칙이 조용히 헐거워지는
 * 자리(조회·스캔·저장)를 하나씩 못 박는다.
 */
class VisitCardRuleTest {

  private static final String XML = "mapper/TbCardMapper.xml";
  private static final List<Integer> AREA_234 = List.of(2, 3, 4);

  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbAcGroupMapper acGroupMapper = mock(TbAcGroupMapper.class);
  private final CardService cardService = mock(CardService.class);
  private final VisitCardService service =
      new VisitCardService(cardMapper, acGroupMapper, cardService, mock(MenuAuthService.class));

  private final TbLoginUser actor = new TbLoginUser();

  /** 2·3·4 구역을 고른 방문 — 카드 이름의 구역은 "234". */
  private void areaOn234() {
    when(acGroupMapper.selectNamesByIds(AREA_234)).thenReturn(List.of("인원구역2", "인원구역3", "인원구역4"));
  }

  private static TbCard card(String cardName, String passTypeName) {
    TbCard c = new TbCard();
    c.setCardId(7);
    c.setCardName(cardName);
    c.setPassTypeName(passTypeName);
    return c;
  }

  // ── 후보 조회 ─────────────────────────────────────────────────────────────

  @Test
  void 후보는_구역으로_좁혀_조회한다() {
    areaOn234();
    when(cardMapper.selectUnassignedForVisit(any(), any(), any())).thenReturn(List.of());

    service.candidates(AREA_234, "kw", actor, 1);

    // 방문유형은 넘기지 않는다 — 실물 카드는 3종뿐이고 어느 방문에서나 같은 카드를 쓴다
    verify(cardMapper).selectUnassignedForVisit("kw", "CDT01", "234");
  }

  @Test
  void 유형이_달라도_구역이_같으면_후보다() {
    // 임시 등록이든 장기 등록이든 임시·상주·대여 카드를 같이 쓴다
    areaOn234();
    when(cardMapper.selectUnassignedForVisit(any(), any(), any()))
        .thenReturn(
            List.of(
                card("임시234-0001", "임시"),
                card("상주234-0001", "상주"),
                card("대여-0001", "대여"),
                card("임시23-0001", "임시"),
                card("임시2345-0001", "임시")));

    List<String> names =
        service.candidates(AREA_234, null, actor, 1).stream().map(TbCard::getCardName).toList();

    assertEquals(List.of("임시234-0001", "상주234-0001", "대여-0001"), names);
  }

  @Test
  void 출입그룹을_고르기_전에는_후보가_없다() {
    // 전체를 보여 주면 맞지 않는 카드를 고르게 되고, 그 사실은 문 앞에서야 드러난다
    assertTrue(service.candidates(List.of(), null, actor, 1).isEmpty());
    verifyNoInteractions(cardMapper);
  }

  @Test
  void 규칙_밖의_이름은_LIKE_를_통과해도_후보에서_뺀다() {
    areaOn234();
    // '임시11111112' 는 숫자 뒤에 하이픈이 없어 SQL 의 '구역 없는 카드' 갈래에 걸려 올라온다
    when(cardMapper.selectUnassignedForVisit(any(), any(), any()))
        .thenReturn(List.of(card("임시234-0001", "임시"), card("임시11111112", "임시")));

    List<TbCard> rows = service.candidates(AREA_234, null, actor, 1);

    assertEquals(1, rows.size());
    assertEquals("임시234-0001", rows.get(0).getCardName());
  }

  @Test
  void 패스구분이_이름과_달라도_후보에서_빼지_않는다() {
    // 패스구분은 카드등록관리 목록·검색에만 쓰는 우리 쪽 메모라 장비로 나가지 않는다 — 이름과 어긋나도 문은 똑같이 열린다.
    // 대조해서 빼면 이름이 멀쩡한 카드가 이유도 없이 사라지고, 화면에는 "카드가 없습니다" 만 남는다
    areaOn234();
    when(cardMapper.selectUnassignedForVisit(any(), any(), any()))
        .thenReturn(
            List.of(card("상주234-0001", "장기"), card("장기234-0001", "상주"), card("임시234-0001", null)));

    List<String> names =
        service.candidates(AREA_234, null, actor, 1).stream().map(TbCard::getCardName).toList();

    assertEquals(List.of("상주234-0001", "장기234-0001", "임시234-0001"), names);
  }

  // ── 스캔 ─────────────────────────────────────────────────────────────────

  @Test
  void 스캔한_카드가_구역과_다르면_어떤_카드여야_하는지_알린다() {
    areaOn234();
    when(cardService.scan(actor, 1)).thenReturn(BiostarCard.ok("0", "12345"));
    when(cardMapper.selectByCardNo("12345")).thenReturn(card("임시23-0001", "임시"));

    BiostarCard res = service.scan(AREA_234, actor, 1);

    assertFalse(res.success());
    // "목록에 없습니다" 만으로는 회수가 안 된 것인지 구역이 다른 것인지 알 수 없다
    assertTrue(res.message().contains("234"), res.message());
  }

  @Test
  void 구역이_정해지기_전에는_리더를_부르지도_않는다() {
    assertFalse(service.scan(List.of(), actor, 1).success());
    verify(cardService, never()).scan(any(), any());
  }

  @Test
  void 구역이_없는_카드를_스캔하면_그대로_돌려준다() {
    areaOn234();
    when(cardService.scan(actor, 1)).thenReturn(BiostarCard.ok("0", "12345"));
    when(cardMapper.selectByCardNo("12345")).thenReturn(card("대여-0001", "대여"));

    assertTrue(service.scan(AREA_234, actor, 1).success());
  }

  // ── 저장 직전 검증 ────────────────────────────────────────────────────────

  @Test
  void 저장_때도_같은_규칙으로_막는다() {
    // 화면 필터는 1차 방어일 뿐이다 — 저장 요청은 화면을 거치지 않고도 온다
    areaOn234();
    when(cardMapper.selectById(7)).thenReturn(card("임시345-0001", "임시"));

    BusinessException e =
        assertThrows(BusinessException.class, () -> service.requireUsable(7, AREA_234, "방문객 홍길동"));
    assertTrue(e.getMessage().contains("234"), e.getMessage());
  }

  @Test
  void 구역을_고르지_않고_카드를_주려_하면_막는다() {
    when(cardMapper.selectById(7)).thenReturn(card("임시234-0001", "임시"));

    assertThrows(BusinessException.class, () -> service.requireUsable(7, List.of(), "방문객 홍길동"));
  }

  @Test
  void 카드를_주지_않으면_검사할_것이_없다() {
    service.requireUsable(null, AREA_234, "방문객 홍길동");
    verify(cardMapper, never()).selectById(anyInt());
  }

  // ── SQL 조건(목 테스트로는 지킬 수 없는 자리) ─────────────────────────────

  @Test
  void 후보_조회_SQL_이_두_갈래로_좁힌다() throws IOException {
    // 조건이 SQL 안에만 있어 목 기반 테스트로는 지킬 수 없다 — 빠져도 초록이고, 화면에서만 새어 나온다
    String sql = select("selectUnassignedForVisit");

    assertTrue(sql.contains("c.card_name LIKE '%' + #{areaKey} + '-%'"), "구역으로 거르지 않는다:\n" + sql);
    assertTrue(sql.contains("c.card_name NOT LIKE '%[0-9]-%'"), "구역 없는 카드(대여)가 후보에서 빠진다:\n" + sql);
    assertTrue(!sql.contains("pass_type"), "방문유형으로 거르고 있다 — 임시 등록에서 상주·대여 카드가 사라진다:\n" + sql);
    assertTrue(
        sql.contains("c.person_id IS NULL") && sql.contains("c.car_id IS NULL"),
        "이미 발급된 카드가 후보로 올라온다:\n" + sql);
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
