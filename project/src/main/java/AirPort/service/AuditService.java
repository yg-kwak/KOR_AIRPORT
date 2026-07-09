package AirPort.service;

import AirPort.mapper.TbSystemLogMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystemLog;
import org.springframework.stereotype.Service;

/**
 * 감사 이력 기록(tb_system_log). 불변식: 메뉴 접속·조회·입력·수정·삭제는 이력을 남긴다. (docs/security.md)
 *
 * <p>action_type 은 tb_common(cmm_id='AT')의 code_id 를 사용한다.
 */
@Service
public class AuditService {

  // tb_common cmm_id='AT' 의 코드값
  public static final String MENU = "MENU";
  public static final String READ = "READ";
  public static final String CREATE = "CREATE";
  public static final String UPDATE = "UPDATE";
  public static final String DELETE = "DELETE";

  private final TbSystemLogMapper systemLogMapper;

  public AuditService(TbSystemLogMapper systemLogMapper) {
    this.systemLogMapper = systemLogMapper;
  }

  public void log(TbLoginUser actor, String actionType, Integer menuId, String detail) {
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
    systemLogMapper.insert(row);
  }
}
