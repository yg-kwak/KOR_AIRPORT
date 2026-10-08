package AirPort.mapper;

import AirPort.model.TbGraphicMap;
import AirPort.model.TbGraphicMapDoor;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TbGraphicMapMapper {

  /** 목록 — 이미지는 빼고(무겁다), 놓인 출입문 수를 함께. */
  List<TbGraphicMap> selectList();

  TbGraphicMap selectById(@Param("mapId") int mapId);

  /** 이미지 원본 — 화면이 평면도를 그릴 때만. */
  TbGraphicMap selectImage(@Param("mapId") int mapId);

  int insert(TbGraphicMap row);

  int updateName(@Param("mapId") int mapId, @Param("mapName") String mapName);

  int updateImage(
      @Param("mapId") int mapId,
      @Param("imageData") byte[] imageData,
      @Param("imageType") String imageType);

  int softDelete(@Param("mapId") int mapId);

  List<TbGraphicMapDoor> selectDoors(@Param("mapId") int mapId);

  /** 이 맵에 놓인 문 — 없으면 null. 원격 제어는 놓인 문만 받는다. */
  TbGraphicMapDoor selectPlacedDoor(@Param("mapId") int mapId, @Param("doorId") long doorId);

  int deleteDoors(@Param("mapId") int mapId);

  int insertDoors(@Param("mapId") int mapId, @Param("doors") List<TbGraphicMapDoor> doors);
}
