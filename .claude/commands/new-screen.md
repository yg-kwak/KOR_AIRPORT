---
description: 화면 1개의 수직 슬라이스(Thymeleaf + 백엔드)를 관례대로 스캐폴딩한다
argument-hint: <화면명 예: visitor-register> [--read-only]
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(*gradlew*)
---

## 목표
새 화면 하나를 **Thymeleaf 화면 + 백엔드(Controller→Service→Mapper)** 수직 슬라이스로 만든다.
스캐폴딩만 하고 도메인 로직은 최소로 둔다. 관례는 문서가 진실 원천이다.

## 먼저 읽을 문서 (해당하는 것만)
- 화면/템플릿 관례: `docs/frontend.md`
- 백엔드 계층/패키지 관례: `docs/architecture.md`, `docs/backend.md`
- SQL/mapper/테이블: `docs/database.md`
- 외부(BiostarX 등)가 얽히면: `docs/integration.md`

## 절차
1. **화면명**을 인자에서 받는다. 없으면 사용자에게 묻는다. (kebab-case, 예: `visitor-register`)
2. 기존 유사 화면 1개를 찾아 **패턴을 그대로 따른다** (새 패턴을 발명하지 않는다).
3. **화면(Thymeleaf)**:
   - 템플릿: `src/main/resources/templates/{도메인}/{화면}.html` — `fragments/` 공통 조각 재사용.
   - 스크립트: `src/main/resources/static/js/pages/{도메인}/{화면}.js`.
   - shadcn 룩을 참고한 공통 CSS/컴포넌트를 재사용(직접 재구현 금지).
4. **백엔드**: 역할별 평면 패키지에 `{도메인}Controller` → `{도메인}Service` → `Tb{Table}Mapper` + mapper XML 스텁 생성.
   - Controller 는 요청/응답 매핑만. 로직은 Service. SQL 은 mapper XML(`#{}`).
   - 도메인 구분은 클래스명 접두사로. (`docs/conventions.md`)
5. **암호화/감사 확인**:
   - 개인정보(성명/비밀번호 등) 저장 시 ARIA 암호화. (`docs/security.md`)
   - 조회/입력/수정/삭제 경로면 `tb_system_log` 감사 기록 포함.
6. `--read-only` 면 조회 전용으로만 스캐폴딩(쓰기 엔드포인트 생략).
7. 컴파일 확인: `project/gradlew.bat compileJava`.
8. 생성한 파일 목록과 다음 할 일(TODO)을 요약한다.

## 불변식 (AGENTS.md §4)
- 계층 단방향, MyBatis 규약, JPA 금지, 외부 연동은 `adapter` 로만, 개인정보 암호화, 감사 기록.

$ARGUMENTS
