package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 인원 증빙문서 (tb_person_file) — 인원별 문서 종류당 1건.
 *
 * <p>파일 실체를 DB 에 보관한다(백업 일원화·업로드 경로/고아파일 문제 제거). 인원 목록 조회는 이 테이블을 조인하지 않는다(행 크기).
 * (docs/database.md)
 */
@Data
public class TbPersonFile {

  /** 회보근거문서 */
  public static final String TYPE_ID_CHECK = "ID_CHECK";

  /** 승인근거문서 */
  public static final String TYPE_APPROVE = "APPROVE";

  private String personId;
  private String fileType;
  private String fileName;
  private Integer fileSize;
  private byte[] fileData;
  private LocalDateTime regDt;
}
