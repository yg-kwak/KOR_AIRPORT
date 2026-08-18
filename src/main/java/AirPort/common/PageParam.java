package AirPort.common;

/**
 * 목록 검색·페이징·정렬 공통 파라미터. 모든 목록 화면이 재사용한다(골든 패턴).
 *
 * <p>정렬 컬럼(sort)은 SQL 인젝션 방지를 위해 mapper XML 의 화이트리스트(choose)로만 매핑한다.
 */
public class PageParam {

  /**
   * 한 페이지 최대 행 수. URL 로 임의의 값이 들어와도 이 위를 넘기지 않는다.
   *
   * <p>엑셀 내려받기는 {@code selectListAll}(OFFSET/FETCH 없음)로 전건을 뽑으므로 이 상한과 무관하다.
   */
  private static final int MAX_SIZE = 1000;

  private int page = 1; // 1-base
  private int size = 30;
  private String keyword;
  private String keywordEnc; // 암호화 컬럼(성명 등) 완전일치 검색용 — 서비스가 keyword 를 ARIA 암호화해 설정
  private String searchType = "all"; // 검색 조건(도메인별 화면에서 정의)
  private String sort; // 정렬 컬럼 키(화이트리스트)
  private String dir = "asc"; // asc | desc

  /**
   * 건너뛸 행 수. {@code page}·{@code size} 가 아무리 커도 int 를 넘기지 않게 long 으로 곱한 뒤 자른다 — 곱셈이 오버플로하면 음수
   * OFFSET 이 되어 SQL 이 터진다.
   */
  public int getOffset() {
    long offset = (long) (Math.max(page, 1) - 1) * getSize();
    return (int) Math.min(offset, Integer.MAX_VALUE);
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

  /**
   * 한 페이지 행 수 — <b>1 이상 {@value #MAX_SIZE} 이하로 보정</b>한다.
   *
   * <p>보정하지 않으면 {@code size=0}·음수가 그대로 {@code FETCH NEXT ? ROWS} 로 내려가 SQL 오류(10744)가 난다. 이 클래스는 모든
   * 목록 화면이 공유하므로 한 곳이 뚫리면 전 화면이 같이 500 이 된다.
   */
  public int getSize() {
    return Math.min(Math.max(size, 1), MAX_SIZE);
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

  public String getKeywordEnc() {
    return keywordEnc;
  }

  public void setKeywordEnc(String keywordEnc) {
    this.keywordEnc = keywordEnc;
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
