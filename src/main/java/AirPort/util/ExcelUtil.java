package AirPort.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 목록 화면 → 엑셀(xlsx) 다운로드 공통 유틸. 헤더 + 문자열 행을 그대로 내려준다. */
public final class ExcelUtil {

  private ExcelUtil() {}

  public static void download(
      HttpServletResponse response, String filename, String[] headers, List<String[]> rows)
      throws IOException {
    try (Workbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      // 헤더: 볼드 + 옅은 회색 배경
      CellStyle headStyle = wb.createCellStyle();
      Font headFont = wb.createFont();
      headFont.setBold(true);
      headStyle.setFont(headFont);
      headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      Row head = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell c = head.createCell(i);
        c.setCellValue(headers[i]);
        c.setCellStyle(headStyle);
      }

      int r = 1;
      for (String[] row : rows) {
        Row rr = sheet.createRow(r++);
        for (int i = 0; i < row.length; i++) {
          rr.createCell(i).setCellValue(row[i] == null ? "" : row[i]);
        }
      }

      try {
        for (int i = 0; i < headers.length; i++) {
          sheet.autoSizeColumn(i);
          sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 255 * 256));
        }
      } catch (Exception ignore) {
        // headless 환경 등에서 폰트 계측 실패 시 기본 폭 유지
      }

      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
      response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
      wb.write(response.getOutputStream());
    }
  }

  /**
   * 업로드 xlsx 의 첫 시트를 문자열 행 목록으로 읽는다(헤더 1행 제외). 모든 셀은 표시 문자열로 통일(숫자·날짜 포함).
   *
   * @param colCount 읽을 열 개수(부족한 셀은 빈 문자열). 완전히 빈 행은 건너뛴다.
   */
  public static List<String[]> read(InputStream in, int colCount) throws IOException {
    List<String[]> rows = new ArrayList<>();
    DataFormatter fmt = new DataFormatter();
    try (Workbook wb = new XSSFWorkbook(in)) {
      Sheet sheet = wb.getSheetAt(0);
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        String[] vals = new String[colCount];
        boolean allBlank = true;
        for (int c = 0; c < colCount; c++) {
          Cell cell = row.getCell(c);
          String v = cell == null ? "" : fmt.formatCellValue(cell).trim();
          // 엑셀이 숫자로 인식한 코드값의 소수점 꼬리(예: 1002.0) 제거
          if (cell != null && cell.getCellType() == CellType.NUMERIC && v.endsWith(".0")) {
            v = v.substring(0, v.length() - 2);
          }
          vals[c] = v;
          if (!v.isEmpty()) {
            allBlank = false;
          }
        }
        if (!allBlank) {
          rows.add(vals);
        }
      }
    }
    return rows;
  }
}
