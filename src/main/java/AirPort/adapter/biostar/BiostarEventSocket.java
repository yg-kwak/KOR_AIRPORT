package AirPort.adapter.biostar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.WebSocket;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BiostarX 실시간 이벤트 소켓 — {@code wss://{IP}/wsapi}. (docs/integration.md)
 *
 * <p>흐름은 <b>소켓 연결 → 세션 알림 → {@code POST /api/events/start} → MESSAGE 수신</b> 이다. 셋 다 성공해야 이벤트가 흐른다.
 *
 * <p>브라우저가 이 소켓을 직접 열 수는 없다 — BiostarX 인증서는 self-signed 이고 세션(bs-session-id)은 서버만 갖고 있다. 그래서 서버가 소켓
 * <b>하나</b>를 유지하고, 화면에는 서버가 다시 밀어 준다.
 *
 * <p><b>상시 운용 전제로 만들었다.</b> 이 화면은 하루 종일 켜 둔다. 그래서 가장 경계한 것은 끊김이 아니라 <b>죽은 줄 모르는 것</b>이다:
 *
 * <ul>
 *   <li>어느 단계에서 실패하든 {@link #isReady()} 가 거짓이 되고 사유({@link #error()})가 화면까지 간다.
 *   <li>어느 단계에서 실패하든 재연결을 예약한다. 소켓이 열린 채 이벤트만 안 오는 상태로 방치하지 않는다.
 *   <li>{@link #verify()} 가 주기적으로 살아 있음을 <b>능동 확인</b>한다 — 무이벤트는 "아무도 안 지나감"과 구분되지 않는다.
 * </ul>
 */
@Component
public class BiostarEventSocket {

  private static final Logger log = LoggerFactory.getLogger(BiostarEventSocket.class);

  /** 재연결 간격 — 장비가 재시작 중일 수 있어 촘촘히 두드리지 않는다. */
  private static final int RETRY_SECONDS = 10;

  /** 소켓 인증에 쓰는 세션 이름 — 본문 메시지 {@code bs-session-id={세션}} 의 앞부분. */
  private static final String SESSION_HEADER = "bs-session-id";

  /** 세션 확인 응답을 기다리는 시간 — 장비는 곧바로 답한다. 넘으면 연결이 잘못된 것이다. */
  private static final int AUTH_REPLY_SECONDS = 10;

  private final ObjectMapper objectMapper;
  private final BiostarSession session;
  private final BiostarEventAdapter eventAdapter;

  private final Object lock = new Object();
  private ScheduledExecutorService worker; // 연결·재연결 전용(호출자를 막지 않는다)
  private WebSocket socket;
  private boolean wanted; // 화면을 보는 사람이 있는가 — 없으면 다시 붙지 않는다
  private boolean ready; // 소켓 + 세션 + events/start 가 모두 성공했는가
  private String lastError; // 화면에 그대로 보여 줄 사유. 정상이면 null
  private String boundSessionId; // 이 소켓이 묶인 BiostarX 세션 — 바뀌면 소켓은 죽은 것이다
  private String ip;
  private String loginId;
  private String password;
  private Consumer<BiostarAuthEvent> sink;
  private Runnable statusListener; // 상태가 바뀌면 알린다(화면 갱신용)

  public BiostarEventSocket(
      ObjectMapper objectMapper, BiostarSession session, BiostarEventAdapter eventAdapter) {
    this.objectMapper = objectMapper;
    this.session = session;
    this.eventAdapter = eventAdapter;
  }

  /**
   * 구독자가 생겼다 — 소켓이 없으면 연다. 이미 열려 있으면 아무 것도 하지 않는다(구독자가 늘어도 소켓은 하나).
   *
   * <p><b>연결은 비동기다.</b> 로그인+핸드셰이크에 1초 가까이 걸리는데, 그동안 호출자(구독 요청)를 붙잡으면 사람이 몰릴 때 화면 열기가 느려지고 구독자 목록
   * 잠금도 오래 잡힌다.
   */
  public void start(
      String ip,
      String loginId,
      String password,
      Consumer<BiostarAuthEvent> sink,
      Runnable statusListener) {
    synchronized (lock) {
      this.ip = ip;
      this.loginId = loginId;
      this.password = password;
      this.sink = sink;
      this.statusListener = statusListener;
      if (worker == null) {
        worker = Executors.newSingleThreadScheduledExecutor(BiostarEventSocket::thread);
      }
      if (wanted) {
        return; // 이미 돌고 있다
      }
      wanted = true;
    }
    submit(this::connect);
  }

  /** 마지막 구독자가 떠났을 때. 소켓을 닫고 재연결도 멈춘다(아무도 안 보는 이벤트를 계속 받지 않는다). */
  public void stop() {
    WebSocket ws;
    synchronized (lock) {
      wanted = false;
      ready = false;
      lastError = null;
      boundSessionId = null;
      ws = socket;
      socket = null;
    }
    abort(ws);
    if (ws != null) {
      log.info("BiostarX 실시간 이벤트 소켓 종료 (구독자 없음)");
    }
  }

  /** 이벤트를 받을 수 있는 상태인가 — 소켓만 열린 것으로는 부족하다(세션·events/start 까지 성공해야 한다). */
  public boolean isReady() {
    synchronized (lock) {
      return ready;
    }
  }

  /** 마지막 실패 사유(화면 표시용). 정상이면 null. */
  public String error() {
    synchronized (lock) {
      return lastError;
    }
  }

  /**
   * 살아 있는지 능동 확인 — 주기적으로 불린다.
   *
   * <p>이벤트가 안 온다는 사실만으로는 <b>고장인지 한산한 것인지</b> 알 수 없다. 그래서 직접 물어본다. 확인 방법은 {@code events/start} 재호출이다
   * — 우리가 의존하는 바로 그 기능이고, 성공하면 세션 idle 시간도 함께 갱신된다.
   *
   * <p><b>성공만으로는 부족하다.</b> 세션이 만료됐다면 {@link BiostarSession} 이 조용히 재로그인해 성공을 돌려주는데, 그 순간 소켓이 묶인 세션은
   * 죽은 것이다(소켓은 열려 있고 이벤트만 안 온다). 그래서 세션이 바뀌었는지도 함께 본다.
   */
  public void verify() {
    String currentIp;
    String currentId;
    String currentPw;
    String bound;
    synchronized (lock) {
      if (!wanted || !ready) {
        return; // 안 돌고 있거나 이미 재연결 대기 중 — 확인할 것이 없다
      }
      currentIp = ip;
      currentId = loginId;
      currentPw = password;
      bound = boundSessionId;
    }
    String err = eventAdapter.start(currentIp, currentId, currentPw);
    if (err != null) {
      dropAndRetry("장비 확인에 실패했습니다: " + err);
      return;
    }
    String now = currentSessionId(currentIp, currentId, currentPw);
    if (!Objects.equals(bound, now)) {
      // 다른 화면(인원 저장 등)에서 세션이 갱신되면 이 소켓은 죽은 세션에 묶인 채 남는다
      dropAndRetry("BiostarX 세션이 바뀌어 소켓을 다시 엽니다");
    }
  }

  private String currentSessionId(String ip, String loginId, String password) {
    try {
      return session.sessionId(baseUrl(ip), loginId, password, false); // 캐시값 — 통신 없음
    } catch (Exception e) {
      return null;
    }
  }

  private void connect() {
    String url;
    String currentIp;
    String currentId;
    String currentPw;
    synchronized (lock) {
      if (!wanted) {
        return;
      }
      currentIp = ip;
      currentId = loginId;
      currentPw = password;
      url = wsUrl(currentIp);
    }
    String sid;
    try {
      sid = session.sessionId(baseUrl(currentIp), currentId, currentPw, true); // 항상 새 세션으로 연다
    } catch (Exception e) {
      dropAndRetry("BiostarX 로그인에 실패했습니다: " + message(e));
      return;
    }
    WebSocket ws;
    java.util.concurrent.CompletableFuture<String> authReply =
        new java.util.concurrent.CompletableFuture<>();
    try {
      ws =
          session
              .client()
              .newWebSocketBuilder()
              .buildAsync(URI.create(url), new Listener(authReply))
              .join();
      // 세션은 헤더가 아니라 소켓 본문으로 알린다 — 연 직후 첫 메시지가 "bs-session-id={세션}" 이다
      // (BiostarX 자체 화면이 그렇게 한다. 브라우저는 WebSocket 핸드셰이크에 임의 헤더를 못 붙인다).
      // 이걸 빼면 연결도 되고 events/start 도 성공(code 0)하지만, 소켓이 세션에 묶이지 않아
      // 이벤트가 한 건도 오지 않는다 — 조용히 아무 일도 안 일어나는 가장 나쁜 실패다.
      ws.sendText(SESSION_HEADER + "=" + sid, true).join();

      // 장비가 소켓을 세션에 묶었다고 답할 때까지 기다린다. 기다리지 않고 곧바로 events/start 를
      // 부르면, 장비가 아직 이 소켓을 세션에 못 붙인 상태에서 시작 요청을 받는다 — 요청은 code 0 을
      // 주고 이벤트는 오지 않는다. 증상이 없어 가장 찾기 어려운 실패다.
      String code = authReply.get(AUTH_REPLY_SECONDS, TimeUnit.SECONDS);
      if (!"0".equals(code)) {
        dropAndRetryWith(ws, "이벤트 소켓이 세션을 거부했습니다 (code " + code + ")");
        return;
      }
    } catch (java.util.concurrent.TimeoutException e) {
      dropAndRetry("장비가 세션 확인에 응답하지 않았습니다 (" + AUTH_REPLY_SECONDS + "초)");
      return;
    } catch (Exception e) {
      dropAndRetry("BiostarX 이벤트 소켓에 연결하지 못했습니다: " + message(e));
      return;
    }
    synchronized (lock) {
      if (!wanted) { // 붙는 사이에 마지막 구독자가 떠났다
        abort(ws);
        return;
      }
      socket = ws;
      boundSessionId = sid;
    }
    log.info("BiostarX 실시간 이벤트 소켓 연결 — {}", url);

    // 소켓이 열린 뒤에 시작을 알려야 이벤트가 흐른다(같은 세션이어야 한다).
    // 여기서 실패하면 소켓은 열려 있는데 이벤트만 안 온다 — 반드시 버리고 다시 붙는다.
    String err = eventAdapter.start(currentIp, currentId, currentPw);
    if (err != null) {
      dropAndRetry("실시간 이벤트를 시작하지 못했습니다: " + err);
      return;
    }
    markReady();
  }

  private void markReady() {
    synchronized (lock) {
      ready = true;
      lastError = null;
    }
    notifyStatus();
  }

  /** 아직 필드에 담기 전의 소켓을 버릴 때 — 핸드셰이크 직후 단계용. */
  private void dropAndRetryWith(WebSocket ws, String why) {
    abort(ws);
    dropAndRetry(why);
  }

  /** 어느 단계에서 실패하든 여기로 모인다 — 소켓을 버리고, 사유를 남기고, 다시 붙는다. */
  private void dropAndRetry(String why) {
    WebSocket ws;
    boolean stillWanted;
    synchronized (lock) {
      ws = socket;
      socket = null;
      ready = false;
      boundSessionId = null;
      stillWanted = wanted;
      lastError = stillWanted ? why + " (" + RETRY_SECONDS + "초 뒤 다시 시도합니다)" : null;
    }
    abort(ws);
    if (!stillWanted) {
      return;
    }
    log.warn("BiostarX 실시간 이벤트 — {} / {}초 뒤 재연결", why, RETRY_SECONDS);
    notifyStatus();
    schedule(this::connect, RETRY_SECONDS);
  }

  private static void abort(WebSocket ws) {
    if (ws != null) {
      ws.abort(); // 정상 종료 핸드셰이크를 기다리지 않는다 — 어차피 버릴 연결이다
    }
  }

  private void submit(Runnable task) {
    schedule(task, 0);
  }

  private void schedule(Runnable task, int delaySeconds) {
    ScheduledExecutorService ex;
    synchronized (lock) {
      ex = worker;
    }
    if (ex == null) {
      return;
    }
    try {
      ex.schedule(task, delaySeconds, TimeUnit.SECONDS);
    } catch (java.util.concurrent.RejectedExecutionException e) {
      log.debug("이벤트 소켓 작업 예약 생략 (종료 중)");
    }
  }

  /** MESSAGE 는 조각으로 나뉘어 올 수 있다 — last 가 될 때까지 모아 한 건으로 파싱한다. */
  private class Listener implements WebSocket.Listener {

    private final StringBuilder buffer = new StringBuilder();
    private final java.util.concurrent.CompletableFuture<String> authReply;

    Listener(java.util.concurrent.CompletableFuture<String> authReply) {
      this.authReply = authReply;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      return accumulate(webSocket, data, last);
    }

    /** 장비가 텍스트가 아닌 이진 프레임으로 보내는 경우 — 받지 않으면 이벤트가 통째로 사라진다. */
    @Override
    public CompletionStage<?> onBinary(
        WebSocket webSocket, java.nio.ByteBuffer data, boolean last) {
      return accumulate(webSocket, java.nio.charset.StandardCharsets.UTF_8.decode(data), last);
    }

    private CompletionStage<?> accumulate(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        String message = buffer.toString();
        buffer.setLength(0);
        dispatch(message, authReply);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      dropAndRetry("BiostarX 연결이 끊겼습니다 (" + statusCode + ")");
      return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      dropAndRetry("BiostarX 연결 오류 (" + error.getClass().getSimpleName() + ")");
    }
  }

  /**
   * 한 건 파싱 — 이벤트가 아닌 메시지(세션 응답 등)는 따로 처리한다.
   *
   * <p>받은 프레임은 <b>DEBUG</b> 로 한 줄 남긴다. 연동이 자리를 잡아 평소에는 필요 없지만, <b>"인증했는데 화면에 안 뜬다"를 가르는 첫 단서</b>다 —
   * 이 줄이 보이면 장비는 보냈고 우리가 거른 것이고, 안 보이면 애초에 안 온 것이다. 그 증상이 나오면 {@code AirPort.adapter}·{@code
   * AirPort.service} 로거를 DEBUG 로 올린다 — <b>걸러진 사유도 {@code MonitorService} 가 DEBUG 로 남긴다</b>(상시 운용에서
   * 인증마다 여러 줄이 쌓여 INFO 에서 내렸다).
   */
  private void dispatch(String message, java.util.concurrent.CompletableFuture<String> authReply) {
    BiostarAuthEvent parsed;
    try {
      parsed = parse(objectMapper, message);
    } catch (Exception e) {
      log.info("BiostarX 이벤트 파싱 실패(무시) — {} 본문: {}", e.toString(), abbreviate(message));
      return;
    }
    if (parsed == null) {
      handleNonEvent(message, authReply);
      return;
    }
    log.debug(
        "BiostarX 이벤트 수신 — {}({}) 장치={} 인원={} 사진ID={}",
        parsed.eventName(),
        parsed.eventCode(),
        parsed.deviceId(),
        parsed.userId(),
        parsed.imageId());
    Consumer<BiostarAuthEvent> target;
    synchronized (lock) {
      target = sink;
    }
    if (target != null) {
      target.accept(parsed);
    }
  }

  /**
   * 이벤트가 아닌 프레임 — 세션 알림에 대한 응답({@code {"Response":{"code":"0"}}})이 여기로 온다.
   *
   * <p>첫 응답은 {@link #connect()} 가 기다리고 있으므로 그쪽으로 넘긴다. 그 뒤에 오는 거부 응답은 소켓이 열린 채 이벤트만 끊긴 상태이므로 직접 버리고
   * 다시 붙는다.
   */
  private void handleNonEvent(
      String message, java.util.concurrent.CompletableFuture<String> authReply) {
    String code;
    try {
      JsonNode resp = objectMapper.readTree(message).path("Response");
      if (resp.isMissingNode()) {
        log.debug("BiostarX 이벤트 아님(무시): {}", abbreviate(message));
        return;
      }
      code = resp.path("code").asText("");
    } catch (Exception e) {
      log.debug("BiostarX 소켓 응답 해석 실패(무시): {}", abbreviate(message));
      return;
    }
    if (authReply.complete(code)) {
      log.info("BiostarX 이벤트 소켓 세션 응답 — code {}", code);
      return; // 첫 응답 — connect() 가 판정한다
    }
    if (!"0".equals(code)) {
      dropAndRetry("이벤트 소켓이 세션을 거부했습니다 (code " + code + ")");
    }
  }

  /** MESSAGE 본문 → 이벤트. {@code Event} 가 없으면(하트비트 등) null. 테스트에서 직접 확인한다. */
  static BiostarAuthEvent parse(ObjectMapper mapper, String message) throws Exception {
    JsonNode event = mapper.readTree(message).path("Event");
    if (event.isMissingNode() || event.isNull()) {
      return null;
    }
    JsonNode type = event.path("event_type_id");
    JsonNode user = event.path("user_id");
    return new BiostarAuthEvent(
        text(type, "code"),
        text(type, "name"),
        text(event, "datetime"),
        text(event.path("device_id"), "id"),
        text(event.path("device_id"), "name"),
        text(user, "user_id"),
        text(event.path("image_id"), "image_data"));
  }

  private static String text(JsonNode node, String field) {
    String v = node.path(field).asText(null);
    return (v == null || v.isBlank()) ? null : v;
  }

  /** 로그용 절단 — 예상 밖 프레임이 길 수 있다. */
  private static String abbreviate(String s) {
    if (s == null) {
      return "(없음)";
    }
    return s.length() <= 300 ? s : s.substring(0, 300) + "…";
  }

  private void notifyStatus() {
    Runnable listener;
    synchronized (lock) {
      listener = statusListener;
    }
    if (listener != null) {
      listener.run();
    }
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  private static Thread thread(Runnable r) {
    Thread t = new Thread(r, "biostar-event-socket");
    t.setDaemon(true); // 종료를 막지 않는다
    return t;
  }

  /** {@code 192.168.0.10[:9443]} 또는 {@code https://...} → {@code wss://.../wsapi}. */
  static String wsUrl(String ip) {
    return baseUrl(ip).replaceFirst("^http", "ws") + "/wsapi";
  }

  private static String baseUrl(String ip) {
    return (ip.startsWith("http://") || ip.startsWith("https://")) ? ip : "https://" + ip;
  }
}
