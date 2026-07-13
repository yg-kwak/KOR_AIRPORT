package AirPort.mapper;

import AirPort.model.SystemLogSearchParam;
import AirPort.model.TbCommon;
import AirPort.model.TbSystemLog;
import java.util.List;

/** 감사 이력 매퍼. SQL 은 mapper/TbSystemLogMapper.xml. */
public interface TbSystemLogMapper {

  int insert(TbSystemLog log);

  // ── 감사추적 화면(조회 전용) ─────────────────────────────
  List<TbSystemLog> selectList(SystemLogSearchParam param);

  List<TbSystemLog> selectListAll(SystemLogSearchParam param);

  long selectCount(SystemLogSearchParam param);

  /** 유형 필터 옵션 — tb_common(cmm_id='AT'). codeId/codeName 만 채워짐. */
  List<TbCommon> selectActionTypes();
}
