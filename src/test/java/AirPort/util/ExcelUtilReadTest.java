package AirPort.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * 엑셀 읽기 — 엑셀이 '날짜'로 알아본 칸은 표시 형식이 아니라 값으로 읽는다.
 *
 * <p>사용자가 1990-12-31 이라고 치면 엑셀은 숫자(일련번호)+날짜 서식으로 저장하고, DataFormatter 는 그 서식대로(12/31/90 · 1990년 12월
 * 31일 …) 문자열을 만든다. 그대로 넘기면 "생년월일은 YYYY-MM-DD 형식으로" 로 거절된다 — 사용자는 분명히 그 형식으로 적었는데.
 */
class ExcelUtilReadTest {

  @Test
  void 날짜_칸은_서식과_무관하게_YYYY_MM_DD_로_읽는다() throws Exception {
    String[] formats = {"m/d/yy", "yyyy\"년\" m\"월\" d\"일\"", "yyyy-mm-dd hh:mm"};
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Workbook wb = new XSSFWorkbook()) {
      Sheet sh = wb.createSheet();
      sh.createRow(0).createCell(0).setCellValue("생년월일");
      Row r = sh.createRow(1);
      for (int c = 0; c < formats.length; c++) {
        CellStyle st = wb.createCellStyle();
        st.setDataFormat(wb.createDataFormat().getFormat(formats[c]));
        r.createCell(c).setCellStyle(st);
      }
      r.getCell(0).setCellValue(java.time.LocalDate.of(1990, 12, 31));
      r.getCell(1).setCellValue(java.time.LocalDate.of(1990, 12, 31));
      r.getCell(2).setCellValue(java.time.LocalDateTime.of(2026, 1, 1, 9, 0));
      r.createCell(3).setCellValue(1002.0); // 숫자로 인식된 코드값
      wb.write(out);
    }
    List<String[]> rows = ExcelUtil.read(new ByteArrayInputStream(out.toByteArray()), 4);

    assertEquals("1990-12-31", rows.get(0)[0], "m/d/yy 서식");
    assertEquals("1990-12-31", rows.get(0)[1], "한국식 서식");
    assertEquals("2026-01-01T09:00", rows.get(0)[2], "시각이 있으면 출입시작일 형식");
    assertEquals("1002", rows.get(0)[3], "코드값 소수점 꼬리 제거는 그대로");
  }
}
