package AirPort.service;

import AirPort.adapter.biostar.BiostarAuthEvent;
import AirPort.adapter.biostar.BiostarEventSocket;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbLoginUserMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방문객 카드 태깅 — 임시·장기 등록의 <b>방문객 탭에서 리더에 카드를 대면</b> 그 사실을 화면으로 밀어 준다.
 *
 * <p>[SCAN] 은 한 장에 버튼 한 번이라 다섯 명이면 다섯 번을 눌러야 한다. 여기서는 <b>대는 대로</b> 위에서부터 차례로 채운다 — 현장에서 카드를 쥔 손과
 * 마우스를 오가지 않아도 된다.
 *
 * <p>미등록 카드를 리더에 대면 장비는 인증 실패({@value #CARD_TAG_CODE} VERIFY_FAIL_CARD)를 올리는데, 그 이벤트의 {@code
 * user_id} 자리에 <b>읽은 카드번호</b>가 담긴다. 그 값이 우리 {@code tb_card.biostar_card_value} 다.
 *
 * <p>내 리더에서 읽은 것만 본다({@code tb_login_user.dev_id}). 공항에는 리더가 여럿이라, 거르지 않으면 <b>다른 사람이 지나가며 찍은
 * 카드</b>가 내 화면의 방문객에게 배정된다.
 *
 * <p>여기서 하는 일은 <b>알리는 것까지</b>다. 어느 방문객에게 넣을지, 그 카드가 이 방문의 구역에 맞는지는 화면과 {@link VisitCardService} 가
 * 판정한다 — 그래야 지금 화면에 고른 출입그룹 기준으로 본다.
 */
@Service
public class CardTagService {

  private static final Logger log = LoggerFactory.getLogger(CardTagService.class);

  /** 미등록 카드를 읽었을 때 장비가 올리는 이벤트 — 이때만 user_id 자리에 카드번호가 온다. */
  public static final String CARD_TAG_CODE = "4354";

  /** 소켓 구독 채널 — 실시간 이벤트 화면과 장비 연결을 함께 쓴다. */
  private static final String CHANNEL = "cardTag";

  /** 화면을 켜 두는 용도라 서버가 먼저 끊지 않는다. */
  private static final long NO_TIMEOUT = 0L;

  private final TbSystemMapper systemMapper;
  private final TbLoginUserMapper loginUserMapper;
  private final BiostarEventSocket eventSocket;
  private final MenuAuthService menuAuthService;

  /** 구독자 → 그 사람의 리더(dev_id). 리더가 다르면 같은 이벤트라도 보내지 않는다. */
  private final Map<SseEmitter, String> viewers = new ConcurrentHashMap<>();

  /** 구독자 목록과 소켓 수명을 함께 지키는 잠금 — 둘이 엇갈리면 소켓 없이 구독자만 남는다. */
  private final Object viewerLock = new Object();

  public CardTagService(
      TbSystemMapper systemMapper,
      TbLoginUserMapper loginUserMapper,
      BiostarEventSocket eventSocket,
      MenuAuthService menuAuthService) {
    this.systemMapper = systemMapper;
    this.loginUserMapper = loginUserMapper;
    this.eventSocket = eventSocket;
    this.menuAuthService = menuAuthService;
  }

  /**
   * 방문객 탭이 열려 있는 동안의 구독 — 내 리더에 카드가 닿으면 카드번호를 밀어 준다.
   *
   * <p>리더가 지정되지 않은 계정은 구독 자체를 거부한다. 조용히 아무 일도 안 일어나면 "카드를 댔는데 왜 안 되지"로만 보이고, 무엇을 고쳐야 하는지 알 수 없다.
   */
  public SseEmitter subscribe(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 카드를 붙이는 일이라 등록 권한이 필요하다
    String devId = CardService.currentDevId(loginUserMapper, actor);
    if (devId == null || devId.isBlank()) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "이 계정에 지정된 단말기가 없습니다. 사용자관리에서 단말기를 지정하면 카드를 대는 것만으로 배정됩니다.");
    }
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }

    SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
    emitter.onCompletion(() -> release(emitter));
    emitter.onTimeout(() -> release(emitter));
    emitter.onError(e -> release(emitter));

    synchronized (viewerLock) {
      viewers.put(emitter, devId);
      eventSocket.start(
          cfg.getBiostarIp(),
          cfg.getBiostarId(),
          pw(cfg),
          CHANNEL,
          this::onEvent,
          this::pushStatus);
    }
    send(emitter, "status", status());
    return emitter;
  }

  /** 소켓 수신 스레드에서 불린다 — 판정만 하고 넘긴다. */
  private void onEvent(BiostarAuthEvent event) {
    if (!isCardRead(event)) {
      return; // 인증 성공·문 열림 등 — 카드를 읽은 사건이 아니다
    }
    viewers.forEach(
        (emitter, devId) -> {
          if (forReader(event, devId)) {
            send(emitter, "card", Map.of("cardNo", event.userId()));
          }
        });
  }

  /**
   * 카드를 읽은 사건인가 — <b>미등록 카드 인증 실패</b>에만 {@code user_id} 자리에 카드번호가 온다.
   *
   * <p>이미 사람에게 발급된 카드를 대면 인증 <b>성공</b>이 뜨고 그 자리에는 사람의 인원ID 가 온다. 그것을 카드번호로 읽으면 엉뚱한 카드를 찾는다.
   */
  static boolean isCardRead(BiostarAuthEvent event) {
    return event != null
        && CARD_TAG_CODE.equals(event.eventCode())
        && event.userId() != null
        && !event.userId().isBlank();
  }

  /**
   * 그 화면에 보낼 이벤트인가 — <b>내 리더에서 읽은 것만</b> 본다.
   *
   * <p>공항에는 리더가 여럿이라, 거르지 않으면 다른 사람이 지나가며 찍은 카드가 내 화면의 방문객에게 배정된다.
   */
  static boolean forReader(BiostarAuthEvent event, String devId) {
    return isCardRead(event) && devId != null && devId.equals(event.deviceId());
  }

  /**
   * 연결 유지 신호 — 겸사겸사 <b>죽은 구독자를 걷어낸다.</b>
   *
   * <p>둘 다 필요하다. ① 아무것도 흐르지 않는 SSE 연결은 중간의 프록시·브라우저가 끊는다 — 그러면 카드를 대도 아무 일이 없다. ② 화면을 닫거나
   * <b>새로고침</b>하면 서버는 다음에 무언가를 쓸 때까지 그 사실을 모른다. 걷어내지 않으면 죽은 구독자가 쌓여 {@code viewers} 가 영영 비지 않고, 아무도
   * 보지 않는데도 장비 소켓이 계속 열려 있게 된다.
   */
  @Scheduled(fixedDelay = 25_000)
  public void ping() {
    viewers.forEach(
        (emitter, devId) -> {
          try {
            synchronized (emitter) {
              emitter.send(SseEmitter.event().comment("keep-alive"));
            }
          } catch (Exception e) {
            release(emitter); // 이미 닫힌 화면 — 조용히 정리한다
          }
        });
  }

  /** 소켓 상태가 바뀌면 알린다 — 조용히 끊기면 "카드를 안 댄 것"과 구분되지 않는다. */
  private void pushStatus() {
    Map<String, Object> payload = status();
    viewers.forEach((emitter, devId) -> send(emitter, "status", payload));
  }

  private Map<String, Object> status() {
    String error = eventSocket.error();
    return error == null
        ? Map.of("ready", eventSocket.isReady())
        : Map.of("ready", eventSocket.isReady(), "error", error);
  }

  private void send(SseEmitter emitter, String name, Object data) {
    try {
      emitter.send(SseEmitter.event().name(name).data(data));
    } catch (Exception e) {
      release(emitter); // 이미 닫힌 화면 — 목록에서 뺀다
    }
  }

  /**
   * 마지막 화면이 닫히면 이 채널 구독도 끊는다.
   *
   * <p>{@link #subscribe} 와 같은 잠금을 쓴다 — 나누면 "지금 비었다"고 판단한 뒤 끊는 사이에 새 구독자가 끼어들어, 그 구독자만 남고 이벤트는 오지
   * 않는 상태가 된다.
   */
  private void release(SseEmitter emitter) {
    synchronized (viewerLock) {
      if (viewers.remove(emitter) != null && viewers.isEmpty()) {
        eventSocket.stop(CHANNEL);
        log.debug("카드 태깅 구독 종료 — 보는 화면 없음");
      }
    }
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }
}
