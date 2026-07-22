package AirPort.mapper;

import AirPort.model.CarSearchParam;
import AirPort.model.CompanyCarSearchParam;
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
  /** 기관차량등록 목록 — 기관명·발급 카드수 포함. */
  List<TbCar> selectCompanyCarList(CompanyCarSearchParam param);

  long selectCompanyCarCount(CompanyCarSearchParam param);

  /** 기관차량등록 수정 — 소속 기관까지 함께 고친다(차량등록관리의 update 는 기관을 건드리지 않는다). */
  int updateWithCompany(TbCar car);

  int softDelete(@Param("carId") Integer carId);
}
