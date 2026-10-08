package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import AirPort.common.exception.BusinessException;
import AirPort.model.TbGraphicMapDoor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 그래픽맵 — 평면도 형식 판정과 출입문 배치 정리.
 *
 * <p>평면도는 파일 앞부분의 서명으로 판정한다. 업로드한 쪽의 MIME 을 믿으면 HTML·SVG 를 이미지라 우겨 올린 파일이 이 화면의 출처로 열릴 수 있다.
 */
class GraphicMapServiceTest {

  @Test
  void 래스터_이미지는_서명으로_알아본다() {
    assertEquals(
        "image/png",
        GraphicMapService.imageType(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}));
    assertEquals(
        "image/jpeg",
        GraphicMapService.imageType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
    assertEquals(
        "image/gif", GraphicMapService.imageType("GIF89a".getBytes(StandardCharsets.US_ASCII)));
    assertEquals(
        "image/webp",
        GraphicMapService.imageType("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
  }

  @Test
  void SVG_HTML_빈_파일_큰_파일은_거절한다() {
    assertThrows(
        BusinessException.class,
        () ->
            GraphicMapService.imageType("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)));
    assertThrows(
        BusinessException.class,
        () -> GraphicMapService.imageType("<html><script>".getBytes(StandardCharsets.UTF_8)));
    assertThrows(BusinessException.class, () -> GraphicMapService.imageType(new byte[0]));
    byte[] big = new byte[GraphicMapService.MAX_IMAGE_BYTES + 1];
    big[0] = (byte) 0x89;
    big[1] = 'P';
    big[2] = 'N';
    big[3] = 'G';
    assertThrows(BusinessException.class, () -> GraphicMapService.imageType(big));
  }

  @Test
  void 배치는_평면도_안으로_자르고_같은_단말기는_두_번_놓을_수_없다() {
    List<TbGraphicMapDoor> out =
        GraphicMapService.cleanDoors(
            List.of(door("543737030", "-0.2", "1.37"), door(" 11 ", "0.123456", "0.5")));
    assertEquals(2, out.size());
    assertEquals(new BigDecimal("0.0000"), out.get(0).getPosX(), "평면도 밖으로 끌어낸 값은 가장자리로");
    assertEquals(new BigDecimal("1.0000"), out.get(0).getPosY());
    assertEquals("11", out.get(1).getDeviceId(), "앞뒤 공백은 턴다");
    assertEquals(new BigDecimal("0.1235"), out.get(1).getPosX(), "소수 넷째 자리까지");

    BusinessException dup =
        assertThrows(
            BusinessException.class,
            () ->
                GraphicMapService.cleanDoors(
                    List.of(door("1", "0.1", "0.1"), door("1", "0.2", "0.2"))));
    assertTrue(dup.getMessage().contains("두 번"), dup.getMessage());
  }

  private static TbGraphicMapDoor door(String id, String x, String y) {
    TbGraphicMapDoor d = new TbGraphicMapDoor();
    d.setDeviceId(id);
    d.setDeviceName("문 " + id);
    d.setPosX(new BigDecimal(x));
    d.setPosY(new BigDecimal(y));
    return d;
  }
}
