package AirPort.service;

import AirPort.adapter.parking.AmanoParkingAdapter;
import AirPort.adapter.parking.ParkingPassRequest;
import AirPort.adapter.parking.ParkingResult;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbCar;
import AirPort.model.TbCommon;
import AirPort.model.TbLoginUser;
import AirPort.model.VisitCarForm;
import AirPort.model.VisitForm;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 주차 차단기 정기권 동기화 — 차량구역을 고르면 그 구역 차단기가 열리도록 아마노에 정기권을 등록한다. 외부 호출은 {@link AmanoParkingAdapter} 로만
 * 나간다. (docs/integration.md)
 *
 * <p>차량이 붙는 화면이 둘이라 한 곳에 모은다 — <b>방문 차량</b>({@link #syncVisit}, 종료일=방문 종료일)과 <b>기관차량</b>({@link
 * #syncCar}, 종료일={@link #PERMANENT_END_DATE}). 구역→종별 매핑과 회수 규칙이 갈리면 두 화면의 차단기 동작이 달라진다.
 *
 * <p>정책
 *
 * <ul>
 *   <li>저장할 때마다 <b>지우고 다시 등록</b>한다 — 아마노는 같은 차량의 재등록을 거부하므로 등록 한 번으로 갱신할 수 없다.
 *   <li>차량이 빠지거나 차량구역을 모두 해제하면 <b>정기권을 삭제</b>한다(차단기가 계속 열리면 안 된다).
 *   <li>실패해도 <b>저장을 취소하지 않는다</b> — 차단기는 출입통제의 부가 기능이라, 여기서 롤백하면 주차관제가 죽었을 때 차량 등록 자체가 멈춘다. 대신 사유를
 *       화면 경고와 감사(tb_system_log)에 남겨 놓친 차량이 드러나게 한다.
 * </ul>
 */
@Service
public class ParkingPassService {

  private static final Logger log = LoggerFactory.getLogger(ParkingPassService.class);

  /** 차량구역 공통코드 — tb_common(cmm_id='CAR'). code_tag 가 아마노 정기권 종별을 정한다. */
  private static final String CAR_AREA_CMM_ID = "CAR";

  /** 기관차량(정규)은 방문처럼 끝나는 날이 없다 — 아마노 상한까지 길게 잡는다. */
  public static final String PERMANENT_END_DATE = "20371231";

  private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final AmanoParkingAdapter parking;
  private final TbVisitMapper visitMapper;
  private final TbCarMapper carMapper;
  private final TbCommonMapper commonMapper;
  private final AuditService auditService;

  public ParkingPassService(
      AmanoParkingAdapter parking,
      TbVisitMapper visitMapper,
      TbCarMapper carMapper,
      TbCommonMapper commonMapper,
      AuditService auditService) {
    this.parking = parking;
    this.visitMapper = visitMapper;
    this.carMapper = carMapper;
    this.commonMapper = commonMapper;
    this.auditService = auditService;
  }

  /** 지금 방문에 붙어 있는 차량번호(정규화) — 저장 전 스냅샷용. 연동이 꺼져 있으면 조회하지 않는다. */
  public Set<String> visitCarNos(int visitNo) {
    Set<String> out = new LinkedHashSet<>();
    if (!parking.enabled()) {
      return out;
    }
    for (Integer carId : visitMapper.selectCarIds(visitNo)) {
      TbCar car = carMapper.selectById(carId);
      String no = normalize(car == null ? null : car.getCarNo());
      if (no != null) {
        out.add(no);
      }
    }
    return out;
  }

  /**
   * 방문 저장 뒤 정기권 반영 — 종료일은 방문 종료일이다.
   *
   * @param before 저장 전 차량번호({@link #visitCarNos}) — 여기서 빠진 차량은 정기권을 지운다
   * @return 화면에 덧붙일 경고(모두 성공이거나 미대상이면 null)
   */
  public String syncVisit(
      int visitNo, VisitForm form, Collection<String> before, TbLoginUser actor, Integer menuId) {
    if (!parking.enabled()) {
      return null;
    }
    String what = "방문 " + visitNo;
    List<VisitCarForm> cars = validCars(form);
    Set<String> now = new LinkedHashSet<>();
    cars.forEach(c -> now.add(normalize(c.getCarNo())));
    List<String> failures = new ArrayList<>();

    // 방문에서 빠진 차량 — 차단기가 계속 열리지 않도록 먼저 회수한다
    for (String carNo : before) {
      if (!now.contains(carNo)) {
        remove(carNo, failures);
      }
    }

    String passType = passType(form.getCarAcCodes(), what);
    if (passType == null) {
      // 차량구역을 하나도 고르지 않았다 = 주차 권한 없음. 남아 있는 차량도 전부 회수한다.
      now.forEach(carNo -> remove(carNo, failures));
      return report(what, failures, actor, menuId);
    }
    String end = endDate(form.getWorkEndDt());
    for (VisitCarForm cf : cars) {
      String carNo = normalize(cf.getCarNo());
      if (end == null) {
        // 종료일이 없으면 언제까지 열어 줄지 정할 수 없다. 무기한 개방보다 미등록이 안전하다.
        failures.add(carNo + "(방문 종료일이 없어 정기권을 등록하지 못했습니다)");
        continue;
      }
      register(carNo, cf.getCarName(), passType, end, what, failures);
    }
    return report(what, failures, actor, menuId);
  }

  /**
   * 기관차량 1대의 정기권 반영 — 종료일은 {@link #PERMANENT_END_DATE}.
   *
   * @param areaCodes 차량 출입구역(tb_car_ac_group). 비어 있으면 정기권을 지운다
   * @param previousCarNo 저장 전 차량번호 — 번호를 고쳤으면 옛 번호의 정기권을 회수한다(신규는 null)
   * @return 화면에 덧붙일 경고(성공/미대상이면 null)
   */
  public String syncCar(
      TbCar car, List<String> areaCodes, String previousCarNo, TbLoginUser actor, Integer menuId) {
    if (!parking.enabled()) {
      return null;
    }
    String carNo = normalize(car.getCarNo());
    if (carNo == null) {
      return null;
    }
    String what = "기관차량 " + carNo;
    List<String> failures = new ArrayList<>();
    String was = normalize(previousCarNo);
    if (was != null && !was.equals(carNo)) {
      remove(was, failures); // 번호를 고치면 옛 번호가 남아 계속 열린다
    }
    String passType = passType(areaCodes, what);
    if (passType == null) {
      remove(carNo, failures); // 구역 없음 = 주차 권한 없음
      return report(what, failures, actor, menuId);
    }
    register(carNo, car.getCarName(), passType, PERMANENT_END_DATE, what, failures);
    return report(what, failures, actor, menuId);
  }

  /** 방문 삭제·정리, 차량 삭제 시 정기권 회수. 실패는 감사에 남기고 삭제 자체는 막지 않는다. */
  public String removeAll(
      String what, Collection<String> carNos, TbLoginUser actor, Integer menuId) {
    if (!parking.enabled() || carNos.isEmpty()) {
      return null;
    }
    List<String> failures = new ArrayList<>();
    carNos.forEach(carNo -> remove(normalize(carNo), failures));
    return report(what, failures, actor, menuId);
  }

  private void register(
      String carNo, String carName, String passType, String end, String what, List<String> fails) {
    ParkingResult r =
        parking.register(
            new ParkingPassRequest(carNo, carName, passType, LocalDate.now().format(YMD), end));
    if (r.success()) {
      log.info("주차 정기권 등록 — {} 차량 {} 종별 {} ~{}", what, carNo, passType, end);
    } else {
      fails.add(carNo + "(" + r.message() + ")");
    }
  }

  private void remove(String carNo, List<String> failures) {
    if (carNo == null) {
      return;
    }
    ParkingResult r = parking.delete(carNo);
    if (!r.success()) {
      failures.add(carNo + "(" + r.message() + ")");
    }
  }

  /** 실패분을 감사에 남기고 화면 경고 문구로 돌려준다. */
  private String report(String what, List<String> failures, TbLoginUser actor, Integer menuId) {
    if (failures.isEmpty()) {
      return null;
    }
    String detail = String.join(", ", failures);
    auditService.logAlways(
        actor, AuditService.UPDATE, menuId, "주차 정기권 반영 실패(" + what + "): " + detail);
    return "주차 차단기 등록에 실패한 차량이 있습니다: " + detail + " — 주차관제 연동 상태를 확인한 뒤 다시 저장하세요.";
  }

  /** 번호가 있는 차량 행만. 저장 로직과 같은 기준이라 화면에 보이는 것과 어긋나지 않는다. */
  private static List<VisitCarForm> validCars(VisitForm form) {
    List<VisitCarForm> out = new ArrayList<>();
    if (form.getCars() == null) {
      return out;
    }
    for (VisitCarForm c : form.getCars()) {
      if (c.getCarNo() != null && !c.getCarNo().isBlank()) {
        out.add(c);
      }
    }
    return out;
  }

  /**
   * 차량구역 → 아마노 정기권 종별.
   *
   * <p><b>아마노 정기권은 (주차장, 차량번호) 하나에 종별이 1개다</b> — 같은 차량을 종별만 바꿔 다시 등록하면 "이미 등록된 차량" 으로
   * 거부된다(2026-08-13 시험서버 실증). 종별이 붙은 구역이 늘어 2개 이상 겹치는 경우를 어떻게 보낼지는 아직 정해지지 않았다(조합 종별 passType3~8 신설
   * 여부 협의 중 — docs/integration.md). 종별이 하나뿐인 지금은 이 분기를 타지 않는다.
   *
   * <p><b>차단기가 달린 구역만 종별을 갖는다.</b> 지금은 단말기가 한 대라 차량구역2 에만 {@code code_tag} 가 채워져 있다. 그래서 고른 구역 중
   * <b>종별이 있는 것</b>을 찾아야 한다 — 정렬해서 맨 앞을 집으면, 종별 없는 구역(차량구역1 등)을 함께 고른 순간 정작 차단기가 붙은 구역이 통째로 빠진다.
   *
   * <p>잠정: 종별이 있는 구역 중 가장 앞선 하나로 등록하고 나머지는 경고로 남겨, 열리지 않는 구역이 조용히 묻히지 않게 한다.
   */
  private String passType(List<String> areaCodes, String what) {
    if (areaCodes == null || areaCodes.isEmpty()) {
      return null;
    }
    List<String> passTypes =
        areaCodes.stream()
            .filter(c -> c != null && !c.isBlank())
            .sorted()
            .map(this::passTypeOf)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (passTypes.isEmpty()) {
      return null; // 고른 구역 중 차단기가 달린 곳이 없다
    }
    if (passTypes.size() > 1) {
      log.warn(
          "{} 의 차량구역 종별이 {}개({})지만 아마노 정기권은 차량 1대에 종별 1개다 — {} 로만 등록한다. 조합 종별이 정해지면 이 분기를 바꾼다.",
          what,
          passTypes.size(),
          String.join("/", passTypes),
          passTypes.get(0));
    }
    return passTypes.get(0);
  }

  /**
   * 공통코드 CAR 의 code_tag(예: 02) → passType2. 값이 없으면 차단기가 없는 구역이라 null.
   *
   * <p>종별이 비어 있는 것은 <b>정상</b>이다(주차 차단기와 무관한 구역) — DEBUG 로만 남긴다. 반대로 값이 있는데 범위를 벗어난 것은 설정 실수라 WARN
   * 이다.
   */
  private String passTypeOf(String codeId) {
    TbCommon code = commonMapper.selectOne(CAR_AREA_CMM_ID, codeId);
    String tag = code == null ? null : code.getCodeTag();
    String digits = tag == null ? "" : tag.replaceAll("\\D", "");
    if (digits.isEmpty()) {
      log.debug("차량구역 {} 에는 정기권 종별(code_tag)이 없다 — 주차 차단기와 무관한 구역.", codeId);
      return null;
    }
    int n = Integer.parseInt(digits);
    if (n < 1 || n > 8) {
      log.warn("차량구역 {} 의 정기권 종별 {} 은 아마노 범위(1~8) 밖이다 — 주차 등록을 건너뛴다.", codeId, tag);
      return null;
    }
    return "passType" + n;
  }

  /** 방문 종료일시 → yyyyMMdd. 값이 없으면 null(등록하지 않는다). */
  static String endDate(String workEndDt) {
    String digits = workEndDt == null ? "" : workEndDt.replaceAll("\\D", "");
    return digits.length() >= 8 ? digits.substring(0, 8) : null;
  }

  /** 아마노는 "공백 없이 전체번호" 를 요구한다. 앞뒤·중간 공백을 지운다. */
  static String normalize(String carNo) {
    if (carNo == null || carNo.isBlank()) {
      return null;
    }
    return carNo.replaceAll("\\s+", "");
  }
}
