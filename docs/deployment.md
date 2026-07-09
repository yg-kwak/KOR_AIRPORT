# 빌드 · 배포

> 배포의 진실 원천. 자동화·가드레일은 `/deploy`. DB 는 **MSSQL 단일**.

## 빌드/실행
- **JDK 17+ 필요**(Spring Boot 3.2 Gradle 플러그인). `JAVA_HOME` 을 17 이상으로 지정한다(예: `jdk-17`). JDK 15 로는 빌드 실패.
- `project/gradlew.bat clean build` — 컴파일 + 테스트 + 패키징(jar).
- 로컬 실행: `project/gradlew.bat bootRun`. DB 접속정보는 환경변수(`DB_MSSQL_URL/USERNAME/PASSWORD`)로 주입.
- 프론트는 같은 앱(Thymeleaf) — 별도 프론트 빌드 단계 없음. (`frontend.md`)

## IntelliJ 로컬 실행
빌드는 `project/` 에 있지만, **저장소 루트를 열고 Gradle 을 링크**하면 docs/·sql/·AGENTS.md 등 전체를 보면서 개발할 수 있다.

1. **저장소 루트를 연다**: `File > Open` → `D:\...\CJAirPort` → Trust. (트리에 전체 폴더가 보인다)
2. **Gradle 링크**: 보통 우측 하단에 "Gradle build scripts found" 알림 → **Import/Link**. 안 뜨면 `View > Tool Windows > Gradle` → **＋(Link Gradle Project)** → `project/build.gradle` 선택.
   - ⚠️ 예전 하네스 시절의 낡은 루트 `.idea` 가 남아 있으면 non-Gradle 프로젝트로 열려 링크가 막힌다 → 루트 `.idea` 삭제 후 다시 열 것.
3. **JDK 17+**: `project/gradle.properties`(로컬, gitignore)의 `org.gradle.java.home` 이 JDK17 을 지정하므로 시스템 `JAVA_HOME` 이 15 여도 동기화된다. 없으면 `Settings > Build Tools > Gradle > Gradle JVM = 17` 로 지정.
4. **로컬 접속정보**: `project/src/main/resources/application-local.properties.example` → 같은 폴더에 `application-local.properties` 로 복사 후 DB 비밀번호 수정. **커밋 금지(gitignore)**.
5. **DB 준비**: MSSQL 에 `CJ_AIRPORT` 생성 후 `sql/ddl/01_tables.sql` → `sql/seed/02_seed.sql` 실행.
6. **실행**: 상단 Run 목록의 **`bootRun (local)`**(권장) 또는 **`ProjectApplication (local)`** ▶. (루트 `.run/` 에 포함, 프로파일 `local` 자동 활성, Gradle 대상은 `$PROJECT_DIR$/project`)
7. `http://localhost:8080/login` → `admin` / `admin123` → 공통코드관리 CRUD 확인.

> 검증됨(2026-07): 로그인·공통코드 CRUD·감사 이력 적재까지 로컬 MSSQL 로 정상 동작 확인.
> `ProjectApplication (local)` 이 모듈(`cjairport.main`)을 못 찾으면 `bootRun (local)` 을 사용.

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
