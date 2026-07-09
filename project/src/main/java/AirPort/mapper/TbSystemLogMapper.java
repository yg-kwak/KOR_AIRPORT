package AirPort.mapper;

import AirPort.model.TbSystemLog;

/** 감사 이력 매퍼. SQL 은 mapper/TbSystemLogMapper.xml. */
public interface TbSystemLogMapper {

  int insert(TbSystemLog log);
}
