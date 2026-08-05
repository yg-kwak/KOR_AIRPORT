package AirPort.service;

import AirPort.mapper.TbSystemLogMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystemLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 이력 기록(tb_system_log). 불변식: 메뉴 접속·조회·입력·수정·삭제는 이력을 남긴다. (docs/security.md)
 *
 * <p>action_type 은 tb_common(cmm_id='AT')의 code_id 를 사용한다.
 *
 * <p><b>DB 와 파일에 같이 남긴다.</b> 감사추적 화면은 DB 를 보지만, 서버에서 무슨 일이 일어나는지 파일로 확인해야 할 때가 있다. 파일 로그가 기동·오류만
 * 남으면 정상 운영 중에는 아무것도 쌓이지 않아 시스템이 멈춘 것처럼 보인다(로그백 롤오버도 로그가 있어야 일어난다).
 */
@Service
public class AuditService {

  /** 업무 이력 전용 로거 — 이름이 따로라 필요하면 이 줄기만 레벨을 조절할 수 있다. */
  private static final Logger audit = LoggerFactory.getLogger("AUDIT");

  // tb_common cmm_id='AT' 의 코드값
  public static final String MENU = "MENU";
  public static final String READ = "READ";
  public static final String CREATE = "CREATE";
  public static final String UPDATE = "UPDATE";
  public static final String DELETE = "DELETE";
  public static final String DOWNLOAD = "DOWNLOAD";
  public static final String LOGIN = "LOGIN";
  public static final String LOGOUT = "LOGOUT";

  private final TbSystemLogMapper systemLogMapper;

  public AuditService(TbSystemLogMapper systemLogMapper) {
    this.systemLogMapper = systemLogMapper;
  }

  public void log(TbLoginUser actor, String actionType, Integer menuId, String detail) {
    log(actor, actionType, menuId, detail, null);
  }

  /**
   * 실패 이력 기록 — 호출자 트랜잭션이 롤백돼도 이 기록은 남는다(REQUIRES_NEW). BiostarX 동기화 실패처럼 저장은 취소하되 시도 사실은 감사에 남겨야 할
   * 때 쓴다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logAlways(TbLoginUser actor, String actionType, Integer menuId, String detail) {
    log(actor, actionType, menuId, detail, null);
  }

  /** remark: 부가 사유(예: 엑셀 다운로드 목적). */
  public void log(
      TbLoginUser actor, String actionType, Integer menuId, String detail, String remark) {
    TbSystemLog row = new TbSystemLog();
    if (actor != null) {
      row.setUserId(actor.getUserId());
      row.setUserName(actor.getUserName());
    } else {
      row.setUserId("SYSTEM");
    }
    row.setActionType(actionType);
    row.setMenuId(menuId);
    row.setActionDetail(detail);
    row.setRemark(remark);
    systemLogMapper.insert(row);
    // 파일에도 같은 내용을 남긴다(사용자ID 는 로그 패턴의 %X{userId} 로 함께 찍힌다)
    audit.info(
        "{} {}{}", actionType, detail, (remark == null || remark.isBlank()) ? "" : " — " + remark);
  }
}
