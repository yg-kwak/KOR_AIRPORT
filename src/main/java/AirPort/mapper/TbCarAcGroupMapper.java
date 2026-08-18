package AirPort.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 차량 출입구역 매퍼 (tb_car_ac_group) — 차량 ↔ tb_common(cmm_id='CAR') 매핑. */
public interface TbCarAcGroupMapper {

  int deleteByCar(@Param("carId") int carId);

  int insertBatch(@Param("carId") int carId, @Param("codeIds") List<String> codeIds);

  List<String> selectCodeIds(@Param("carId") int carId);
}
