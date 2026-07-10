package AirPort.model;

import java.time.LocalDateTime;
import lombok.Data;

/** 시스템 설정 (tb_system) — BiostarX 연동정보. 단일 행 운영. docs/database.md */
@Data
public class TbSystem {
  private String biostarIp;
  private String biostarId;
  private String biostarPw; // ARIA 암호화 저장 (docs/security.md)
  private LocalDateTime regDt;
  private LocalDateTime modDt;
}
