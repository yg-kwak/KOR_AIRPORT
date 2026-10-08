package AirPort.adapter.biostar;

/**
 * BiostarX 출입문(door) 요약 — 그래픽맵에 놓는 단위.
 *
 * <p>{@code entryDeviceId} 는 그 문의 입구 단말기다 — 인증 이벤트는 단말기 ID 로 오므로, 어느 문에서 인증했는지는 이 값으로 잇는다.
 */
public record BiostarDoor(long id, String name, String entryDeviceId, String entryDeviceName) {}
