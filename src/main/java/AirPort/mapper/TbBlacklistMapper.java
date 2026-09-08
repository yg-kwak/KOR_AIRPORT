package AirPort.mapper;

import AirPort.model.BlacklistSearchParam;
import AirPort.model.TbBlacklist;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 제재인원 (tb_blacklist). SQL 은 mapper XML 에만 둔다. */
@Mapper
public interface TbBlacklistMapper {

  List<TbBlacklist> selectList(BlacklistSearchParam param);

  int selectCount(BlacklistSearchParam param);

  TbBlacklist selectById(@Param("blacklistId") int blacklistId);

  /**
   * 성명+생년월일로 <b>지금 유효한</b> 제재를 찾는다 — 없으면 null.
   *
   * <p>두 값 모두 ARIA 암호문으로 넘긴다(결정적이라 완전일치 비교가 된다). 정지기간이 지난 행은 남아 있어도 걸리지 않는다.
   */
  TbBlacklist selectActiveBan(
      @Param("personNameEnc") String personNameEnc, @Param("birthDateEnc") String birthDateEnc);

  /** 같은 사람이 이미 등록돼 있는지 — 수정 시 자기 자신 제외. 기간과 무관하게 본다(중복 등록 방지). */
  TbBlacklist selectByPerson(
      @Param("personNameEnc") String personNameEnc,
      @Param("birthDateEnc") String birthDateEnc,
      @Param("exceptId") Integer exceptId);

  int insert(TbBlacklist row);

  int update(TbBlacklist row);

  /** 소프트 삭제 — 물리 DELETE 금지. */
  int softDelete(@Param("blacklistId") int blacklistId);
}
