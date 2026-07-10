package AirPort.common;

/**
 * 목록 검색·페이징·정렬 공통 파라미터. 모든 목록 화면이 재사용한다(골든 패턴).
 *
 * <p>정렬 컬럼(sort)은 SQL 인젝션 방지를 위해 mapper XML 의 화이트리스트(choose)로만 매핑한다.
 */
public class PageParam {

  private int page = 1; // 1-base
  private int size = 30;
  private String keyword;
  private String searchType = "all"; // 검색 조건(도메인별 화면에서 정의)
  private String sort; // 정렬 컬럼 키(화이트리스트)
  private String dir = "asc"; // asc | desc

  public int getOffset() {
    return (Math.max(page, 1) - 1) * size;
  }

  public boolean isDesc() {
    return "desc".equalsIgnoreCase(dir);
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

  public String getSearchType() {
    return searchType;
  }

  public void setSearchType(String searchType) {
    this.searchType = searchType;
  }

  public String getSort() {
    return sort;
  }

  public void setSort(String sort) {
    this.sort = sort;
  }

  public String getDir() {
    return dir;
  }

  public void setDir(String dir) {
    this.dir = dir;
  }
}
