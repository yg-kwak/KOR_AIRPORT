/* CJAirPort DDL — MSSQL 단일. 스키마 원천: docs/database.md
   실행 전제: 대상 DB(CJ_AIRPORT) 생성 후 이 스크립트 실행. */

/* 사용자/로그인 계정 */
CREATE TABLE dbo.tb_login_user (
  user_id            nvarchar(30)  NOT NULL,
  user_name          nvarchar(255) NULL,        -- ARIA 암호화
  password           nvarchar(255) NULL,        -- ARIA 암호화
  dept_name          nvarchar(50)  NULL,
  use_yn             nchar(1)      NOT NULL DEFAULT 'Y',
  root_yn            nchar(1)      NOT NULL DEFAULT 'N',
  auth_id            int           NULL,
  login_fail_cnt     int           NOT NULL DEFAULT 0,
  password_change_dt datetime2(0)  NULL,
  start_menu_id      int           NULL,
  work_location_code nvarchar(10)  NULL,
  work_type          nvarchar(30)  NULL,
  desk_ip            nvarchar(30)  NULL,
  dev_id             nvarchar(30)  NULL,
  reg_dt             datetime2(0)  NOT NULL DEFAULT getdate(),
  mod_dt             datetime2(0)  NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_login_user PRIMARY KEY (user_id),
  CONSTRAINT CHK_tb_login_user_use_yn  CHECK (use_yn IN ('Y','N')),
  CONSTRAINT CHK_tb_login_user_root_yn CHECK (root_yn IN ('Y','N'))
);

/* 메뉴 (트리) — menu_id 는 고정값 부여(비 IDENTITY) */
CREATE TABLE dbo.tb_menu (
  menu_id        int          NOT NULL,
  menu_name      nvarchar(100) NULL,
  parent_menu_id int          NULL,
  menu_url       nvarchar(255) NULL,
  menu_level     int          NULL,
  menu_order     int          NULL,
  menu_icon      nvarchar(30) NULL,
  use_yn         nchar(1)     NOT NULL DEFAULT 'Y',
  CONSTRAINT PK_tb_menu PRIMARY KEY (menu_id)
);

/* 권한(그룹) */
CREATE TABLE dbo.tb_menu_auth (
  auth_id   int IDENTITY(1,1) NOT NULL,
  auth_name nvarchar(100) NULL,
  reg_dt    datetime2(0) NOT NULL DEFAULT getdate(),
  mod_dt    datetime2(0) NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_menu_auth PRIMARY KEY (auth_id)
);

/* 권한별 메뉴 CRUD 권한 */
CREATE TABLE dbo.tb_menu_auth_detail (
  auth_id     int      NOT NULL,
  menu_id     int      NOT NULL,
  read_auth   nchar(1) NOT NULL DEFAULT 'N',
  create_auth nchar(1) NOT NULL DEFAULT 'N',
  update_auth nchar(1) NOT NULL DEFAULT 'N',
  delete_auth nchar(1) NOT NULL DEFAULT 'N',
  reg_dt      datetime2(0) NOT NULL DEFAULT getdate(),
  mod_dt      datetime2(0) NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_menu_auth_detail PRIMARY KEY (auth_id, menu_id),
  CONSTRAINT CHK_mad_read   CHECK (read_auth   IN ('Y','N')),
  CONSTRAINT CHK_mad_create CHECK (create_auth IN ('Y','N')),
  CONSTRAINT CHK_mad_update CHECK (update_auth IN ('Y','N')),
  CONSTRAINT CHK_mad_delete CHECK (delete_auth IN ('Y','N'))
);

/* 공통 코드 */
CREATE TABLE dbo.tb_common (
  cmm_id      nvarchar(50)  NOT NULL,
  cmm_name    nvarchar(100) NULL,
  code_id     nvarchar(50)  NOT NULL,
  code_name   nvarchar(100) NULL,
  code_tag    nvarchar(50)  NULL,
  code_remark nvarchar(100) NULL,
  user_input  nchar(1)      NULL DEFAULT 'N',
  use_yn      nchar(1)      NOT NULL DEFAULT 'Y',
  CONSTRAINT PK_tb_common PRIMARY KEY (cmm_id, code_id),
  CONSTRAINT CHK_tb_common_use_yn CHECK (use_yn IN ('Y','N'))
);

/* 시스템 설정 (BiostarX 연동정보, 단일 행) */
CREATE TABLE dbo.tb_system (
  biostar_ip nvarchar(50)  NULL,
  biostar_id nvarchar(100) NULL,
  biostar_pw nvarchar(255) NULL,
  reg_dt     datetime2(0)  NOT NULL DEFAULT getdate(),
  mod_dt     datetime2(0)  NOT NULL DEFAULT getdate()
);

