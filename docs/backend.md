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

## 예외 / 에러 응답 규격
- 에러 메시지·코드는 **외부 문서로 분리**해 필요할 때 주입한다. 표준: `src/main/resources/messages.properties`(+`messages_ko.properties`) 에 `error.{code}=메시지` 형태로 정의.
- 코드 상수는 `ErrorCode` enum(코드+기본메시지 키) 하나로 모으고, 화면/응답은 이 enum 만 참조(문자열 하드코딩 금지).
- 전역 처리: `@RestControllerAdvice`(데이터/AJAX 응답) + Thymeleaf 에러 페이지(화면). 표준 응답 `ApiResponse{success,code,message,data}` 로 통일.
- 구현체: `AirPort.common.ApiResponse`, `AirPort.common.exception.{ErrorCode, BusinessException, GlobalExceptionHandler}`. 페이징은 `AirPort.common.{PageParam, PageResult}`.
- **골든 샘플**: 공통코드관리(`CommonController`/`CommonService`/`TbCommonMapper` + `templates/web/system/commonCode.html`). 신규 CRUD 는 이 수직 슬라이스를 복제한다.
- **권장(더 나은 방법)**: 코드가 많아지면 `messages.properties`(i18n·핫스왑 용이)를 1차로, 정책성 매핑(HTTP 상태↔코드)은 별도 `error-codes.yml` 로 관리. YAML 은 구조화에, properties 는 다국어 문구에 강함 → 둘을 역할 분리해서 쓴다.

## 로깅 컨벤션 (권장)
- SLF4J 파사드 + Log4j2(또는 Logback). 설정은 `log4j2.properties`(운영/개발 프로파일 분리).
- **MDC 로 요청 컨텍스트 주입**: `requestId`(필터에서 UUID 발급), `userId`(세션) 를 패턴에 포함 → 로그 상관관계 추적.
- 레벨: 운영 INFO 기준, 외부연동(BiostarX)·보안 이벤트는 명시적 로깅. 개인정보/비밀번호는 **로그에 남기지 않는다**(마스킹).
- 감사(`tb_system_log`)와 애플리케이션 로그는 **목적이 다르다** — 감사는 DB, 진단은 파일 로그. (`security.md`)

## 기타 공통 규칙
- 코드값은 하드코딩 대신 `tb_common` 참조.
- 리소스: `src/main/resources/` — `mapper/`(MyBatis XML), `templates/`·`static/`(화면), `application.properties`(포트/DB/세션/SSL/로그 등 환경설정 원천).

## 관련 문서
[architecture.md](architecture.md) · [database.md](database.md) · [frontend.md](frontend.md) · [security.md](security.md) · [conventions.md](conventions.md) · [testing.md](testing.md)
