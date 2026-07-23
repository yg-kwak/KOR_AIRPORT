package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarAcGroupMapper;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbPersonMapper;
import AirPort.model.CarCardForm;
import AirPort.model.CarForm;
import AirPort.model.CompanySearchParam;
import AirPort.model.TbCar;
import AirPort.model.TbCompany;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import AirPort.model.TbPerson;
import AirPort.security.ARIAUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관차량등록 (/company/companyCar) — 기관 소속 차량(tb_car)과 그 차량용 카드(tb_card) 발급. (docs/backend.md)
 *
 * <p>차량 카드는 <b>카드구분=차량 고정</b>·패스구분 미사용이며, <b>BiostarX 에 등록하지 않는다</b>(차량은 BiostarX 사용자/카드
 * 대상이 아님). tb_card 에만 저장한다. 회수는 삭제가 아니라 {@code car_id=NULL} 이다(다른 차량이 재사용).
 */
@Service
public class CompanyCarService {

  /** 차량 카드 — tb_common(CDT). 이 화면이 발급하는 카드는 차량 고정(화면 값 불신). */
  private static final String CARD_TYPE_CAR = "CDT02";

  private final TbCarMapper carMapper;
  private final TbCompanyMapper companyMapper;
  private final TbCardMapper cardMapper;
  private final TbCarAcGroupMapper carAcGroupMapper;
  private final TbPersonMapper personMapper;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public CompanyCarService(
      TbCarMapper carMapper,
      TbCompanyMapper companyMapper,
      TbCardMapper cardMapper,
      TbCarAcGroupMapper carAcGroupMapper,
      TbPersonMapper personMapper,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.carMapper = carMapper;
    this.companyMapper = companyMapper;
    this.cardMapper = cardMapper;
    this.carAcGroupMapper = carAcGroupMapper;
    this.personMapper = personMapper;
    this.menuAuthService = menuAuthService;
    this.auditService = auditService;
  }

  /** 목록 조회 — <b>기관</b> 목록(삭제되지 않은 기관) + 등록차량 수. 차량 자체는 기관을 눌러 모달에서 다룬다. */
  public PageResult<TbCompany> list(CompanySearchParam param, TbLoginUser actor, Integer menuId) {
    long total = companyMapper.selectCount(param);
    List<TbCompany> rows = companyMapper.selectCarOwnerList(param);
    auditService.log(actor, AuditService.READ, menuId, "기관차량 기관 목록 조회 (결과 " + total + "건)");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  /** 기관의 차량 목록 — 모달에서 표시. */
  public List<TbCar> carsOf(String companyCode, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    List<TbCar> rows = carMapper.selectByCompany(companyCode);
    rows.forEach(c -> c.setCarManagerName(decrypt(c.getCarManagerName())));
    return rows;
  }

  /** 기관의 정규인원 — 차량관리자 선택 팝업(성명 복호화). */
  public List<TbPerson> managersOf(String companyCode, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    List<TbPerson> rows = personMapper.selectByCompany(companyCode);
    rows.forEach(p -> p.setPersonName(decrypt(p.getPersonName())));
    return rows;
  }

  /** 아직 기관에 속하지 않은 차량 — 차량 불러오기 팝업. */
  public List<TbCar> unassignedCars(String keyword, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return carMapper.selectUnassigned(keyword);
  }

  /** 차량의 출입구역 코드 목록(tb_common CAR). */
  public List<String> acCodesOf(int carId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return carAcGroupMapper.selectCodeIds(carId);
  }

  private static String decrypt(String cipher) {
    return (cipher == null || cipher.isBlank()) ? cipher : ARIAUtil.ariaDecrypt(cipher);
  }

  /** 차량의 발급 카드 목록. */
  public List<TbCard> cards(int carId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectByCar(carId);
  }

  /** 차량 등록 — 신규 차량. 출입구역·관리자도 함께 저장한다. */
  @Transactional
  public void create(CarForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(form);
    if (carMapper.existsByCarNo(form.getCarNo(), null) > 0) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 차량번호입니다.");
    }
    TbCar row = toRow(form);
    carMapper.insert(row);
    saveAcCodes(row.getCarId(), form.getAcCodes());
    auditService.log(actor, AuditService.CREATE, menuId, "기관차량 등록: " + form.getCarNo());
  }

