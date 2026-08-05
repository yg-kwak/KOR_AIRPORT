# 빌드 · 배포

> 배포의 진실 원천. 자동화·가드레일은 `/deploy`. DB 는 **MSSQL 단일**.

## 빌드/실행
- **JDK 17+ 필요**(Spring Boot 3.2 Gradle 플러그인). `JAVA_HOME` 을 17 이상으로 지정(예: `jdk-17`). JDK 15 로는 빌드 실패.
- `gradlew.bat clean build` — 컴파일 + 테스트 + 패키징(jar). (저장소 루트에서)
- 로컬 실행: `gradlew.bat bootRun`. DB 접속정보는 환경변수(`DB_MSSQL_URL/USERNAME/PASSWORD`) 또는 `application-local.properties` 로 주입.
- 프론트는 같은 앱(Thymeleaf) — 별도 프론트 빌드 단계 없음. (`frontend.md`)

## 원커맨드 개발환경 셋업 (권장 — 팀원 온보딩)
```bash
# Git Bash. DB 생성 → DDL → seed → application-local.properties 생성까지 한 번에
DB_SA_PASSWORD=**** scripts/dev-setup.sh          # 재생성: --reset
./gradlew bootRun --args="--spring.profiles.active=local"
scripts/smoke-test.sh                              # E2E 검증(13개 체크: 로그인/CRUD/권한/엑셀)
```
- `smoke-test.sh` 는 앱을 직접 부팅해 검증 후 종료한다(떠 있는 앱 대상은 `--no-boot`).
- 기능 작업 완료 시 커밋 전에 smoke-test 통과를 확인한다.

## IntelliJ 로컬 실행
Gradle 빌드(`build.gradle`)가 **저장소 루트**에 있어, 루트를 열면 Gradle 이 자동 임포트되고 docs/·sql/·AGENTS.md 등 전체가 트리에 보인다.

1. **저장소 루트를 연다**: `File > Open` → `D:\...\CJAirPort` → Trust. → Gradle 자동 임포트(안 되면 Gradle 툴윈도우 🔄 Reload).
2. **JDK 17+**: 루트 `gradle.properties`(로컬, gitignore)의 `org.gradle.java.home` 이 JDK17 을 지정하므로 시스템 `JAVA_HOME` 이 낮아도 동기화된다. 없으면 `Settings > Build Tools > Gradle > Gradle JVM = 17`.
3. **로컬 접속정보**: `src/main/resources/application-local.properties.example` → 같은 폴더에 `application-local.properties` 로 복사 후 DB 비밀번호 수정. **커밋 금지(gitignore)**.
4. **DB 준비**: MSSQL 에 `CJ_AIRPORT` 생성 후 `sql/ddl/01_tables.sql` → `sql/seed/02_seed.sql` 실행.
5. **실행**: 상단 Run 목록의 **`bootRun (local)`**(권장) 또는 **`ProjectApplication (local)`** ▶. (`.run/` 포함, 프로파일 `local` 자동 활성)
6. `http://localhost:8080/login` → `admin` / `admin123` → 공통코드관리 CRUD 확인.

> 검증됨(2026-07): 로그인·공통코드 CRUD·감사 이력 적재까지 로컬 MSSQL 로 정상 동작 확인.

## 환경
- `dev` / `staging` / `prod`. Spring 프로파일 `--spring.profiles.active` 로 전환.
- 포트/DB/세션/SSL/로그 경로 등은 `application.properties`(+프로파일별)에서 관리.
- 비밀값은 코드/커밋에 두지 않고 운영 환경에서 주입(프로퍼티는 Jasypt 암호화). (`security.md`)

### DB 커넥션 풀 (현장에서 반드시 확인)
BiostarX 동기화는 **트랜잭션 안에서** 일어난다(실패 시 저장을 롤백하는 정합성 정책 — `integration.md`). 따라서 인원·방문 저장 1건이 외부 응답을 기다리는 동안 DB 커넥션을 계속 점유한다. HikariCP 기본값 `maximum-pool-size=10` 을 그대로 두면 **등록 작업 10건만으로 풀이 말라 조회를 포함한 전체가 멈춘다**.

| 키 | 기본값(jar) | 의미 |
|----|-------------|------|
| `spring.datasource.hikari.maximum-pool-size` | `${DB_POOL_MAX:30}` | 동시 사용자(관리자 PC + 키오스크)보다 넉넉히 |
| `spring.datasource.hikari.minimum-idle` | `${DB_POOL_MIN:5}` | 유휴 유지 |
| `spring.datasource.hikari.connection-timeout` | `10000` | 풀이 마르면 무한 대기 대신 10초 후 실패(사유가 보이게) |
| `spring.datasource.hikari.max-lifetime` | `1800000` | 커넥션 최대 수명 30분 |
| `spring.datasource.hikari.leak-detection-threshold` | `60000` | 60초 미반납 시 경고 로그 — 외부 연동이 트랜잭션을 오래 잡는지 추적 |

BiostarX 호출 타임아웃은 연결 5초 / 요청 7~10초(`BiostarSession`)다. 한 저장이 여러 번 왕복할 수 있으므로 풀 크기는 그 곱을 감안한다.

### 로그 — 두 종류를 함께 본다
| 파일 | 무엇이 남나 | 언제 자라나 |
|------|-------------|-------------|
| `cjairport.log` | 기동 · 오류 · 경고 (기술 로그) | **문제가 있을 때만** |
| `access.{날짜}.log` | 요청 1건 = 1줄 (IP·URL·상태·처리시간) | 요청이 올 때마다 |

