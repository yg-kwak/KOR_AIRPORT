package AirPort.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import AirPort.adapter.AmanoParkingAdapter;
import AirPort.adapter.ParkingPassRequest;
import AirPort.adapter.ParkingResult;
import AirPort.mapper.TbCarMapper;
import AirPort.mapper.TbCommonMapper;
import AirPort.mapper.TbVisitMapper;
import AirPort.model.TbCar;
import AirPort.model.TbCommon;
import AirPort.model.VisitCarForm;
import AirPort.model.VisitForm;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 주차 차단기 정기권 동기화 단위 테스트 — 방문 차량과 기관차량 두 경로.
 *
 * <p>아마노 정기권은 <b>(주차장, 차량번호) 하나에 종별 1개</b>다. 그래서 "차량구역을 여러 군데 고르면 여러 군데 등록"이 불가능하고, 구역이 빠지면 반드시 지워야
 * 한다 — 안 지우면 방문·차량이 없어져도 그 차는 계속 들어온다. 그 두 가지를 여기서 고정한다.
 */
class ParkingPassServiceTest {

  private final AmanoParkingAdapter parking = mock(AmanoParkingAdapter.class);
  private final TbVisitMapper visitMapper = mock(TbVisitMapper.class);
  private final TbCarMapper carMapper = mock(TbCarMapper.class);
  private final TbCommonMapper commonMapper = mock(TbCommonMapper.class);
  private final AuditService auditService = mock(AuditService.class);

  private final ParkingPassService service =
      new ParkingPassService(parking, visitMapper, carMapper, commonMapper, auditService);

  /** 현장과 같은 설정 — <b>단말기가 한 대라 차량구역2 에만 종별이 붙어 있다</b>. 나머지 구역은 주차 차단기와 무관하다. */
  @BeforeEach
  void enableParking() {
    when(parking.enabled()).thenReturn(true);
    when(parking.register(any())).thenReturn(ParkingResult.ok());
    when(parking.delete(anyString())).thenReturn(ParkingResult.ok());
    area("CAR01", null);
    area("CAR02", "02");
    area("CAR03", null);
  }

  /** 차량구역 공통코드 — code_tag 가 아마노 정기권 종별을 정한다(없으면 차단기 없는 구역). */
  private void area(String codeId, String tag) {
    TbCommon c = new TbCommon();
    c.setCodeId(codeId);
    c.setCodeTag(tag);
    when(commonMapper.selectOne("CAR", codeId)).thenReturn(c);
  }

  private static VisitForm form(List<String> areas, String... carNos) {
    VisitForm f = new VisitForm();
    f.setWorkEndDt("2026-08-20T18:00");
    f.setCarAcCodes(areas);
    f.setCars(
        java.util.Arrays.stream(carNos)
            .map(
                no -> {
                  VisitCarForm c = new VisitCarForm();
                  c.setCarNo(no);
                  c.setCarName("차량" + no);
                  return c;
                })
            .toList());
    return f;
  }

  private static TbCar car(String carNo) {
    TbCar c = new TbCar();
    c.setCarId(500);
    c.setCarNo(carNo);
    c.setCarName("기관차량");
    return c;
  }

  private ParkingPassRequest captureRegister() {
    ArgumentCaptor<ParkingPassRequest> cap = ArgumentCaptor.forClass(ParkingPassRequest.class);
    verify(parking).register(cap.capture());
    return cap.getValue();
  }

  // ── 방문 차량 ──────────────────────────────────────────────

  @Test
  void 차량구역의_code_tag_가_정기권_종별이_된다() {
    assertNull(service.syncVisit(7, form(List.of("CAR02"), "12가3456"), Set.of(), null, 101));

    ParkingPassRequest req = captureRegister();
    assertEquals("passType2", req.passType());
    assertEquals("12가3456", req.carNo());
    assertEquals("20260820", req.endDate()); // 방문 종료일 = 정기권 종료일
  }

  @Test
  void 종별이_없는_구역을_함께_골라도_차단기가_붙은_구역으로_등록한다() {
    // 구역을 정렬해 맨 앞을 집으면 CAR01(종별 없음)이 잡혀 등록이 통째로 빠진다.
    // 고른 것 중 '종별이 있는' 구역을 찾아야 한다.
    service.syncVisit(7, form(List.of("CAR01", "CAR02"), "12가3456"), Set.of(), null, 101);

    assertEquals("passType2", captureRegister().passType());
  }

  @Test
  void 차량번호의_공백은_지우고_보낸다() {
    // 아마노 규격이 "공백 없이 전체번호" 다 — 그대로 보내면 다른 차로 등록돼 차단기가 안 열린다
    service.syncVisit(7, form(List.of("CAR02"), " 12가 3456 "), Set.of(), null, 101);

    assertEquals("12가3456", captureRegister().carNo());
  }

