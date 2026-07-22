package AirPort.mapper;

import AirPort.model.CardSearchParam;
import AirPort.model.TbCard;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 카드 매퍼 (tb_card, 인원 1:N). 조회는 항상 del_yn='N'. */
public interface TbCardMapper {

  List<TbCard> selectList(CardSearchParam param);

  long selectCount(CardSearchParam param);

  TbCard selectById(@Param("cardId") int cardId);

  List<TbCard> selectByPerson(@Param("personId") String personId);

  /** 미할당(회수된) 카드 목록 — 할당하기 팝업용. */
  List<TbCard> selectUnassigned(@Param("keyword") String keyword);

  /** 카드번호로 단건 조회 — 회수된 카드 재사용 / 중복 발급 차단용. */
  TbCard selectByCardNo(@Param("cardNo") String cardNo);

  int insert(TbCard card);

  /** 인원 저장 흐름 전용 — person_id 를 재배정한다(회수된 카드를 다시 붙인다). */
  int update(TbCard card);

  /** 카드등록관리 전용 — 카드 정보만 고친다(할당 인원은 건드리지 않는다). */
  int updateInfo(TbCard card);

  /** 소프트 삭제 — 카드 마스터 자체를 지울 때만(할당된 카드는 서비스가 막는다). */
  int softDelete(@Param("cardId") int cardId);

  /** 인원의 카드를 전부 회수(미배정)한다 — 카드는 살아 있어 다른 인원이 다시 쓸 수 있다. */
  int releaseByPerson(@Param("personId") String personId);
}
