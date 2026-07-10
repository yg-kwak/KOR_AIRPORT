---
description: 화면 1개의 수직 슬라이스(Thymeleaf + 백엔드)를 관례대로 스캐폴딩한다
argument-hint: <화면명 예: visitor-register> [--read-only]
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(*gradlew*)
---

## 목표
새 화면 하나를 **Thymeleaf 화면 + 백엔드(Controller→Service→Mapper)** 수직 슬라이스로 만든다.
스캐폴딩만 하고 도메인 로직은 최소로 둔다. 관례는 문서가 진실 원천이다.

## 먼저 읽을 문서 (해당하는 것만)
- **명명 규칙·전개 흐름(필독)**: `docs/conventions.md` — HTML id/class, JS 함수/흐름, Controller/Service/Mapper 명명, 표준 CRUD 순서(§7)
- 화면 구조/파일 위치: `docs/frontend.md`
- 화면 시각 규칙(색·타이포·컴포넌트): `docs/design.md`
- 백엔드 계층/패키지 관례: `docs/architecture.md`, `docs/backend.md`
- SQL/mapper/테이블: `docs/database.md`
- 외부(BiostarX 등)가 얽히면: `docs/integration.md`

## 절차
1. **화면명**을 인자에서 받는다. 없으면 사용자에게 묻는다. (kebab-case, 예: `visitor-register`)
2. 기존 유사 화면 1개를 찾아 **패턴을 그대로 따른다** (새 패턴을 발명하지 않는다).
3. **화면(Thymeleaf)** — 대상 영역(web/kiosk) 확인 후:
   - 템플릿: `templates/web/{도메인}/{화면}.html` (kiosk 는 `templates/kiosk/...`) — `fragments/`(head/main/sidebar)를 `th:replace` 로 조합. 탭/iframe 금지.
   - 스크립트: `static/js/web/{도메인}/{화면}.js` (템플릿과 미러 경로).
   - 모달/팝업은 새로 만들지 말고 `components/` 공통 조각을 재사용.
   - 공통 CSS/컴포넌트(토큰·버튼·테이블·모달)를 재사용. 시각 규칙은 `docs/design.md`.
4. **백엔드**: 역할별 평면 패키지에 `{도메인}Controller` → `{도메인}Service` → `Tb{Table}Mapper` + mapper XML 스텁 생성.
   - Controller 는 요청/응답 매핑만. 로직은 Service. SQL 은 mapper XML(`#{}`).
   - 도메인 구분은 클래스명 접두사로. (`docs/conventions.md`)
5. **암호화/감사 확인**:
   - 개인정보(성명/비밀번호 등) 저장 시 ARIA 암호화. (`docs/security.md`)
   - 조회/입력/수정/삭제 경로면 `tb_system_log` 감사 기록 포함.
6. `--read-only` 면 조회 전용으로만 스캐폴딩(쓰기 엔드포인트 생략).
7. 컴파일 확인: `gradlew.bat compileJava`.
8. 생성한 파일 목록과 다음 할 일(TODO)을 요약한다.

## 불변식 (AGENTS.md §4)
- 계층 단방향, MyBatis 규약, JPA 금지, 외부 연동은 `adapter` 로만, 개인정보 암호화, 감사 기록.

$ARGUMENTS
