package AirPort.service;

import AirPort.adapter.BiostarCard;
import AirPort.common.PageResult;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCompanyMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.model.CarCardForm;
import AirPort.model.CompanySearchParam;
import AirPort.model.TbCar;
import AirPort.model.TbCompany;
import AirPort.model.TbCard;
import AirPort.model.TbLoginUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관차량등록 (/company/car) — 기관 소속 차량(tb_car)과 그 차량용 카드(tb_card) 발급. (docs/backend.md)
 *
 * <p>차량 카드는 <b>카드구분=차량 고정</b>이고 패스구분을 쓰지 않는다. 카드는 실물이라 BiostarX 등록이 선행돼야 하며(카드 발급 시
 * {@link CardService#register}), 회수는 삭제가 아니라 {@code car_id=NULL} 이다(다른 차량이 재사용).
 */
@Service
public class CompanyCarService {

  /** 차량 카드 — tb_common(CDT). 이 화면이 발급하는 카드는 차량 고정(화면 값 불신). */
  private static final String CARD_TYPE_CAR = "CDT02";

  private final TbCarMapper carMapper;
  private final TbCompanyMapper companyMapper;
  private final TbCardMapper cardMapper;
  private final CardService cardService;
  private final MenuAuthService menuAuthService;
  private final AuditService auditService;

  public CompanyCarService(
      TbCarMapper carMapper,
      TbCompanyMapper companyMapper,
      TbCardMapper cardMapper,
      CardService cardService,
      MenuAuthService menuAuthService,
      AuditService auditService) {
    this.carMapper = carMapper;
    this.companyMapper = companyMapper;
    this.cardMapper = cardMapper;
    this.cardService = cardService;
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
    return carMapper.selectByCompany(companyCode);
  }

  /** 차량의 발급 카드 목록. */
  public List<TbCard> cards(int carId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    return cardMapper.selectByCar(carId);
  }

  @Transactional
  public void create(TbCar row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(row);
    if (carMapper.existsByCarNo(row.getCarNo(), null) > 0) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 차량번호입니다.");
    }
    carMapper.insert(row);
    auditService.log(actor, AuditService.CREATE, menuId, "기관차량 등록: " + row.getCarNo());
  }

  @Transactional
  public void update(TbCar row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId); // 정책: 등록/수정은 create_auth 로 판정
    if (row.getCarId() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "차량ID가 필요합니다.");
    }
    validate(row);
    if (carMapper.selectById(row.getCarId()) == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (carMapper.existsByCarNo(row.getCarNo(), row.getCarId()) > 0) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 차량번호입니다.");
    }
    carMapper.updateWithCompany(row);
    auditService.log(actor, AuditService.UPDATE, menuId, "기관차량 수정: " + row.getCarNo());
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
    carMapper.softDelete(carId);
    auditService.log(actor, AuditService.DELETE, menuId, "기관차량 삭제: " + car.getCarNo());
  }

  /**
   * 차량용 카드 발급 — BiostarX 카드 등록(이미 있는 번호는 재사용) 후 tb_card 에 저장한다.
   *
   * <p>연동 실패 시 저장하지 않는다(biostar_card_id 없는 카드는 쓸모가 없다 — 카드등록관리와 같은 정책).
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
    BiostarCard issued = cardService.register(form.getCardNo(), actor, menuId);
    if (!issued.success()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "BiostarX 카드 등록 실패: " + issued.message());
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
    row.setBiostarCardId(issued.biostarCardId());
    row.setBiostarCardValue(issued.cardNo());
    if (row.getCardId() == null) {
      cardMapper.insert(row); // 새 실물 카드
    } else {
      cardMapper.updateInfo(row); // 회수돼 있던 카드 재사용
      cardMapper.assignCar(row.getCardId(), form.getCarId());
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

  private void validate(TbCar row) {
    require(row.getCompanyCode(), "기관");
    require(row.getCarNo(), "차량번호");
    require(row.getCarName(), "차량명칭");
    require(row.getCarType(), "차종");
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
