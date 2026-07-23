package AirPort.service;

import AirPort.adapter.BiostarCard;
import AirPort.adapter.BiostarCardAdapter;
import AirPort.adapter.BiostarUserCard;
import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.CardForm;
import AirPort.model.CardSearchParam;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 카드(tb_card) — 카드등록관리(/card/card) 마스터 CRUD + 정규인원등록 카드정보 탭. (docs/backend.md)
 *
 * <p>두 화면의 공통 규칙: 카드는 <b>실물</b>이라 BiostarX 등록이 선행돼야 하고(POST /api/cards, 이미 있는 번호는 재사용),
 * 인원에서 빼는 것은 삭제가 아니라 <b>회수</b>(person_id=NULL)다. 인원 화면은 카드 추가 시점에 BiostarX 등록만 하고
 * tb_card 저장·사용자 부여(cards[])는 인원 저장 시 한 번에 처리한다.
 */
@Service
public class CardService {

  private static final Logger log = LoggerFactory.getLogger(CardService.class);

  /** 카드종류 — 인원 화면이 발급하는 카드는 '인원'(tb_common CDT) 고정. 화면 값을 믿지 않고 서버가 정한다. */
  private static final String CARD_TYPE_PERSON = "CDT01";

  /** 차량 카드 — 패스구분(사람의 출입 패스 구분)을 쓰지 않는다. tb_common(CDT) */
  private static final String CARD_TYPE_CAR = "CDT02";

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

  // ── 카드등록관리(/card/card) — 카드 마스터 CRUD ────────────────────────────

  /** 목록 조회 — 검색조건·결과 건수 감사(READ). */
  public PageResult<TbCard> list(CardSearchParam param, TbLoginUser actor, Integer menuId) {
    long total = cardMapper.selectCount(param);
    List<TbCard> rows = cardMapper.selectList(param);
    auditService.log(actor, AuditService.READ, menuId, "카드 목록 조회 (결과 " + total + "건)");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  /**
   * 카드 등록 — <b>DB 저장 후 BiostarX 등록</b> 순서. 제약 위반은 BiostarX 호출 전에 걸리고, BiostarX 실패는 트랜잭션을 롤백해
   * 고아 카드를 막는다(장비엔 남고 우리 DB엔 없는 상태 방지). 이미 등록된 카드번호는 중복으로 막는다.
   */
  @Transactional
  public void createCard(TbCard row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validateCard(row);
    if (cardMapper.selectByCardNo(row.getBiostarCardValue()) != null) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 카드번호입니다.");
    }
    normalize(row);
    row.setBiostarCardId(null);
    cardMapper.insert(row); // DB 먼저 — unique/CHECK 위반은 BiostarX 호출 전에 잡힌다
    // 차량 카드(CDT02)는 BiostarX 에 등록하지 않는다 — 인원 카드만 장비에 올린다
    if (!CARD_TYPE_CAR.equals(row.getCardType())) {
      registerBiostar(row, actor, menuId); // BiostarX 나중 — 실패하면 위 insert 도 롤백
    }
    auditService.log(actor, AuditService.CREATE, menuId, "카드 등록: " + row.getBiostarCardValue());
  }

