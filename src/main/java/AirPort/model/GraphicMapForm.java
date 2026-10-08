package AirPort.model;

import java.util.List;
import lombok.Data;

/** 그래픽맵 저장 요청 — 이름 변경, 또는 출입문 배치 전체 저장(doors 가 있으면 그 맵의 배치를 통째로 바꾼다). */
@Data
public class GraphicMapForm {
  private Integer mapId;
  private String mapName;
  private List<TbGraphicMapDoor> doors;
}
