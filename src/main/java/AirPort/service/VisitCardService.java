package AirPort.service;

import AirPort.adapter.biostar.BiostarCard;
import AirPort.common.AccessAreas;
import AirPort.common.CardNames;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 임시·장기 방문의 카드 후보를 <b>고른 출입그룹에 맞는 것만</b>으로 좁힌다. (docs/backend.md)
 *
 * <p>카드 이름이 곧 용도다({@link CardNames}) — {@code 임시234-0001}·{@code 상주234-0001} 은 2·3·4 구역 전용이고,
 * {@code 대여-0001} 처럼 이름에 구역이 없는 카드는 어느 구역에나 쓴다. 구역이 맞지 않는 카드를 쥐어 주면 화면에서는 아무 문제가 없고 <b>문 앞에서야</b> 안
 * 열리는 것을 알게 된다.
 *
 * <p><b>방문유형도 패스구분도 보지 않는다.</b> 임시 등록이든 장기 등록이든 같은 실물 카드를 쓰고, 패스구분은 장비로 나가지 않는 우리 쪽 메모다. 판정의 원천은 이름
 * 하나뿐이다.
 *
 * <p>화면이 먼저 거르지만 판정은 여기가 한다. 저장 요청은 화면을 거치지 않고도 올 수 있다.
 */
@Service
public class VisitCardService {

  /** 출입그룹을 고르기 전에는 어느 구역 카드가 맞는지 알 수 없다 — 후보를 좁힐 근거 자체가 없다. */
  private static final String NEED_AC_GROUP = "사용자 출입그룹을 먼저 선택하세요. 어느 구역 카드를 써야 할지 정해지지 않았습니다.";

  private final TbCardMapper cardMapper;
  private final TbAcGroupMapper acGroupMapper;
  private final CardService cardService;
  private final MenuAuthService menuAuthService;

  public VisitCardService(
      TbCardMapper cardMapper,
      TbAcGroupMapper acGroupMapper,
      CardService cardService,
      MenuAuthService menuAuthService) {
    this.cardMapper = cardMapper;
    this.acGroupMapper = acGroupMapper;
    this.cardService = cardService;
    this.menuAuthService = menuAuthService;
  }

  /**
   * 고른 출입그룹의 구역 번호 — 예: {@code "234"}. 아직 고르지 않았으면 빈 문자열.
   *
   * <p>빈 값은 "거르지 않는다"가 아니라 <b>"아직 고를 수 없다"</b>는 뜻이다.
   */
  public String areaKey(List<Integer> acGroupIds) {
    return (acGroupIds == null || acGroupIds.isEmpty())
        ? ""
        : AccessAreas.key(acGroupMapper.selectNamesByIds(acGroupIds));
  }

  /**
   * 방문객 카드 후보 — 미할당 인원카드 중 <b>이 방문의 구역에 맞는 것만</b>.
   *
   * <p>출입그룹을 아직 고르지 않았으면 빈 목록이다. 전체를 보여 주면 맞지 않는 카드를 고르게 되고, 그 사실은 문 앞에서야 드러난다. 화면이 먼저 "출입그룹을 고르라"고
   * 안내한다.
   *
   * <p>SQL 은 후보를 줄일 뿐이라 판정은 {@link CardNames} 로 한 번 더 한다 — 예전에 하이픈 없이 지은 {@code 임시11111112} 나 구역이 더
   * 긴 {@code 임시1234-0001} 이 LIKE 를 통과해 올라온다.
   */
  public List<TbCard> candidates(
      List<Integer> acGroupIds, String keyword, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    String area = areaKey(acGroupIds);
    if (area.isEmpty()) {
      return List.of();
    }
    List<TbCard> rows =
        cardMapper.selectUnassignedForVisit(keyword, CardService.CARD_TYPE_PERSON, area);
    List<TbCard> matched = new ArrayList<>();
    for (TbCard c : rows) {
      if (usable(c, area)) {
        matched.add(c);
      }
    }
    return matched;
  }

  /**
   * 방문객 카드 스캔 — 읽은 카드가 이 방문에 맞을 때만 돌려준다. 아니면 <b>왜 못 쓰는지</b>를 알린다.
   *
   * <p>"미할당 목록에 없습니다" 만으로는 회수가 안 된 것인지 구역이 다른 것인지 알 수 없다. 손에 든 카드를 내려놓고 다음에 무엇을 할지 정하려면 이유를 보여 줘야
   * 한다.
   */
  public BiostarCard scan(List<Integer> acGroupIds, TbLoginUser actor, Integer menuId) {
    String area = areaKey(acGroupIds);
    if (area.isEmpty()) {
      return BiostarCard.fail(NEED_AC_GROUP);
    }
    BiostarCard read = cardService.scan(actor, menuId);
    if (!read.success()) {
      return read;
    }
    TbCard card = read.cardNo() == null ? null : cardMapper.selectByCardNo(read.cardNo());
    if (card == null) {
      return BiostarCard.fail("등록되지 않은 카드입니다(" + read.cardNo() + "). 카드등록관리에서 먼저 등록하세요.");
    }
    if (!usable(card, area)) {
      return BiostarCard.fail(mismatch("스캔한 카드(" + card.getCardName() + ")", area));
    }
    return read;
  }

  /**
   * 저장 직전 검증 — 화면을 거치지 않은 요청도 같은 규칙으로 막는다.
   *
   * @param cardId 방문객에게 주려는 카드. null 이면 검사할 것이 없다
   * @param who 안내에 쓸 대상 표기(예: "방문객 홍길동")
   */
  public void requireUsable(Integer cardId, List<Integer> acGroupIds, String who) {
    if (cardId == null) {
      return;
    }
    TbCard card = cardMapper.selectById(cardId);
    if (card == null) {
      return; // 카드 존재·상태 검증은 발급 경로(CardIssueService)가 맡는다
    }
    String area = areaKey(acGroupIds);
    if (area.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, who + " 에게 카드를 주려면 " + NEED_AC_GROUP);
    }
    if (!usable(card, area)) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, mismatch(who + " 카드(" + card.getCardName() + ")", area));
    }
  }

  /**
   * 쓸 수 있는 카드인가 — <b>이름만</b> 본다.
   *
   * <p>패스구분(`tb_card.pass_type`)과 대조하지 않는다. 그 값은 카드등록관리 목록·검색에만 쓰는 우리 쪽 메모라 <b>장비로 나가지 않고</b>(사용자
   * payload 의 cards[] 는 카드번호와 BiostarX 카드ID 뿐이다), 이름과 어긋나도 문이 열리고 닫히는 데는 아무 영향이 없다. 반대로 대조하면 이름이
   * 멀쩡한 카드가 <b>이유도 없이 목록에서 사라진다</b> — 화면에는 "카드가 없습니다" 만 남아 무엇을 고쳐야 하는지 알 수 없다.
   */
  private boolean usable(TbCard card, String areaKey) {
    return CardNames.matchesArea(card.getCardName(), areaKey);
  }

  /** 안내 문구 — 무엇이 맞지 않는지와 <b>어떤 카드여야 하는지</b>를 함께 말한다. */
  private String mismatch(String what, String areaKey) {
    return what
        + " 는 이 방문의 출입구역("
        + areaKey
        + ")에 맞지 않습니다. 이름의 구역이 "
        + areaKey
        + " 인 카드이거나, 이름에 구역이 없는 카드(예: 대여-0001)를 사용하세요.";
  }
}
