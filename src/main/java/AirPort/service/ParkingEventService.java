package AirPort.service;

import AirPort.adapter.parking.ParkingEventNotice;
import AirPort.common.PageResult;
import AirPort.mapper.TbParkingEventMapper;
import AirPort.model.ParkingEventSearchParam;
import AirPort.model.TbLoginUser;
import AirPort.model.TbParkingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주차 입·출차 이벤트 — 수신 저장 + 주차 조회 화면. (docs/integration.md)
 *
 * <p>다른 연동과 방향이 반대다. BiostarX·아마노 정기권은 우리가 호출하지만, 이 이벤트는 <b>주차서버가 우리를 호출한다</b>({@code POST
 * /api/InOutCar}). 그래서 로그인 세션도 메뉴 권한도 없는 요청이며, 받은 것을 이력으로 남기는 일만 한다.
 */
@Service
public class ParkingEventService {

  private static final Logger log = LoggerFactory.getLogger(ParkingEventService.class);

  private final TbParkingEventMapper eventMapper;
  private final AuditService auditService;
  private final MenuAuthService menuAuthService;

  public ParkingEventService(
      TbParkingEventMapper eventMapper,
      AuditService auditService,
      MenuAuthService menuAuthService) {
    this.eventMapper = eventMapper;
    this.auditService = auditService;
    this.menuAuthService = menuAuthService;
  }

  /**
   * 주차서버가 보내온 이벤트 1건을 이력으로 남긴다.
   *
   * @param raw 받은 원문 — 규격이 늘어도 지난 이벤트를 다시 읽을 수 있게 그대로 보관한다
   * @return 새로 저장했으면 true, 이미 받은 건(재전송)이면 false
   */
  @Transactional
  public boolean receive(ParkingEventNotice notice, String raw) {
    TbParkingEvent row = toRow(notice, raw);
    boolean saved = eventMapper.insert(row) > 0;
    if (saved) {
      log.info(
          "주차 {} — 차량 {} {} (장치 {})",
          notice.entered() ? "입차" : "출차",
          row.getCarNo(),
          row.getEventDt(),
          row.getEqpmId());
    } else {
      // 주차서버는 응답을 못 받으면 같은 건을 다시 보낸다 — 정상이라 DEBUG 로만 남긴다
      log.debug("주차 이벤트 재수신(이미 저장됨) — {} {}", row.getCarNo(), row.getEventDt());
    }
    return saved;
  }

  /** 수신 payload → 이력 행. 시각이 없으면 이력을 만들 수 없다(호출자가 먼저 거른다). */
  private static TbParkingEvent toRow(ParkingEventNotice n, String raw) {
    TbParkingEvent row = new TbParkingEvent();
    row.setEventType(n.eventType());
    row.setEventName(n.eventName());
    row.setLotArea(n.lotArea());
    row.setEqpmId(n.eqpmID());
    // 미인식(No_Detection)도 그대로 남긴다 — 차단기가 열렸는지는 그것대로 기록이다
    row.setCarNo(n.unrecognized() ? ParkingEventNotice.NO_DETECTION : n.carNumber().trim());
    row.setEventDt(n.eventDateTime());
    row.setInDt(n.inDateTime());
    row.setInEqpmId(n.inEqpmID());
    row.setUserName(n.userName());
    row.setPassType(n.passType());
    row.setIsCustDef(Boolean.TRUE.equals(n.isCustDef()) ? "Y" : "N");
    row.setParkingId(n.iID());
    row.setCarImageUrl(n.carImagePath());
    row.setHistoryId(n.historyID());
    row.setLprTrnsId(n.lprTrnsID());
    row.setRawJson(raw);
    return row;
  }

  /** 목록 조회 — 검색조건·건수 감사(READ). */
  public PageResult<TbParkingEvent> list(
      ParkingEventSearchParam param, TbLoginUser actor, Integer menuId) {
    menuAuthService.requireRead(actor, menuId);
    long total = eventMapper.selectCount(param);
    auditService.log(
        actor, AuditService.READ, menuId, "주차 조회 (" + searchSummary(param, total) + ")");
    return new PageResult<>(eventMapper.selectList(param), total, param.getPage(), param.getSize());
  }

  private static String searchSummary(ParkingEventSearchParam param, long total) {
    StringBuilder sb = new StringBuilder();
    if (param.getStartDate() != null && !param.getStartDate().isBlank()) {
      sb.append("기간=").append(param.getStartDate()).append('~').append(param.getEndDate());
    } else {
      sb.append("기간=전체");
    }
    if (param.getDirection() != null && !param.getDirection().isEmpty()) {
      sb.append(", 구분=").append("in".equals(param.getDirection()) ? "입차" : "출차");
    }
    if (param.isNotOpenOnly()) {
      sb.append(", 미개방만");
    }
    if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
      sb.append(", 검색어=").append(param.getSearchType()).append(':').append(param.getKeyword());
    }
    sb.append(", 결과 ").append(total).append("건");
    return sb.toString();
  }
}
