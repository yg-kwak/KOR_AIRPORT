# 보안 · 권한 · 감사

> 출입관리 시스템의 핵심. 인증/권한/비밀값/이력의 진실 원천.

## 비밀값 (불변식)
- `.env`, 키/키스토어, DB 접속정보를 **코드/커밋에 넣지 않는다** (PreToolUse 훅이 차단).
- `.env.example` 에는 **키 이름만**. 실제 값은 배포 시크릿 매니저.
- TODO: 환경별 시크릿 주입 방식(CI/CD, eGov property).

## 인증
- TODO: 인증 방식(세션/JWT/SSO), eGov 보안 모듈 사용 여부.
- TODO: 프론트 토큰 저장/갱신 정책.

## 권한 (Authorization)
- 출입 권한 부여·회수는 최소권한 원칙. 역할 기반(RBAC) 여부 TODO.
- 민감 작업(도어 개방, 권한 부여)은 서버에서 재검증.

## 감사 이력 (불변식)
- **권한 부여·회수, 도어 제어, 방문자 발급·회수**는 반드시 이력을 남긴다.
- 기록 항목: 누가/무엇을/언제/대상/결과. TODO: 이력 테이블 스키마.
- BiostarX 로 나가는 제어도 이력 대상. (`biostar-integration.md`)

## 관련 문서
[architecture.md](architecture.md) · [biostar-integration.md](biostar-integration.md) · [database.md](database.md)
