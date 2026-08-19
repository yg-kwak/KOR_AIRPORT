package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import AirPort.adapter.parking.AmanoParkingAdapter;
import AirPort.adapter.parking.ParkingPassRequest;
import AirPort.adapter.parking.ParkingResult;
import AirPort.mapper.TbCarAcGroupMapper;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCardMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbCar;
import AirPort.model.TbCommon;
import AirPort.model.TbVisit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 같은 차량이 여러 방문에 걸렸을 때의 정기권 회수.
 *
 * <p>아마노 정기권은 <b>(주차장, 차량번호) 하나에 한 장</b>이라, 한 방문을 지웠다고 차량번호만 보고 지우면 아직 유효한 다른 방문의 차가 차단기 앞에서 막힌다 —
 * 그쪽에서는 아무것도 바꾼 적이 없는데 갑자기 안 열린다. 그 경계를 여기서 고정한다.
 */
class ParkingSharedCarTest {

  private static final String CAR = "109거9672";

  private final AmanoParkingAdapter parking = mock(AmanoParkingAdapter.class);
  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCarAcGroupMapper carAcGroupMapper = mock(TbCarAcGroupMapper.class);
  private final TbCardMapper cardMapper = mock(TbCardMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final AuditService audit = mock(AuditService.class);

  private final ParkingPassService service =
      new ParkingPassService(
          parking, visitMapper, carMapper, carAcGroupMapper, cardMapper, commonMapper, audit);

  @BeforeEach
  void setUp() {
    when(parking.enabled()).thenReturn(true);
    when(parking.register(any())).thenReturn(ParkingResult.ok());
    when(parking.delete(anyString())).thenReturn(ParkingResult.ok());
    TbCommon c = new TbCommon();
    c.setCodeId("CAR02");
    c.setCodeTag("02"); // 차단기가 달린 유일한 구역
    when(commonMapper.selectOne("CAR", "CAR02")).thenReturn(c);
  }

  /** 아직 살아 있는 방문 — endDt 는 화면 형식(yyyy-MM-dd'T'HH:mm) 그대로 준다. */
  private static TbVisit visit(int visitNo, String endDt) {
    TbVisit v = new TbVisit();
    v.setVisitNo(visitNo);
    v.setWorkEndDt(endDt);
    v.setCompanyName("업체");
    return v;
  }

  @Test
  void 다른_방문이_아직_쓰는_차량은_지우지_않고_그쪽_기준으로_다시_등록한다() {
    // 방문2 를 지운다. 방문1 이 같은 차를 9/30 까지 쓰고 있다.
    when(visitMapper.selectParkingVisitsByCarNo(CAR, 2))
        .thenReturn(List.of(visit(1, "2026-09-30T18:00")));
    when(visitMapper.selectCarAcCodes(1)).thenReturn(List.of("CAR02"));

    service.removeAll("방문 2", Set.of(CAR), 2, null, null, 101);

    verify(parking, never()).delete(anyString());
    ArgumentCaptor<ParkingPassRequest> cap = ArgumentCaptor.forClass(ParkingPassRequest.class);
    verify(parking).register(cap.capture());
    assertEquals("20260930", cap.getValue().endDate()); // 남은 방문의 종료일로 돌아간다
    assertEquals(CAR, cap.getValue().carNo());
  }

  @Test
  void 남은_주체가_없으면_지운다() {
    when(visitMapper.selectParkingVisitsByCarNo(CAR, 2)).thenReturn(List.of());

    service.removeAll("방문 2", Set.of(CAR), 2, null, null, 101);

    verify(parking).delete(CAR);
    verify(parking, never()).register(any());
  }

  @Test
  void 남은_방문이_차단기_없는_구역만_가지면_지운다() {
    // 구역은 있지만 종별이 없다 = 주차와 무관한 구역이라 정기권을 유지할 이유가 없다
    TbCommon plain = new TbCommon();
    plain.setCodeId("CAR01");
    plain.setCodeTag(null);
    when(commonMapper.selectOne("CAR", "CAR01")).thenReturn(plain);
    when(visitMapper.selectParkingVisitsByCarNo(CAR, 2))
        .thenReturn(List.of(visit(1, "2026-09-30T18:00")));
    when(visitMapper.selectCarAcCodes(1)).thenReturn(List.of("CAR01"));

    service.removeAll("방문 2", Set.of(CAR), 2, null, null, 101);

    verify(parking).delete(CAR);
    verify(parking, never()).register(any());
  }

  @Test
  void 기관차량이_쓰고_있으면_2037년까지로_되돌린다() {
    TbCar car = new TbCar();
    car.setCarId(500);
    car.setCarNo(CAR);
    car.setCarName("기관차량");
    when(carMapper.selectParkingCarByNo(CAR, null)).thenReturn(car);
    when(carAcGroupMapper.selectCodeIds(500)).thenReturn(List.of("CAR02"));

    service.removeAll("방문 2", Set.of(CAR), 2, null, null, 101);

    verify(parking, never()).delete(anyString());
    ArgumentCaptor<ParkingPassRequest> cap = ArgumentCaptor.forClass(ParkingPassRequest.class);
    verify(parking).register(cap.capture());
    assertEquals("20371231", cap.getValue().endDate());
  }
}