  /**
   * 신규 실물 카드를 BiostarX 에 등록하고 {@code biostar_card_id} 를 채운다 — <b>활성 트랜잭션 안에서 tb_card insert 이후</b>
   * 호출해야 한다(호출 계약을 아래 가드로 강제한다).
   *
   * <p>순서 보장이 핵심이다: DB 행이 이미 있으므로 BiostarX 등록이 실패하면 트랜잭션이 통째로 롤백돼 BiostarX·DB 어느 쪽에도 남지 않는다.
   * 트랜잭션이 없으면 이 롤백 보장이 깨져 <b>장비엔 있고 DB엔 없는</b> 고아 카드가 생길 수 있어, 계약 위반은 즉시 예외로 막는다.
   */
  void registerBiostar(TbCard row, TbLoginUser actor, Integer menuId) {
    // ① 활성 트랜잭션 강제 — 스레드 바인딩 확인이라 self-invocation(같은 빈 호출)에도 정확하다
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("registerBiostar 는 활성 트랜잭션 안에서만 호출해야 합니다(롤백 보장 필요).");
    }
    // ② insert 선행 강제 — cardId 가 없으면 DB 행이 없다는 뜻(그대로 두면 updateBiostarCardId 가 0행 갱신으로 조용히 실패)
    if (row.getCardId() == null) {
      throw new IllegalStateException("registerBiostar 는 tb_card insert 후(cardId 확보) 호출해야 합니다.");
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    BiostarCard issued =
        biostarCardAdapter.createCard(
            cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), row.getBiostarCardValue());
    if (!issued.success()) {
      // 실패 원인을 운영 로그(관리자)와 응답 메시지(사용자) 양쪽에 남긴다 — 트랜잭션은 롤백된다
      log.warn("BiostarX 카드 등록 실패: cardNo={}, 원인={}", row.getBiostarCardValue(), issued.message());
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 카드 등록 실패: " + issued.message());
    }
    cardMapper.updateBiostarCardId(row.getCardId(), issued.biostarCardId());
    auditService.log(actor, AuditService.CREATE, menuId, "BiostarX 카드 등록: " + issued.cardNo());
  }

  /** 카드 수정 — 카드번호·BiostarX 식별자·할당 인원은 바꾸지 않는다(실물 카드). */
  @Transactional
  public void updateCard(TbCard row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    if (row.getCardId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "카드ID가 필요합니다.");
    }
    TbCard existing = cardMapper.selectById(row.getCardId());
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    validateCard(row);
    normalize(row);
    cardMapper.updateInfo(row);
    auditService.log(actor, AuditService.UPDATE, menuId, "카드 수정: " + existing.getBiostarCardValue());
  }

  /** 카드 삭제(소프트) — 인원에게 할당된 카드는 막는다(먼저 회수해야 한다). */
  @Transactional
  public void deleteCard(int cardId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbCard existing = cardMapper.selectById(cardId);
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    String holder = issuedTo(existing);
    if (holder != null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, holder + " 발급된 카드입니다. 먼저 회수하세요.");
    }
    cardMapper.softDelete(cardId);
    auditService.log(actor, AuditService.DELETE, menuId, "카드 삭제: " + existing.getBiostarCardValue());
  }

  /** 발급 대상 설명 — 인원/차량 어느 쪽에든 붙어 있으면 그 대상을, 미발급이면 null. */
  private static String issuedTo(TbCard card) {
    if (card.getPersonId() != null) {
      return "인원(" + card.getPersonId() + ")에게";
    }
    if (card.getCarId() != null) {
      return "차량(" + (card.getCarNo() == null ? card.getCarId() : card.getCarNo()) + ")에";
    }
    return null;
  }

  /** 카드 마스터 필수값 — 인원 화면(CardForm)과 같은 기준. 차량 카드는 패스구분을 받지 않는다. */
  private static void validateCard(TbCard row) {
    require(row.getBiostarCardValue(), "카드번호");
    require(row.getCardType(), "카드구분");
    if (!CARD_TYPE_CAR.equals(row.getCardType())) {
      require(row.getPassType(), "패스구분");
    }
    require(row.getCardName(), "카드명칭");
    require(row.getCardStatus(), "카드상태");
  }

  /** 저장 전 보정 — 차량 카드의 패스구분은 화면에서 무엇이 오든 비운다(화면 값 불신). */
  private static void normalize(TbCard row) {
    if (CARD_TYPE_CAR.equals(row.getCardType())) {
      row.setPassType(null);
    }
    row.setFeePaidDt(blankToNull(row.getFeePaidDt()));
  }

  /** 인원의 카드 목록 — 수정 모달에서 기존 카드 표시용. */
  public List<TbCard> listByPerson(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectByPerson(personId);
  }

  /** 미할당 카드 목록 — 할당하기 팝업(회수되어 다시 쓸 수 있는 카드). */
  public List<TbCard> listUnassigned(String keyword, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectUnassigned(keyword);
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
    // 이미 발급된 실물 카드면 다시 만들지 않는다 — 회수(미배정)된 카드는 그대로 재사용한다.
    // 인원·차량 어느 쪽에든 붙어 있으면 거부한다(한 카드는 둘 중 하나에만 귀속).
    TbCard known = cardNo == null ? null : cardMapper.selectByCardNo(cardNo);
    if (known != null) {
      String holder = issuedTo(known);
      if (holder != null) {
        return BiostarCard.fail("이미 " + holder + " 발급된 카드입니다. 먼저 회수하세요.");
      }
      return BiostarCard.ok(known.getBiostarCardId(), known.getBiostarCardValue());
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
   * <p>기존 카드를 전부 <b>회수(미배정)</b>한 뒤 화면에 남아 있는 것만 다시 붙인다(새 카드=INSERT, 기존 카드=UPDATE).
   * 목록에서 제외된 카드는 삭제되지 않고 {@code person_id=NULL, use_yn='Y', del_yn='N'} 로 남아 <b>다른 인원이 재사용</b>할 수 있다.
   */
  public void saveCards(String personId, List<CardForm> cards) {
    cardMapper.releaseByPerson(personId);
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
      // 화면에서 온 cardId 가 없어도 같은 카드번호가 이미 있으면 그 행을 쓴다
      // (할당하기·SCAN 으로 고른 회수 카드가 새 행으로 복제되는 것을 막는다 — 카드번호는 실물 1:1)
      if (row.getCardId() == null) {
        TbCard known = cardMapper.selectByCardNo(form.getCardNo());
        if (known != null) {
          row.setCardId(known.getCardId());
        }
      }
      if (row.getCardId() == null) {
        cardMapper.insert(row);
        form.setCardId(row.getCardId());
      } else {
        cardMapper.update(row); // update 가 person_id 재배정 + del_yn='N' 복원
        form.setCardId(row.getCardId());
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
