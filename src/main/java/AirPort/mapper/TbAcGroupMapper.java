package AirPort.mapper;

import AirPort.model.TbAcGroup;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 출입권한 그룹 매퍼. SQL 은 mapper/TbAcGroupMapper.xml. */
public interface TbAcGroupMapper {

  /** 전체(트리 조립용, level/order 정렬). */
  List<TbAcGroup> selectList();

  TbAcGroup selectById(@Param("acGroupId") int acGroupId);

  /** 최상위(동기화 대상) 노드의 ar_code 목록. */
  List<String> selectTopArCodes();

  /** 이미 매핑된 biostar_ac_id 목록(중복 추가 방지용). */
  List<Integer> selectUsedBiostarIds();

  int insert(TbAcGroup row); // useGeneratedKeys → acGroupId

  int update(TbAcGroup row); // 이름·biostar 매핑만

  /** ar_code 가 인자 목록에 없는 행 전부 삭제(동기화 delete). 목록이 비면 전체 삭제. */
  int deleteOrphans(@Param("arCodes") List<String> arCodes);

  /** 노드와 그 하위 전부 삭제(재귀). */
  int deleteSubtree(@Param("acGroupId") int acGroupId);
}
