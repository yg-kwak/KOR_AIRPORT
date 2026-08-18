package AirPort.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * BiostarX 가져오기 요청 — 화면에서 고른 사용자와 함께 가져올 항목.
 *
 * <p>{@code userIds} 는 <b>필수</b>다. 예전에는 대상 전체를 한 번에 끌어왔지만, 지금은 이미 가져온 인원까지 장비 기준으로 덮어쓰므로 무엇이 바뀌는지
 * 모르고 전체를 돌리면 되돌릴 수 없다. 고른 사람만 건드린다.
 */
@Data
public class ImportForm {

  private List<String> userIds = new ArrayList<>();
  private boolean cards;
  private boolean face;
  private boolean acGroups;
}
