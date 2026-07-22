package AirPort.mapper;

import AirPort.model.TbCard;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 카드 매퍼 (tb_card, 인원 1:N). 조회는 항상 del_yn='N'. */
public interface TbCardMapper {

  List<TbCard> selectByPerson(@Param("personId") String personId);

  int insert(TbCard card);

  int update(TbCard card);

  /** 인원의 카드 전체 소프트 삭제 — 화면에서 빠진 카드를 정리할 때. */
  int softDeleteByPerson(@Param("personId") String personId);
}
