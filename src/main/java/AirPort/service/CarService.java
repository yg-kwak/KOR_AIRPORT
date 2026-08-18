package AirPort.service;

import AirPort.common.PageResult;
import AirPort.common.Texts;
import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.mapper.TbCarMapper;
import AirPort.model.CarSearchParam;
import AirPort.model.TbCar;
import AirPort.model.TbLoginUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량(tb_car) 등록관리 CRUD. 골든 샘플(LoginUserService) 패턴을 따른다. (docs/backend.md)
 *
 * <p>차량번호(car_no)는 중복 불가(del_yn='N' 기준). 삭제는 소프트 삭제(del_yn='Y')이며 삭제 로그에 차량번호를 스냅샷으로 남긴다. 쓰기는 메뉴
 * 권한(tb_menu_auth_detail)을 서버에서 재검증한다.
 */
@Service
public class CarService {

  private final TbCarMapper carMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public CarService(
      TbCarMapper carMapper, AuditService auditService, MenuAuthService menuAuthService) {
    this.carMapper = carMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  /** 목록 조회 — 검색조건·결과 건수 감사(READ). */
  public PageResult<TbCar> list(CarSearchParam param, TbLoginUser actor, Integer menuId) {
    long total = carMapper.selectCount(param);
    List<TbCar> rows = carMapper.selectList(param);
    auditService.log(
        actor, AuditService.READ, menuId, "차량 목록 조회 (" + searchSummary(param, total) + ")");
    return new PageResult<>(rows, total, param.getPage(), param.getSize());
  }

  private String searchSummary(CarSearchParam param, long total) {
    StringBuilder sb = new StringBuilder();
    if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
      sb.append("검색어=").append(param.getSearchType()).append(':').append(param.getKeyword());
    } else {
      sb.append("검색어=없음");
    }
    sb.append(", 정렬=")
        .append(param.getSort() == null ? "기본" : param.getSort())
        .append(' ')
        .append(param.getDir())
        .append(", 페이지=")
        .append(param.getPage())
        .append(", 결과 ")
        .append(total)
        .append("건");
    return sb.toString();
  }

  /** 엑셀 다운로드용 전체 목록(동일 검색/정렬). 목적(purpose)은 감사 remark 로 기록. */
  public List<TbCar> listAllForExcel(
      CarSearchParam param, TbLoginUser actor, Integer menuId, String purpose) {
    menuAuthService.requireRead(actor, menuId);
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "다운로드 목적을 입력해주세요.");
    }
    List<TbCar> rows = carMapper.selectListAll(param);
    auditService.log(
        actor, AuditService.DOWNLOAD, menuId, "차량 엑셀 다운로드 (" + rows.size() + "건)", purpose);
    return rows;
  }

  @Transactional
  public void create(TbCar row, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireCreate(actor, menuId);
    validate(row);
    if (carMapper.existsByCarNo(row.getCarNo(), null) > 0) {
      throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 차량번호입니다.");
    }
    carMapper.insert(row);
    auditService.log(actor, AuditService.CREATE, menuId, "차량 등록: " + row.getCarNo());
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
    carMapper.update(row);
    auditService.log(actor, AuditService.UPDATE, menuId, "차량 수정: " + row.getCarNo());
  }

  /** 소프트 삭제 — 차량번호를 감사 스냅샷으로 남긴다(행 상태와 무관하게 이력 보존). */
  @Transactional
  public void delete(int carId, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireDelete(actor, menuId);
    TbCar car = carMapper.selectById(carId);
    if (car == null || "Y".equals(car.getDelYn())) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    carMapper.softDelete(carId);
    auditService.log(actor, AuditService.DELETE, menuId, "차량 삭제: " + car.getCarNo());
  }

  private void validate(TbCar row) {
    if (row.getCarNo() == null || row.getCarNo().isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "차량번호는 필수입니다.");
    }
    Texts.maxLen(row.getCarNo(), 20, "차량번호"); // tb_car.car_no nvarchar(20)
    Texts.maxLen(row.getCarName(), 50, "차량명"); // tb_car.car_name nvarchar(50)
  }
}
