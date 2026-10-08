package AirPort.model;

import lombok.Data;

/** 그래픽맵 평면도 — tb_graphic_map. 목록 조회는 이미지를 빼고 읽는다(무겁다). */
@Data
public class TbGraphicMap {
  private Integer mapId;
  private String mapName;
  private byte[] imageData; // 이미지 조회(selectImage)에서만 채운다
  private String imageType; // image/png ...
  private Integer sortOrder;
  private Integer doorCount; // 목록 표시용 — 놓인 출입문 수
  private String delYn;
}
