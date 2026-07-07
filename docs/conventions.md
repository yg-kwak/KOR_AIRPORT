# 코드 관례

> "불변식은 강제, 구현은 자유". 여기 있는 건 **관례(권장)** 이고, 강제 규칙은 `AGENTS.md §4`.
> 애매하면 **기존 코드의 지배적 패턴을 따른다** — 새 패턴을 발명하지 않는다.

## 네이밍
- Java 클래스: `PascalCase`. 메서드/필드: `camelCase`. 상수: `UPPER_SNAKE`.
- 도메인 접두/패키지: `access`, `visitor`, `biostar`, `common` (TODO 확정).
- 프론트 컴포넌트: `PascalCase`, 파일 kebab 또는 Pascal (TODO 확정).

## 포맷팅
- Java: Gradle spotless (`/commit` 시 자동). TODO: 포맷터(google-java-format 등) 확정.
- 프론트: prettier (PostToolUse 훅이 저장 시 자동).

## 커밋
- Conventional Commits. 타입: `feat|fix|refactor|docs|test|chore|perf`.
- scope: `access|visitor|biostar|web|db|infra`.

## 주석
- "무엇"이 아니라 "왜"를 남긴다. 자명한 코드에 주석 달지 않는다.

## TODO
- TODO: 예외/에러코드 네이밍.
- TODO: DTO/VO 접미사 규약.

## 관련 문서
[backend.md](backend.md) · [frontend.md](frontend.md) · [database.md](database.md)
