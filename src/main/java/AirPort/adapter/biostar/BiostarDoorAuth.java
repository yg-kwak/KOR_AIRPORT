package AirPort.adapter.biostar;

import java.util.List;

/**
 * 출입문 한 개의 에스코트 설정 — {@code GET /api/doors/{id}} 의 {@code Door.auth_door_control}.
 *
 * <p>사용자를 바꿀 때 {@code PUT} 은 사용자 목록을 <b>통째로</b> 바꾸므로, 지금 목록({@code users})과 일정({@code scheduleId})을
 * 함께 들고 있어야 한다.
 *
 * @param enabled 에스코트가 켜져 있는가({@code "true"}) — 꺼져 있으면 사용자를 넣지 않는다
 * @param scheduleId 지금 걸린 일정(예: 1=Always). 없으면 null
 */
public record BiostarDoorAuth(
    long doorId,
    String doorName,
    boolean enabled,
    String scheduleId,
    String scheduleName,
    List<BiostarDoorUser> users) {}