/* 출입권한 그룹 (BiostarX 매핑) */
CREATE TABLE dbo.tb_ac_group (
  ac_group_id        int IDENTITY(1,1) NOT NULL,
  ac_group_name      nvarchar(50) NULL,
  parent_ac_group_id int NULL,
  ar_code            nvarchar(50) NULL,
  ac_group_level     int NULL,
  ac_group_order     int NULL,
  biostar_ac_id      int NULL,
  biostar_ac_name    nvarchar(50) NULL,
  reg_dt             datetime2(0) NOT NULL DEFAULT getdate(),
  mod_dt             datetime2(0) NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_ac_group PRIMARY KEY (ac_group_id)
);

/* 차량 (1:1 — 차량마다 관리자 1명. FK 미강제: 기존 테이블과 동일하게 논리적 관계만 둔다) */
CREATE TABLE dbo.tb_car (
  car_id         int IDENTITY(1,1) NOT NULL,
  car_no         nvarchar(20)  NOT NULL,               -- 차량번호 (예: 12가3456)
  car_name       nvarchar(50)  NULL,                   -- 차량명칭
  car_type       nvarchar(30)  NULL,                   -- 차종
  car_manager_id nvarchar(30)  NULL,                   -- 관리자ID (→ tb_login_user.user_id, FK 미강제)
  del_yn         nchar(1)      NOT NULL DEFAULT 'N',   -- 삭제여부(소프트 삭제): 삭제 시 'Y', 조회는 'N'
  reg_dt         datetime2(0)  NOT NULL DEFAULT getdate(),
  mod_dt         datetime2(0)  NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_car PRIMARY KEY (car_id),
  CONSTRAINT CHK_tb_car_del_yn CHECK (del_yn IN ('Y','N'))
);

/* 기관 (기관관리) — PK 업무코드. 삭제=del_yn 소프트 삭제, 활성/비활성=use_yn */
CREATE TABLE dbo.tb_company (
  company_code     nvarchar(30)  NOT NULL,                -- 기관코드 (PK, 업무코드)
  company_type     nvarchar(50)  NULL,                    -- 기관구분 → tb_common(cmm_id='CO').code_id
  company_name     nvarchar(100) NULL,                    -- 기관명
  ceo_name         nvarchar(255) NULL,                    -- 대표자 (ARIA 암호화)
  tel              nvarchar(30)  NULL,                    -- 연락처
  fax              nvarchar(30)  NULL,                    -- FAX
  addr             nvarchar(200) NULL,                    -- 주소
  service_start_dt datetime2(0)  NULL,                    -- 용역시작일
  service_end_dt   datetime2(0)  NULL,                    -- 용역종료일
  biostar_group_id int           NULL,                    -- BiostarX 사용자 그룹 ID (기관 ↔ user group 연동, integration.md)
  use_yn           nchar(1)      NOT NULL DEFAULT 'Y',    -- 사용유무 (UI 활성/비활성)
  del_yn           nchar(1)      NOT NULL DEFAULT 'N',    -- 삭제유무 (소프트 삭제: 삭제 시 'Y')
  reg_dt           datetime2(0)  NOT NULL DEFAULT getdate(),
  mod_dt           datetime2(0)  NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_company PRIMARY KEY (company_code),
  CONSTRAINT CHK_tb_company_use_yn CHECK (use_yn IN ('Y','N')),
  CONSTRAINT CHK_tb_company_del_yn CHECK (del_yn IN ('Y','N'))
);

/* 인원 (출입 대상자) — tb_login_user(로그인 계정)와 다른 개체.
   성명·생년월일·연락처는 ARIA 암호문이라 부분검색·정렬 불가(검색은 인원ID/기관/직위 등으로) */
CREATE TABLE dbo.tb_person (
  person_id          nvarchar(30)   NOT NULL,               -- 인원ID (PK)
  person_name        nvarchar(255)  NULL,                   -- 성명 (ARIA 암호화)
  birth_date         nvarchar(255)  NULL,                   -- 생년월일 (ARIA 암호화)
  person_phone       nvarchar(255)  NULL,                   -- 연락처 (ARIA 암호화)
  company_code       nvarchar(30)   NULL,                   -- → tb_company.company_code
  title_code         nvarchar(50)   NULL,                   -- 직위코드 → tb_common(cmm_id='UT')
  person_type        nvarchar(50)   NULL,                   -- 발급유형(정규/임시/상주 등) → tb_common(cmm_id='PT')
  status_code        nvarchar(50)   NULL,                   -- 상태 → tb_common(cmm_id='PS')
  main_task          nvarchar(200)  NULL,                   -- 주요업무
  id_check_dt        datetime2(0)   NULL,                   -- 신원조회 회보일
  id_check_file      nvarchar(500)  NULL,                   -- 회보근거문서 (경로/파일명, 1건)
  security_edu_dt    datetime2(0)   NULL,                   -- 보안교육 합격일
  security_edu_score int            NULL,                   -- 보안교육 점수
  final_approve_dt   datetime2(0)   NULL,                   -- 최종승인일
  approve_file       nvarchar(500)  NULL,                   -- 승인근거문서 (경로/파일명, 1건)
  access_start_dt    datetime2(0)   NULL,                   -- 출입시작일
  access_end_dt      datetime2(0)   NULL,                   -- 출입종료일
  remark             nvarchar(1000) NULL,                   -- 메모
  biostar_user_id    nvarchar(50)   NULL,                   -- BiostarX 사용자ID
  use_yn             nchar(1)       NOT NULL DEFAULT 'Y',
  del_yn             nchar(1)       NOT NULL DEFAULT 'N',
  reg_dt             datetime2(0)   NOT NULL DEFAULT getdate(),
  mod_dt             datetime2(0)   NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_person PRIMARY KEY (person_id),
  CONSTRAINT CHK_tb_person_use_yn CHECK (use_yn IN ('Y','N')),
  CONSTRAINT CHK_tb_person_del_yn CHECK (del_yn IN ('Y','N'))
);

