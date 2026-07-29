package AirPort.adapter.cardprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.List;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Sides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 카드 프린터 연동 — card_project 템플릿 로드 + 렌더 이미지 실제 인쇄. (docs/integration.md, 외부연동 격리 §4)
 *
 * <p>CR-80 규격(86mm×54mm)으로 용지를 잡고, 세로형이면 방향을 바꾼다. 프린터명은 부분 일치로 찾고 비면 기본 프린터를
 * 쓴다. 템플릿은 한 번 읽어 캐시한다.
 */
@Component
public class CardPrintAdapter {

  private static final Logger log = LoggerFactory.getLogger(CardPrintAdapter.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${card-print.project-file:card-templates/card_project.json}")
  private String projectFile;

  @Value("${card-print.printer-name:}")
  private String printerName;

  /** 인쇄 위치 미세조정(mm) — 양수면 오른쪽/아래로 이동. 프린터 원점 오차 보정용(기본 0). */
  @Value("${card-print.offset-x-mm:0}")
  private double offsetXmm;

  @Value("${card-print.offset-y-mm:0}")
  private double offsetYmm;

  /** 인쇄 배율 — 1.0=카드에 맞춰 채움. 프린터 여백 등으로 작게 나오면 1보다 키워 꽉 차게(경계 넘치면 잘림). */
  @Value("${card-print.scale:1.0}")
  private double printScale;

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

  /** 앞/뒤 면을 한 잡의 페이지로 묶어 인쇄한다(양면이면 DUPLEX — 한 장에 양면). */
  public void print(List<BufferedImage> sides) throws PrinterException {
    PrintService svc = findPrinter();
    if (svc == null) {
      throw new PrinterException("카드 프린터를 찾을 수 없습니다: " + printerName);
    }
    PrinterJob job = PrinterJob.getPrinterJob();
    job.setPrintService(svc);
    job.setJobName("CJAirPort Card");
    job.setPageable(
        new Pageable() {
          @Override
          public int getNumberOfPages() {
            return sides.size();
          }

          @Override
          public PageFormat getPageFormat(int i) {
            return pageFormat(sides.get(i));
          }

          @Override
          public Printable getPrintable(int i) {
            return printable(sides.get(i));
          }
        });
    PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
    if (sides.size() > 1) {
      attrs.add(Sides.DUPLEX); // 앞/뒤를 한 장에
    }
    job.print(attrs);
    log.info("카드 인쇄 완료: {}면 → {}", sides.size(), svc.getName());
  }

  private PageFormat pageFormat(BufferedImage image) {
    double mmToPt = 72.0 / 25.4;
    double cardW = 86 * mmToPt;
    double cardH = 54 * mmToPt;
    boolean portrait = image.getHeight() > image.getWidth();
    double pw = portrait ? cardH : cardW;
    double ph = portrait ? cardW : cardH;
    Paper paper = new Paper();
    paper.setSize(pw, ph);
    paper.setImageableArea(0, 0, pw, ph);
    PageFormat pf = new PageFormat();
    pf.setPaper(paper);
    pf.setOrientation(portrait ? PageFormat.PORTRAIT : PageFormat.LANDSCAPE);
    return pf;
  }

  private Printable printable(BufferedImage image) {
    return (graphics, pageFormat, pageIndex) -> {
      Graphics2D g = (Graphics2D) graphics;
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      double mmToPt = 72.0 / 25.4;
      // 인쇄영역을 가로·세로 모두 꽉 채운다(비율 유지 아님) — 카드 규격 디자인이라 왜곡은 무시할 수준.
      // printScale=1.0 이면 영역에 딱 채우고, >1 이면 경계를 넘겨 확대(가장자리 잘림=풀블리드). 오프셋으로 위치 보정.
      double dw = pageFormat.getImageableWidth() * printScale;
      double dh = pageFormat.getImageableHeight() * printScale;
      double dx =
          pageFormat.getImageableX()
              + (pageFormat.getImageableWidth() - dw) / 2
              + offsetXmm * mmToPt;
      double dy =
          pageFormat.getImageableY()
              + (pageFormat.getImageableHeight() - dh) / 2
              + offsetYmm * mmToPt;
      g.drawImage(
          image,
          (int) Math.round(dx),
          (int) Math.round(dy),
          (int) Math.round(dw),
          (int) Math.round(dh),
          null);
      return Printable.PAGE_EXISTS;
    };
  }

  private PrintService findPrinter() {
    PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
    if (printerName != null && !printerName.isEmpty()) {
      for (PrintService ps : services) {
        if (ps.getName().toLowerCase().contains(printerName.toLowerCase())) {
          return ps;
        }
      }
    }
    return PrintServiceLookup.lookupDefaultPrintService();
  }
}
