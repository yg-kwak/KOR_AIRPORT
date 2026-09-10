package AirPort.service;

import AirPort.common.AccessAreas;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbAcGroupMapper;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.PermitForm;
import AirPort.model.TbCar;
import AirPort.model.TbCard;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.model.TbVisit;
import AirPort.security.ARIAUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 보호구역 임시출입허가 신청서 데이터. (임시인원등록 → [신청서 출력])
 *
 * <p>출입증번호는 <b>지금 배정된 카드</b>가 원칙이고, 회수된 뒤라면 마지막 카드번호를 쓴다 — 퇴실한 방문도 신청서를 다시 뽑을 수 있어야 한다.
 *
 * <p>양식의 확인자·근무확인·운전자·주소 칸은 시스템이 보관하지 않는 값이라 비운다(인쇄 후 손으로 적는다).
 */
@Service
public class VisitPermitService {

  private final TbVisitMapper visitMapper;
  private final TbPersonMapper personMapper;
  private final TbCarMapper carMapper;
  private final TbCardMapper cardMapper;
  private final TbCommonMapper commonMapper;
  private final TbAcGroupMapper acGroupMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public VisitPermitService(
      TbVisitMapper visitMapper,
      TbPersonMapper personMapper,
      TbCarMapper carMapper,
      TbCardMapper cardMapper,
      TbCommonMapper commonMapper,
      TbAcGroupMapper acGroupMapper,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.visitMapper = visitMapper;
    this.personMapper = personMapper;
    this.carMapper = carMapper;
    this.cardMapper = cardMapper;
    this.commonMapper = commonMapper;
    this.acGroupMapper = acGroupMapper;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 신청서 1건 — 출력은 화면이 한다(서버는 값만 준다). */
  public PermitForm permit(int visitNo, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    TbVisit v = visitMapper.selectById(visitNo);
    if (v == null || "Y".equals(v.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    PermitForm f = new PermitForm();
    f.setAccessStart(v.getWorkStartDt());
    f.setAccessEnd(v.getWorkEndDt());
    f.setPurpose(v.getWorkPurpose());
    f.setApplyDate(datePart(v.getWorkStartDt()));
    // 구역이 하나도 없는 방문도 신청서는 나와야 한다.
    // 빈 목록을 그대로 넘기면 mapper 가 IN () 을 만들어 SQL 이 깨진다.
    List<Integer> acIds = visitMapper.selectAcGroupIds(visitNo);
    f.setPersonAreas(acIds.isEmpty() ? "" : AccessAreas.csv(acGroupMapper.selectNamesByIds(acIds)));
    f.setCarAreas(AccessAreas.csv(carAreaNames(visitMapper.selectCarAcCodes(visitNo))));

    for (String pid : visitMapper.selectPersonIds(visitNo)) {
      TbPerson p = personMapper.selectById(pid);
      if (p == null) {
        continue;
      }
      PermitForm.Visitor x = new PermitForm.Visitor();
      x.setName(decrypt(p.getPersonName()));
      x.setBirthDate(decrypt(p.getBirthDate()));
      x.setAffiliation(p.getAffiliation());
      x.setCardName(cardNameOfPerson(pid, visitNo));
      f.getVisitors().add(x);
    }
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      TbCar c = carMapper.selectById(carId);
      if (c == null) {
        continue;
      }
      PermitForm.Car x = new PermitForm.Car();
      x.setCarNo(c.getCarNo());
      x.setCarTypeName(codeName("CT", c.getCarType()));
      // 차량표의 '출입자소속' — 차량마다 따로 적는 소속이다.
      // 예전에는 방문(그룹)의 업체명을 썼는데, 등록 화면에서 업체명을 더 이상 받지 않는다.
      // 값이 없는 과거 방문은 그때 쓰던 업체명으로 물러선다 — 이미 인쇄된 신청서와 어긋나지 않게.
      x.setAffiliation(
          (c.getAffiliation() == null || c.getAffiliation().isBlank())
              ? v.getCompanyName()
              : c.getAffiliation());
      List<TbCard> cards = cardMapper.selectByCar(carId);
      x.setCardName(cards.isEmpty() ? null : cards.get(0).getCardName());
      f.getCars().add(x);
    }
    for (AirPort.model.TbVisitManager m : visitMapper.selectManagers(visitNo)) {
      String pid = m.getPersonId();
      TbPerson p = personMapper.selectById(pid);
      if (p == null) {
        continue;
      }
      PermitForm.Manager x = new PermitForm.Manager();
      x.setName(decrypt(p.getPersonName()));
      x.setCompany(p.getCompanyName());
      // 연락처는 정규인원 정보가 아니라 그 방문에 적어 둔 번호다
      x.setPhone(decrypt(m.getManagerPhone()));
      List<TbCard> cards = cardMapper.selectByPerson(pid);
      x.setCardName(cards.isEmpty() ? null : cards.get(0).getCardName());
      f.getManagers().add(x);
    }
    // 신청인은 첫 인솔자 — 양식 하단 "신청인 {소속} 성명 {성명} (인)"
    if (!f.getManagers().isEmpty()) {
      f.setApplicantCompany(f.getManagers().get(0).getCompany());
      f.setApplicantName(f.getManagers().get(0).getName());
    }
    auditService.log(actor, AuditService.DOWNLOAD, menuId, "출입허가 신청서 출력: " + visitNo);
    return f;
  }

  /**
   * 출입증번호 칸에 넣을 카드명칭. 지금 배정된 카드가 원칙이고, 회수됐으면 마지막 카드번호로 되짚어 명칭을 찾는다 — 퇴실한 방문도 신청서를 다시 뽑을 수 있어야 한다.
   *
   * <p>그 카드가 사라졌으면 번호라도 남긴다(빈칸보다 낫다).
   */
  private String cardNameOfPerson(String personId, int visitNo) {
    List<TbCard> cards = cardMapper.selectByPerson(personId);
    if (!cards.isEmpty()) {
      return cards.get(0).getCardName();
    }
    String lastNo = visitMapper.selectVisitorLastCard(visitNo, personId);
    if (lastNo == null || lastNo.isBlank()) {
      return null;
    }
    TbCard last = cardMapper.selectByCardNo(lastNo);
    return (last == null || last.getCardName() == null) ? lastNo : last.getCardName();
  }

  /** 차량 출입구역 코드(tb_common CAR) → 구역명. */
  private List<String> carAreaNames(List<String> codes) {
    List<String> names = new ArrayList<>();
    for (String code : codes) {
      String name = codeName("CAR", code);
      names.add(name == null ? code : name);
    }
    return names;
  }

  private String codeName(String cmmId, String codeId) {
    if (codeId == null || codeId.isBlank()) {
      return null;
    }
    TbCommon c = commonMapper.selectOne(cmmId, codeId);
    return c == null ? null : c.getCodeName();
  }

  /** "2026-07-02T10:01" → "2026-07-02" */
  private static String datePart(String dateTime) {
    if (dateTime == null || dateTime.length() < 10) {
      return dateTime;
    }
    return dateTime.substring(0, 10);
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }
}
