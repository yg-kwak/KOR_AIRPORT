package AirPort.service;

import AirPort.adapter.BiostarAdapter;
import AirPort.adapter.BiostarGroups;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbSystemMapper;
import AirPort.model.AcGroupAddForm;
import AirPort.model.TbAcGroup;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.TbSystem;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출입권한 그룹(tb_ac_group) — tb_common(cmm_id='AR')과 최상위 동기화 + 트리 + BiostarX 출입그룹 매핑.
 *
 * <p>최상위(level 1)=AR 코드(ar_code=code_id). 하위=BiostarX 출입그룹. 외부 연동은 adapter 로만(불변식).
 */
@Service
public class AcGroupService {

  private static final String AR = "AR";

  private final TbAcGroupMapper acGroupMapper;
  private final TbCommonMapper commonMapper;
  private final TbSystemMapper systemMapper;
  private final BiostarAdapter biostarAdapter;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public AcGroupService(
      TbAcGroupMapper acGroupMapper,
      TbCommonMapper commonMapper,
      TbSystemMapper systemMapper,
      BiostarAdapter biostarAdapter,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.acGroupMapper = acGroupMapper;
    this.commonMapper = commonMapper;
    this.systemMapper = systemMapper;
    this.biostarAdapter = biostarAdapter;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 화면 진입 시 동기화 — tb_common(AR) 기준: 없는 코드는 최상위로 insert, tb_common 에 없어진 ar_code 는 하위 포함 delete. */
  @Transactional
  public void sync(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    List<TbCommon> arCodes = commonMapper.selectCodesForPicker(AR, null); // codeId/codeName
    List<String> codeIds = arCodes.stream().map(TbCommon::getCodeId).toList();

    // 1) tb_common 에 없는 ar_code(및 하위) 제거
    acGroupMapper.deleteOrphans(codeIds);

    // 2) 없는 AR 코드는 최상위로 추가
    List<String> existingTop = acGroupMapper.selectTopArCodes();
    int order = existingTop.size();
    for (TbCommon c : arCodes) {
      if (!existingTop.contains(c.getCodeId())) {
        TbAcGroup top = new TbAcGroup();
        top.setAcGroupName(c.getCodeName());
        top.setArCode(c.getCodeId());
        top.setAcGroupLevel(1);
        top.setAcGroupOrder(order++);
        acGroupMapper.insert(top);
      }
    }
  }

  /** 트리(roots + children). READ 감사. */
  public List<TbAcGroup> tree(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    List<TbAcGroup> all = acGroupMapper.selectList();
    auditService.log(actor, AuditService.READ, menuId, "출입권한 그룹 조회 (" + all.size() + "건)");
    Map<Integer, TbAcGroup> byId = new LinkedHashMap<>();
    for (TbAcGroup g : all) {
      byId.put(g.getAcGroupId(), g);
    }
    List<TbAcGroup> roots = new ArrayList<>();
    for (TbAcGroup g : all) {
      TbAcGroup parent = (g.getParentAcGroupId() == null) ? null : byId.get(g.getParentAcGroupId());
      if (parent == null) {
        roots.add(g);
      } else {
        parent.getChildren().add(g);
      }
    }
    return roots;
  }

  /** BiostarX 출입그룹 목록 — 저장된 설정(tb_system)으로 조회. */
  public BiostarGroups biostarGroups(TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbSystem cfg = systemMapper.selectOne();
    if (cfg == null) {
      return BiostarGroups.fail("BiostarX 설정이 없습니다. 설정관리에서 등록하세요.");
    }
    String pw = cfg.getBiostarPw() == null ? "" : ARIAUtil.ariaDecrypt(cfg.getBiostarPw());
    return biostarAdapter.searchAccessGroups(cfg.getBiostarIp(), cfg.getBiostarId(), pw);
  }

  /** 하위 그룹 추가 — 선택한 BiostarX 출입그룹들을 상위 노드 아래에 매핑 저장. */
  @Transactional
  public void addChildren(AcGroupAddForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (form.getParentId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "상위 그룹이 필요합니다.");
    }
    TbAcGroup parent = acGroupMapper.selectById(form.getParentId());
    if (parent == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    java.util.Set<Integer> used = new java.util.HashSet<>(acGroupMapper.selectUsedBiostarIds());
    int order = 0;
    for (TbAcGroup g : form.getGroups()) {
      // 이미 매핑된 출입그룹은 중복 추가하지 않는다(요청 내 중복도 방지)
      if (g.getBiostarAcId() == null || !used.add(g.getBiostarAcId())) {
        continue;
      }
      TbAcGroup child = new TbAcGroup();
      child.setParentAcGroupId(parent.getAcGroupId());
      child.setArCode(parent.getArCode()); // 상위 상속(동기화 삭제 대상 판정용)
      child.setAcGroupLevel((parent.getAcGroupLevel() == null ? 1 : parent.getAcGroupLevel()) + 1);
      child.setAcGroupOrder(order++);
      child.setBiostarAcId(g.getBiostarAcId());
      child.setBiostarAcName(g.getBiostarAcName());
      child.setAcGroupName(g.getBiostarAcName()); // 표시명 기본값
      acGroupMapper.insert(child);
    }
    auditService.log(
        actor, AuditService.CREATE, menuId, "출입그룹 하위 추가: parent=" + parent.getAcGroupId());
  }

  @Transactional
  public void update(TbAcGroup row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    if (row.getAcGroupName() == null || row.getAcGroupName().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "그룹명은 필수입니다.");
    }
    if (acGroupMapper.update(row) == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    auditService.log(actor, AuditService.UPDATE, menuId, "출입그룹 수정: " + row.getAcGroupId());
  }

  /** 삭제 — 최상위(동기화 노드)는 삭제 불가. 하위는 subtree 삭제. */
  @Transactional
  public void delete(int acGroupId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbAcGroup node = acGroupMapper.selectById(acGroupId);
    if (node == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (node.isTop()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "최상위 출입구역은 삭제할 수 없습니다. (공통코드 AR 관리)");
    }
    acGroupMapper.deleteSubtree(acGroupId);
    auditService.log(actor, AuditService.DELETE, menuId, "출입그룹 삭제: " + acGroupId);
  }
}
