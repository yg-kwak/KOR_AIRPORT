package AirPort.adapter.biostar;

/**
 * 출입문 에스코트(auth_door_control) 사용자 한 명 — 이미 등록된 사람과 추가할 수 있는 사람이 같은 모양이다.
 *
 * @param groupName 사용자 그룹 이름 — 추가할 수 있는 사용자 조회에만 온다(등록된 목록에는 없다)
 */
public record BiostarDoorUser(String userId, String name, String groupName) {}
