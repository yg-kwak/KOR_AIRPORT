package AirPort.adapter;

import java.util.List;

/**
 * BiostarX 사용자 생성 요청 값 — {@code POST /api/users} payload 구성용.
 *
 * <p>{@code disabled} 는 tb_common(PS).code_tag, {@code userGroupId} 는 tb_company.biostar_group_id,
 * {@code accessGroupIds} 는 tb_ac_group.biostar_ac_id 목록이다. 일시는 BiostarX 형식(예:
 * 2001-01-01T00:00:00.00Z).
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
    Integer operationMode) {}
