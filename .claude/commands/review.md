---
description: 현재 변경을 AGENTS.md 불변식 기준으로 검토한다
argument-hint: [선택: 특정 파일/디렉터리]
allowed-tools: Bash(git*), Read, Grep, Glob
---

## 목표
현재 변경(또는 지정 대상)을 **AGENTS.md §4 불변식** 기준으로 검토하고, 위반과 개선점을 보고한다.
스타일 취향이 아니라 **경계 위반**에 집중한다 (불변식은 강제, 구현은 자유).

## 점검 체크리스트
1. **비밀값**: 하드코딩된 접속정보/토큰/키가 없는가. `.env` 값이 코드에 들어오지 않았는가. (`docs/security.md`)
2. **계층 경계**: `Controller → Service → Mapper` 단방향인가. Controller 에 SQL·비즈니스 로직이 새지 않았는가. (`docs/architecture.md`)
3. **MyBatis 규약**: SQL 이 mapper XML 에만 있는가. 문자열 연결 SQL 이 없는가. 파라미터가 `#{}` 인가 (`${}` 는 정당한 사유만). JPA(`@Entity`) 도입이 없는가. (`docs/database.md`)
4. **BiostarX 격리**: 외부 연동이 어댑터 계층으로만 나가는가. Service 가 SDK 를 직접 부르지 않는가. (`docs/biostar-integration.md`)
5. **감사/이력**: 권한 부여·회수, 도어 제어 경로에 이력 기록이 있는가. (`docs/security.md`)
6. **관례**: 네이밍·패키지 배치가 `docs/conventions.md` 와 일치하는가.
7. **테스트**: 변경 로직에 대한 테스트가 있는가/필요한가. (`docs/testing.md`)

## 출력 형식
- 🔴 **차단(불변식 위반)** — 반드시 고쳐야 함 (파일:라인, 이유, 수정 방향)
- 🟡 **권고** — 고치면 좋음
- 🟢 **양호** — 잘 지킨 점
- 각 항목은 `파일:라인` 으로 클릭 가능하게.

## 하지 말 것
- 코드를 직접 수정하지 않는다 (검토만). 수정은 사용자 승인 후 별도 진행.

$ARGUMENTS
