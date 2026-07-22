package AirPort.service;

import AirPort.adapter.BiostarCard;
import AirPort.adapter.BiostarCardAdapter;
import AirPort.adapter.BiostarUserCard;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.CardForm;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 카드(tb_card) — 인원 모달의 카드정보 탭 담당. (docs/backend.md)
 *
 * <p>정책: <b>카드 추가 시점에 BiostarX 에 카드를 등록</b>하고(POST /api/cards), 그 결과(biostar_card_id/카드번호)를
 * 화면이 들고 있다가 인원 저장 시 tb_card 로 저장한다. 사용자에게 붙이는 것은 인원 저장 시 사용자 payload 의 cards[] 가 한다.
 */
@Service
public class CardService {

  /** 카드종류 — 인원 화면이 발급하는 카드는 '인원'(tb_common CDT) 고정. 화면 값을 믿지 않고 서버가 정한다. */
  private static final String CARD_TYPE_PERSON = "CDT01";

  private final TbCardMapper cardMapper;
  private final TbSystemMapper systemMapper;
  private final BiostarCardAdapter biostarCardAdapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public CardService(
      TbCardMapper cardMapper,
      TbSystemMapper systemMapper,
      BiostarCardAdapter biostarCardAdapter,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.cardMapper = cardMapper;
    this.systemMapper = systemMapper;
    this.biostarCardAdapter = biostarCardAdapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 인원의 카드 목록 — 수정 모달에서 기존 카드 표시용. */
  public List<TbCard> listByPerson(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectByPerson(personId);
  }

  /** 장치 리더로 카드번호 읽기 — 로그인 계정의 장치(tb_login_user.dev_id). */
  public BiostarCard scan(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarCard.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    String devId = actor == null ? null : actor.getDevId();
    return biostarCardAdapter.scanCard(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), devId);
  }

  /** 카드 등록(BiostarX) — 카드 추가 확인 시 즉시 호출된다. 성공하면 id/카드번호를 화면에 돌려준다. */
  public BiostarCard register(String cardNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarCard.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    BiostarCard res =
        biostarCardAdapter.createCard(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), cardNo);
    if (res.success()) {
      auditService.log(actor, AuditService.CREATE, menuId, "BiostarX 카드 등록: " + res.cardNo());
    }
    return res;
  }

  /**
   * 인원의 카드를 화면 목록 그대로 반영한다 — 인원 저장(등록/수정) 트랜잭션 안에서 호출.
   *
   * <p>기존 카드는 전부 소프트 삭제한 뒤 화면에 남아 있는 것만 다시 살린다(새 카드=INSERT, 기존 카드=UPDATE + 되살리기).
   */
  public void saveCards(String personId, List<CardForm> cards) {
    cardMapper.softDeleteByPerson(personId);
    if (cards == null) {
      return;
    }
    for (CardForm form : cards) {
      validate(form);
      TbCard row = new TbCard();
      row.setCardId(form.getCardId());
      row.setPersonId(personId);
      row.setCardType(CARD_TYPE_PERSON);
      row.setCardName(form.getCardName());
      row.setCardStatus(form.getCardStatus());
      row.setPassType(form.getPassType());
      row.setFeePaidDt(blankToNull(form.getFeePaidDt()));
      row.setIssueReason(form.getIssueReason());
      row.setRemark(form.getRemark());
      row.setBiostarCardId(form.getBiostarCardId());
      row.setBiostarCardValue(form.getCardNo());
      if (form.getCardId() == null) {
        cardMapper.insert(row);
        form.setCardId(row.getCardId());
      } else {
        cardMapper.update(row); // update 가 del_yn 을 'N' 으로 되돌린다
      }
    }
  }

  /** 화면 카드 목록 → BiostarX 사용자 payload 의 cards[]. BiostarX 등록이 끝난 카드만 싣는다. */
  public static List<BiostarUserCard> toBiostarCards(List<CardForm> cards) {
    List<BiostarUserCard> result = new ArrayList<>();
    if (cards != null) {
      cards.stream()
          .filter(c -> c.getBiostarCardId() != null && c.getCardNo() != null)
          .forEach(c -> result.add(new BiostarUserCard(c.getBiostarCardId(), c.getCardNo())));
    }
    return result;
  }

  /** 저장된 카드 → BiostarX 사용자 payload 의 cards[] (수정 시 '변경 전' 비교용). */
  public static List<BiostarUserCard> toBiostarCardsOf(List<TbCard> cards) {
    List<BiostarUserCard> result = new ArrayList<>();
    if (cards != null) {
      cards.stream()
          .filter(c -> c.getBiostarCardId() != null && c.getBiostarCardValue() != null)
          .forEach(c -> result.add(new BiostarUserCard(c.getBiostarCardId(), c.getBiostarCardValue())));
    }
    return result;
  }

  /** 카드 필수값 — 화면(card-list.js)과 같은 기준. 카드구분은 서버가 고정하므로 검사 대상이 아니다. */
  private static void validate(CardForm form) {
    require(form.getCardNo(), "카드번호");
    require(form.getPassType(), "패스구분");
    require(form.getCardName(), "카드명칭");
    require(form.getCardStatus(), "카드상태");
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "카드 " + label + "은(는) 필수입니다.");
    }
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }
}
