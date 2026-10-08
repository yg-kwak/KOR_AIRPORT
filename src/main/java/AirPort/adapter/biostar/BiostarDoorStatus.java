package AirPort.adapter.biostar;

/**
 * BiostarX 출입문 현재 상태 — {@code POST /api/doors/status} 한 줄.
 *
 * @param unlocked 개방(릴레이 켜짐) — 응답 {@code unlocked} "1". "0" 이면 잠금
 * @param opened 문짝이 실제로 열려 있는가(도어 센서) — 응답 {@code opened}
 */
public record BiostarDoorStatus(long doorId, boolean unlocked, boolean opened) {}
