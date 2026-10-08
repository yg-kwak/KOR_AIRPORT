package AirPort.adapter.biostar;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BiostarX 이벤트 소켓이 <b>정말</b> 살아 있는가 — {@link BiostarEventSocket} 가 쓴다.
 *
 * <p>주기 확인의 REST 호출({@code events/start}·세션 비교)로는 소켓이 조용히 죽은 것을 못 본다. 방화벽이 유휴 연결을 버리거나 장비가 끊김 신호 없이
 * 닫으면 소켓은 열린 채 이벤트만 안 오고, 끊김 신호가 없으니 다시 붙지도 않는다 — 화면은 "수신 중"인 채 멈춘다. 그래서 소켓 자체를 본다:
 *
 * <ul>
 *   <li>ping 에 답(pong)이 없으면 {@link Result#DEAD} — 버리고 다시 붙는다.
 *   <li>답은 하는데 {@link #QUIET_MINUTES} 분 동안 받은 프레임이 하나도 없으면 {@link Result#QUIET} — 장비 쪽 구독만 풀렸을 수 있어
 *       미리 다시 연다(1초 남짓).
 * </ul>
 */
final class BiostarSocketProbe {

  private static final Logger log = LoggerFactory.getLogger(BiostarSocketProbe.class);

  enum Result {
    ALIVE,
    DEAD,
    QUIET
  }

  /** ping 응답을 기다리는 시간 — 장비는 곧바로 답한다. */
  static final int PONG_SECONDS = 10;

  /** 이만큼 아무 프레임도 없으면 미리 다시 연다. */
  static final long QUIET_MINUTES = 30;

  /** 새 구독자가 올 때의 확인 간격 — 새로고침이 몰려도(EventSource 는 3초마다 다시 붙는다) 장비를 두드리지 않게. */
  private static final long GAP_MS = 30_000;

  private volatile long lastFrameAt; // 마지막으로 받은 프레임(연결 시각 포함)
  private volatile long lastProbeAt;
  private volatile CompletableFuture<Void> pong; // 보낸 ping 의 답을 기다리는 자리

  /** 프레임을 받았다(연결된 순간도 여기서 센다 — 조용함은 그때부터). */
  void frame() {
    lastFrameAt = System.currentTimeMillis();
  }

  /** ping 의 답이 왔다. */
  void pong() {
    CompletableFuture<Void> p = pong;
    if (p != null) {
      p.complete(null);
    }
  }

  /** 새 구독자를 계기로 확인해도 되는가 — {@link #GAP_MS} 에 한 번만 참. */
  boolean claim() {
    long now = System.currentTimeMillis();
    if (now - lastProbeAt <= GAP_MS) {
      return false;
    }
    lastProbeAt = now;
    return true;
  }

  /** ping 을 보내 답을 기다린다 — 확인하는 스레드를 최대 {@code pongSeconds}×2 초 붙잡는다. */
  Result check(WebSocket ws, int pongSeconds) {
    CompletableFuture<Void> answer = new CompletableFuture<>();
    pong = answer;
    long sent = System.currentTimeMillis();
    lastProbeAt = sent;
    try {
      ws.sendPing(ByteBuffer.wrap(new byte[] {1})).get(pongSeconds, TimeUnit.SECONDS);
      answer.get(pongSeconds, TimeUnit.SECONDS);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.debug("BiostarX 소켓 확인 — ping 응답 없음 ({})", e.getClass().getSimpleName());
      return Result.DEAD;
    }
    long now = System.currentTimeMillis();
    // 평소엔 남기지 않는다(3분마다) — "화면이 멈춘다"를 볼 때 AirPort.adapter 를 DEBUG 로 올리면 보인다
    log.debug("BiostarX 소켓 확인 — pong {}ms, 마지막 프레임 {}초 전", now - sent, (now - lastFrameAt) / 1000);
    return now - lastFrameAt > QUIET_MINUTES * 60_000L ? Result.QUIET : Result.ALIVE;
  }
}
