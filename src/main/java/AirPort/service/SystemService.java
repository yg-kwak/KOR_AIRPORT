package AirPort.service;

import AirPort.adapter.BiostarAdapter;
import AirPort.adapter.BiostarResult;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시스템 설정(tb_system) — BiostarX 연동정보. 단일 행. (docs/backend.md 골든 패턴)
 *
 * <p>biostar_pw 는 ARIA 암호화 저장(Service 계층에서만 암복호화). 화면에는 비밀번호를 되돌려주지 않는다.
 */
@Service
public class SystemService {

  private final TbSystemMapper systemMapper;
  private final BiostarAdapter biostarAdapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public SystemService(
      TbSystemMapper systemMapper,
      BiostarAdapter biostarAdapter,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.systemMapper = systemMapper;
    this.biostarAdapter = biostarAdapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 화면 표시용 — 비밀번호는 제거하고 반환. */
  public TbSystem getForView(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbSystem row = systemMapper.selectOne();
    if (row != null) {
      row.setBiostarPw(null);
    }
    return row;
  }

  /** 저장 — 비밀번호가 비어 있으면 기존 값 유지, 입력되면 ARIA 암호화하여 저장. */
  @Transactional
  public void save(TbSystem input, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    TbSystem existing = systemMapper.selectOne();
    String pw = input.getBiostarPw();
    if (pw == null || pw.isBlank()) {
      input.setBiostarPw(existing != null ? existing.getBiostarPw() : null);
    } else {
      input.setBiostarPw(ARIAUtil.ariaEncrypt(pw));
    }
    if (existing == null) {
      systemMapper.insert(input);
    } else {
      systemMapper.update(input);
    }
    auditService.log(actor, AuditService.UPDATE, menuId, "BiostarX 설정 저장: " + input.getBiostarIp());
  }

  /** BiostarX 연결 테스트 — 비밀번호 미입력 시 저장된 값(복호화)으로 시도. */
  public BiostarResult testConnection(TbSystem input, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    String pw = input.getBiostarPw();
    if (pw == null || pw.isBlank()) {
      TbSystem existing = systemMapper.selectOne();
      pw =
          (existing != null && existing.getBiostarPw() != null)
              ? ARIAUtil.ariaDecrypt(existing.getBiostarPw())
              : "";
    }
    BiostarResult result = biostarAdapter.testLogin(input.getBiostarIp(), input.getBiostarId(), pw);
    auditService.log(
        actor, AuditService.READ, menuId, "BiostarX 연결 테스트: " + (result.success() ? "성공" : "실패"));
    return result;
  }
}
