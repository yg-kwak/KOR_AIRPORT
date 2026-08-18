package AirPort.adapter.biostar;

/** BiostarX 사용자 그룹(user group) 요약 — id/name/parentId. 본 시스템에서는 '기관'으로 표현한다. */
public record BiostarUserGroup(long id, String name, Long parentId) {}
