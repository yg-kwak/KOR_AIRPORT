package AirPort.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** 엑셀 일괄 등록 결과 — 성공 건수 + 행별 실패 사유. 화면에 요약해 보여준다. */
@Getter
public class ExcelImportResult {

  private int success;
  private final List<String> errors = new ArrayList<>();

  public void addSuccess() {
    success++;
  }

  /** 실패 행 기록 — 엑셀 행번호(2부터: 헤더 제외)와 사유. */
  public void addError(int excelRow, String reason) {
    errors.add(excelRow + "행: " + reason);
  }

  public int getFail() {
    return errors.size();
  }
}
