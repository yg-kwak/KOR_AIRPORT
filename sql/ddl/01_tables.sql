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
  ac_group_level     int NULL,
  ac_group_order     int NULL,
  biostar_ac_id      int NULL,
  biostar_ac_name    nvarchar(50) NULL,
  reg_dt             datetime2(0) NOT NULL DEFAULT getdate(),
  mod_dt             datetime2(0) NOT NULL DEFAULT getdate(),
  CONSTRAINT PK_tb_ac_group PRIMARY KEY (ac_group_id)
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
