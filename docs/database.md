# 데이터베이스 · MyBatis

> SQL/mapper 작업의 진실 원천. **DB: MSSQL 단일**. JPA 미사용.
> 테이블 스키마의 단일 출처는 이 문서다(다른 문서는 여기를 링크만 한다).

## MyBatis 규약 (불변식)
- 모든 SQL 은 **mapper XML** 에. Java 문자열로 SQL 을 조립하지 않는다.
- 파라미터 바인딩은 `#{}` (PreparedStatement). `${}` 는 컬럼/테이블명 등 불가피한 경우만, 입력 검증 후.
- 동적 SQL 은 `<if>/<choose>/<foreach>` 사용.
- resultMap 명시. `select *` 지양.
- mapper XML 경로: `src/main/resources/mapper/**` (단일 벤더이므로 벤더별 폴더 이중화 없음).
- Mapper 인터페이스: `mapper` 패키지, 이름 `Tb{Table}Mapper`. mapper id 는 인터페이스 메서드명과 일치.

## MSSQL 규약
- 페이징: `OFFSET ... ROWS FETCH NEXT ... ROWS ONLY`.
- 자동 증가 PK: `IDENTITY(1,1)`.
- 날짜: `datetime2(0)`, 기본값 `getdate()`.
- 불리언성 플래그: `nchar(1)` + `Y`/`N` CHECK 제약. (예: `use_yn`, `root_yn`)
- 문자열: `nvarchar`(유니코드). 아래 표의 길이는 **문자 수**(DDL 기준).

## 명명 규칙
- 테이블: `tb_` 접두 + 소문자 스네이크. 컬럼: 소문자 스네이크.
- 공통 감사 컬럼: `reg_dt`(입력일자), `mod_dt`(수정일자).
- 코드값은 하드코딩 대신 `tb_common`(코드구분 `cmm_id` + 코드 `code_id`)을 참조.
- Mapper 인터페이스/XML/SQL 명명·작성 규칙(표준 메소드 세트, searchWhere/orderBy 조각, 정렬 화이트리스트)은 **`conventions.md` §6** 이 원천.

---

## 설계된 테이블 (공통관리·보안 도메인)

> 아래 8종은 현재 설계 완료. 나머지 도메인(임시/정규카드, 기관, 차량, 카드)은 설계 후 추가한다.
> `Enc=Y` 컬럼은 **ARIA 암호화 대상**(저장 시 hex 대문자). 암호화 규약은 `security.md`.

### tb_login_user — 사용자등록 (로그인 계정)
PK: `user_id`

| 컬럼 | 타입 | PK | Enc | 설명 | 비고 |
|------|------|----|-----|------|------|
| user_id | nvarchar(30) | Y | | 사용자ID | |
| user_name | nvarchar(255) | | **Y** | 성명 | ARIA 암호화 |
| password | nvarchar(255) | | **Y** | 비밀번호 | ARIA 암호화 |
| dept_name | nvarchar(50) | | | 소속부서 | |
| use_yn | nchar(1) | | | 사용여부 | 기본 'Y', CHK Y/N |
| root_yn | nchar(1) | | | 관리자여부 | 기본 'N', CHK Y/N |
| auth_id | int | | | 권한ID | → `tb_menu_auth.auth_id` |
| login_fail_cnt | int | | | 로그인 실패 횟수 | 기본 0 |
| password_change_dt | datetime2(0) | | | 비밀번호 변경일자 | |
| start_menu_id | int | | | 시작메뉴ID | → `tb_menu.menu_id` |
| work_location_code | nvarchar(10) | | | 근무지역코드 | → `tb_common`(cmm_id='LO').code_id |
| work_type | nvarchar(30) | | | 근무유형 | |
| desk_ip | nvarchar(30) | | | IP | |
| dev_id | nvarchar(30) | | | 장치ID | |
| reg_dt / mod_dt | datetime2(0) | | | 입력/수정일자 | 기본 getdate() |

### tb_menu — 메뉴
PK: `menu_id`

| 컬럼 | 타입 | PK | 설명 |
|------|------|----|------|
| menu_id | int | Y | 메뉴ID |
| menu_name | nvarchar(100) | | 메뉴명 |
| parent_menu_id | int | | 부모메뉴ID (트리) |
| menu_url | nvarchar(255) | | 메뉴 접속 URL |
| menu_level | int | | 메뉴 레벨 (1=그룹, 2~=하위) |
| menu_order | int | | 메뉴 순서 |
| menu_icon | nvarchar(30) | | level 1 그룹 아이콘 키 (사이드바, 예: `settings`). 프론트 ICONS 매핑 |
| use_yn | nchar(1) | | 사용여부 |

### tb_menu_auth — 권한(그룹)
PK: `auth_id` (IDENTITY)

| 컬럼 | 타입 | PK | 설명 |
|------|------|----|------|
| auth_id | int | Y | 권한ID |
| auth_name | nvarchar(100) | | 권한명 |
| reg_dt / mod_dt | datetime2(0) | | 입력/수정일자 (기본 getdate()) |

### tb_menu_auth_detail — 권한별 메뉴 CRUD 권한
PK: `auth_id` + `menu_id` (복합)

| 컬럼 | 타입 | PK | 설명 |
|------|------|----|------|
| auth_id | int | Y | 권한ID → `tb_menu_auth.auth_id` |
| menu_id | int | Y | 메뉴ID → `tb_menu.menu_id` |
| read_auth | nchar(1) | | 읽기권한 (기본 'N', CHK Y/N) |
| create_auth | nchar(1) | | 생성권한 (기본 'N', CHK Y/N) |
| update_auth | nchar(1) | | 수정권한 (기본 'N', CHK Y/N) |
| delete_auth | nchar(1) | | 삭제권한 (기본 'N', CHK Y/N) |
| reg_dt / mod_dt | datetime2(0) | | 입력/수정일자 |

