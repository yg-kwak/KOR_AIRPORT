# 코드 관례

> "불변식은 강제, 구현은 자유". 여기 있는 건 **관례(권장)** 이고, 강제 규칙은 `AGENTS.md §4`.
> **규칙이 없는 패턴을 새로 만들면 이 파일에 적어 둔다.**
> 애매하면 **기존 코드의 지배적 패턴을 따른다** — 새 패턴 발명을 최소화한다.

## 패키지/구조
- **역할별 평면 구조**(controller/service/mapper/model/adapter/config/security/util). 도메인별 하위 폴더를 만들지 않는다. (구조 원천: `architecture.md`)
- 도메인 구분은 **클래스명 접두사**로 한다: `Visitor*`, `AcGroup*`, `Menu*`, `Common*`, `System*` 등.
- 패키지명은 **소문자**(`adapter`, `security`). 클래스만 PascalCase.

## 네이밍
- Java 클래스: `PascalCase`. 메서드/필드: `camelCase`. 상수: `UPPER_SNAKE`.
- Controller/Service/Mapper: `{도메인}Controller` / `{도메인}Service` / `Tb{Table}Mapper`.
- 테이블 매핑 모델: `Tb{Table}` (예: `TbLoginUser`).
- DB 테이블/컬럼: 소문자 스네이크, 테이블 `tb_` 접두. (`database.md`)
- 화면: 템플릿 `templates/{도메인}/*.html`, 스크립트 `static/js/pages/{도메인}/*.js`. (`frontend.md`)

## 포맷팅
- Java: Gradle spotless (`/commit` 시 자동). TODO: 포맷터(google-java-format 등) 확정.
- 프론트(HTML/JS/CSS): prettier (PostToolUse 훅이 저장 시 자동).

## 커밋
- Conventional Commits(한국어 메시지). 타입: `feat|fix|refactor|docs|test|chore|perf`.
- scope 예: `visitor|access|acgroup|menu|common|system|biostar|log|web|db|infra`.

## 주석
- "무엇"이 아니라 "왜"를 남긴다. 자명한 코드에 주석 달지 않는다.

## 강제 (Enforcement) — "선언만 있는 규칙"이 되지 않게
불변식은 세 층에서 강제한다. 새 규칙을 만들면 가능한 한 아래 중 하나에 **자동 검사**를 붙인다.

1. **로컬 훅** `.claude/hooks/` — 파일 저장/커밋 시점 차단(비밀값·생성물 등). 위반 메시지에 **교정 방법**을 함께 출력한다.
2. **CI** `.github/workflows/ci.yml` — 빌드/테스트 + 정적 검사(시크릿 스캔, 인라인 SQL 금지, JPA 금지 등). PR 게이트.
3. **구조 테스트(ArchUnit)** `src/test/.../ArchitectureTest` — 계층 경계(controller→service→mapper), 패키지 규칙을 테스트로 강제. *(프로젝트 코드 생성 후 추가)*

> 원칙: 검사 실패 메시지는 "무엇이 왜 막혔고 **어떻게 고쳐라**"까지 담는다(에이전트가 스스로 교정하도록).

## 관련 문서
[architecture.md](architecture.md) · [backend.md](backend.md) · [database.md](database.md) · [frontend.md](frontend.md)
