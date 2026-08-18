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

  /** 기관차량등록 목록 — 기관 + 등록차량 수. 조회는 기존 검색조건(del_yn='N')을 그대로 쓴다. */
  List<TbCompany> selectCarOwnerList(CompanySearchParam param);

  long selectCount(CompanySearchParam param);

  /** PK 단건 조회(del_yn 무관 — 중복/존재 판정용). */
  TbCompany selectById(@Param("companyCode") String companyCode);

  /** 기관 select 옵션 — 사용중인 기관만(코드/기관명). */
  List<TbCompany> selectOptions();

  /** 다음 기관코드 자동 채번 — 숫자형 코드의 최댓값+1(삭제분 포함, PK 충돌 방지). */
  String selectNextCompanyCode();

  int insert(TbCompany row);

  /** 소프트 삭제된 기관코드 재등록 — 전체 필드 갱신 + del_yn='N' 되살림. */
  int reactivate(TbCompany row);

  int update(TbCompany row);

  /** BiostarX 사용자그룹 생성 성공 후 연동 ID 만 반영. */
  int updateBiostarGroupId(
      @Param("companyCode") String companyCode, @Param("biostarGroupId") Integer biostarGroupId);

  /** 소프트 삭제 — del_yn='Y'. */
  int softDelete(@Param("companyCode") String companyCode);

  /** BiostarX 사용자그룹 ID 에 매핑된 기관코드 — 없으면 null(가져오기 제외). */
  String selectCodeByBiostarGroupId(@Param("biostarGroupId") Integer biostarGroupId);

  /**
   * BiostarX 사용자그룹이 매핑된 기관 전부 — {@code biostar_group_id}/{@code company_code}/{@code company_name}
   * 만 채운다.
   *
   * <p>가져오기 대상 목록이 장비 사용자 1명마다 {@link #selectCodeByBiostarGroupId} 를 부르면 인원 수만큼 질의가 나간다. 매핑은 기관
   * 수만큼(보통 수십 개)뿐이라 한 번에 읽어 화면에서 맞춘다.
   */
  List<TbCompany> selectBiostarGroupMappings();
}
