package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 기관 (tb_company). PK=company_code(업무코드, 사용자 입력). 삭제는 {@code del_yn='Y'} 소프트 삭제, 활성/비활성은 {@code
 * use_yn}. docs/database.md
 *
 * <p>{@code ceo_name}(대표자)은 ARIA 암호화 대상(security.md). {@code company_type} 은
 * tb_common(cmm_id='CO').code_id. 용역일자는 문자열("YYYY-MM-DD")로 바인딩한다.
 */
@Data
public class TbCompany {
  private String companyCode; // PK 업무코드
  private String companyType; // tb_common(CO) code_id
  private String companyName;
  private String ceoName; // 대표자 (ARIA 암호화)
  private String tel;
  private String fax;
  private String addr;
  private String serviceStartDt; // datetime2 (문자열 바인딩)
  private String serviceEndDt;
  private Integer biostarGroupId; // BiostarX 사용자 그룹 ID (기관 ↔ user group, integration.md)
  private String useYn;
  private String delYn;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  private Integer carCount; // 등록차량 수(기관차량등록 목록) — 저장 컬럼 아님
  private String companyTypeName; // 목록 표시용(tb_common CO 조인) — 저장 컬럼 아님
}
