package AirPort.adapter.biostar;

import java.util.List;

/**
 * 출입문에 추가할 수 있는 사용자 한 쪽 — {@code POST /api/doors/accessible_users}.
 *
 * @param total 전체 건수. 모르면 -1 — 사용자 그룹으로 거르면 장비가 건수를 주지 않는다
 * @param more 다음 쪽이 있는가
 */
public record BiostarDoorUserPage(long total, boolean more, List<BiostarDoorUser> rows) {}