🔴 **`cjairport.log` 가 안 자라는 것은 정상일 수 있다.** 정상 조회·저장은 INFO 레벨에서 아무 로그도 남기지 않는다. 종일 문제없이 운영하면 파일 크기가 그대로고, **로그백 롤오버는 '날짜가 바뀐 뒤 첫 로그가 기록될 때' 일어나므로 자정이 지나도 새 파일이 생기지 않는다.** 시스템이 멈춘 것처럼 보이지만 실제로는 잘 돌고 있는 상태다 — 살아 있는지는 `access.{날짜}.log` 로 확인한다.

업무 이력(누가 무엇을 조회·등록·수정했는지)은 파일이 아니라 **감사추적 화면(`tb_system_log`)** 에 남는다. 파일 로그와 목적이 다르다.

## 운영 환경(DMZ) 전제
- **적용(운영) 환경은 DMZ — 외부 인터넷 불가.** 의존성/드라이버는 사내 저장소 또는 빌드 산출물에 포함해 반입한다.
- BiostarX·MSSQL 등 내부망 연동만 가능. (개발 환경은 이 제약 없음)

## 배포 절차
- **산출물: 실행 가능 `jar`** (Spring Boot fat jar, `gradlew build` → `build/libs/*-SNAPSHOT.jar`, `*-plain.jar` 아님). `java -jar` 로 기동.
- 배치: jar + 외부 설정 파일을 서버에 반입 → 아래 "설정·비밀값 주입" 대로 실행.
- **로그**: 서비스로 돌리면 콘솔이 없어 로그가 사라지므로 **파일 로그가 기본 활성**이다(`logging.file.name`).
  기본 경로는 작업 디렉터리의 `logs/` — 서비스는 작업 디렉터리가 달라질 수 있어 **운영은 절대경로 권장**:
  `LOG_PATH=D:/cjairport/logs` (환경변수) 또는 외부 설정에 `logging.file.name`. 하루 단위 + 50MB 분할, 30일·총 1GB 보관.
- **DB 준비**: `sql/deploy/CJAirPort_install.sql` — SSMS 로 열고 F5. 신규 설치·기존 갱신 모두 이 파일 하나. (`database.md`)
- TODO: 배포 대상(서비스 등록/데몬 — systemd 등), 기동 스크립트.
- TODO: 무중단/롤백 전략.
- TODO: DB DDL/마이그레이션 적용 순서. (`database.md`)
- TODO: 배포 후 헬스체크 엔드포인트.

## 설정·비밀값 주입 (운영)
DB 접속정보·ARIA 키 등 **비밀값은 jar 안에 넣지 않고 "외부 `application.properties`"로 주입**한다.
jar 내부 파일은 `${DB_MSSQL_URL:...}` placeholder 뿐이라, 외부에서 주면 그 값이 우선한다.

**설정 우선순위(아래일수록 우선 = 위를 덮어씀):**
```
jar 내부 application.properties            (placeholder만)
→ jar 옆 ./application.properties
→ jar 옆 ./config/application.properties   (권장)
→ OS 환경변수 (DB_MSSQL_PASSWORD 등)
→ 실행 인자 --spring.datasource.password=… (최우선)
```

**설정 예시 파일**: `deploy/application.properties.example` — 모든 키에 설명이 붙어 있다. 복사해 값만 채운다.
**BiostarX 접속정보는 이 파일에 넣지 않는다** — `app.biostar.*` 는 `local` 프로파일 전용 시더가 읽으므로 운영에서는 무시된다.
앱을 띄운 뒤 [설정관리] 화면에서 입력한다(tb_system 에 암호화 저장).

**권장 배치:**
```
/opt/cjairport/
├─ cjairport-0.0.1-SNAPSHOT.jar
└─ config/
   └─ application.properties      # DB·ARIA·SSL — 서버에만 존재, git 밖
```
```bash
cd /opt/cjairport && java -jar cjairport-0.0.1-SNAPSHOT.jar
# 또는: java -jar app.jar --spring.config.additional-location=file:/opt/cjairport/config/
```
외부 `config/application.properties` 예시:
```properties
spring.datasource.url=jdbc:sqlserver://<DB호스트>:1433;databaseName=CJ_AIRPORT;encrypt=true;trustServerCertificate=true
spring.datasource.username=<운영계정>
spring.datasource.password=<실제 비밀번호>
app.crypto.aria-key=<운영 ARIA 32자 키>
server.ssl.enabled=true
```

**주의**
- 🔴 `app.crypto.aria-key` 는 **DB 암호문(성명·비밀번호)을 만들 때 쓴 키와 동일**해야 한다. 운영은 운영키로 재암호화한 seed 를 넣고 그 키를 설정한다(개발 seed 키 `0123…01` 그대로 쓰지 말 것). (`security.md`)
- 외부 설정 파일은 권한 제한(`chmod 600`, 서비스 계정만).
- 더 강하게: 비밀번호를 환경변수(systemd `EnvironmentFile`)나 **Jasypt**(`ENC(...)` + 마스터키 기동 시 주입)로. Jasypt 의존성은 이미 포함.
- `application-local.properties` 는 **개발(IDE) 전용** — 운영엔 쓰지 않는다.

## 가드레일 (강제)
- 테스트 실패 시 배포 금지.
- `prod` 는 명시적 승인 필요.
- 미커밋 변경 상태로 배포 금지.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [security.md](security.md)
