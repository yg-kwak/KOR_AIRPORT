package AirPort.service;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.model.CardForm;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 카드를 저장·발급하기 전에 통과해야 하는 규칙 — 필수값·공통코드 검증과 <b>발급 가능 여부</b>. (docs/backend.md)
 *
 * <p>{@link CardService} 에서 분리했다. 카드 서비스는 장비(BiostarX) 연동과 저장 흐름을 맡고, "이 값으로 저장해도 되는가 / 이 카드를 지금
 * 발급해도 되는가" 는 여기로 모았다. 카드등록관리·정규인원 카드탭·방문 카드·기관차량이 <b>같은 규칙</b>을 쓰게 하는 것이 목적이다.
 */
@Service
public class CardIssueService {

  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;
  private final CodeValidationService codeValidator;

  public CardIssueService(
      TbCardMapper cardMapper, TbCommonMapper commonMapper, CodeValidationService codeValidator) {
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
    this.codeValidator = codeValidator;
  }

  /** 카드 상태(tb_common CS)의 code_tag='Y' 면 차단 대상(= 정상 아님). 코드가 없으면 비차단으로 본다. */
  public boolean isBlocked(String cardStatus) {
    TbCommon cs = commonMapper.selectOne("CS", cardStatus);
    return cs != null && "Y".equals(cs.getCodeTag());
  }

  /**
   * <b>정상이 아닌 카드는 새로 발급할 수 없다.</b> 분실·정지·반납·폐기 카드가 그대로 다른 사람에게 나가면 장비에서 차단된 카드를 쥐여 주는 셈이라 문이 열리지
   * 않고, 분실 카드는 회수 확인 없이 재사용된다.
   *
   * <p>판정은 블랙리스트와 같은 기준({@code CS.code_tag='Y'})을 쓴다 — 상태를 새로 추가해도 코드만 맞추면 함께 적용된다.
   *
   * <p>이미 그 대상이 들고 있던 카드({@code held})는 검사하지 않는다. 분실 신고된 카드를 그대로 둔 채 다른 항목만 고치는 저장까지 막으면 정정이
   * 불가능해진다. 회수 후 재사용은 상태가 정상이면 종전대로 된다.
   *
   * @param cardId 배정하려는 카드 (null 이면 통과)
   * @param held 이 대상이 이미 보유 중이던 카드ID 집합 (null 허용)
   * @param who 오류 문구에 쓸 대상 표시(인원ID·차량번호 등)
   */
  public void requireIssuable(Integer cardId, Set<Integer> held, String who) {
    if (cardId == null || (held != null && held.contains(cardId))) {
      return;
    }
    TbCard card = cardMapper.selectById(cardId);
    if (card == null || !isBlocked(card.getCardStatus())) {
      return; // 아직 행이 없는 새 카드번호이거나, 정상 카드
    }
    TbCommon cs = commonMapper.selectOne("CS", card.getCardStatus());
    String name =
        (cs == null || cs.getCodeName() == null) ? card.getCardStatus() : cs.getCodeName();
    throw new BusinessException(
        ErrorCode.INVALID_INPUT,
        "카드 "
            + card.getBiostarCardValue()
            + " 은(는) 상태가 '"
            + name
            + "' 이라 발급할 수 없습니다"
            + (who == null || who.isBlank() ? "" : " (" + who + ")")
            + ". 카드등록관리에서 상태를 '정상'으로 되돌린 뒤 발급하세요.");
  }

  /** 카드번호만 온 경우에도 기존 행을 찾아 발급 가능 여부를 본다. */
  public void requireIssuableByNo(String cardNo, Set<Integer> held, String who) {
    if (cardNo == null || cardNo.isBlank()) {
      return;
    }
    TbCard known = cardMapper.selectByCardNo(cardNo);
    requireIssuable(known == null ? null : known.getCardId(), held, who);
  }

