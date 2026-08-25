package AirPort.service;

import AirPort.adapter.biostar.BiostarAdapter;
import AirPort.adapter.biostar.BiostarAuthEvent;
import AirPort.adapter.biostar.BiostarDevice;
import AirPort.adapter.biostar.BiostarDevices;
import AirPort.adapter.biostar.BiostarEventAdapter;
import AirPort.adapter.biostar.BiostarEventSocket;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbPersonAcGroupMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbPersonPhotoMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.AuthEventResult;
import AirPort.model.TbCompany;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbSystem;
import AirPort.model.TbVisit;
import AirPort.security.ARIAUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 이벤트 모니터링 (모니터링 → 실시간 이벤트).
 *
 * <p>BiostarX 소켓은 서버가 <b>하나만</b> 연다({@link BiostarEventSocket}). 화면이 여럿 열려도 장비 연결은 하나이고, 마지막 화면이
 * 닫히면 소켓도 닫는다. 화면으로는 SSE 로 밀어 준다 — 브라우저는 BiostarX 를 직접 볼 수 없다(self-signed 인증서 + 세션은 서버만 보유).
 *
 * <p>화면에 올리는 것은 <b>고른 장치의 인증 성공</b>뿐이다. 그 한 건마다 우리 DB 에서 성명·소속·허가구역·등록사진을, 장비에서 인증사진을 붙인다.
 */
@Service
public class MonitorService {

  private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

  /** 정규인원 — 허가구역을 사람에게 직접 붙인다. 그 밖은 방문 단위로 잡힌다. */
  private static final String PERSON_TYPE_REGULAR = "PT01";

  /** 구역명에서 번호만 — "인원구역3" → 3. (신청서 출력과 같은 규칙) */
  private static final Pattern AREA_NO = Pattern.compile("(\\d+)");

  /** SSE 연결 유지 신호 간격보다 길게 잡은 무제한 타임아웃 — 화면을 켜 두는 용도라 서버가 먼저 끊지 않는다. */
  private static final long NO_TIMEOUT = 0L;

  private final TbSystemMapper systemMapper;
  private final TbPersonMapper personMapper;
  private final TbPersonPhotoMapper photoMapper;
  private final TbPersonAcGroupMapper acGroupMapper;
  private final TbCompanyMapper companyMapper;
  private final TbVisitMapper visitMapper;
  private final BiostarAdapter biostarAdapter;
  private final BiostarEventAdapter eventAdapter;
  private final BiostarEventSocket eventSocket;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  /** 같은 구독 감사를 다시 남기기까지의 조용한 시간(분). */
  private static final int AUDIT_QUIET_MINUTES = 10;

  /** 보강 대기열 상한 — 넘치면 <b>가장 오래된 것</b>을 버린다(실시간 화면에서 중요한 것은 최신이다). */
  private static final int QUEUE_LIMIT = 50;

  /** 구독자 → 보고 있는 장치 ID. 장치가 다르면 같은 이벤트라도 보내지 않는다. */
  private final Map<SseEmitter, String> viewers = new ConcurrentHashMap<>();

  /** 구독자 목록과 소켓 수명을 함께 지키는 잠금 — 둘이 엇갈리면 소켓 없이 구독자만 남는다. */
  private final Object viewerLock = new Object();

  /** 사용자·단말기별 마지막 구독 감사 시각 — 재연결마다 감사가 쌓이는 것을 막는다. */
  private final Map<String, Long> auditedAt = new ConcurrentHashMap<>();

  /**
   * 이벤트 보강(DB 조회 + 인증사진 HTTP)을 소켓 수신 스레드에서 떼어낸다.
   *
   * <p>단일 스레드다 — 보강이 늦어도 <b>도착 순서</b>가 지켜져야 한다. 순서가 섞이면 화면에서 방금 인증한 사람이 뒤로 밀린다.
   *
   * <p>대기열에 상한을 둔다. 인증사진 조회는 장비가 느리면 최대 10초를 기다리는데, 출근 시간처럼 몰릴 때 무제한으로 쌓이면 <b>몇 분 전 사람</b>이 화면에 뜬다
   * — 실시간 화면에서 그건 틀린 정보다. 밀리면 오래된 것부터 버린다.
   */
  private final ExecutorService worker =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<>(QUEUE_LIMIT),
          r -> {
            Thread t = new Thread(r, "monitor-event");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.DiscardOldestPolicy());

