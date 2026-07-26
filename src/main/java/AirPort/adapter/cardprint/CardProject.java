package AirPort.adapter.cardprint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 카드 디자인 export(card_project) JSON 모델 — IDENTI 계열 카드 편집기 저장 포맷. (docs/integration.md)
 *
 * <p>앞/뒤 면({@code cardData.front/back}) 각각 배경 이미지 + 사진 영역 + 텍스트 요소를 담는다. 텍스트는 {@code
 * "{컬럼명}"} 바인딩(예 {@code {이름}})을 쓰며 인쇄 시 실제 값으로 치환한다. 좌표는 디자인 캔버스 좌표계라 배경 해상도로
 * 스케일해 렌더한다. qr/barcode/imageElements 등 미사용 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardProject {

  public Settings settings = new Settings();
  public CardData cardData;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Settings {
    public String printSides = "front"; // both | front
    public String printOrientation = "portrait";
    public int dpi = 300;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CardData {
    public String orientation; // vertical | horizontal
    public Side front;
    public Side back;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Side {
    public String backgroundColor = "#ffffff";
    public String backgroundImageData; // data URL
    public Pos photoPosition;
    public Size photoSize;
    public String photoImageData; // 샘플 사진(인쇄 시 실제 얼굴로 대체)
    public int photoBorderRadius;
    public List<TextEl> textElements;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Pos {
    public double x;
    public double y;
    public double rotation;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Size {
    public double width;
    public double height;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TextEl {
    public String text;
    public double fontSize = 24;
    public String fontFamily = "맑은 고딕";
    public boolean bold;
    public boolean italic;
    public boolean underline;
    public String color = "#000000";
    public String align = "center"; // left | center | right
    public double x;
    public double y;
    public double boxWidth;
    public double boxHeight;
    public double letterSpacing;
    public double rotation;
  }
}
