package AirPort.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BiostarX 정규인원 가져오기 결과 — 미리보기와 실행이 같은 형태를 쓴다(무엇이 들어오고 무엇이 빠지는지). */
public class ImportResult {

  private boolean preview; // 미리보기면 DB 를 건드리지 않았다는 뜻
  private int total; // 대상 그룹(정규등록 아래)에 속한 인원 수 — 다른 그룹은 세지 않는다
  private int target; // 그중 선별을 통과해 가져올 수 있는 인원
  private int imported; // 새로 넣은 인원
  private int updated; // 이미 있어 장비 기준으로 갱신한 인원
  private int unchanged; // 장비와 이미 같아 손대지 않은 인원
  private int skipped; // 건너뛴 인원
  private int cards; // 배정한 카드 수
  private int faces; // 가져온 얼굴 수
  private int facesRemoved; // 장비에 없어 지운 얼굴 수
  private int acGroups; // 연결한 출입권한 수

  /**
   * 사람별 상세 — {@code 사용자ID → 바뀔(바뀐) 내용 또는 건너뛴 사유}. <b>목록의 비고 열</b>에 붙는다.
   *
   * <p>이 가져오기는 카드·출입권한을 <b>Biostar X 기준으로 덮어쓴다</b>. 건수만 보여 주면 무엇이 사라지는지 모른 채 실행하게 된다. 그렇다고 결과 상자에
   * 사람을 나열하면 수천 명일 때 읽을 수 없으므로, 숫자는 결과 상자에 두고 <b>사람별 내용은 그 사람 행에</b> 붙인다.
   */
  private Map<String, String> details = new LinkedHashMap<>();

  /**
   * 사람별 분류 — 화면이 이 목록으로 <b>대상자를 찾는다</b>(신규만/갱신만 걸러 보기).
   *
   * <p>건수만으로는 "갱신 3명" 이 누구인지 알 수 없다. 미리보기의 목적은 실행 전에 그 사람들을 짚어 보는 것이다.
   */
  private List<String> newUserIds = new ArrayList<>();

  private List<String> updatedUserIds = new ArrayList<>();

  private List<String> unchangedUserIds = new ArrayList<>();

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

  public int getUpdated() {
    return updated;
  }

  public void setUpdated(int updated) {
    this.updated = updated;
  }

  public int getUnchanged() {
    return unchanged;
  }

  public void setUnchanged(int unchanged) {
    this.unchanged = unchanged;
  }

  public int getFacesRemoved() {
    return facesRemoved;
  }

  public void setFacesRemoved(int facesRemoved) {
    this.facesRemoved = facesRemoved;
  }

  public Map<String, String> getDetails() {
    return details;
  }

  public void setDetails(Map<String, String> details) {
    this.details = details;
  }

  public List<String> getNewUserIds() {
    return newUserIds;
  }

  public void setNewUserIds(List<String> newUserIds) {
    this.newUserIds = newUserIds;
  }

  public List<String> getUpdatedUserIds() {
    return updatedUserIds;
  }

  public void setUpdatedUserIds(List<String> updatedUserIds) {
    this.updatedUserIds = updatedUserIds;
  }

  public List<String> getUnchangedUserIds() {
    return unchangedUserIds;
  }

  public void setUnchangedUserIds(List<String> unchangedUserIds) {
    this.unchangedUserIds = unchangedUserIds;
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
}
