package AirPort.common;

import java.util.List;

/** 목록 응답(내용 + 총건수 + 페이지 정보). */
public class PageResult<T> {

  private List<T> content;
  private long total;
  private int page;
  private int size;

  public PageResult(List<T> content, long total, int page, int size) {
    this.content = content;
    this.total = total;
    this.page = page;
    this.size = size;
  }

  public List<T> getContent() {
    return content;
  }

  public long getTotal() {
    return total;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }

  public int getTotalPages() {
    return size == 0 ? 0 : (int) Math.ceil((double) total / size);
  }
}
