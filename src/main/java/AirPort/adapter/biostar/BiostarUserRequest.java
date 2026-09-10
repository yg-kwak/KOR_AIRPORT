package AirPort.adapter.biostar;

import java.util.List;

/**
 * BiostarX 사용자 생성 요청 값 — {@code POST /api/users} payload 구성용.
 *
 * <p>{@code disabled} 는 tb_common(PS).code_tag, {@code userGroupId} 는 tb_company.biostar_group_id,
 * {@code accessGroupIds} 는 tb_ac_group.biostar_ac_id 목록이다. 일시는 BiostarX 형식(예:
 * 2001-01-01T00:00:00.00Z).
 *
 * <p>{@code department} 는 <b>허가구역 번호</b>다(예: 인원구역 1·2·4 → "124"). 장비 화면과 이벤트 목록에서 그 사람이 어디를 다니는
 * 사람인지 한눈에 보이라고 넣는다 — 표기 규칙은 실시간 이벤트의 [허가 구역]과 같다({@link AirPort.common.AccessAreas}).
 *
 * <p>{@code operationMode} 는 개인 인증 모드(private_operation_modes)다. <b>null 이면 보내지 않는다</b> — 장비/사용자그룹
 * 기본 설정을 그대로 쓴다는 뜻이다. 정규인원은 얼굴+카드라 장비 기본을 따르고, 방문객(임시·장기·상주·순찰·대여)만 카드 전용({@link
 * BiostarUserAdapter#OPERATION_MODE_CARD_ONLY})을 지정한다.
 */
public record BiostarUserRequest(
    String userId,
    String name,
    String phone,
    String photo,
    Integer userGroupId,
    String disabled,
    String startDatetime,
    String expiryDatetime,
    String userTitle,
    List<Integer> accessGroupIds,
    String faceImage,
    String faceTemplate9,
    String faceTemplate5,
    List<BiostarUserCard> cards,
    Integer operationMode,
    String department) {}
