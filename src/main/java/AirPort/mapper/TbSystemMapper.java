package AirPort.mapper;

import AirPort.model.TbSystem;

/** 시스템 설정 매퍼 (단일 행). SQL 은 mapper/TbSystemMapper.xml. */
public interface TbSystemMapper {

  TbSystem selectOne();

  int countAll();

  int insert(TbSystem row);

  int update(TbSystem row);
}
