---
description: 빌드·테스트 후 환경별로 배포한다 (가드레일 포함)
argument-hint: <dev | staging | prod>
allowed-tools: Bash(*gradlew*), Bash(git*), Bash(npx*), Read
---

## 목표
지정 환경으로 안전하게 배포한다. **가드레일을 통과하지 못하면 배포하지 않는다.**

## 가드레일 (하나라도 실패 시 중단)
1. 인자로 **대상 환경**이 명시되어야 한다: `dev` / `staging` / `prod`. 없으면 사용자에게 확인.
2. `git status` 가 clean 인가 (미커밋 변경이 있으면 중단하고 알림).
3. **prod** 이면: 반드시 사용자에게 명시적 확인을 받는다. 승인 없이 진행 금지.
4. 운영 환경은 DMZ(외부통신 불가) — 필요한 의존성/드라이버가 산출물에 포함됐는가. (`docs/deployment.md`)

## 절차
1. 클린 빌드 + 전체 테스트: `project/gradlew.bat clean build`.
2. 테스트 실패 시 즉시 중단하고 실패 로그를 보고한다.
3. 프론트 산출물 빌드(있으면): `npx --no-install`(또는 web 빌드 스크립트).
4. 환경별 프로파일로 아티팩트 생성 (`-Pprofile=<env>` / `--spring.profiles.active`).
5. 배포 대상/명령은 `docs/deployment.md` 를 따른다. (인프라 세부는 그 문서가 진실 원천)
6. 배포 후 헬스체크 결과와 배포된 커밋 해시를 요약한다.

## 하지 말 것
- 테스트 실패 상태로 배포하지 않는다.
- prod 배포를 무단으로 진행하지 않는다.
- 비밀값을 명령행/로그에 노출하지 않는다.

$ARGUMENTS
