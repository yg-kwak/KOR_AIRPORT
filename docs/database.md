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
  - **`use_yn`(사용여부)** = UI 활성/비활성 토글(운영 상태). `Y`=사용중, 기본 `'Y'`. 대부분 테이블.
  - **`del_yn`(삭제여부)** = 소프트 삭제 표식(tombstone). `Y`=삭제됨, 기본 `'N'`, 조회는 `WHERE del_yn='N'`. 실제 삭제해도 이력/조인 보존이 필요한 테이블(예: `tb_car`). **두 컬럼은 극성이 반대이니 혼동 주의** — 같은 개념이 아니라 서로 다른 관심사(활성상태 vs 삭제표식)라 한 테이블에 공존 가능.
- 문자열: `nvarchar`(유니코드). 아래 표의 길이는 **문자 수**(DDL 기준).

## 명명 규칙
- 테이블: `tb_` 접두 + 소문자 스네이크. 컬럼: 소문자 스네이크.
- 공통 감사 컬럼: `reg_dt`(입력일자), `mod_dt`(수정일자).
- 코드값은 하드코딩 대신 `tb_common`(코드구분 `cmm_id` + 코드 `code_id`)을 참조.
- Mapper 인터페이스/XML/SQL 명명·작성 규칙(표준 메소드 세트, searchWhere/orderBy 조각, 정렬 화이트리스트)은 **`conventions.md` §6** 이 원천.

---

## 설계된 테이블 (공통관리·보안 도메인)

> 아래 테이블은 현재 설계 완료. 나머지 도메인(방문자 임시/정규카드 등)은 설계 후 추가한다.
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

| 컬럼 | 타입 | PK | 설명 | 비고                                 |
|------|------|----|------|------------------------------------|
| ac_group_id | int | Y | 출입그룹ID | |
| ac_group_name | nvarchar(50) | | 출입그룹명 | |
| parent_ac_group_id | int | | 부모 출입그룹ID (트리) | |
| ar_code | nvarchar(50) | | 출입구역코드 | → `tb_common`(cmm_id='AR').code_id |
| ac_group_level | int | | 출입그룹 레벨 | |
| ac_group_order | int | | 출입그룹 순서 | |
| biostar_ac_id | int | | BiostarX 출입그룹 ID (매핑) | |
| biostar_ac_name | nvarchar(50) | | BiostarX 출입그룹명 | |
| reg_dt / mod_dt | datetime2(0) | | 생성/수정일자 | |

### tb_car — 차량
PK: `car_id` (IDENTITY). 차량 1대에 관리자 1명(1:1). **삭제는 물리 DELETE 금지 — `del_yn='Y'` 소프트 삭제**로 이력/조인을 보존한다.

| 컬럼 | 타입 | PK | Enc | 설명 | 비고                                   |
|------|------|----|-----|------|--------------------------------------|
| car_id | int | Y | | 차량ID | IDENTITY(1,1)                        |
| car_no | nvarchar(20) | | | 차량번호 | 예: 12가3456 (개인정보 암호화 대상 여부 재검토 TODO) |
| car_name | nvarchar(50) | | | 차량명칭 |                                      |
| car_type | nvarchar(30) | | | 차종 | `tb_common`(cmm_id='CT').code_id     |
| car_manager_id | nvarchar(30) | | 차량관리자 | → `tb_person.person_id` (소속 기관의 정규인원). 기관차량등록에서 지정 |
| company_code | nvarchar(30) | | 소속 기관 | → `tb_company.company_code`. 기관차량등록(`/company/companyCar`)에서 채운다 |
| del_yn | nchar(1) | | | 삭제여부 | 기본 'N', CHK Y/N. 삭제 시 'Y' (소프트 삭제)   |
| reg_dt / mod_dt | datetime2(0) | | | 입력/수정일자 | 기본 getdate()                         |

> 삭제 로그는 `tb_system_log` 에 차량번호를 **스냅샷**(`action_detail`)으로 남긴다 — 차량 행 상태와 무관하게 이력이 보존되도록 조인 의존을 없앤다.

### tb_car_ac_group — 차량 출입구역
PK: `car_id + code_id` (복합키로 중복 부여 차단). 인원의 `tb_person_ac_group` 과 같은 역할이지만, 차량은 BiostarX 출입그룹이 아니라 **공통코드 구역**(`tb_common` cmm_id='CAR')으로 관리한다.

