# 빌드 · 배포

> 배포의 진실 원천. 자동화·가드레일은 `/deploy`. DB 는 **MSSQL 단일**.

## 빌드/실행
- **JDK 17+ 필요**(Spring Boot 3.2 Gradle 플러그인). `JAVA_HOME` 을 17 이상으로 지정한다(예: `jdk-17`). JDK 15 로는 빌드 실패.
- `project/gradlew.bat clean build` — 컴파일 + 테스트 + 패키징(jar).
- 로컬 실행: `project/gradlew.bat bootRun`. DB 접속정보는 환경변수(`DB_MSSQL_URL/USERNAME/PASSWORD`)로 주입.
- 프론트는 같은 앱(Thymeleaf) — 별도 프론트 빌드 단계 없음. (`frontend.md`)

## IntelliJ 로컬 실행
1. **프로젝트 열기**: 저장소 루트(`CJAirPort`)를 열면 IntelliJ 가 `project/build.gradle` 을 감지 → Gradle 로 링크/임포트.
2. **JDK 17 지정 (필수)**:
   - `File > Project Structure > SDKs` 에 JDK 17 등록(예: `jdk-17.0.12`), Project SDK = 17.
   - `Settings > Build Tools > Gradle > Gradle JVM = 17`. ⚠️ 시스템 `JAVA_HOME` 이 JDK 15 라 이 설정을 17 로 바꾸지 않으면 Gradle 동기화가 실패한다.
3. **로컬 접속정보**: `project/src/main/resources/application-local.properties.example` 을 같은 폴더에 `application-local.properties` 로 복사 후 DB 비밀번호 등 수정(이 파일은 커밋 금지).
4. **DB 준비**: MSSQL 에 DB 생성 후 `sql/ddl/01_tables.sql` → `sql/seed/02_seed.sql` 실행. (DataGrip/SSMS)
5. **실행**: 상단 Run 목록에서 **`bootRun (local)`**(권장) 또는 **`ProjectApplication (local)`** 선택 후 Run ▶. (`.run/` 에 포함, 프로파일 `local` 자동 활성)
6. 브라우저 `http://localhost:8080/login` → `admin` / `admin123` → 공통코드관리에서 CRUD 확인.

> `ProjectApplication (local)` 이 모듈을 못 찾으면(모듈명 상이) 메인 클래스 `AirPort.ProjectApplication` 로 새 Spring Boot 실행구성을 만들고 Active profiles 에 `local` 을 넣으면 된다. 또는 `bootRun (local)` 을 사용.

## 환경
- `dev` / `staging` / `prod`. Spring 프로파일 `--spring.profiles.active` 로 전환.
- 포트/DB/세션/SSL/로그 경로 등은 `application.properties`(+프로파일별)에서 관리.
- 비밀값은 코드/커밋에 두지 않고 운영 환경에서 주입(프로퍼티는 Jasypt 암호화). (`security.md`)

## 운영 환경(DMZ) 전제
- **적용(운영) 환경은 DMZ — 외부 인터넷 불가.** 의존성/드라이버는 사내 저장소 또는 빌드 산출물에 포함해 반입한다.
- BiostarX·MSSQL 등 내부망 연동만 가능. (개발 환경은 이 제약 없음)

## 배포 절차
- **산출물: 실행 가능 `jar`** (Spring Boot fat jar). `java -jar` 로 기동.
- TODO: 배포 대상(서비스 등록/데몬), 기동 스크립트.
- TODO: 무중단/롤백 전략.
- TODO: DB DDL/마이그레이션 적용 순서. (`database.md`)
- TODO: 배포 후 헬스체크 엔드포인트.

## 가드레일 (강제)
- 테스트 실패 시 배포 금지.
- `prod` 는 명시적 승인 필요.
- 미커밋 변경 상태로 배포 금지.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [security.md](security.md)