/* 인원 등록사진 — 본 테이블에서 분리(행 크기·목록 성능·생체정보 보호). tb_person 과 1:1 */
CREATE TABLE dbo.tb_person_photo (
  person_id  nvarchar(30)  NOT NULL,                        -- → tb_person.person_id
  photo_data nvarchar(max) NULL,                            -- BiostarX 등록사진 (BASE64)
  reg_dt     datetime2(0)  NOT NULL DEFAULT getdate(),
  mod_dt     datetime2(0)  NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_person_photo PRIMARY KEY (person_id)
);

/* 카드 (인원 1 : 카드 N). 카드 상태는 card_status 단일 컬럼이 진실의 원천 —
   분실/반납 등 '언제' 는 아래 날짜 컬럼이 기록한다 */
CREATE TABLE dbo.tb_card (
  card_id            int IDENTITY(1,1) NOT NULL,            -- 카드ID (PK)
  card_type          nvarchar(50)   NULL,                   -- 카드구분 → tb_common(cmm_id='CDT')
  card_name          nvarchar(100)  NULL,                   -- 카드명칭
  card_status        nvarchar(50)   NULL,                   -- 카드상태 → tb_common(cmm_id='CS')
  fee_paid_dt        datetime2(0)   NULL,                   -- 발급료 납부일
  issue_dt           datetime2(0)   NULL,                   -- 카드발급일
  issue_type         nvarchar(50)   NULL,                   -- 발급구분 → tb_common(cmm_id='IS')
  issue_reason       nvarchar(500)  NULL,                   -- 발급근거
  lost_dt            datetime2(0)   NULL,                   -- 카드분실일
  return_dt          datetime2(0)   NULL,                   -- 카드반납일
  remark             nvarchar(1000) NULL,                   -- 메모
  person_id          nvarchar(30)   NULL,                   -- 인원ID → tb_person (FK 미강제)
  biostar_card_id    nvarchar(50)   NULL,                   -- BiostarX 카드ID
  biostar_card_value nvarchar(255)  NULL,                   -- BiostarX 카드값 (암호화 안 함)
  use_yn             nchar(1)       NOT NULL DEFAULT 'Y',
  del_yn             nchar(1)       NOT NULL DEFAULT 'N',
  reg_dt             datetime2(0)   NOT NULL DEFAULT getdate(),
  mod_dt             datetime2(0)   NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_card PRIMARY KEY (card_id),
  CONSTRAINT CHK_tb_card_use_yn CHECK (use_yn IN ('Y','N')),
  CONSTRAINT CHK_tb_card_del_yn CHECK (del_yn IN ('Y','N'))
);

/* 인원 출입그룹 (인원 N : 출입그룹 M 매핑).
   복합 PK 가 동일 인원-그룹 중복 부여를 막는다. 부여/회수 이력은 tb_system_log 에 스냅샷으로 남긴다 */
CREATE TABLE dbo.tb_person_ac_group (
  person_id   nvarchar(30) NOT NULL,                        -- → tb_person.person_id
  ac_group_id int          NOT NULL,                        -- → tb_ac_group.ac_group_id
  reg_dt      datetime2(0) NOT NULL DEFAULT getdate(),
  mod_dt      datetime2(0) NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_person_ac_group PRIMARY KEY (person_id, ac_group_id)
);

/* 감사추적 (이력) */
CREATE TABLE dbo.tb_system_log (
  log_id        bigint IDENTITY(1,1) NOT NULL,
  user_id       nvarchar(30)  NOT NULL,
  user_name     nvarchar(200) NULL,
  action_type   nvarchar(50)  NULL,
  menu_id       int           NULL,
  action_detail nvarchar(1000) NULL,
  remark        nvarchar(1000) NULL,
  reg_dt        datetime2(0)  NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_system_log PRIMARY KEY (log_id)
);