| 컬럼 | 타입 | PK | 설명 | 비고 |
|------|------|----|------|------|
| car_id | int | Y | 차량ID | → `tb_car.car_id` |
| code_id | nvarchar(50) | Y | 출입구역 | → `tb_common`(cmm_id='CAR').code_id |
| reg_dt / mod_dt | datetime2(0) | | 입력/수정일자 | 기본 getdate() |

### tb_company — 기관 (기관관리)
PK: `company_code` (업무코드). **삭제는 물리 DELETE 금지 — `del_yn='Y'` 소프트 삭제.** `ceo_name` 은 ARIA 암호화 대상.

| 컬럼 | 타입 | PK | Enc | 설명 | 비고 |
|------|------|----|-----|------|------|
| company_code | nvarchar(30) | Y | | 기관코드 | 업무코드(사람이 부여) |
| company_type | nvarchar(50) | | | 기관구분 | → `tb_common`(cmm_id='CO').code_id |
| company_name | nvarchar(100) | | | 기관명 | |
| ceo_name | nvarchar(255) | | **Y** | 대표자 | ARIA 암호화 |
| tel | nvarchar(30) | | | 연락처 | |
| fax | nvarchar(30) | | | FAX | |
| addr | nvarchar(200) | | | 주소 | |
| service_start_dt | datetime2(0) | | | 용역시작일 | |
| service_end_dt | datetime2(0) | | | 용역종료일 | |
| biostar_group_id | int | | | BiostarX 사용자그룹ID | 기관 ↔ BiostarX user group 연동 (`integration.md`) |
| use_yn | nchar(1) | | | 사용유무 | 기본 'Y', CHK Y/N (UI 활성/비활성) |
| del_yn | nchar(1) | | | 삭제유무 | 기본 'N', CHK Y/N (소프트 삭제) |
| reg_dt / mod_dt | datetime2(0) | | | 입력/수정일자 | 기본 getdate() |

### tb_person — 인원 (출입 대상자)
PK: `person_id`. **`tb_login_user`(로그인 계정)와 다른 개체** — 혼동 금지. 삭제는 `del_yn='Y'` 소프트 삭제.

> **검색 제약(중요)**: `person_name`·`birth_date`·`person_phone` 은 ARIA 암호문이라 **부분검색(LIKE)·정렬 불가**.
> 목록 검색/정렬은 `person_id`·`company_code`·`title_code`·`main_task` 등 평문 컬럼으로만 구성한다
> (`tb_login_user.user_name` 과 동일한 제약 — `TbLoginUserMapper.xml` 참고).

| 컬럼 | 타입 | PK | Enc | 설명 | 비고 |
|------|------|----|-----|------|------|
| person_id | nvarchar(30) | Y | | 인원ID | |
| car_id | int | | 차량ID | → `tb_car.car_id`. **차량 카드**(card_type=차량)일 때 채운다. person_id 와 배타적 |
| person_name | nvarchar(255) | | **Y** | 성명 | ARIA 암호화 |
| birth_date | nvarchar(255) | | **Y** | 생년월일 | ARIA 암호화 |
| person_phone | nvarchar(255) | | **Y** | 연락처 | ARIA 암호화 |
| company_code | nvarchar(30) | | | 기관코드 | → `tb_company.company_code` |
| title_code | nvarchar(50) | | | 직위코드 | → `tb_common`(cmm_id='UT').code_id |
| person_type | nvarchar(50) | | | 발급유형 | 정규/임시/상주 등 → `tb_common`(cmm_id='PT').code_id. 발급 절차가 유형별로 달라짐 |
| status_code | nvarchar(50) | | | 상태 | → `tb_common`(cmm_id='PS').code_id |
| main_task | nvarchar(200) | | | 주요업무 | |
| id_check_dt | datetime2(0) | | | 신원조회 회보일 | |
| id_check_file | nvarchar(500) | | | 회보근거문서 | 표시용 원본 파일명. 실체는 `tb_person_file`(file_type='ID_CHECK') |
| security_edu_dt | datetime2(0) | | | 보안교육 합격일 | |
| security_edu_score | int | | | 보안교육 점수 | |
| final_approve_dt | datetime2(0) | | | 최종승인일 | |
| approve_file | nvarchar(500) | | | 승인근거문서 | 표시용 원본 파일명. 실체는 `tb_person_file`(file_type='APPROVE') |
| access_start_dt | datetime2(0) | | | 출입시작일 | 출입 유효기간 시작 |
| access_end_dt | datetime2(0) | | | 출입종료일 | 출입 유효기간 종료 |
| remark | nvarchar(1000) | | | 메모 | |
| biostar_user_id | nvarchar(50) | | | BiostarX 사용자ID | |
| use_yn | nchar(1) | | | 사용유무 | 기본 'Y', CHK Y/N |
| del_yn | nchar(1) | | | 삭제유무 | 기본 'N', CHK Y/N (소프트 삭제) |
| reg_dt / mod_dt | datetime2(0) | | | 입력/수정일자 | 기본 getdate() |

