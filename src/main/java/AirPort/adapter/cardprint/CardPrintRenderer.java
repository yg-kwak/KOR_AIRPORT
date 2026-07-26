package AirPort.adapter.cardprint;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * card_project 한 면(Side)을 인쇄용 {@link BufferedImage} 로 렌더한다. (docs/integration.md)
 *
 * <p>배경 이미지의 원본 해상도를 캔버스로 삼고, 디자인 좌표(캔버스 폭 {@code canvasWidth} 기준)를 배경 해상도로
 * 스케일해 사진·텍스트를 얹는다. 텍스트의 {@code {컬럼}} 바인딩은 전달된 값으로 치환한다.
 */
@Component
public class CardPrintRenderer {

  private static final Logger log = LoggerFactory.getLogger(CardPrintRenderer.class);

  /** 한글 폴백 폰트 — 템플릿 폰트(Arial 등)가 한글 글리프가 없을 때 사용. */
  private static final String KOREAN_FONT = "Malgun Gothic";

  /** 한 면 렌더 — photoOverride 가 있으면 템플릿 샘플 사진 대신 사용(실제 얼굴). */
  public BufferedImage render(CardProject.Side side, Map<String, String> fields, String photoOverride,
      double canvasWidth) {
    BufferedImage bg = decode(side.backgroundImageData);
    int w = bg != null ? bg.getWidth() : 638;
    int h = bg != null ? bg.getHeight() : 1012;
    double scale = w / canvasWidth; // 디자인 좌표 → 배경 해상도

    BufferedImage card = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = card.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    g.setColor(color(side.backgroundColor, Color.WHITE));
    g.fillRect(0, 0, w, h);
    if (bg != null) {
      g.drawImage(bg, 0, 0, w, h, null);
    }

    String photo = photoOverride != null && !photoOverride.isBlank() ? photoOverride : side.photoImageData;
    if (side.photoPosition != null && side.photoSize != null && photo != null && !photo.isBlank()) {
      drawPhoto(g, side, photo, scale);
    }

    if (side.textElements != null) {
      for (CardProject.TextEl t : side.textElements) {
        drawText(g, t, bind(t.text, fields), scale);
      }
    }
    g.dispose();
    return card;
  }

  private void drawPhoto(Graphics2D g, CardProject.Side side, String photo, double scale) {
    BufferedImage img = decode(photo);
    if (img == null) {
      return;
    }
    int x = (int) Math.round(side.photoPosition.x * scale);
    int y = (int) Math.round(side.photoPosition.y * scale);
    int pw = (int) Math.round(side.photoSize.width * scale);
    int ph = (int) Math.round(side.photoSize.height * scale);
    int radius = (int) Math.round(side.photoBorderRadius * scale);
    Shape clip = g.getClip();
    if (radius > 0) {
      g.setClip(new RoundRectangle2D.Float(x, y, pw, ph, radius * 2f, radius * 2f));
    } else {
      g.setClip(x, y, pw, ph);
    }
    drawCropped(g, img, x, y, pw, ph);
    g.setClip(clip);
  }

  /** 비율 유지 + 중앙 크롭으로 영역을 채운다. */
  private void drawCropped(Graphics2D g, BufferedImage src, int dx, int dy, int dw, int dh) {
    double srcRatio = (double) src.getWidth() / src.getHeight();
    double dstRatio = (double) dw / dh;
    int sx;
    int sy;
    int sw;
    int sh;
    if (srcRatio > dstRatio) {
      sh = src.getHeight();
      sw = (int) (sh * dstRatio);
      sx = (src.getWidth() - sw) / 2;
      sy = 0;
    } else {
      sw = src.getWidth();
      sh = (int) (sw / dstRatio);
      sx = 0;
      sy = (src.getHeight() - sh) / 2;
    }
    g.drawImage(src, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
  }

  private void drawText(Graphics2D g, CardProject.TextEl t, String value, double scale) {
    if (value == null || value.isEmpty()) {
      return;
    }
    int style = Font.PLAIN | (t.bold ? Font.BOLD : 0) | (t.italic ? Font.ITALIC : 0);
    int size = (int) Math.round(t.fontSize * scale);
    Font font = new Font(t.fontFamily, style, size);
    if (font.canDisplayUpTo(value) != -1) {
      font = new Font(KOREAN_FONT, style, size); // 지정 폰트(예 Arial)가 한글을 못 그리면 폴백
    }
    g.setFont(font);
    g.setColor(color(t.color, Color.BLACK));
    FontMetrics fm = g.getFontMetrics();
    double spacing = t.letterSpacing * scale;
    int textWidth = textWidth(fm, value, spacing);
    double boxLeft = t.x * scale;
    double boxW = t.boxWidth * scale;
    double drawX;
    switch (t.align != null ? t.align : "left") {
      case "center":
        drawX = boxLeft + (boxW - textWidth) / 2;
        break;
      case "right":
        drawX = boxLeft + boxW - textWidth;
        break;
      default:
        drawX = boxLeft;
        break;
    }
    // 디자인 y 는 박스 상단 — 박스 높이 안에서 세로 중앙 정렬
    double boxTop = t.y * scale;
    double boxH = t.boxHeight * scale;
    int baseline = (int) Math.round(boxTop + (boxH - fm.getHeight()) / 2 + fm.getAscent());
    drawString(g, value, (int) Math.round(drawX), baseline, spacing);
  }

  private int textWidth(FontMetrics fm, String s, double spacing) {
    if (spacing == 0) {
      return fm.stringWidth(s);
    }
    int total = 0;
    for (int i = 0; i < s.length(); i++) {
      total += fm.charWidth(s.charAt(i)) + (i < s.length() - 1 ? spacing : 0);
    }
    return total;
  }

  private void drawString(Graphics2D g, String s, int x, int y, double spacing) {
    if (spacing == 0) {
      g.drawString(s, x, y);
      return;
    }
    int cx = x;
    FontMetrics fm = g.getFontMetrics();
    for (int i = 0; i < s.length(); i++) {
      String ch = String.valueOf(s.charAt(i));
      g.drawString(ch, cx, y);
      cx += fm.charWidth(s.charAt(i)) + (int) Math.round(spacing);
    }
  }

  /** "{이름}" → fields["이름"]. 없는 키는 빈 문자열. */
  private String bind(String text, Map<String, String> fields) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(text);
    while (m.find()) {
      m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
          fields.getOrDefault(m.group(1).trim(), "")));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  public String toDataUrl(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (Exception e) {
      throw new RuntimeException("카드 이미지 변환 실패", e);
    }
  }

  private BufferedImage decode(String dataUrl) {
    if (dataUrl == null || dataUrl.isBlank()) {
      return null;
    }
    try {
      String b64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(",") + 1) : dataUrl;
      return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
    } catch (Exception e) {
      log.warn("카드 이미지 디코딩 실패: {}", e.getMessage());
      return null;
    }
  }

  private Color color(String hex, Color fallback) {
    try {
      return Color.decode(hex);
    } catch (Exception e) {
      return fallback;
    }
  }
}
