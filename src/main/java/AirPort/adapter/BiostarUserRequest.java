package AirPort.adapter;

import java.util.List;

/**
 * BiostarX 사용자 생성 요청 값 — {@code POST /api/users} payload 구성용.
 *
 * <p>{@code disabled} 는 tb_common(PS).code_tag, {@code userGroupId} 는 tb_company.biostar_group_id,
 * {@code accessGroupIds} 는 tb_ac_group.biostar_ac_id 목록이다. 일시는 BiostarX 형식(예: 2001-01-01T00:00:00.00Z).
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
    List<BiostarUserCard> cards) {}
