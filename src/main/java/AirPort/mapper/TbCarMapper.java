package AirPort.mapper;

import AirPort.model.CarSearchParam;
import AirPort.model.TbCar;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 차량 매퍼 (tb_car). SQL 은 mapper/TbCarMapper.xml. 조회/삭제는 del_yn='N' 기준(소프트 삭제). */
public interface TbCarMapper {

  List<TbCar> selectList(CarSearchParam param);

  /** 엑셀 다운로드용 — 동일 검색/정렬, 페이징 없음(전체). */
  List<TbCar> selectListAll(CarSearchParam param);

  long selectCount(CarSearchParam param);

  TbCar selectById(@Param("carId") Integer carId);

  /** 차량번호 중복 검사 — del_yn='N' 기준. 수정 시 자기 자신(excludeId) 제외. 0 이면 사용 가능. */
  int existsByCarNo(@Param("carNo") String carNo, @Param("excludeId") Integer excludeId);

  int insert(TbCar row);

  int update(TbCar row);

  /** 소프트 삭제 — del_yn='Y'. */
  /** 기관의 차량 목록 — 기관차량등록 모달. 발급 카드수·관리자·출입구역 포함. */
  List<TbCar> selectByCompany(@Param("companyCode") String companyCode);

  /** 아직 기관에 할당되지 않은 차량 — 차량 불러오기 팝업. */
  List<TbCar> selectUnassigned(@Param("keyword") String keyword);

  /** 기관차량등록 수정 — 관리자까지 함께. */
  int updateManager(@Param("carId") int carId, @Param("carManagerId") String carManagerId);

  /** 기관차량등록 수정 — 소속 기관까지 함께 고친다(차량등록관리의 update 는 기관을 건드리지 않는다). */
  int updateWithCompany(TbCar car);

  /** 정기 파기 — 방문 차량 물리 삭제(출입구역 매핑 포함). 되돌릴 수 없다. */
  int purge(@Param("carId") int carId);

  int softDelete(@Param("carId") Integer carId);

  /**
   * 이 차량번호를 쓰는 살아 있는 기관차량(차량구역이 붙은 것). 없으면 null.
   *
   * @param carNo 공백을 뺀 차량번호
   * @param excludeCarId 지금 지우는 기관차량(제외). 없으면 null
   */
  TbCar selectParkingCarByNo(
      @Param("carNo") String carNo, @Param("excludeCarId") Integer excludeCarId);
}
