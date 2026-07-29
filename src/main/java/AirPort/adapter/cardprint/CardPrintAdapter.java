package AirPort.adapter.cardprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 카드 프린트 템플릿(card_project) 로더. (docs/integration.md, 외부연동 격리 §4)
 *
 * <p>실제 인쇄는 <b>클라이언트 브라우저</b>가 렌더 이미지를 {@code window.print()} 로 출력한다(프린터가 클라이언트 PC 에
 * USB/LAN 으로 연결). 여기서는 디자인 export 템플릿을 한 번 읽어 캐시만 한다.
 */
@Component
public class CardPrintAdapter {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${card-print.project-file:card-templates/card_project.json}")
  private String projectFile;

  private CardProject cached;

  /** 디자인 템플릿(card_project) 로드 — 캐시. */
  public synchronized CardProject project() {
    if (cached == null) {
      File file = new File(projectFile);
      if (!file.exists()) {
        throw new RuntimeException("카드 템플릿 파일이 없습니다: " + file.getAbsolutePath());
      }
      try {
        cached = objectMapper.readValue(file, CardProject.class);
      } catch (Exception e) {
        throw new RuntimeException("카드 템플릿 로드 실패: " + e.getMessage(), e);
      }
    }
    return cached;
  }
}
