package AirPort.service;

import AirPort.common.BirthDates;
import AirPort.common.PageResult;
import AirPort.common.Texts;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbBlacklistMapper;
import AirPort.model.BlacklistSearchParam;
import AirPort.model.TbBlacklist;
import AirPort.model.TbLoginUser;
import AirPort.security.ARIAUtil;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제재인원(tb_blacklist) 관리 + <b>등록 차단 대조</b>. (보안관리 → 제재인원관리)
 *
 * <p>성명·생년월일은 ARIA 암호문으로 저장한다. 결정적 암호화라 <b>암호문끼리 비교</b>하면 완전일치를 찾을 수 있다 — 대조는 그 성질에 기댄다. 대신
 * 부분검색·정렬은 되지 않아 목록 검색은 소속·비고(평문)로 한다.
 *
 * <p>대조는 <b>등록할 때마다</b> 도는 길목이다({@link #requireNotBanned}) — 임시·장기 방문객, 정규인원 신규 등록이 모두 여기를 지난다.
 * 정지기간이 지난 행은 남아 있어도 걸리지 않는다.
 */
@Service
public class BlacklistService {

  /**
   * 차단 안내 — <b>사유(비고)는 싣지 않는다.</b>
   *
   * <p>비고에는 제재 경위가 적힌다. 등록 화면은 방문 접수 창구·키오스크에서도 뜨므로 그 문구가 당사자나 옆사람에게 그대로 보인다. 무엇 때문에 막혔는지는 제재인원관리에서
   * 담당자만 본다.
   */
  private static final String BANNED_MESSAGE = "제재인원에 등록된 사용자입니다.";

  private final TbBlacklistMapper blacklistMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public BlacklistService(
      TbBlacklistMapper blacklistMapper,
      AuditService auditService,
      MenuAuthService menuAuthService) {
    this.blacklistMapper = blacklistMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  // ── 등록 차단 대조 (다른 화면들이 쓰는 길목) ─────────────────────────────

  /**
   * 제재인원이면 등록을 막는다 — 임시·장기 방문객과 정규인원 신규 등록이 모두 여기를 지난다.
   *
   * <p>성명·생년월일이 <b>둘 다</b> 있어야 대조한다. 하나라도 없으면 사람을 특정할 수 없어 그냥 통과시킨다 — 그 경우는 각 화면의 필수 검사가 먼저 잡는다.
   */
  public void requireNotBanned(String personName, String birthDate) {
    if (findActiveBan(personName, birthDate) != null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, BANNED_MESSAGE);
    }
  }

  /** 지금 유효한 제재를 찾는다 — 없으면 null. 화면이 "등록되어 있다"고 안내할 때도 쓴다. */
  public TbBlacklist findActiveBan(String personName, String birthDate) {
    if (isBlank(personName) || isBlank(birthDate)) {
      return null;
    }
    return blacklistMapper.selectActiveBan(
        ARIAUtil.ariaEncrypt(personName.trim()), ARIAUtil.ariaEncrypt(birthDate.trim()));
  }

  // ── 화면 CRUD ────────────────────────────────────────────────────────────

  public PageResult<TbBlacklist> list(
      BlacklistSearchParam param, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    // 성명은 암호문이라 LIKE 가 안 된다 — 완전일치 비교용으로 암호화해 넣는다(tb_person 과 같은 선례)
    if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
      param.setKeywordEnc(ARIAUtil.ariaEncrypt(param.getKeyword().trim()));
    }
    long total = blacklistMapper.selectCount(param);
    List<TbBlacklist> rows = blacklistMapper.selectList(param);
    rows.forEach(BlacklistService::decrypt);
    auditService.log(actor, AuditService.READ, menuId, "제재인원 목록 조회 (" + total + "건)");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  public TbBlacklist detail(int blacklistId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbBlacklist row = blacklistMapper.selectById(blacklistId);
    if (row == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    decrypt(row);
    return row;
  }

  @Transactional
  public void create(TbBlacklist row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(row, null);
    String name = row.getPersonName().trim();
    blacklistMapper.insert(encrypted(row));
    auditService.log(actor, AuditService.CREATE, menuId, "제재인원 등록: " + name);
  }

  @Transactional
  public void update(TbBlacklist row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정(골든 샘플과 동일)
    if (row.getBlacklistId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "제재ID가 없습니다.");
    }
    // 해제된 제재는 고치지 않는다 — 지난 이력을 덧칠하는 셈이다. 다시 막으려면 새로 등록한다
    if (blacklistMapper.selectById(row.getBlacklistId()) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    validate(row, row.getBlacklistId());
    String name = row.getPersonName().trim();
    blacklistMapper.update(encrypted(row));
    auditService.log(actor, AuditService.UPDATE, menuId, "제재인원 수정: " + name);
  }

  /**
   * 소프트 삭제(=제재 해제). 물리 DELETE 를 쓰지 않는다 — 누가 언제 제재됐다 풀렸는지가 남아야 한다.
   *
   * <p>지운 사람의 성명을 감사에 <b>스냅샷</b>으로 남긴다. 행이 지워진 뒤에는 조인으로 되찾을 수 없다.
   */
  @Transactional
  public void delete(int blacklistId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbBlacklist row = blacklistMapper.selectById(blacklistId);
    if (row == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    blacklistMapper.softDelete(blacklistId);
    auditService.log(
        actor,
        AuditService.DELETE,
        menuId,
        "제재인원 해제: " + ARIAUtil.ariaDecrypt(row.getPersonName()));
  }

  /**
   * 정규인원이 <b>정지</b>로 바뀔 때 제재인원에 올린다 — 화면에서 확인을 받은 뒤에만 부른다.
   *
   * <p>이미 올라가 있으면 아무 것도 하지 않는다(중복 등록 방지). 정지기간은 비워 둔다 — 언제까지인지는 담당자가 제재인원관리에서 채운다.
   *
   * @return 실제로 올렸으면 true (화면 안내 문구를 가르는 값)
   */
  @Transactional
  public boolean addFromPerson(
      String personName, String birthDate, String affiliation, TbLoginUser actor, Integer menuId) {
    if (isBlank(personName) || isBlank(birthDate)) {
      return false; // 사람을 특정할 수 없으면 올리지 않는다
    }
    String nameEnc = ARIAUtil.ariaEncrypt(personName.trim());
    String birthEnc = ARIAUtil.ariaEncrypt(birthDate.trim());
    if (blacklistMapper.selectByPerson(nameEnc, birthEnc, null) != null) {
      return false;
    }
    TbBlacklist row = new TbBlacklist();
    row.setPersonName(nameEnc);
    row.setBirthDate(birthEnc);
    row.setAffiliation(blankToNull(affiliation));
    // 정지로 바꾼 그날부터 막는다. 종료는 비워 둔다(무기한) — 언제까지인지는 담당자가 정한다
    row.setBanStartDt(LocalDate.now().toString());
    row.setRemark("정규인원 상태를 [정지] 로 변경하면서 등록");
    blacklistMapper.insert(row);
    auditService.log(actor, AuditService.CREATE, menuId, "제재인원 등록(인원상태 정지): " + personName.trim());
    return true;
  }

  // ── 내부 ─────────────────────────────────────────────────────────────────

  private void validate(TbBlacklist row, Integer exceptId) {
    if (isBlank(row.getPersonName())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "성명은(는) 필수입니다.");
    }
    // 생년월일은 대조의 절반이다 — 없으면 동명이인을 통째로 막게 된다
    row.setBirthDate(BirthDates.require(row.getBirthDate(), "생년월일"));
    Texts.maxLen(row.getPersonName(), 100, "성명");
    Texts.maxLen(row.getAffiliation(), 100, "소속");
    Texts.maxLen(row.getRemark(), 1000, "비고");
    if (!isBlank(row.getBanStartDt())
        && !isBlank(row.getBanEndDt())
        && row.getBanStartDt().compareTo(row.getBanEndDt()) > 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "정지기간 시작은 종료보다 늦을 수 없습니다.");
    }
    TbBlacklist dup =
        blacklistMapper.selectByPerson(
            ARIAUtil.ariaEncrypt(row.getPersonName().trim()),
            ARIAUtil.ariaEncrypt(row.getBirthDate()),
            exceptId);
    if (dup != null) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 제재인원입니다(성명·생년월일 동일).");
    }
  }

  /** 저장 직전 암호화 — 원본 객체를 그대로 바꾸면 화면 응답에 암호문이 실린다. */
  private static TbBlacklist encrypted(TbBlacklist row) {
    TbBlacklist out = new TbBlacklist();
    out.setBlacklistId(row.getBlacklistId());
    out.setPersonName(ARIAUtil.ariaEncrypt(row.getPersonName().trim()));
    out.setBirthDate(ARIAUtil.ariaEncrypt(row.getBirthDate()));
    out.setAffiliation(blankToNull(row.getAffiliation()));
    out.setRemark(blankToNull(row.getRemark()));
    out.setBanStartDt(blankToNull(row.getBanStartDt()));
    out.setBanEndDt(blankToNull(row.getBanEndDt()));
    return out;
  }

  /** 조회 결과 복호화 + 화면 표시용 상태 계산. */
  private static void decrypt(TbBlacklist row) {
    row.setPersonName(ARIAUtil.ariaDecrypt(row.getPersonName()));
    row.setBirthDate(ARIAUtil.ariaDecrypt(row.getBirthDate()));
    row.setBanStatus(status(row));
  }

  /** 기간과 오늘을 견줘 한 단어로 — 목록에서 지금 막히는 사람인지 바로 보이게 한다. */
  private static String status(TbBlacklist row) {
    if ("Y".equals(row.getDelYn())) {
      return "해제"; // 해제분도 목록에 남는다 — 이 화면의 이력이 그것이다
    }
    String today = LocalDate.now().toString();
    if (!isBlank(row.getBanStartDt()) && row.getBanStartDt().compareTo(today) > 0) {
      return "예정";
    }
    if (!isBlank(row.getBanEndDt()) && row.getBanEndDt().compareTo(today) < 0) {
      return "만료";
    }
    return "정지 중";
  }

  private static boolean isBlank(String v) {
    return v == null || v.isBlank();
  }

  private static String blankToNull(String v) {
    return isBlank(v) ? null : v.trim();
  }
}
