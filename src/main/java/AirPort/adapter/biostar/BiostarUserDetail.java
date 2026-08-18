package AirPort.adapter.biostar;

import java.util.List;

/**
 * BiostarX 사용자 1명 — <b>읽어 온</b> 값. (정규인원 가져오기)
 *
 * <p>장비에 쓰지 않는다. 우리 DB 를 장비에 맞추는 단방향 이관에만 쓴다 — 반대로 쓰면 현장에 이미 올라간 얼굴·카드·출입그룹을 덮어쓴다.
 *
 * @param userId 장비 사용자ID(우리 인원ID 로 그대로 쓴다)
 * @param name 성명 — 저장할 때 ARIA 로 암호화한다
 * @param phone 연락처(없을 수 있다)
 * @param userTitle 직위 — 우리 직위코드(UT)와 이름으로 맞춘다
 * @param userGroupId 사용자그룹 ID — 우리 기관(tb_company.biostar_group_id)과 맞춘다
 * @param startDatetime 출입 시작(ISO)
 * @param expiryDatetime 출입 종료(ISO)
 * @param photo 얼굴 사진(BASE64, 없으면 null)
 * @param cardNos 카드번호 목록(display_card_id)
 * @param accessGroupIds 장비 출입그룹 ID 목록
 */
public record BiostarUserDetail(
    String userId,
    String name,
    String phone,
    String userTitle,
    Integer userGroupId,
    String startDatetime,
    String expiryDatetime,
    String photo,
    List<String> cardNos,
    List<Integer> accessGroupIds) {}
