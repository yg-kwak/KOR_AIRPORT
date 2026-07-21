package AirPort.mapper;

import AirPort.model.CompanySearchParam;
import AirPort.model.TbCompany;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 기관 매퍼 (tb_company). SQL 은 mapper/TbCompanyMapper.xml. 조회/삭제는 del_yn='N' 기준(소프트 삭제). */
public interface TbCompanyMapper {

  List<TbCompany> selectList(CompanySearchParam param);

  /** 엑셀 다운로드용 — 동일 검색/정렬, 페이징 없음(전체). */
  List<TbCompany> selectListAll(CompanySearchParam param);

  long selectCount(CompanySearchParam param);

  /** PK 단건 조회(del_yn 무관 — 중복/존재 판정용). */
  TbCompany selectById(@Param("companyCode") String companyCode);

  int insert(TbCompany row);

  /** 소프트 삭제된 기관코드 재등록 — 전체 필드 갱신 + del_yn='N' 되살림. */
  int reactivate(TbCompany row);

  int update(TbCompany row);

  /** 소프트 삭제 — del_yn='Y'. */
  int softDelete(@Param("companyCode") String companyCode);
}