  /** 차량 수정 — 기관 미할당 차량을 불러온 경우도 여기로 들어와 소속 기관이 채워진다. */
  @Transactional
  public void update(CarForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    if (form.getCarId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "차량ID가 필요합니다.");
    }
    validate(form);
    if (carMapper.selectById(form.getCarId()) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (carMapper.existsByCarNo(form.getCarNo(), form.getCarId()) > 0) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 차량번호입니다.");
    }
    TbCar row = toRow(form);
    carMapper.updateWithCompany(row);
    carMapper.updateManager(form.getCarId(), blankToNull(form.getCarManagerId()));
    saveAcCodes(form.getCarId(), form.getAcCodes());
    auditService.log(actor, AuditService.UPDATE, menuId, "기관차량 수정: " + form.getCarNo());
  }

  private TbCar toRow(CarForm form) {
    TbCar row = new TbCar();
    row.setCarId(form.getCarId());
    row.setCompanyCode(form.getCompanyCode());
    row.setCarNo(form.getCarNo());
    row.setCarName(form.getCarName());
    row.setCarType(form.getCarType());
    row.setCarManagerId(blankToNull(form.getCarManagerId()));
    return row;
  }

  /** 출입구역 반영 — 전체 삭제 후 선택분만 다시 넣는다(인원 출입권한과 같은 방식). */
  private void saveAcCodes(Integer carId, List<String> codeIds) {
    if (carId == null) {
      return;
    }
    carAcGroupMapper.deleteByCar(carId);
    if (codeIds != null && !codeIds.isEmpty()) {
      carAcGroupMapper.insertBatch(carId, codeIds);
    }
  }

  /** 차량 삭제(소프트) — 발급된 카드가 있으면 막는다(먼저 회수해야 한다). */
  @Transactional
  public void delete(int carId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbCar car = carMapper.selectById(carId);
    if (car == null || "Y".equals(car.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (!cardMapper.selectByCar(carId).isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "발급된 카드가 있는 차량입니다. 카드를 먼저 회수하세요.");
    }
    carAcGroupMapper.deleteByCar(carId);
    carMapper.softDelete(carId);
    auditService.log(actor, AuditService.DELETE, menuId, "기관차량 삭제: " + car.getCarNo());
  }

  /**
   * 차량용 카드 발급 — tb_card 에만 저장한다(차량 카드는 BiostarX 미등록). 회수된 카드 재사용은 재배정만 한다.
   *
   * <p>같은 카드번호가 인원/다른 차량에 발급돼 있으면 거부한다(한 실물 카드는 한 대상에만).
   */
  @Transactional
  public void issueCard(CarCardForm form, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    require(form.getCarId() == null ? null : String.valueOf(form.getCarId()), "차량");
    require(form.getCardNo(), "카드번호");
    require(form.getCardName(), "카드명칭");
    require(form.getCardStatus(), "카드상태");
    TbCar car = carMapper.selectById(form.getCarId());
    if (car == null || "Y".equals(car.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "차량을 찾을 수 없습니다.");
    }

    TbCard known = cardMapper.selectByCardNo(form.getCardNo());
    if (known != null && (known.getPersonId() != null || known.getCarId() != null)) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 발급된 카드번호입니다. 먼저 회수하세요.");
    }

    TbCard row = new TbCard();
    row.setCardId(known == null ? null : known.getCardId());
    row.setCarId(form.getCarId());
    row.setCardType(CARD_TYPE_CAR);
    row.setCardName(form.getCardName());
    row.setCardStatus(form.getCardStatus());
    row.setFeePaidDt(blankToNull(form.getFeePaidDt()));
    row.setIssueReason(form.getIssueReason());
    row.setRemark(form.getRemark());
    row.setBiostarCardValue(form.getCardNo());
    if (known != null) {
      // 회수돼 있던 카드 재사용 — BiostarX 에 이미 있으므로 등록 호출 없이 재배정만
      cardMapper.updateInfo(row);
      cardMapper.assignCar(row.getCardId(), form.getCarId());
    } else {
      // 신규 차량 카드: BiostarX 에 등록하지 않는다(차량은 BiostarX 사용자/카드 대상이 아님). tb_card 에만 저장.
      cardMapper.insert(row);
    }
    auditService.log(
        actor, AuditService.CREATE, menuId, "차량카드 발급: " + car.getCarNo() + " / " + form.getCardNo());
  }

  /** 차량 카드 회수 — 삭제가 아니라 {@code car_id=NULL}(다른 차량이 재사용할 수 있다). */
  @Transactional
  public void releaseCard(int cardId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbCard card = cardMapper.selectById(cardId);
    if (card == null || "Y".equals(card.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    cardMapper.assignCar(cardId, null);
    auditService.log(actor, AuditService.DELETE, menuId, "차량카드 회수: " + card.getBiostarCardValue());
  }

  private void validate(CarForm form) {
    require(form.getCompanyCode(), "기관");
    require(form.getCarNo(), "차량번호");
    require(form.getCarName(), "차량명칭");
    require(form.getCarType(), "차종");
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, label + "은(는) 필수입니다.");
    }
  }

  private static String blankToNull(String v) {
    return (v == null || v.isBlank()) ? null : v;
  }
}
