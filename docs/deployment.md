# 빌드 · 배포

> 배포의 진실 원천. 자동화·가드레일은 `/deploy`.

## 빌드
- `project/gradlew.bat clean build` — 컴파일 + 테스트 + 패키징.
- 프론트 빌드: TODO(`web/` 빌드 스크립트).

## 환경
- `dev` / `staging` / `prod`. Spring 프로파일 `--spring.profiles.active` 로 전환.
- DB 프로파일: `mssql` / `mariadb`. TODO: 환경별 DB 매핑.
- 비밀값은 배포 시크릿으로 주입 (`security.md`).

## 배포 절차 (TODO 채우기)
- TODO: 배포 대상(WAS/컨테이너/온프렘), 산출물 형태(jar/war).
- TODO: 무중단/롤백 전략.
- TODO: DB 마이그레이션 적용 순서.
- TODO: 배포 후 헬스체크 엔드포인트.

## 가드레일 (강제)
- 테스트 실패 시 배포 금지.
- `prod` 는 명시적 승인 필요.
- 미커밋 변경 상태로 배포 금지.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [security.md](security.md)
