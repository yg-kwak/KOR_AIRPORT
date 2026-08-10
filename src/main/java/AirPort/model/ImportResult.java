package AirPort.model;

import java.util.ArrayList;
import java.util.List;

/** BiostarX 정규인원 가져오기 결과 — 미리보기와 실행이 같은 형태를 쓴다(무엇이 들어오고 무엇이 빠지는지). */
public class ImportResult {

  private boolean preview; // 미리보기면 DB 를 건드리지 않았다는 뜻
  private int total; // 대상 그룹(정규등록 아래)에 속한 인원 수 — 다른 그룹은 세지 않는다
  private int target; // 그중 선별을 통과해 가져올 수 있는 인원
  private int imported; // 실제로 넣은 인원
  private int skipped; // 건너뛴 인원
  private int cards; // 배정한 카드 수
  private int faces; // 가져온 얼굴 수
  private int acGroups; // 연결한 출입권한 수

  /** 건너뛴 사유 — 화면에 그대로 보여 준다. 왜 안 들어왔는지 모르면 손쓸 수 없다. */
  private List<String> skippedReasons = new ArrayList<>();

  public boolean isPreview() {
    return preview;
  }

  public void setPreview(boolean preview) {
    this.preview = preview;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getTarget() {
    return target;
  }

  public void setTarget(int target) {
    this.target = target;
  }

  public int getImported() {
    return imported;
  }

  public void setImported(int imported) {
    this.imported = imported;
  }

  public int getSkipped() {
    return skipped;
  }

  public void setSkipped(int skipped) {
    this.skipped = skipped;
  }

  public int getCards() {
    return cards;
  }

  public void setCards(int cards) {
    this.cards = cards;
  }

  public int getFaces() {
    return faces;
  }

  public void setFaces(int faces) {
    this.faces = faces;
  }

  public int getAcGroups() {
    return acGroups;
  }

  public void setAcGroups(int acGroups) {
    this.acGroups = acGroups;
  }

  public List<String> getSkippedReasons() {
    return skippedReasons;
  }

  public void setSkippedReasons(List<String> skippedReasons) {
    this.skippedReasons = skippedReasons;
  }
}
