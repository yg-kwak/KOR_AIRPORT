package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 인원 (tb_person) — 출입 대상자. tb_login_user(로그인 계정)와 다른 개체.
 *
 * <p>성명·생년월일·연락처는 ARIA 암호화 대상(부분검색·정렬 불가). 삭제는 del_yn='Y' 소프트 삭제. 정규인원등록 화면은 person_type='PT01'
 * 만 다룬다. (docs/database.md)
 */
@Data
public class TbPerson {
  private String personId;
  private String personName; // ARIA
  private String birthDate; // ARIA
  private String personPhone; // ARIA
  private String companyCode; // → tb_company.company_code
  private String titleCode; // → tb_common(UT)
  private String personType; // → tb_common(PT). 본 화면은 PT01 고정
  private String statusCode; // → tb_common(PS)
  private String mainTask;
  private String accessStartDt; // 출입시작일 (문자열 바인딩 "YYYY-MM-DD")
  private String accessEndDt; // 출입종료일
  private String remark;
  private String biostarUserId; // BiostarX 사용자ID
  private String useYn;
  private String delYn;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  // 목록 표시용(조인) — 저장 컬럼 아님
  private String companyName;
  private String titleName;
  private String statusName;
}