### tb_person_photo — 인원 등록사진
PK: `person_id` (`tb_person` 과 1:1). **본 테이블에서 분리한 이유**: BASE64 사진이 행 크기를 키워 인원 목록 조회 성능을 해치고, 얼굴은 생체정보라 보호 수준이 높다. 인원 목록/검색은 이 테이블을 조인하지 않는다.

| 컬럼 | 타입 | PK | 설명 | 비고 |
|------|------|----|------|------|
| person_id | nvarchar(30) | Y | 인원ID | → `tb_person.person_id` |
| photo_data | nvarchar(max) | | 등록사진 | BASE64 문자열. **암호화 안 함**(확정) — 대신 본 테이블 분리로 노출면 축소 (`security.md`) |
| reg_dt / mod_dt | datetime2(0) | | 입력/수정일자 | 기본 getdate() |

### tb_person_file — 인원 증빙문서
PK: `person_id + file_type` (인원별 **문서 종류당 1건**). **파일 실체를 DB 에 두는 이유**: DB 백업만으로 문서까지 복구되고, 업로드 경로 설정·권한·고아파일 문제가 사라진다. 목록/검색은 이 테이블을 조인하지 않는다(행 크기).

| 컬럼 | 타입 | PK | 설명 | 비고 |
|------|------|----|------|------|
| person_id | nvarchar(30) | Y | 인원ID | → `tb_person.person_id` |
| file_type | nvarchar(20) | Y | 문서구분 | `ID_CHECK`(회보근거) / `APPROVE`(승인근거) |
| file_name | nvarchar(260) | | 원본 파일명 | `tb_person.id_check_file`/`approve_file` 과 동일 값(표시용 비정규화) |
| file_size | int | | 파일 크기 | byte. 업로드 상한 5MB |
| file_data | varbinary(max) | | 파일 실체 | 다운로드 시 attachment 로 전송 |
| reg_dt | datetime2(0) | | 입력일자 | 기본 getdate() |

### tb_card — 카드 (출입통제 카드)
PK: `card_id` (IDENTITY). **인원 1 : 카드 N** (`person_id`). 삭제는 `del_yn='Y'` 소프트 삭제.

> **귀속 규칙**: 한 카드는 **인원(`person_id`) 또는 차량(`car_id`) 중 한쪽에만** 붙는다 — `CHK_tb_card_holder CHECK (person_id IS NULL OR car_id IS NULL)` 로 강제한다. 둘 다 비면 '미발급'(회수 포함)이라 다른 대상이 재사용할 수 있다. 미발급 판정·삭제 차단·재사용 검사는 모두 이 두 컬럼을 함께 본다.

> **상태 관리 규칙**: 카드 상태의 진실의 원천은 **`card_status` 단일 컬럼**이다(정지 전용 플래그를 따로 두지 않는다 — 상태가 두 곳에 표현되면 모순 저장이 가능해지므로).
> `lost_dt`·`return_dt` 는 상태 판정용이 아니라 **"언제 그렇게 됐는지"** 기록용이다.

