package AirPort.service;

import AirPort.adapter.biostar.BiostarCard;
import AirPort.adapter.biostar.BiostarCardAdapter;
import AirPort.adapter.biostar.BiostarUserCard;
import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbLoginUserMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.CardForm;
import AirPort.model.CardSearchParam;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 카드(tb_card) — 카드등록관리(/card/card) 마스터 CRUD + 정규인원등록 카드정보 탭. (docs/backend.md)
 *
 * <p>두 화면의 공통 규칙: 카드는 <b>실물</b>이라 BiostarX 등록이 선행돼야 하고(POST /api/cards, 이미 있는 번호는 재사용), 인원에서 빼는 것은
 * 삭제가 아니라 <b>회수</b>(person_id=NULL)다. 인원 화면은 카드 추가 시점에 BiostarX 등록만 하고 tb_card 저장·사용자 부여(cards[])는
 * 인원 저장 시 한 번에 처리한다.
 */
@Service
public class CardService {

  private static final Logger log = LoggerFactory.getLogger(CardService.class);

  /** 카드종류 — 인원 화면이 발급하는 카드는 '인원'(tb_common CDT) 고정. 화면 값을 믿지 않고 서버가 정한다. */
  public static final String CARD_TYPE_PERSON = "CDT01";

  /** 차량 카드 — 패스구분(사람의 출입 패스 구분)을 쓰지 않는다. tb_common(CDT) */
  public static final String CARD_TYPE_CAR = "CDT02";

  private final TbCardMapper cardMapper;
  private final TbSystemMapper systemMapper;
  private final TbCommonMapper commonMapper;
  private final TbLoginUserMapper loginUserMapper;
  private final BiostarCardAdapter biostarCardAdapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;
  private final CardIssueService cardIssue;

  public CardService(
      TbCardMapper cardMapper,
      TbSystemMapper systemMapper,
      TbCommonMapper commonMapper,
      TbLoginUserMapper loginUserMapper,
      BiostarCardAdapter biostarCardAdapter,
      MenuAuthService menuAuthService,
      AuditService auditService,
      CardIssueService cardIssue) {
    this.cardMapper = cardMapper;
    this.systemMapper = systemMapper;
    this.commonMapper = commonMapper;
    this.loginUserMapper = loginUserMapper;
    this.biostarCardAdapter = biostarCardAdapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
    this.cardIssue = cardIssue;
  }

