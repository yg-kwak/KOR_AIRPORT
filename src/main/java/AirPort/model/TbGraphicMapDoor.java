package AirPort.model;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 평면도 위 출입문 — tb_graphic_map_door. <b>BiostarX 출입문(door)</b> 단위로 놓는다(원격 개방·잠금·해제 대상).
 *
 * <p>{@code deviceId} 는 그 문의 입구 단말기다 — 인증 이벤트는 단말기 ID 로 오므로 어느 문에서 인증했는지는 이 값으로 잇는다. 위치는 평면도 크기에 대한
 * 0~1 비율이라 확대하거나 창 크기가 바뀌어도 같은 자리에 선다.
 */
@Data
public class TbGraphicMapDoor {
  private Integer mapId;
  private Long doorId;
  private String doorName;
  private String deviceId;
  private BigDecimal posX;
  private BigDecimal posY;
}