| 컬럼 | 타입 | PK | 설명 | 비고 |
|------|------|----|------|------|
| card_id | int | Y | 카드ID | IDENTITY(1,1) |
| card_type | nvarchar(50) | | 카드구분 | → `tb_common`(cmm_id='CDT').code_id |
| card_name | nvarchar(100) | | 카드명칭 | |
| card_status | nvarchar(50) | | 카드상태 | → `tb_common`(cmm_id='CS').code_id. 정지 포함 |
| pass_type | nvarchar(50) | | 패스구분 | → `tb_common`(cmm_id='PT').code_id (정규/임시/장기/상주) |
| fee_paid_dt | datetime2(0) | | 발급료 납부일 | |
| issue_dt | datetime2(0) | | 카드발급일 | |
| issue_type | nvarchar(50) | | 발급구분 | → `tb_common`(cmm_id='IS').code_id |
| issue_reason | nvarchar(500) | | 발급근거 | |
| lost_dt | datetime2(0) | | 카드분실일 | 기록용 |
| return_dt | datetime2(0) | | 카드반납일 | 기록용 |
| remark | nvarchar(1000) | | 메모 | |
| person_id | nvarchar(30) | | 인원ID | → `tb_person.person_id` (FK 미강제) |
| biostar_card_id | nvarchar(50) | | BiostarX 카드ID | |
| biostar_card_value | nvarchar(255) | | BiostarX 카드값 | **암호화 안 함** — 근거: 카드값은 개인정보가 아님 (`security.md`) |

> **카드번호 유일성**: `UX_tb_card_value` (필터 유니크 인덱스, `del_yn='N'`) — 실물 카드와 1:1이므로 살아 있는 행끼리 카드번호가 겹칠 수 없다. 회수·재사용도 새 행을 만들지 않고 같은 행의 `person_id` 만 바꾼다.
| use_yn | nchar(1) | | 사용유무 | 기본 'Y', CHK Y/N |
| del_yn | nchar(1) | | 삭제유무 | 기본 'N', CHK Y/N (소프트 삭제) |
| reg_dt / mod_dt | datetime2(0) | | 입력/수정일자 | 기본 getdate() |

### tb_person_ac_group — 인원 출입그룹 (권한 부여)
PK: `person_id` + `ac_group_id` (복합). **인원 N : 출입그룹 M** — 인원 1명이 출입그룹 여러 개를 가질 수 있고, 한 출입그룹에 인원 여러 명이 속한다.

> **복합 PK 의 역할**: 같은 인원에게 같은 출입그룹이 **중복 부여되는 것을 DB 가 막는다**(`tb_menu_auth_detail` 과 동일 패턴).
> **부여/회수 이력**은 이 테이블이 아니라 `tb_system_log` 에 남긴다 — 인원명·출입그룹명을 **스냅샷**으로 적재해 매핑 행이 삭제돼도 "언제 누구에게 어느 권한을 줬다 회수했는지"가 보존되도록 한다.

| 컬럼 | 타입 | PK | 설명 | 비고 |
|------|------|----|------|------|
| person_id | nvarchar(30) | Y | 인원ID | → `tb_person.person_id` |
| ac_group_id | int | Y | 출입그룹ID | → `tb_ac_group.ac_group_id` |
| reg_dt / mod_dt | datetime2(0) | | 입력/수정일자 | 기본 getdate() |

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
- `tb_car.car_manager_id` → `tb_login_user.user_id` (논리 관계, FK 미강제. 관리자 삭제도 소프트 삭제 전제)
- `tb_company.company_type` → `tb_common`(cmm_id='CO')
- `tb_person.company_code` → `tb_company.company_code`, `tb_person.title_code` → `tb_common`(cmm_id='UT')
- `tb_person.person_type`(발급유형: 정규/임시/상주) → `tb_common`(cmm_id='PT')
- `tb_person.status_code`(상태) → `tb_common`(cmm_id='PS')
- `tb_person_photo.person_id` → `tb_person.person_id` (1:1)
- `tb_person_file.person_id` → `tb_person.person_id` (1:N — 문서 종류당 1건)
- `tb_card.person_id` → `tb_person.person_id` (**1:N** — 인원 1명이 카드 여러 장)
- `tb_card.card_type` → `tb_common`(cmm_id='CDT'), `card_status` → (cmm_id='CS'), `issue_type` → (cmm_id='IS')
- `tb_person` ←(`tb_person_ac_group`)→ `tb_ac_group` (**N:M** 인원별 출입권한 부여)

## 마이그레이션
- 스키마 원천: `D:\작업\2026\청주공항\설계\table.xlsx` (설계) → 본 문서 → 실행 스크립트 `sql/`.
- **DDL: `sql/ddl/01_tables.sql`**, **seed: `sql/seed/02_seed.sql`**(공통코드 AT/LO, 메뉴, 관리자 계정 admin/admin123).
- TODO: 스키마 형상관리 자동화(Flyway 등) 도입 여부.

## 관련 문서
[backend.md](backend.md) · [architecture.md](architecture.md) · [security.md](security.md) · [integration.md](integration.md)
