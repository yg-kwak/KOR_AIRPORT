# 백엔드 (Spring Boot)

> 백엔드 작업 시 읽는다. **패키지/계층 구조의 단일 출처는 `architecture.md`** (여기서 반복하지 않는다).
> DB/SQL 은 `database.md`, 커밋은 `/commit`.

## 런타임/빌드
- Java 17, Spring Boot 3.2.0. 서버사이드 렌더링(Thymeleaf) — 프론트는 `frontend.md`.
- 빌드: `project/gradlew.bat` (wrapper). 태스크: `compileJava`, `test`, `build`, `bootRun`.
- 루트 패키지: `AirPort`.

## eGovFrame — 포함하되 사용하지 않음
- 사업 요건상 **의존성은 포함**한다(idgnr, property, cmmn, mvc, dataaccess + `maven.egovframe.go.kr` 저장소).
- 그러나 실제 코드는 **순수 Spring Boot + MyBatis** 로 작성한다. eGov 인터페이스+`*Impl` 패턴, 표준 공통매퍼는 쓰지 않는다.
- `resources/egovframework/**` 는 레퍼런스 리소스로만 존재. 신규 코드에서 참조하지 않는다.

## 주요 의존성 (예정)
MyBatis(`mybatis-spring-boot-starter`), Thymeleaf(+layout-dialect), Validation, Lombok, MSSQL JDBC(`mssql-jdbc`), Apache POI(엑셀), commons-lang3, Jasypt(프로퍼티 암호화), WebSocket(필요 시). **JPA/MariaDB 드라이버는 넣지 않는다.**

## 계층별 작성 규칙
- **Controller**: 요청/응답 매핑·검증만. 세션 체크는 `AuthInterceptor` 에 위임. 비즈니스 로직·SQL 금지.
- **Service**: `@Service` 구체 클래스. 도메인 로직·트랜잭션 경계. 암호화(ARIA)·감사 기록도 여기서. 외부 SDK 직접 호출 금지(→ `adapter`).
- **mapper**: MyBatis 인터페이스(`Tb*Mapper`) + XML. SQL 은 XML 에만. (`database.md`)
- **adapter**: BiostarX·카드프린터·주차 등 외부 연동 격리. (`integration.md`)
- **model**: DTO/VO. 테이블 1:1 매핑 모델은 `Tb*` 명명.
- 의존 방향(단방향): `controller → service → mapper` / `service → adapter`.

## 공통 규칙
- 예외/에러 응답 규격 통일. TODO: 공통 예외 핸들러.
- 로깅 구조화(요청ID/사용자ID 포함). TODO: 로깅 컨벤션.
- 코드값은 하드코딩 대신 `tb_common` 참조.
- 리소스: `src/main/resources/` — `mapper/`(MyBatis XML), `templates/`·`static/`(화면), `application.properties`(포트/DB/세션/SSL/로그 등 환경설정 원천).

## 관련 문서
[architecture.md](architecture.md) · [database.md](database.md) · [frontend.md](frontend.md) · [security.md](security.md) · [conventions.md](conventions.md) · [testing.md](testing.md)