  /**
   * BiostarX 블랙리스트 동기화 — <b>차단 여부가 실제로 바뀔 때만</b> 호출한다. BiostarX 는 멱등하지 않아 이미 해제된 카드를 다시 해제하면 HTTP
   * 500 을 돌려주므로, 변화 없는 저장에서 불필요한 호출을 하지 않는다(신규 카드는 장비 기본이 비차단이라 prev=null 을 비차단으로 본다).
   *
   * <p>실패 정책은 비대칭이다: <b>차단 실패는 예외로 롤백</b>(분실 카드가 장비에서 계속 유효하면 보안 위험), <b>해제 실패는 경고만</b> (실패해도 카드가
   * 계속 차단될 뿐이라 업무 불편이지 보안 위험이 아니고, '이미 해제됨'이 오류로 오는 경우가 많다). 실패는 어느 쪽이든 감사에 남긴다.
   *
   * @param card 대상 카드(장비 등록 여부·종류 판단용)
   */
  private void syncBlacklist(TbCard card, String prevStatus, String newStatus) {
    boolean block = cardIssue.isBlocked(newStatus);
    String biostarCardId = card == null ? null : card.getBiostarCardId();
    if (biostarCardId == null || biostarCardId.isBlank()) {
      // 차량 카드는 애초에 BiostarX 대상이 아니다 — 조용히 넘어가는 것이 맞다.
      // 그러나 인원 카드가 장비에 없는데 '차단'을 조용히 성공 처리하면, 사용자는 막았다고 믿지만
      // 실제로는 아무 일도 일어나지 않는다. 그 카드가 나중에 장비에 등록되면 차단 없이 유효해진다.
      if (block && card != null && !CARD_TYPE_CAR.equals(card.getCardType())) {
        log.warn("장비 미등록 인원카드 차단 요청 — cardNo={}, 상태={}", card.getBiostarCardValue(), newStatus);
        auditService.logAlways(
            null,
            AuditService.UPDATE,
            null,
            "카드 차단 대상이나 BiostarX 미등록: " + card.getBiostarCardValue() + " (상태 " + newStatus + ")");
        throw new BusinessException(
            ErrorCode.INVALID_INPUT,
            "카드 "
                + card.getBiostarCardValue()
                + " 은(는) BiostarX 에 등록되어 있지 않아 차단할 수 없습니다."
                + " 정규인원 화면에서 카드를 저장해 장비에 등록한 뒤 상태를 바꾸세요.");
      }
      return; // 차량 카드이거나, 차단이 아닌 변경(해제) — 동기화 대상 아님
    }
    if (block == (prevStatus != null && cardIssue.isBlocked(prevStatus))) {
      return; // 차단 여부 변화 없음 — 장비 호출 불필요(중복 해제로 인한 500 방지)
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 설정이 없습니다. 설정관리에서 먼저 등록하세요.");
    }
    AirPort.adapter.biostar.BiostarResult res =
        block
            ? biostarCardAdapter.blacklistCard(
                cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), biostarCardId)
            : biostarCardAdapter.removeBlacklist(
                cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), biostarCardId);
    if (res.success()) {
      return;
    }
    String act = block ? "차단" : "차단 해제";
    log.warn("카드 블랙리스트 동기화 실패(cardId={}, {}): {}", biostarCardId, act, res.message());
    auditService.logAlways(
        null,
        AuditService.UPDATE,
        null,
        "BiostarX 카드 " + act + " 실패(cardId=" + biostarCardId + "): " + res.message());
    if (block) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT,
          "BiostarX 카드 차단 실패로 저장이 취소되었습니다. 사유: " + res.message() + " — 다시 시도하세요.");
    }
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
   * 카드 등록 — <b>DB 저장 후 BiostarX 등록</b> 순서. 제약 위반은 BiostarX 호출 전에 걸리고, BiostarX 실패는 트랜잭션을 롤백해 고아 카드를
   * 막는다(장비엔 남고 우리 DB엔 없는 상태 방지). 이미 등록된 카드번호는 중복으로 막는다.
   */
  @Transactional
  public void createCard(TbCard row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    cardIssue.validateCard(row, null);
    if (cardMapper.selectByCardNo(row.getBiostarCardValue()) != null) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 카드번호입니다.");
    }
    CardIssueService.normalize(row);
    row.setBiostarCardId(null);
    try {
      cardMapper.insert(row); // DB 먼저 — unique/CHECK 위반은 BiostarX 호출 전에 잡힌다
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // 동시 등록 레이스(중복검사 통과 후 유니크 충돌) — 친화적 메시지로 변환
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 카드번호입니다.");
    }
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
   * <p>순서 보장이 핵심이다: DB 행이 이미 있으므로 BiostarX 등록이 실패하면 트랜잭션이 통째로 롤백돼 BiostarX·DB 어느 쪽에도 남지 않는다. 트랜잭션이
   * 없으면 이 롤백 보장이 깨져 <b>장비엔 있고 DB엔 없는</b> 고아 카드가 생길 수 있어, 계약 위반은 즉시 예외로 막는다.
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
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "BiostarX 카드 등록 실패: " + issued.message());
    }
    cardMapper.updateBiostarCardId(row.getCardId(), issued.biostarCardId());
    row.setBiostarCardId(issued.biostarCardId()); // 호출자가 이어서 payload 를 만들 수 있게 반영
    auditService.log(actor, AuditService.CREATE, menuId, "BiostarX 카드 등록: " + issued.cardNo());
  }

  /**
   * 인원에게 부여할 카드가 <b>BiostarX 에 미등록이면 지금 등록</b>해 {@code biostar_card_id} 를 채운다 — 실패하면 예외로 저장을 롤백하고
   * 사유를 알린다(장비에 없는 카드를 부여해 실제로는 문이 열리지 않는 상태 방지).
   *
   * <p>차량 카드(CDT02)는 BiostarX 대상이 아니므로 건너뛴다. 활성 트랜잭션 안에서 호출해야 롤백이 보장된다.
   */
  public void ensureBiostarCard(Integer cardId, TbLoginUser actor, Integer menuId) {
    if (cardId == null) {
      return;
    }
    TbCard card = cardMapper.selectById(cardId);
    if (card == null || "Y".equals(card.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "발급할 카드를 찾을 수 없습니다.");
    }
    if (CARD_TYPE_CAR.equals(card.getCardType())) {
      return; // 차량 카드는 장비 미등록이 정상
    }
    String id = card.getBiostarCardId();
    if (id != null && !id.isBlank() && existsOnDevice(id)) {
      return; // 장비에 실제로 있는 카드 — 그대로 사용
    }
    if (id != null && !id.isBlank()) {
      // 장비에서 삭제된 카드(stale id) — 카드번호로 재등록해 새 id 로 맞춘다
      log.warn("BiostarX 에 없는 카드 id({}) — 카드번호 {} 로 재등록합니다.", id, card.getBiostarCardValue());
      card.setBiostarCardId(null);
    }
    registerBiostar(card, actor, menuId); // 실패 시 BusinessException → 트랜잭션 롤백
  }

  /** 장비에 해당 카드 id 가 실제로 있는지 — 조회 실패는 예외로 전파(있다고 단정하지 않는다). */
  private boolean existsOnDevice(String biostarCardId) {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 설정이 없습니다. 설정관리에서 먼저 등록하세요.");
    }
    return biostarCardAdapter
        .registeredCardIds(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg))
        .contains(biostarCardId);
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
    cardIssue.validateCard(row, existing);
    CardIssueService.normalize(row);
    cardMapper.updateInfo(row);
    // 차단 여부가 바뀐 경우에만 BiostarX 차단/해제
    syncBlacklist(existing, existing.getCardStatus(), row.getCardStatus());
    auditService.log(
        actor, AuditService.UPDATE, menuId, "카드 수정: " + existing.getBiostarCardValue());
  }

  /** 카드 삭제(소프트) — 인원에게 할당된 카드는 막는다(먼저 회수해야 한다). */
  @Transactional
  public void deleteCard(int cardId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbCard existing = cardMapper.selectById(cardId);
    if (existing == null || "Y".equals(existing.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    String holder = CardIssueService.issuedTo(existing);
    if (holder != null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, holder + " 발급된 카드입니다. 먼저 회수하세요.");
    }
    cardMapper.softDelete(cardId);
    auditService.log(
        actor, AuditService.DELETE, menuId, "카드 삭제: " + existing.getBiostarCardValue());
  }

  /** 인원의 카드 목록 — 수정 모달에서 기존 카드 표시용. */
  public List<TbCard> listByPerson(String personId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectByPerson(personId);
  }

  /** 미할당 카드 목록 — 할당하기 팝업(회수되어 다시 쓸 수 있는 카드). */
  public List<TbCard> listUnassigned(String keyword, TbLoginUser actor, Integer menuId) {
    return listUnassigned(keyword, null, actor, menuId);
  }

  /** 미할당 카드 — cardType(CDT01 인원 / CDT02 차량) 으로 걸러 조회. null 이면 전체. */
  public List<TbCard> listUnassigned(
      String keyword, String cardType, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectUnassigned(keyword, cardType);
  }

  /**
   * 카드번호로 우리 DB 의 카드 1장 — 없으면 null.
   *
   * <p>스캔한 실물 카드가 <b>이미 등록된 카드</b>인지 보려고 쓴다. 있으면 화면이 패스구분·명칭·상태를 그대로 채워, 같은 카드를 다시 손으로 입력하다 값이 어긋나는
   * 것을 막는다.
   */
  public TbCard findByCardNo(String cardNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return (cardNo == null || cardNo.isBlank()) ? null : cardMapper.selectByCardNo(cardNo);
  }

  /** 장치 리더로 카드번호 읽기 — 로그인 계정의 장치(tb_login_user.dev_id). */
  public BiostarCard scan(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarCard.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    return biostarCardAdapter.scanCard(
        cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), currentDevId(loginUserMapper, actor));
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
      String holder = CardIssueService.issuedTo(known);
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
   * 인원이 들고 있던 카드를 모두 회수(미배정)한다 — 인원 삭제 트랜잭션 안에서 호출.
   *
   * <p>회수하지 않으면 사라진 인원에 카드가 물린 채 남아 목록에 <b>계속 '발급중'</b> 으로 보이고 다른 인원에게 발급할 수 없다. 카드 행은 지우지 않고
   * {@code person_id=NULL} 로만 만들어 재발급 대상이 되게 한다(실물 카드는 그대로 재사용).
   *
   * @return 회수한 카드 수(감사 문구용)
   */
  public int releasePersonCards(String personId) {
    return cardMapper.releaseByPerson(personId);
  }

  /**
   * 인원의 카드를 화면 목록 그대로 반영한다 — 인원 저장(등록/수정) 트랜잭션 안에서 호출.
   *
   * <p>기존 카드를 전부 <b>회수(미배정)</b>한 뒤 화면에 남아 있는 것만 다시 붙인다(새 카드=INSERT, 기존 카드=UPDATE). 목록에서 제외된 카드는
   * 삭제되지 않고 {@code person_id=NULL, use_yn='Y', del_yn='N'} 로 남아 <b>다른 인원이 재사용</b>할 수 있다.
   */
  public void saveCards(String personId, List<CardForm> cards, TbLoginUser actor, Integer menuId) {
    Set<Integer> held = cardIssue.heldCardIds(personId); // 회수 전에 읽어야 자기 카드를 구분할 수 있다
    cardMapper.releaseByPerson(personId);
    if (cards == null) {
      return;
    }
    for (CardForm form : cards) {
      // 기존 카드면 저장된 값을 기준으로 검증한다(안 바꾼 코드 때문에 저장이 막히지 않게)
      cardIssue.validateForm(
          form, form.getCardId() == null ? null : cardMapper.selectById(form.getCardId()));
      // 정상이 아닌 카드는 새로 발급하지 않는다(카드번호만 온 경우도 행을 찾아 확인)
      if (form.getCardId() != null) {
        cardIssue.requireIssuable(form.getCardId(), held, "인원 " + personId);
      } else {
        cardIssue.requireIssuableByNo(form.getCardNo(), held, "인원 " + personId);
      }
      // 새 카드를 처음부터 분실·정지 등으로 고르는 경로도 막는다(발급은 정상 상태로만)
      if (!held.contains(form.getCardId())) {
        cardIssue.requireIssuableStatus(form.getCardStatus(), form.getCardNo(), "인원 " + personId);
      }
      // 차량 카드를 사람에게 주지 않는다 — 목록을 거치지 않는 직접입력·SCAN 도 여기서 걸린다
      cardIssue.requireIssuableToPerson(form.getCardId(), form.getCardNo(), "인원 " + personId);
      TbCard row = new TbCard();
      row.setCardId(form.getCardId());
      row.setPersonId(personId);
      row.setCardType(CARD_TYPE_PERSON);
      row.setCardName(form.getCardName());
      row.setCardStatus(form.getCardStatus());
      row.setPassType(form.getPassType());
      row.setFeePaidDt(CardIssueService.blankToNull(form.getFeePaidDt()));
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
      // 변경 전 상태(차단 여부 비교용) — 신규 카드는 null(장비 기본 비차단)
      TbCard before = row.getCardId() == null ? null : cardMapper.selectById(row.getCardId());
      if (row.getCardId() == null) {
        cardMapper.insert(row);
        form.setCardId(row.getCardId());
      } else {
        cardMapper.update(row); // update 가 person_id 재배정 + del_yn='N' 복원
        form.setCardId(row.getCardId());
      }
      // 장비 미등록 인원카드면 지금 등록해 사용자에게 부여 가능하게 한다(실패 시 예외 → 저장 취소)
      ensureBiostarCard(row.getCardId(), actor, menuId);
      if (row.getBiostarCardId() == null || row.getBiostarCardId().isBlank()) {
        TbCard saved = cardMapper.selectById(row.getCardId()); // 방금 채워진 biostar_card_id 반영
        if (saved != null) {
          row.setBiostarCardId(saved.getBiostarCardId());
          form.setBiostarCardId(saved.getBiostarCardId());
        }
      }
      // 차단 여부가 바뀐 경우에만 BiostarX 차단/해제
      syncBlacklist(row, before == null ? null : before.getCardStatus(), row.getCardStatus());
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
          .forEach(
              c -> result.add(new BiostarUserCard(c.getBiostarCardId(), c.getBiostarCardValue())));
    }
    return result;
  }

  /**
   * 지금 이 계정에 지정된 장치ID — <b>세션이 아니라 DB 에서 읽는다</b>.
   *
   * <p>세션의 로그인 사용자는 로그인 시점 스냅샷이라, 사용자관리에서 장치를 바꿔도 재로그인 전에는 옛 장치로 스캔·촬영이 나갔다.
   */
  static String currentDevId(TbLoginUserMapper mapper, TbLoginUser actor) {
    if (actor == null || actor.getUserId() == null) {
      return null;
    }
    TbLoginUser fresh = mapper.selectById(actor.getUserId());
    return fresh != null ? fresh.getDevId() : actor.getDevId();
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }
}
