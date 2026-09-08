package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 제재인원 (tb_blacklist). 출입을 막을 사람의 명단. docs/database.md
 *
 * <p>{@code personName}·{@code birthDate} 는 <b>ARIA 암호문</b>으로 저장된다(암·복호화는 Service). 결정적 암호화라 완전일치
 * 비교는 되지만 부분검색·정렬은 되지 않는다 — 목록 검색은 소속(평문)으로 한다.
 *
 * <p>삭제는 물리 DELETE 금지 — {@code del_yn='Y'} 소프트 삭제로 제재 이력을 보존한다.
 */
@Data
public class TbBlacklist {
  private Integer blacklistId;
  private String personName; // ARIA
  private String birthDate; // ARIA, 평문은 YYYY-MM-DD
  private String affiliation;
  private String remark;
  private String banStartDt; // "YYYY-MM-DD" (비면 즉시부터)
  private String banEndDt; // "YYYY-MM-DD" (비면 무기한)
  private String delYn;
  private LocalDateTime regDt;
  private LocalDateTime modDt;

  // 목록 표시용 — 저장 컬럼 아님
  private String banStatus; // "정지 중" / "예정" / "만료"
}
