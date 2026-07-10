---
description: 포맷·검증을 통과시킨 뒤 Conventional Commit 을 생성한다
argument-hint: [선택: 커밋 메시지 힌트]
allowed-tools: Bash(git*), Bash(*gradlew*), Bash(npx*), Read, Grep
---

## 목표
현재 변경을 안전하게 커밋한다. **불변식 위반이 있으면 커밋하지 않고 중단**한다.

## 절차
1. `git status --short` 와 `git diff` 로 무엇이 바뀌었는지 파악한다.
2. **비밀값 검사**: 변경 내용에 접속정보/토큰/키가 섞였는지 grep 으로 확인. 있으면 즉시 중단하고 사용자에게 알린다. (`docs/security.md`)
   - 함께 확인(불변식): `@Entity`/JpaRepository 도입, Java 내 인라인 SQL 문자열, `adapter` 우회 외부호출이 없는지.
3. **포맷**: 
   - Java 변경이 있으면 `gradlew.bat spotlessApply` (없으면 건너뛴다).
   - 프론트 변경이 있으면 `npx --no-install prettier --write` 대상 파일.
4. **빌드/테스트 최소 검증**: 변경 범위에 맞게 `gradlew.bat compileJava` 또는 관련 테스트를 돌려 깨지지 않는지 확인.
5. **규칙 대장 갱신 확인**: 이번 변경에 `docs/conventions.md` 에 없는 새 명명·패턴·경우의수가 있는지 diff 를 훑는다. 있으면 conventions.md 해당 섹션에 "규칙 한 줄 + 예시"를 추가해 **같은 커밋에 포함**한다.
6. 관련 파일만 `git add` 한다. 무관한 변경을 함께 담지 않는다.
7. **Conventional Commits** 형식으로 메시지를 만든다:
   - 한국어로 메시지를 작성한다.
   - `feat|fix|refactor|docs|style|test|chore|perf(scope): 요약`
   - scope 목록은 `docs/conventions.md` §8 을 따른다 (common|visitor|acgroup|menu|biostar|auth|audit|ui|db|infra|deploy)
   - 본문에 "무엇을/왜" 를 한국어로 간단히.
8. 커밋 후 `git log --oneline -1` 로 결과를 보여준다.

## 하지 말 것
- 실패하는 빌드/테스트를 커밋하지 않는다.
- `.env`·키·생성물(`build/` 등)을 스테이징하지 않는다 (훅이 막지만 스스로도 확인).
- `--no-verify` 로 훅을 우회하지 않는다.

$ARGUMENTS