  public MonitorService(
      TbSystemMapper systemMapper,
      TbPersonMapper personMapper,
      TbPersonPhotoMapper photoMapper,
      TbPersonAcGroupMapper acGroupMapper,
      TbCompanyMapper companyMapper,
      TbVisitMapper visitMapper,
      BiostarAdapter biostarAdapter,
      BiostarEventAdapter eventAdapter,
      BiostarEventSocket eventSocket,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.systemMapper = systemMapper;
    this.personMapper = personMapper;
    this.photoMapper = photoMapper;
    this.acGroupMapper = acGroupMapper;
    this.companyMapper = companyMapper;
    this.visitMapper = visitMapper;
    this.biostarAdapter = biostarAdapter;
    this.eventAdapter = eventAdapter;
    this.eventSocket = eventSocket;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 조회할 장치 목록 — BiostarX 에서 그대로 읽는다. */
  public List<BiostarDevice> devices(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbSystem cfg = config();
    BiostarDevices res =
        biostarAdapter.searchDevices(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg));
    if (!res.success()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, res.message());
    }
    return res.devices();
  }

  /**
   * 화면 구독 시작 — 고른 장치의 인증 이벤트를 SSE 로 받는다.
   *
   * <p>구독자 등록과 소켓 시작을 <b>한 잠금 안에서</b> 한다. 나누면 "마지막 구독자 이탈"과 "새 구독"이 엇갈려, 새 구독자는 목록에 있는데 소켓은 닫힌 채
   * 아무도 다시 열지 않는 상태가 된다(그 화면은 새로고침 전까지 영구 정지). 소켓 연결 자체는 비동기라 이 잠금은 짧다.
   */
  public SseEmitter subscribe(String deviceId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    if (deviceId == null || deviceId.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "조회할 단말기를 선택하세요.");
    }
    TbSystem cfg = config();
    auditIfNew(actor, menuId, deviceId);

    SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
    emitter.onCompletion(() -> release(emitter));
    emitter.onTimeout(() -> release(emitter));
    emitter.onError(e -> release(emitter));

    synchronized (viewerLock) {
      viewers.put(emitter, deviceId);
      eventSocket.start(
          cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), this::onEvent, this::pushStatus);
    }
    send(emitter, "status", statusPayload());
    return emitter;
  }

  /**
   * 구독 감사 — 같은 사용자·같은 단말기는 {@link #AUDIT_QUIET_MINUTES} 분에 한 번만 남긴다.
   *
   * <p>브라우저의 EventSource 는 끊기면 <b>3초마다</b> 스스로 다시 붙는다. 망이 한 번 출렁이면 구독 요청이 분당 수십 건이 되고, 그대로 기록하면
   * 감사추적이 이 줄로 덮여 정작 사람이 한 일을 못 찾는다.
   */
  private void auditIfNew(TbLoginUser actor, Integer menuId, String deviceId) {
    String key = (actor == null ? "?" : actor.getUserId()) + "\u0000" + deviceId;
    long now = System.currentTimeMillis();
    Long last = auditedAt.get(key);
    if (last != null && now - last < AUDIT_QUIET_MINUTES * 60_000L) {
      return; // 재연결 — 새 구독이 아니다
    }
    auditedAt.put(key, now);
    auditService.log(actor, AuditService.READ, menuId, "실시간 이벤트 구독 (단말기 " + deviceId + ")");
  }

  /** 소켓 수신 스레드에서 불린다 — 여기서 오래 걸리면 다음 이벤트가 밀린다. 판정만 하고 넘긴다. */
  private void onEvent(BiostarAuthEvent event) {
    // 왜 안 떴는지는 여기서 갈린다 — 인증이 아니었는지, 다른 단말기였는지. 사유를 안 남기면
    // "장비가 안 보냈다"와 구분되지 않아 현장에서 원인을 좁힐 수 없다.
    if (!event.displayable()) {
      // 상시 운용에서 인증마다 쌓여 INFO 에서 내렸다. "인증했는데 화면에 안 뜬다" 를 볼 때만
      // AirPort.service 로거를 DEBUG 로 올린다 — 여기 찍힌 코드를 BiostarAuthEvent 표에 넣으면 뜬다.
      log.debug("화면 제외 — 표기 대상이 아닌 이벤트: {}({})", event.eventName(), event.eventCode());
      return;
    }
    if (event.deviceId() == null || !watched(event.deviceId())) {
      log.debug("화면 제외 — 보고 있지 않은 단말기({}). 보는 중: {}", event.deviceId(), viewers.values());
      return;
    }
    worker.execute(() -> enrichAndPush(event));
  }

  private boolean watched(String deviceId) {
    return viewers.containsValue(deviceId);
  }

  private void enrichAndPush(BiostarAuthEvent event) {
    try {
      AuthEventResult row = enrich(event);
      viewers.forEach(
          (emitter, deviceId) -> {
            if (deviceId.equals(event.deviceId())) {
              send(emitter, "auth", row);
            }
          });
    } catch (Exception e) {
      log.warn("실시간 이벤트 처리 실패: {}", e.toString());
    }
  }

  /** 장비 이벤트 + 우리 DB 값. 없는 값은 비워 둔다 — 미등록 인증도 화면에는 보여야 한다(누가 지나갔는지가 정보다). */
  AuthEventResult enrich(BiostarAuthEvent event) { // 테스트에서 직접 확인한다
    AuthEventResult row = new AuthEventResult();
    row.setEventTime(time(event.datetime()));
    row.setDeviceId(event.deviceId());
    row.setDeviceName(event.deviceName());
    row.setPersonId(event.userId());
    row.setResultLabel(event.resultLabel()); // 표기 문구는 서버가 정한다
    row.setGranted(event.granted()); // 통과/거부 — 화면 색이 갈린다

    TbPerson person = (event.userId() == null) ? null : personMapper.selectById(event.userId());
    if (person != null) {
      row.setPersonName(decrypt(person.getPersonName()));
      row.setCompanyName(affiliationOf(person));
      row.setAreas(areaNos(acGroupNames(person)));
      row.setPeriod(period(person));
      row.setRegisteredPhoto(photoMapper.selectPhoto(event.userId()));
      // 사진 없는 칸에 사람 모양을 세울지 카드 모양을 세울지 — 정규인원만 얼굴이 있어야 정상이다
      row.setFaceUser(PERSON_TYPE_REGULAR.equals(person.getPersonType()));
    }

    TbSystem cfg = systemMapper.selectOne();
    if (cfg != null && event.imageId() != null) {
      row.setAuthPhoto(
          eventAdapter.authImage(cfg.getBiostarIp(), cfg.getBiostarId(), pw(cfg), event.imageId()));
    }
    // 사진이 왜 안 나오는지는 이 한 줄로 판가름난다 — 이벤트에 사진 ID 가 없었는지, 장비가 안 줬는지,
    // 우리 DB 에 등록사진이 없었는지. 평소엔 필요 없어 DEBUG 다(상시 운용에서 인증마다 쌓인다).
    log.debug(
        "인증 {}({}) 인원={} 사진ID={} 인증사진={} 등록사진={}",
        event.eventName(),
        event.eventCode(),
        event.userId(),
        event.imageId() == null ? "없음" : event.imageId(),
        row.getAuthPhoto() == null ? "없음" : row.getAuthPhoto().length() + "자",
        row.getRegisteredPhoto() == null ? "없음" : row.getRegisteredPhoto().length() + "자");
    return row;
  }

  /**
   * 소속 — 방문객은 자유입력한 {@code affiliation}, 정규인원은 기관명.
   *
   * <p>방문객은 기관(`tb_company`)에 매이지 않고 소속을 직접 적는다. 그 값이 있으면 그것이 정확하다. 비어 있을 때만 기관명으로 물러선다.
   */
  private String affiliationOf(TbPerson person) {
    String affiliation = person.getAffiliation();
    if (affiliation != null && !affiliation.isBlank()) {
      return affiliation;
    }
    if (person.getCompanyCode() == null) {
      return null;
    }
    TbCompany company = companyMapper.selectById(person.getCompanyCode());
    return company == null ? null : company.getCompanyName();
  }

  /**
   * 허가구역의 출처는 인원 구분에 따라 다르다.
   *
   * <ul>
   *   <li>정규인원 — 사람에게 직접 붙은 출입그룹(`tb_person_ac_group`)
   *   <li>그 밖(임시·장기·상주·순찰·대여) — <b>방문 단위</b>로 잡힌 구역(`tb_visit_ac_group`). 여기를 안 보면 방문객은 허가구역이 늘 비어
   *       보인다.
   * </ul>
   */
  private List<String> acGroupNames(TbPerson person) {
    return PERSON_TYPE_REGULAR.equals(person.getPersonType())
        ? acGroupMapper.selectAcGroupNames(person.getPersonId())
        : visitMapper.selectAcGroupNamesByPerson(person.getPersonId());
  }

  /**
   * 허가기간 — 출처가 허가구역과 같은 갈래로 나뉜다.
   *
   * <ul>
   *   <li>정규인원 — 사람에게 붙은 출입기간(`tb_person.access_start_dt`~`access_end_dt`)
   *   <li>그 밖 — <b>방문 단위</b>의 작업기간(`tb_visit.work_start_dt`~`work_end_dt`). 구역과 <b>같은 방문</b>(가장
   *       최근)을 본다.
   * </ul>
   *
   * <p>둘 다 없으면 {@code null} 이다 — 화면은 "-" 로 둔다. 미등록 인증도 화면에는 올라와야 하므로 없는 값에 예외를 던지지 않는다.
   */
  private String period(TbPerson person) {
    if (PERSON_TYPE_REGULAR.equals(person.getPersonType())) {
      TbPerson period = personMapper.selectAccessPeriod(person.getPersonId());
      return period == null ? null : range(period.getAccessStartDt(), period.getAccessEndDt());
    }
    TbVisit visit = visitMapper.selectLatestVisitByPerson(person.getPersonId());
    return visit == null ? null : range(visit.getWorkStartDt(), visit.getWorkEndDt());
  }

  /**
   * 기간 표기 — "2026-01-01 09:00:00 ~ 2037-12-31 23:59:00".
   *
   * <p>정규·방문 구분 없이 <b>같은 형식</b>이다. 화면에서 두 인원의 값을 나란히 볼 때 형식이 다르면 같은 뜻인지 매번 되짚어야 한다.
   *
   * <p>초까지 쓴다. 출입 판정은 초 단위로 일어나므로, 경계에 걸린 사람을 두고 "왜 막혔나"를 볼 때 분까지만으로는 답이 나오지 않는다. 값은 DB 에서 초까지 읽어
   * 온다 — 없는 초를 {@code :00} 으로 지어내지 않는다.
   *
   * <p>한쪽만 비어 있어도 있는 쪽은 보여 준다(빈 쪽은 "?"). 기간이 반쯤 비었다는 사실 자체가 현장에서 확인할 거리다.
   */
  private static String range(String start, String end) {
    String s = stamp(start);
    String e = stamp(end);
    if (s == null && e == null) {
      return null;
    }
    return (s == null ? "?" : s) + " ~ " + (e == null ? "?" : e);
  }

  /** SQL 이 "YYYY-MM-DDThh:mm:ss"(style 126)로 내려준다. 화면에는 가운데 T 대신 공백을 쓴다. */
  private static String stamp(String dt) {
    return (dt == null || dt.isBlank()) ? null : dt.replace('T', ' ');
  }

  /** 소켓 상태가 바뀌면 보고 있는 모든 화면에 알린다 — 조용히 끊기면 "인증이 없는 것"과 구분되지 않는다. */
  private void pushStatus() {
    Map<String, Object> payload = statusPayload();
    viewers.forEach((emitter, deviceId) -> send(emitter, "status", payload));
  }

  /**
   * 화면에 보내는 상태.
   *
   * <p>{@code connected} 는 "소켓 객체가 있는가"가 아니라 <b>이벤트를 받을 수 있는가</b>다. 소켓만 열리고 세션이 거부됐거나 events/start
   * 가 실패한 상태를 '연결됨'으로 보내면, 화면은 "수신 중"인데 이벤트는 영영 안 오는 가장 나쁜 그림이 된다.
   */
  private Map<String, Object> statusPayload() {
    String error = eventSocket.error();
    return Map.of("connected", eventSocket.isReady(), "message", error == null ? "" : error);
  }

  /**
   * SSE 연결 유지 — 중간 프록시가 조용한 연결을 끊는 것을 막는다.
   *
   * <p>이름 붙은 이벤트가 아니라 <b>주석</b>({@code :} 로 시작하는 줄)으로 보낸다. 규격상 주석은 브라우저가 이벤트로 올리지 않고 연결 유지 신호로만 쓴다
   * — 화면 쪽에 처리할 것이 없어진다.
   */
  @Scheduled(fixedDelay = 25_000)
  public void ping() {
    viewers.forEach((emitter, deviceId) -> keepAlive(emitter));
  }

  private void keepAlive(SseEmitter emitter) {
    try {
      synchronized (emitter) {
        emitter.send(SseEmitter.event().comment("keep-alive"));
      }
    } catch (Exception e) {
      release(emitter); // 이미 닫힌 화면 — 조용히 정리한다
    }
  }

  /**
   * BiostarX 소켓 살아있음 확인 — 무이벤트는 "고장"과 "한산함"을 구분해 주지 않으므로 직접 물어본다.
   *
   * <p>세션 idle 만료가 이 화면의 가장 현실적인 장애다. 새벽처럼 통행이 없는 시간대에 세션이 죽으면, 소켓은 열린 채로 남고 출근 시간에야 "이벤트가 안 온다"로
   * 드러난다. 주기 확인이 세션 idle 시간도 함께 갱신해 애초에 죽지 않게 한다.
   */
  @Scheduled(fixedDelay = 3 * 60_000)
  public void watchdog() {
    if (viewers.isEmpty()) {
      return; // 보는 사람이 없으면 소켓도 없다
    }
    try {
      eventSocket.verify();
    } catch (Exception e) {
      log.warn("BiostarX 소켓 확인 실패: {}", e.toString());
    }
  }

  /**
   * SSE 한 프레임 전송.
   *
   * <p><b>emitter 마다 잠근다.</b> {@link SseEmitter} 는 동시 전송을 막아 주지 않는데, 여기서는 두 스레드가 같은 화면에 쓴다 — 이벤트를
   * 보내는 작업 스레드와 25초마다 도는 연결유지 스레드다. 겹치면 프레임이 서로 끼어들어 JSON 이 깨지고, 그 이벤트는 화면에서 <b>조용히 사라진다</b>(사진 같은
   * 큰 값이 실린 프레임일수록 길어서 더 잘 겹친다).
   */
  private void send(SseEmitter emitter, String name, Object data) {
    try {
      synchronized (emitter) {
        emitter.send(SseEmitter.event().name(name).data(data));
      }
    } catch (Exception e) {
      release(emitter); // 이미 닫힌 화면 — 조용히 정리한다
    }
  }

  /**
   * 마지막 화면이 닫히면 장비 소켓도 닫는다(아무도 안 보는 이벤트를 계속 받지 않는다).
   *
   * <p>{@link #subscribe} 와 같은 잠금을 쓴다 — 나누면 "지금 비었다"고 판단한 뒤 소켓을 닫는 사이에 새 구독자가 끼어들어, 그 구독자만 남고 소켓은
   * 닫힌 상태가 된다.
   */
  private void release(SseEmitter emitter) {
    synchronized (viewerLock) {
      if (viewers.remove(emitter) != null && viewers.isEmpty()) {
        eventSocket.stop();
      }
    }
  }

  private TbSystem config() {
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null || cfg.getBiostarIp() == null || cfg.getBiostarIp().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 접속정보가 없습니다. 설정관리에서 등록하세요.");
    }
    return cfg;
  }

  private String pw(TbSystem cfg) {
    return cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }

  /** "2026-08-11T01:38:01.00Z" → "01:38:01". 형식이 다르면 원문을 남긴다(조용히 비우지 않는다). */
  static String time(String datetime) {
    if (datetime == null || datetime.length() < 19 || datetime.charAt(10) != 'T') {
      return datetime;
    }
    return datetime.substring(11, 19);
  }

  /**
   * 출입그룹 이름 목록 → 구역 번호를 이어 붙인다. 예: [인원구역1, 인원구역2, 인원구역5] → "125"
   *
   * <p>번호가 없는 이름은 그대로 남긴다 — 조용히 사라지면 어느 구역이 빠졌는지 알 수 없다.
   */
  static String areaNos(List<String> names) {
    StringBuilder sb = new StringBuilder();
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Matcher m = AREA_NO.matcher(name);
      sb.append(m.find() ? m.group(1) : name.trim());
    }
    return sb.toString();
  }
}