### tb_common — 공통 코드
PK: `cmm_id` + `code_id` (복합). 코드구분(`cmm_id`) 아래에 코드(`code_id`)들이 속한다.

| 컬럼 | 타입 | PK | 설명 |
|------|------|----|------|
| cmm_id | nvarchar(50) | Y | 코드구분ID (예: LO=근무지역, AT=감사유형) |
| cmm_name | nvarchar(100) | | 코드구분명 |
| code_id | nvarchar(50) | Y | 코드ID |
| code_name | nvarchar(100) | | 코드명 |
| code_tag | nvarchar(50) | | 코드 기타 |
| code_remark | nvarchar(100) | | 메모 |
| user_input | nchar(1) | | 구분: `N`=시스템 코드, `Y`=사용자 코드 *(설계서 오타 user_ipnut 정정)* |
| use_yn | nchar(1) | | 사용여부 (기본 'Y', CHK Y/N) |

> **구분(user_input) 규칙**: 화면 목록/엑셀에 **전체 노출**하고 `구분` 컬럼으로 표기한다 — `N`=**[시스템]**(AT 감사유형·LO 근무지역 등 시스템 참조 코드), `Y`=**[사용자]**.
> - **시스템 코드(N)**: 화면에서 **삭제 불가**, **이름(code_name)·사용유무(use_yn)만 수정** 가능. delete SQL `AND user_input='Y'` 가드로 삭제 차단.
> - **사용자 코드(Y)**: 전체 편집/삭제. 화면에서 등록한 코드는 항상 `user_input='Y'` 로 저장.
> - update SQL 은 `code_name`·`use_yn` 만 변경(코드ID/구분ID/구분값 불변, tag/remark 보존) — 시스템·사용자 공통.
>
> **코드구분 선택 규칙**: 화면 등록 시 `cmm_id` 는 자유입력이 아니라 **select** — **전체 코드구분(cmm_id)** 이 노출된다(기존 코드가 있는 구분에만 코드 추가). `cmm_name` 은 선택한 구분에서 서버가 파생(사용자 입력/수정 불가).

### tb_system — 시스템 설정 (BiostarX 연동정보, 단일 행)
PK 없음(설정 1행 운영).

| 컬럼 | 타입 | 설명 | 비고 |
|------|------|------|------|
| biostar_ip | nvarchar(50) | 바이오스타 IP | |
| biostar_id | nvarchar(100) | 바이오스타 ID | |
| biostar_pw | nvarchar(255) | 바이오스타 비밀번호 | **ARIA 암호화 저장**(Enc=Y). 화면엔 미노출, 연동 시 복호화 (`security.md`) |
| reg_dt / mod_dt | datetime2(0) | 생성/수정일자 | 기본 getdate() |

### tb_ac_group — 출입권한 그룹 (BiostarX 매핑)
PK: `ac_group_id` (IDENTITY)

| 컬럼 | 타입 | PK | 설명 |
|------|------|----|------|
| ac_group_id | int | Y | 출입그룹ID |
| ac_group_name | nvarchar(50) | | 출입그룹명 |
| parent_ac_group_id | int | | 부모 출입그룹ID (트리) |
| ac_group_level | int | | 출입그룹 레벨 |
| ac_group_order | int | | 출입그룹 순서 |
| biostar_ac_id | int | | BiostarX 출입그룹 ID (매핑) |
| biostar_ac_name | nvarchar(50) | | BiostarX 출입그룹명 |
| reg_dt / mod_dt | datetime2(0) | | 생성/수정일자 |

### tb_system_log — 감사추적 (이력, 불변식)
PK: `log_id` (IDENTITY). **모든 감사 이력은 이 한 테이블에 간략히 적재**한다. 정책은 `security.md`.

| 컬럼 | 타입 | PK | 설명 | 비고 |
|------|------|----|------|------|
| log_id | bigint | Y | 로그ID | |
| user_id | nvarchar(30) | | 사용자ID | NOT NULL, → `tb_login_user.user_id` |
| user_name | nvarchar(200) | | 사용자명 | |
| action_type | nvarchar(50) | | 유형 | → `tb_common`(cmm_id='AT').code_id |
| menu_id | int | | 메뉴ID | → `tb_menu.menu_id` |
| action_detail | nvarchar(1000) | | 상세내용 | |
| remark | nvarchar(1000) | | 비고 | |
| reg_dt | datetime2(0) | | 생성일자 | 기본 getdate() |

---

## 관계 요약
- `tb_login_user.auth_id` → `tb_menu_auth.auth_id` → (`tb_menu_auth_detail`) → `tb_menu.menu_id`
- `tb_login_user.work_location_code` → `tb_common`(cmm_id='LO')
- `tb_system_log.action_type` → `tb_common`(cmm_id='AT'), `tb_system_log.menu_id` → `tb_menu`
- `tb_ac_group.biostar_ac_id` → BiostarX 출입그룹 (외부, `integration.md`)

## 마이그레이션
- 스키마 원천: `D:\작업\2026\청주공항\설계\table.xlsx` (설계) → 본 문서 → 실행 스크립트 `sql/`.
- **DDL: `sql/ddl/01_tables.sql`**, **seed: `sql/seed/02_seed.sql`**(공통코드 AT/LO, 메뉴, 관리자 계정 admin/admin123).
- TODO: 스키마 형상관리 자동화(Flyway 등) 도입 여부.

## 관련 문서
[backend.md](backend.md) · [architecture.md](architecture.md) · [security.md](security.md) · [integration.md](integration.md)
