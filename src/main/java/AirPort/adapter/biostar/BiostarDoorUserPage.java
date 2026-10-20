package AirPort.adapter.biostar;

import java.util.List;

/** 출입문에 추가할 수 있는 사용자 한 쪽 — {@code POST /api/doors/accessible_users} (전체 건수 + 이번 쪽). */
public record BiostarDoorUserPage(long total, List<BiostarDoorUser> rows) {}
