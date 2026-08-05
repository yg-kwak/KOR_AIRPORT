package AirPort.config;

import org.apache.catalina.valves.AccessLogValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 접근 로그(요청 1건 = 1줄) — 형식·보관 규칙을 코드에 고정한다.
 *
 * <p>운영 설정 파일에서 만질 값은 <b>켜고 끄기와 경로뿐</b>이라, 나머지를 properties 에 늘어놓으면 현장에서 읽어야 할 줄만 늘고 잘못 바꿀 여지도
 * 생긴다(특히 형식 문자열). 그래서 세부는 여기에 두고 그 이유를 함께 적는다.
 *
 * <p>끄려면 {@code server.tomcat.accesslog.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(
    name = "server.tomcat.accesslog.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AccessLogConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

  /** 파일 로그와 같은 폴더에 쌓는다. */
  @Value("${LOG_PATH:logs}")
  private String logPath;

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    AccessLogValve valve = new AccessLogValve();
    valve.setDirectory(logPath);
    valve.setPrefix("access");
    valve.setSuffix(".log");
    valve.setFileDateFormat(".yyyy-MM-dd"); // 날짜가 바뀌면 새 파일
    // 시각 · 클라이언트IP · 요청 · 상태코드 · 응답크기 · 처리시간
    //  · %D 는 Tomcat 10 부터 마이크로초라 '12176ms' 처럼 오해를 부른다 → 밀리초는 %{ms}T
    //  · 세션ID(%S)는 넣지 않는다 — 로그가 유출되면 그대로 세션 탈취에 쓰인다(security.md)
    valve.setPattern("%{yyyy-MM-dd HH:mm:ss}t %a \"%r\" %s %b %{ms}Tms");
    valve.setMaxDays(60);
    valve.setBuffered(false); // 요청 즉시 기록 — 장애 시 마지막 요청까지 남는다
    factory.addEngineValves(valve);
  }
}
