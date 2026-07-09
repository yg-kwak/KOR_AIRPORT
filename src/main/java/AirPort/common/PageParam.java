package AirPort.common;

/** 목록 검색·페이징 공통 파라미터. MSSQL OFFSET/FETCH 용 offset 계산 제공. */
public class PageParam {

  private int page = 1; // 1-base
  private int size = 20;
  private String keyword;

  public int getOffset() {
    return (Math.max(page, 1) - 1) * size;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
