# 테스트

> 테스트 작성·실행 시 읽는다. 커밋 전 최소 검증은 `/commit` 이 수행.

## 실행
- 전체: `gradlew.bat test`.
- 단위 범위: 변경한 도메인의 테스트만 선택 실행.

## 전략
- **Service** 로직: 단위 테스트 우선. (`@Service` 구체 클래스이므로 협력 객체는 목으로 주입)
- **Mapper**: MSSQL 대상 통합 테스트. 단일 벤더이므로 벤더별 이중화 검증은 불필요. TODO: 테스트 DB 방식(전용 스키마/로컬 MSSQL).
- **adapter(BiostarX)**: 외부는 목/스텁으로 격리. 실장비 테스트는 별도 표시.
- **암호화(ARIA)**: `ariaEncrypt`↔`ariaDecrypt` 왕복, 암호문이 평문 미노출인지 검증. (`security.md`)
- **감사**: 민감 경로(입력/수정/삭제/조회 진입)에서 `tb_system_log` 적재를 검증. (`security.md`)
- **계층 경계**: ArchUnit 구조 테스트로 controller→service→mapper 단방향 강제(프로젝트 생성 후). (`conventions.md`)

## 관례
- given-when-then 구조. 테스트명은 한글 또는 서술형 허용.
- TODO: 커버리지 목표/도구.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [integration.md](integration.md) · [security.md](security.md)