  /**
   * <b>지금 저장하려는 상태</b>가 정상인지 — 대상(인원·차량)에게 배정하는 저장에만 건다.
   *
   * <p>{@link #requireIssuable} 은 <b>이미 저장된</b> 카드의 상태를 보므로, 카드를 새로 만들면서 처음부터 '분실'로 고르면 검사할 대상이 없어
   * 그대로 통과한다. 발급 화면이 카드상태를 직접 고를 수 있게 되어 있어 실제로 가능한 경로다.
   *
   * <p>카드등록관리(마스터)에는 걸지 않는다 — 거기서는 분실·폐기 카드를 <b>기록</b>해야 하기 때문이다.
   */
  public void requireIssuableStatus(String cardStatus, String cardNo, String who) {
    if (!isBlocked(cardStatus)) {
      return;
    }
    TbCommon cs = commonMapper.selectOne("CS", cardStatus);
    String name = (cs == null || cs.getCodeName() == null) ? cardStatus : cs.getCodeName();
    throw new BusinessException(
        ErrorCode.INVALID_INPUT,
        "카드상태가 '"
            + name
            + "' 인 카드는 발급할 수 없습니다"
            + (who == null || who.isBlank() ? "" : " (" + who + ")")
            + ". 발급은 '정상' 상태로만 가능합니다"
            + (cardNo == null || cardNo.isBlank() ? "" : " — 카드번호 " + cardNo)
            + ".");
  }

  /** 인원이 현재 들고 있는 카드ID — 재저장 시 자기 카드까지 막지 않도록 기준으로 쓴다. */
  public Set<Integer> heldCardIds(String personId) {
    Set<Integer> ids = new HashSet<>();
    for (TbCard c : cardMapper.selectByPerson(personId)) {
      ids.add(c.getCardId());
    }
    return ids;
  }

  /** 카드 마스터 필수값 — 인원 화면(CardForm)과 같은 기준. 차량 카드는 패스구분을 받지 않는다. */
  public void validateCard(TbCard row, TbCard prev) {
    require(row.getBiostarCardValue(), "카드번호");
    require(row.getCardType(), "카드구분");
    if (!CardService.CARD_TYPE_CAR.equals(row.getCardType())) {
      require(row.getPassType(), "패스구분");
    }
    require(row.getCardName(), "카드명칭");
    require(row.getCardStatus(), "카드상태");
    // 엑셀 일괄등록은 코드ID 를 직접 적으므로 없는 코드가 그대로 저장되지 않게 막는다
    // (수정은 기존 값과 다를 때만 — 코드가 나중에 정리돼도 기존 행 수정이 막히지 않게)
    codeValidator.validate(
        "CDT", row.getCardType(), "카드구분", prev == null ? null : prev.getCardType());
    codeValidator.validate(
        "PT", row.getPassType(), "패스구분", prev == null ? null : prev.getPassType());
    codeValidator.validate(
        "CS", row.getCardStatus(), "카드상태", prev == null ? null : prev.getCardStatus());
  }

  /** 카드 필수값 — 화면(card-list.js)과 같은 기준. 카드구분은 서버가 고정하므로 검사 대상이 아니다. */
  public void validateForm(CardForm form, TbCard prev) {
    require(form.getCardNo(), "카드번호");
    require(form.getPassType(), "패스구분");
    require(form.getCardName(), "카드명칭");
    require(form.getCardStatus(), "카드상태");
    // 카드등록관리와 같은 규칙 — 화면 팝업 값이라도 서버가 최종 확인한다(클라이언트 값 불신)
    codeValidator.validate(
        "PT", form.getPassType(), "패스구분", prev == null ? null : prev.getPassType());
    codeValidator.validate(
        "CS", form.getCardStatus(), "카드상태", prev == null ? null : prev.getCardStatus());
  }

  /** 저장 전 보정 — 차량 카드의 패스구분은 화면에서 무엇이 오든 비운다(화면 값 불신). */
  public static void normalize(TbCard row) {
    if (CardService.CARD_TYPE_CAR.equals(row.getCardType())) {
      row.setPassType(null);
    }
    row.setFeePaidDt(blankToNull(row.getFeePaidDt()));
  }

  /** 발급 대상 설명 — 인원/차량 어느 쪽에든 붙어 있으면 그 대상을, 미발급이면 null. */
  public static String issuedTo(TbCard card) {
    if (card.getPersonId() != null) {
      return "인원(" + card.getPersonId() + ")에게";
    }
    if (card.getCarId() != null) {
      return "차량(" + (card.getCarNo() == null ? card.getCarId() : card.getCarNo()) + ")에";
    }
    return null;
  }

  static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "카드 " + label + "은(는) 필수입니다.");
    }
  }

  static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }
}
