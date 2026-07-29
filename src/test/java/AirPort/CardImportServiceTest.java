package AirPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import AirPort.common.exception.BusinessException;
import AirPort.common.exception.ErrorCode;
import AirPort.model.ExcelImportResult;
import AirPort.model.TbCard;
import AirPort.service.AuditService;
import AirPort.service.CardImportService;
import AirPort.service.CardService;
import AirPort.service.MenuAuthService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * 카드 엑셀 일괄등록 로직 단위 테스트 — DB/Spring 없이 CardService 를 mock 으로 대체.
 *
 * <p>검증: 예시행(EXAMPLE_ROW) 건너뜀, 행별 성공/실패 집계, 실패 사유·행번호 기록, 빈/손상 파일 처리.
 */
class CardImportServiceTest {

  /** 헤더 + 주어진 데이터 행들로 xlsx 바이트를 만든다(ExcelUtil.read 가 읽는 형식과 동일). */
  private static InputStream xlsx(String[]... rows) throws Exception {
    try (XSSFWorkbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("Sheet1");
      Row head = sheet.createRow(0);
      for (int i = 0; i < CardImportService.IMPORT_HEADERS.length; i++) {
        head.createCell(i).setCellValue(CardImportService.IMPORT_HEADERS[i]);
      }
      int r = 1;
      for (String[] row : rows) {
        Row rr = sheet.createRow(r++);
        for (int i = 0; i < row.length; i++) {
          rr.createCell(i).setCellValue(row[i]);
        }
      }
      wb.write(out);
      return new ByteArrayInputStream(out.toByteArray());
    }
  }

  private static CardImportService service(CardService cardService) {
    MenuAuthService menuAuth = mock(MenuAuthService.class);
    AuditService audit = mock(AuditService.class);
    return new CardImportService(cardService, menuAuth, audit);
  }

  @Test
  void 예시행은_건너뛰고_성공실패를_집계한다() throws Exception {
    CardService cardService = mock(CardService.class);
    // 카드번호 'DUP001' 행만 중복 예외 — 나머지는 성공 처리
    doAnswer(
            inv -> {
              TbCard c = inv.getArgument(0);
              if ("DUP001".equals(c.getBiostarCardValue())) {
                throw new BusinessException(ErrorCode.DUPLICATE, "이미 등록된 카드번호입니다.");
              }
              return null;
            })
        .when(cardService)
        .createCard(any(), any(), any());

    InputStream in =
        xlsx(
            CardImportService.EXAMPLE_ROW, // 예시행 — 건너뛰어야 함
            new String[] {"OK001", "CDT02", "", "정상카드", "CS01", "신규", ""}, // 성공
            new String[] {"DUP001", "CDT02", "", "중복카드", "CS01", "", ""}); // 실패(중복)

    ExcelImportResult result = service(cardService).importExcel(in, null, 801);

    assertEquals(1, result.getSuccess(), "성공 1건(성공 행)");
    assertEquals(1, result.getFail(), "실패 1건(중복 행)");
    assertTrue(result.getErrors().get(0).contains("이미 등록된 카드번호입니다."));
    assertTrue(result.getErrors().get(0).startsWith("4행"), "예시행 건너뛰어도 엑셀 행번호는 유지(4행)");
    // 예시행은 createCard 로 넘어가지 않는다 → 성공·실패 2건만 호출
    verify(cardService, times(2)).createCard(any(), any(), any());
  }

  @Test
  void 데이터가_예시행뿐이면_모두_건너뛰어_0건이다() throws Exception {
    CardService cardService = mock(CardService.class);
    ExcelImportResult result =
        service(cardService).importExcel(xlsx(CardImportService.EXAMPLE_ROW), null, 801);
    assertEquals(0, result.getSuccess());
    assertEquals(0, result.getFail());
    verify(cardService, times(0)).createCard(any(), any(), any());
  }

  @Test
  void 헤더만_있으면_등록할_데이터_없음_예외() throws Exception {
    CardService cardService = mock(CardService.class);
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service(cardService).importExcel(xlsx(), null, 801));
    assertTrue(ex.getMessage().contains("등록할 데이터가 없습니다"));
  }

  @Test
  void 엑셀이_아니면_읽을_수_없음_예외() {
    CardService cardService = mock(CardService.class);
    InputStream garbage = new ByteArrayInputStream("not an excel".getBytes());
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service(cardService).importExcel(garbage, null, 801));
    assertTrue(ex.getMessage().contains("엑셀을 읽을 수 없습니다"));
  }

  @Test
  void 권한검사를_먼저_호출한다() throws Exception {
    CardService cardService = mock(CardService.class);
    MenuAuthService menuAuth = mock(MenuAuthService.class);
    AuditService audit = mock(AuditService.class);
    CardImportService svc = new CardImportService(cardService, menuAuth, audit);
    svc.importExcel(xlsx(new String[] {"OK001", "CDT02", "", "정상", "CS01", "", ""}), null, 801);
    verify(menuAuth).requireCreate(any(), eq(801));
    verify(audit).log(any(), any(), eq(801), any()); // 완료 요약 감사
  }

  // 참고: searchType 분기·ARIA 완전일치 검색은 MSSQL 전용 SQL(EXISTS/OFFSET-FETCH/'%'+#{})이라
  // 매퍼 단위테스트가 어렵다 — 통합(실 DB) 검증으로 커버한다. keyword→keywordEnc 서비스 로직은 별도 테스트 참조.
  @SuppressWarnings("unused")
  private static final List<String> INTEGRATION_ONLY = List.of("searchType SQL branching");
}
