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
import org.apache.poi.ss.usermodel.DateUtil;
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
          if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            if (looksLikeDate(cell)) {
              // 엑셀이 '날짜'로 알아본 칸 — 표시 형식(12/31/90, 1990년 12월 31일 …)이 아니라 값으로 읽는다.
              // 시각이 있으면 출입시작일처럼 "YYYY-MM-DDTHH:mm", 없으면 생년월일처럼 "YYYY-MM-DD"
              java.time.LocalDateTime dt = cell.getLocalDateTimeCellValue();
              v =
                  (dt.getHour() == 0 && dt.getMinute() == 0)
                      ? dt.toLocalDate().toString()
                      : dt.toLocalDate()
                          + "T"
                          + String.format("%02d:%02d", dt.getHour(), dt.getMinute());
            } else if (v.endsWith(".0")) {
              v = v.substring(0, v.length() - 2); // 숫자로 인식한 코드값의 소수점 꼬리(예: 1002.0) 제거
            }
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

  /**
   * 엑셀이 날짜로 다루는 칸인가 — POI 의 판정에 더해 서식 문자열을 직접 본다. 한국식 서식(yyyy"년" m"월" d"일")처럼 따옴표 글자가 섞인 서식은 POI 가
   * 날짜로 보지 못해 일련번호(33238)가 그대로 나온다.
   */
  private static boolean looksLikeDate(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return true;
    }
    String f = cell.getCellStyle() == null ? null : cell.getCellStyle().getDataFormatString();
    if (f == null) {
      return false;
    }
    // 따옴표 글자("년")와 대괄호 조건([$-412])을 걷어내고 y·m·d 가 남는지 본다
    String bare = f.replaceAll("\"[^\"]*\"", "").replaceAll("\\[[^\\]]*\\]", "").toLowerCase();
    return bare.contains("y") && (bare.contains("d") || bare.contains("m"));
  }
}
