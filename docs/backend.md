# 백엔드 (Spring Boot + eGovFrame)

> 백엔드 작업 시 읽는다. 계층 경계는 `architecture.md`, 커밋은 `/commit`.

## 런타임/빌드
- Java 17, Spring Boot 3.2.0, eGovFrame 4.3.0.
- 빌드: `project/gradlew.bat` (wrapper). 주요 태스크: `compileJava`, `test`, `build`, `spotlessApply`.

## eGovFrame 모듈 사용
- `idgnr` — ID 채번. TODO: 채번 전략/테이블.
- `property` — 환경 프로퍼티. TODO: application-*.yml 과의 경계.
- `cmmn` — 공통 코드/유틸.
- `mvc` — 컨트롤러/뷰 처리.
- `dataaccess` — MyBatis 연동 기반.

## 패키지/파일 배치 (관례)
```
src/main/java/.../{domain}/
  web/        Controller
  service/    Service, ServiceImpl
  service/impl
  mapper/     MyBatis Mapper 인터페이스
  vo/         요청/응답/도메인 VO
src/main/resources/
  mappers/    Mapper XML
  egovframework/  eGov 설정
  application-*.yml
```
> TODO: 실제 base package 확정 후 위 `...` 를 교체.

## 규칙
- Controller 는 얇게. 로직은 Service.
- 예외/에러 응답 규격 통일. TODO: 공통 예외 핸들러 정의.
- 로깅은 구조화(요청ID/사용자ID 포함). TODO: 로깅 컨벤션.
- JPA 미사용 — 퍼시스턴스는 MyBatis 로 통일.

## TODO
- TODO: 공통 응답 포맷(성공/실패) 스펙.
- TODO: 인증 필터/인터셉터 위치.
- TODO: BiostarX 어댑터 인터페이스 시그니처.

## 관련 문서
[architecture.md](architecture.md) · [database.md](database.md) · [conventions.md](conventions.md) · [testing.md](testing.md)
