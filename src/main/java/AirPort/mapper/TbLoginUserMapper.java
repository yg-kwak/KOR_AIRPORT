package AirPort.mapper;

import AirPort.model.LoginUserSearchParam;
import AirPort.model.TbLoginUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 로그인 사용자 매퍼. SQL 은 mapper/TbLoginUserMapper.xml. */
public interface TbLoginUserMapper {

  TbLoginUser selectById(@Param("userId") String userId);

  int updateLoginFailCnt(@Param("userId") String userId, @Param("cnt") int cnt);

  /** 시작메뉴 변경(헤더 계정 메뉴). */
  int updateStartMenu(@Param("userId") String userId, @Param("startMenuId") Integer startMenuId);

  /** 비밀번호 변경(ARIA 암호문). password_change_dt 갱신. */
  int updatePassword(@Param("userId") String userId, @Param("password") String password);

  // ── 사용자관리 화면 CRUD ──────────────────────────────────────
  List<TbLoginUser> selectList(LoginUserSearchParam param);

  /** 엑셀 다운로드용 — 동일 검색/정렬, 페이징 없음(전체). */
  List<TbLoginUser> selectListAll(LoginUserSearchParam param);

  long selectCount(LoginUserSearchParam param);

  int insert(TbLoginUser row);

  /** 사용자 정보 수정. password 는 값이 있을 때만 갱신(빈 값=유지). */
  int update(TbLoginUser row);

  int delete(@Param("userId") String userId);

  /** 특정 권한을 쓰는 사용자 수(권한 삭제 가드용). */
  int countByAuthId(@Param("authId") int authId);
}
