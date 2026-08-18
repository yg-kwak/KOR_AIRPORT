package AirPort.controller;

import AirPort.adapter.parking.ParkingEventNotice;
import AirPort.service.ParkingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주차 입·출차 이벤트 <b>수신</b> — 아마노 주차관제가 우리를 호출한다. (docs/integration.md)
 *
 * <p><b>경로는 우리가 정하지 않는다.</b> 아마노 문서가 파트너사에게 {@code http://{도메인명}/api/InOutCar} 를 제공하라고 규정하므로 이 주소여야
 * 한다 — 우리 화면 규약({@code /{영역}/{stem}} ↔ tb_menu.menu_url)의 예외이고, 메뉴가 아니라서 권한 판정 대상도 아니다. ({@code
 * WebConfig} 에서 로그인·메뉴통제 제외)
 *
 * <p>세션이 없는 외부 요청이므로 <b>보내는 쪽 IP 로만 막는다</b>({@code app.parking.event.allow-ips}). 비워 두면 모두 허용이라,
 * 운영에서는 주차서버 IP 를 반드시 넣는다.
 *
 * <p>응답은 <b>항상 200</b>이다. 저장에 실패했다고 500 을 주면 주차서버가 같은 건을 계속 재전송한다 — 우리 문제로 저쪽 큐를 막지 않는다. 못 남긴 사유는
 * 우리 로그에 남긴다.
 */
@RestController
@RequestMapping("/api/InOutCar")
public class ParkingEventApiController {

  private static final Logger log = LoggerFactory.getLogger(ParkingEventApiController.class);

  private static final Map<String, Object> OK = Map.of("result", "OK");

  private final ParkingEventService parkingEventService;
  private final ObjectMapper objectMapper;
  private final List<String> allowedIps;

  public ParkingEventApiController(
      ParkingEventService parkingEventService,
      ObjectMapper objectMapper,
      @Value("${app.parking.event.allow-ips:}") String allowIps) {
    this.parkingEventService = parkingEventService;
    this.objectMapper = objectMapper;
    this.allowedIps =
        (allowIps == null || allowIps.isBlank())
            ? List.of()
            : Arrays.stream(allowIps.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
  }

  /** 입·출차 이벤트 수신. 본문은 {@link ParkingEventNotice} 규격. */
  @PostMapping
  public ResponseEntity<Map<String, Object>> receive(
      @RequestBody ParkingEventNotice notice, HttpServletRequest request) {
    String from = request.getRemoteAddr();
    if (!allowed(from)) {
      log.warn("주차 이벤트 거부 — 허용되지 않은 IP {}", from);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("result", "FORBIDDEN"));
    }
    if (notice.eventType() == null || notice.eventType().isBlank()) {
      log.warn("주차 이벤트 무시 — eventType 이 없습니다 ({})", from);
      return ResponseEntity.ok(OK);
    }
    if (notice.eventDateTime() == null) {
      // 시각이 없으면 언제 일어난 일인지 알 수 없어 이력이 되지 못한다
      log.warn("주차 이벤트 무시 — eventTime 형식 오류 '{}' ({})", notice.eventTime(), from);
      return ResponseEntity.ok(OK);
    }
    try {
      parkingEventService.receive(notice, objectMapper.writeValueAsString(notice));
    } catch (Exception e) {
      // 200 을 유지한다 — 여기서 500 을 주면 주차서버가 같은 건을 계속 다시 보낸다
      log.error("주차 이벤트 저장 실패 — {} {}", notice.carNumber(), notice.eventTime(), e);
    }
    return ResponseEntity.ok(OK);
  }

  /** 허용 IP 미설정이면 모두 통과(설치 초기). 설정돼 있으면 목록에 있는 주소만. */
  private boolean allowed(String remoteAddr) {
    return allowedIps.isEmpty() || allowedIps.contains(remoteAddr);
  }
}
