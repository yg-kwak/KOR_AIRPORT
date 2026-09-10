package AirPort.model;

import AirPort.common.PageResult;
import java.util.List;

/**
 * 방문 목록 응답 — 보통의 페이지에 <b>미반납 건수</b>를 얹는다. (임시인원등록·장기출입등록)
 *
 * <p>미반납은 "작업기간이 끝났는데 아직 입실 중" — <b>카드가 회수되지 않았다</b>는 뜻이다. 목록을 한 장씩 넘겨 세는 값이 아니라 지금 몇 건이 밀려 있는지를 늘
 * 보여 줘야 하는 값이라, 페이지가 아니라 <b>검색 조건 전체</b>에서 센다.
 *
 * <p>{@link PageResult} 를 늘리지 않고 상속한다 — 모든 목록이 공유하는 클래스에 이 화면만 쓰는 칸을 두면, 다른 화면에서는 영영 비어 있는 값이 응답에
 * 섞인다. 화면이 읽는 이름(content·total·totalPages)은 그대로다.
 */
public class VisitPageResult extends PageResult<TbVisit> {

  private final long unreturned;

  public VisitPageResult(List<TbVisit> content, long total, int page, int size, long unreturned) {
    super(content, total, page, size);
    this.unreturned = unreturned;
  }

  public long getUnreturned() {
    return unreturned;
  }
}
