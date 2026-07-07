---
description: 화면 1개의 수직 슬라이스(프론트+백엔드)를 관례대로 스캐폴딩한다
argument-hint: <화면명 예: visitor-register> [--read-only]
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(*gradlew*)
---

## 목표
새 화면 하나를 **프론트(shadcn) + 백엔드(Controller→Service→Mapper)** 수직 슬라이스로 만든다.
스캐폴딩만 하고 도메인 로직은 최소로 둔다. 관례는 문서가 진실 원천이다.

## 먼저 읽을 문서 (해당하는 것만)
- 화면/컴포넌트 관례: `docs/frontend.md`
- 백엔드 계층/패키지 관례: `docs/backend.md`
- SQL/mapper 관례: `docs/database.md`
- 도어/디바이스가 얽히면: `docs/biostar-integration.md`

## 절차
1. **화면명**을 인자에서 받는다. 없으면 사용자에게 묻는다. (kebab-case, 예: `visitor-register`)
2. 기존 유사 화면 1개를 찾아 **패턴을 그대로 따른다** (새 패턴을 발명하지 않는다).
3. **프론트**: `web/` 아래 shadcn/ui 컴포넌트로 화면·라우트·API 호출부를 생성.
4. **백엔드**: 대응 `Controller` → `Service` → mapper 인터페이스 + mapper XML 스텁 생성.
   - Controller 는 요청/응답 매핑만. 로직은 Service. SQL 은 mapper XML.
5. **감사 대상 여부 확인**: 화면이 권한 부여·회수·도어 제어를 다루면 이력 기록을 포함한다. (`docs/security.md`)
6. `--read-only` 면 조회 전용으로만 스캐폴딩(쓰기 엔드포인트 생략).
7. 컴파일 확인: `project/gradlew.bat compileJava`.
8. 생성한 파일 목록과 다음 할 일(TODO)을 요약한다.

## 불변식 (AGENTS.md §4)
- 계층 단방향, MyBatis 규약, JPA 금지, BiostarX 는 어댑터로만.

$ARGUMENTS
