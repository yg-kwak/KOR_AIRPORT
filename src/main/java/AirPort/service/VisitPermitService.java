package AirPort.service;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  /** 구역명에서 번호만 뽑는다 — "인원구역3" → 3, "차량구역1" → 1. */
  private static final Pattern AREA_NO = Pattern.compile("(\\d+)");

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
    f.setPersonAreas(
        areaNos(acGroupMapper.selectNamesByIds(visitMapper.selectAcGroupIds(visitNo))));
    f.setCarAreas(areaNos(carAreaNames(visitMapper.selectCarAcCodes(visitNo))));

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
      List<TbCard> cards = cardMapper.selectByCar(carId);
      x.setCardName(cards.isEmpty() ? null : cards.get(0).getCardName());
      f.getCars().add(x);
    }
    for (String pid : visitMapper.selectManagerIds(visitNo)) {
      TbPerson p = personMapper.selectById(pid);
      if (p == null) {
        continue;
      }
      PermitForm.Manager x = new PermitForm.Manager();
      x.setName(decrypt(p.getPersonName()));
      x.setCompany(p.getCompanyName());
      x.setPhone(decrypt(p.getPersonPhone()));
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

  /**
   * 구역명 목록 → 번호만 콤마로 이어 붙인다. 예: [인원구역5, 인원구역6] → "5,6"
   *
   * <p>번호가 없는 이름은 그대로 남긴다 — 조용히 사라지면 어느 구역이 빠졌는지 알 수 없다.
   */
  private static String areaNos(List<String> names) {
    StringBuilder sb = new StringBuilder();
    for (String name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      Matcher m = AREA_NO.matcher(name);
      sb.append(sb.length() == 0 ? "" : ",").append(m.find() ? m.group(1) : name.trim());
    }
    return sb.toString();
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
