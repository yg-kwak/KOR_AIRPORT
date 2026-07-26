package AirPort.adapter.cardprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.util.List;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
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

  /** 여러 면(앞/뒤)을 순서대로 인쇄한다. */
  public void print(List<BufferedImage> sides) throws PrinterException {
    PrintService svc = findPrinter();
    if (svc == null) {
      throw new PrinterException("카드 프린터를 찾을 수 없습니다: " + printerName);
    }
    for (BufferedImage img : sides) {
      printOne(svc, img);
    }
    log.info("카드 인쇄 완료: {}면 → {}", sides.size(), svc.getName());
  }

  private void printOne(PrintService svc, BufferedImage image) throws PrinterException {
    PrinterJob job = PrinterJob.getPrinterJob();
    job.setPrintService(svc);
    job.setJobName("CJAirPort Card");

    double mmToPt = 72.0 / 25.4;
    double cardW = 86 * mmToPt;
    double cardH = 54 * mmToPt;
    Paper paper = new Paper();
    boolean portrait = image.getHeight() > image.getWidth();
    double pw = portrait ? cardH : cardW;
    double ph = portrait ? cardW : cardH;
    paper.setSize(pw, ph);
    paper.setImageableArea(0, 0, pw, ph);
    PageFormat pf = new PageFormat();
    pf.setPaper(paper);
    pf.setOrientation(portrait ? PageFormat.PORTRAIT : PageFormat.LANDSCAPE);

    job.setPrintable(
        (graphics, pageFormat, pageIndex) -> {
          if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
          }
          Graphics2D g = (Graphics2D) graphics;
          g.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          double scale =
              Math.min(
                  pageFormat.getImageableWidth() / image.getWidth(),
                  pageFormat.getImageableHeight() / image.getHeight());
          double ox =
              pageFormat.getImageableX()
                  + (pageFormat.getImageableWidth() - image.getWidth() * scale) / 2;
          double oy =
              pageFormat.getImageableY()
                  + (pageFormat.getImageableHeight() - image.getHeight() * scale) / 2;
          g.translate(ox, oy);
          g.scale(scale, scale);
          g.drawImage(image, 0, 0, null);
          return Printable.PAGE_EXISTS;
        },
        pf);
    job.print();
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