  @Test
  void 방문에서_빠진_차량은_정기권을_지운다() {
    service.syncVisit(
        7, form(List.of("CAR02"), "12가3456"), Set.of("12가3456", "99나9999"), null, 101);

    verify(parking).delete("99나9999");
    verify(parking, never()).delete("12가3456"); // 남은 차량은 register 안에서 지웠다 등록한다
  }

  @Test
  void 차량구역을_모두_해제하면_남은_차량도_지운다() {
    // 구역 없음 = 주차 권한 없음. 이걸 빼면 방문은 남고 차단기만 계속 열린다.
    service.syncVisit(7, form(List.of(), "12가3456"), Set.of("12가3456"), null, 101);

    verify(parking).delete("12가3456");
    verify(parking, never()).register(any());
  }

  @Test
  void 종별이_둘_이상_겹치면_앞선_하나로만_등록한다() {
    // 아마노가 차량 1대에 종별 1개만 허용한다. 지금은 종별이 붙은 구역이 하나뿐이라 일어나지 않지만,
    // 단말기가 늘면 여기로 온다. 조합 종별 신설 여부는 미결(docs/integration.md) — 정해지면 이 테스트가 바뀐다.
    area("CAR01", "01");

    service.syncVisit(7, form(List.of("CAR02", "CAR01"), "12가3456"), Set.of(), null, 101);

    assertEquals("passType1", captureRegister().passType());
  }

  @Test
  void 종료일이_없으면_등록하지_않고_알린다() {
    // 언제까지 열어 줄지 모르는 채로 무기한 개방하지 않는다
    VisitForm f = form(List.of("CAR02"), "12가3456");
    f.setWorkEndDt(null);

    String warn = service.syncVisit(7, f, Set.of(), null, 101);

    verify(parking, never()).register(any());
    assertNotNull(warn);
    assertTrue(warn.contains("종료일"), warn);
  }

  @Test
  void 차단기가_없는_구역만_고르면_등록하지_않는다() {
    // CAR01·CAR03 은 종별이 없다 = 주차와 무관한 구역
    assertNull(
        service.syncVisit(7, form(List.of("CAR01", "CAR03"), "12가3456"), Set.of(), null, 101));
    verify(parking, never()).register(any());
  }

  @Test
  void 실패는_감사에_남기고_경고로_돌려준다() {
    when(parking.register(any())).thenReturn(ParkingResult.fail("주차관제 서버에 연결할 수 없습니다."));

    String warn = service.syncVisit(7, form(List.of("CAR02"), "12가3456"), Set.of(), null, 101);

    assertTrue(warn.contains("12가3456"), warn);
    verify(auditService).logAlways(any(), anyString(), anyInt(), anyString());
  }

  @Test
  void 연동이_꺼져_있으면_아무것도_하지_않는다() {
    when(parking.enabled()).thenReturn(false);

    assertNull(service.syncVisit(7, form(List.of("CAR02"), "12가3456"), Set.of(), null, 101));
    assertNull(service.syncCar(car("12가3456"), List.of("CAR02"), null, null, 101));
    assertTrue(service.visitCarNos(7).isEmpty());
    verifyNoInteractions(visitMapper, carMapper, commonMapper);
  }

  @Test
  void 방문_정리_시_차량의_정기권을_회수한다() {
    service.removeAll("방문 7", Set.of("12가3456", "99나9999"), null, 101);

    verify(parking).delete("12가3456");
    verify(parking).delete("99나9999");
  }

  // ── 기관차량 ──────────────────────────────────────────────

  @Test
  void 기관차량은_종료일을_2037년으로_길게_잡는다() {
    // 상주 차량이라 끝나는 날이 없다 — 방문처럼 짧게 잡으면 그날로 차단기가 닫힌다
    assertNull(service.syncCar(car("34나7890"), List.of("CAR02"), null, null, 101));

    ParkingPassRequest req = captureRegister();
    assertEquals("20371231", req.endDate());
    assertEquals("passType2", req.passType());
    assertEquals("34나7890", req.carNo());
  }

  @Test
  void 기관차량_번호를_고치면_옛_번호를_회수한다() {
    // 안 지우면 예전 번호가 아마노에 남아 그 차가 계속 들어온다
    service.syncCar(car("34나7890"), List.of("CAR02"), "11가1111", null, 101);

    verify(parking).delete("11가1111");
    assertEquals("34나7890", captureRegister().carNo());
  }

  @Test
  void 기관차량_구역을_모두_빼면_정기권을_지운다() {
    service.syncCar(car("34나7890"), List.of(), "34나7890", null, 101);

    verify(parking).delete("34나7890");
    verify(parking, never()).register(any());
  }

  @Test
  void 기관차량_번호가_그대로면_옛_번호를_따로_지우지_않는다() {
    // register 가 내부에서 지웠다 등록한다 — 여기서 또 지우면 호출만 늘어난다
    service.syncCar(car("34나7890"), List.of("CAR02"), " 34나 7890 ", null, 101);

    verify(parking, never()).delete(anyString());
  }
}
