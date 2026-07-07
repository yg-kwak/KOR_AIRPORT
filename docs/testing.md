# 테스트

> 테스트 작성·실행 시 읽는다. 커밋 전 최소 검증은 `/commit` 이 수행.

## 실행
- 전체: `project/gradlew.bat test`.
- 단위 범위: 변경한 도메인의 테스트만 선택 실행.

## 전략
- **Service** 로직: 단위 테스트 우선.
- **Mapper**: 실제/임베디드 DB 대상 통합 테스트. TODO: 테스트 DB 방식(H2/Testcontainers/전용 스키마).
- **BiostarX 어댑터**: 외부는 목/스텁으로 격리. 실장비 테스트는 별도 표시.
- 감사 이력이 남는지 검증하는 테스트를 민감 경로에 둔다.

## 관례
- given-when-then 구조. 테스트명은 한글 또는 서술형 허용.
- TODO: 커버리지 목표/도구.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [biostar-integration.md](biostar-integration.md)
