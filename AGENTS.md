# CJAirPort — 에이전트 지도 (AGENTS.md)

> 이 문서는 **지도**입니다. 여기서 모든 것을 설명하지 않습니다.
> 무엇을 어디서 찾는지, 무엇을 절대 어기면 안 되는지만 담습니다.
> 상세 구현 지식은 `docs/` 로, 반복 작업은 `.claude/commands/` (스킬)로 위임합니다.
> — OpenAI Harness Engineering: "지도를 줘라 / 불변식은 강제, 구현은 자유"

## 1. 이 프로젝트가 하는 일 (One-liner)

공항 **출입관리 · 방문자 통제 시스템**. 정규인원, 임시·장기·상주 방문자 등록·발급·회수,
출입통제 플랫폼 **Suprema BiostarX** 와 연동해 사용자, 카드, 얼굴, 사용자 그룹, 출입 그룹 등을 연동하고 이력을 관리한다.

## 2. 기술 스택 (요약)

| 영역 | 내용 |
|------|------|
| 언어/런타임 | Java 17 |
| 빌드 | Gradle 8.13 (wrapper: `gradlew.bat`, 저장소 루트) |
| 백엔드 | Spring Boot 3.2.0 |
| eGovFrame | 4.3.0 의존성 **포함하되 코드에서 미사용** (idgnr, property, cmmn, mvc, dataaccess) |
| 퍼시스턴스 | MyBatis (JPA **미사용**) |
| DB | **MSSQL 단일** |
| 화면 | **Thymeleaf 서버사이드 렌더링** + static (Pretendard, 로컬 자산) |
| 외부 연동 | Suprema BiostarX API |

## 3. 저장소 지도 (어디에 무엇이 있나)

```
build.gradle·settings.gradle·gradlew(.bat)   Spring Boot 백엔드 빌드 (저장소 루트, JDK 17+)
src/main/java/AirPort/   controller / service / mapper / model / adapter / config / security / common / util
src/main/resources/      mapper/(MyBatis XML), templates/·static/(Thymeleaf 화면), application.properties
src/test/                JUnit 테스트
sql/                    DDL(ddl/) · seed(seed/) 스크립트 (docs/database.md)
scripts/                dev-setup(환경 부트스트랩) · smoke-test(E2E 검증) · 린트 3종(docs/schema/code)
docs/                   상세 지식 (아래 §5 문서 지도 참고) · reviews/(코드 리뷰 기록) · tasks-archive.md(작업 완료 기록)
.claude/commands/       반복 작업 스킬 (/commit, /review, /deploy, /cleanup, /new-screen)
.claude/hooks/          강제 규칙 스크립트 (.env·생성물 보호, 포맷, 세션요약)
.github/workflows/      CI — 빌드·테스트·정적 검사(강제)
```

> 폴더가 아직 없다면 신규 프로젝트이기 때문이다. 위 구조를 **관례로 강제**한다.

## 4. 불변식 — 반드시 지킨다 (Enforced Invariants)

이 규칙들은 **훅(로컬)·CI 로 강제**된다. 위반 시 작업이 차단된다. **구현 방식은 자유, 이 경계는 불변.**

- **비밀값 금지**: `.env`, 키/키스토어, DB 접속정보를 코드/커밋에 넣지 않는다.
  → `.env.example` 템플릿 + 배포 시크릿/Jasypt. (`docs/security.md`)
- **생성물 수정 금지**: `build/`, `.gradle/`, `bin/`, `node_modules/`, `dist/`, `target/` 등은 손대지 않는다.
- **계층 경계**: `controller → service → mapper` 단방향. Controller 에 SQL/비즈니스 로직 금지. (`docs/architecture.md`)
- **MyBatis 규약**: 모든 SQL 은 mapper XML 에. 문자열 연결 SQL·인라인 쿼리 금지. 파라미터는 `#{}`. (`docs/database.md`)
- **JPA 미사용**: `@Entity`/JpaRepository 도입 금지. 퍼시스턴스는 MyBatis 로 통일.
- **개인정보 암호화**: 성명·비밀번호 등 지정 컬럼은 ARIA 로 암호화 저장. (`docs/security.md`, 대상=`docs/database.md`)
- **감사 대상**: 사용자의 메뉴 접속, 데이터 조회, 입력, 수정, 삭제는 `tb_system_log` 에 이력을 남긴다. (`docs/security.md`)
- **외부 연동 격리**: BiostarX, 카드 프린트, 주차 등 외부 연동은 전용 `adapter` 계층으로만. (`docs/integration.md`)
- **커밋 위생**: 빌드/포맷 통과 후 커밋. Conventional Commits. (`/commit`)

> 위 목록에 없는 것은 **자유**다. 네이밍·파일 배치·리팩터링은 관례(`docs/conventions.md`)를 따르되 스스로 판단하라.

## 5. 문서 지도 (필요할 때 펼쳐 읽어라 — Progressive Disclosure)

작업 종류에 맞는 문서만 그때그때 연다. 처음부터 전부 읽지 않는다.

| 무엇을 할 때 | 읽을 문서 |
|-------------|-----------|
| 전체 구조·계층·패키지 | `docs/architecture.md` |
| 백엔드(Controller/Service) 작업 | `docs/backend.md` |
| SQL / MyBatis mapper / 테이블 | `docs/database.md` |
| 화면(Thymeleaf) 구조·동작 | `docs/frontend.md` |
| UI/UX·색·타이포·컴포넌트 룩 | `docs/design.md` |
| 외부 시스템 연동(BiostarX 등) | `docs/integration.md` |
| 네이밍·코드 스타일·강제 | `docs/conventions.md` |
| 인증/권한/암호화/감사 | `docs/security.md` |
| 테스트 작성·실행 | `docs/testing.md` |
| 빌드·배포·환경 | `docs/deployment.md` |
| 도메인 용어 | `docs/glossary.md` |

문서 색인: `docs/README.md`

## 6. 스킬 (반복 작업은 명령으로 — `.claude/commands/`)

| 명령 | 용도 |
|------|------|
| `/commit`     | 포맷·검증 후 Conventional Commit 생성 |
| `/review`     | 현재 변경을 §4 불변식 기준으로 검토 |
| `/deploy`     | 빌드·테스트·환경별 배포 (가드레일 포함) |
| `/cleanup`    | 죽은 코드·오래된 문서 정리 (doc-gardening) |
| `/new-screen` | 화면 1개 수직 슬라이스(Thymeleaf+백엔드) 스캐폴딩 |

## 7. 처음 온 에이전트에게 (Start Here)

1. 이 파일(§4 불변식, §3 지도)만 먼저 이해한다.
2. 작업 유형이 정해지면 §5 표에서 **해당 문서 하나만** 연다.
3. 반복 작업이면 §6 스킬을 먼저 찾는다.
4. 애매하면 추측하지 말고 `docs/` 를 확인하거나 사용자에게 묻는다.
5. **새 명명·패턴을 도입하면 `docs/conventions.md`(규칙 대장)에 등록하고 같은 커밋에 포함한다.**
6. 커밋 전 `/review`(기록은 `docs/reviews/`) → `/commit`. 작업 완료분은 `docs/tasks-archive.md` 에 한 줄 기록한다.
