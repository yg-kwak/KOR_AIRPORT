package AirPort.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 평면도 위 출입문 — tb_graphic_map_door. 출입문은 인증 이벤트가 오는 <b>단말기(BiostarX 장치)</b>로 묶는다.
 *
 * <p>위치는 평면도 크기에 대한 0~1 비율이다 — 확대하거나 창 크기가 바뀌어도 같은 자리에 선다.
 */
@Data
public class TbGraphicMapDoor {
  private Integer mapId;
  private String deviceId;
  private String deviceName;
  private BigDecimal posX;
  private BigDecimal posY;
}
