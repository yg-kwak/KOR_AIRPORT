/* ============================================================================
   CJAirPort — 현장 설치용 통합 스크립트 (SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 대상 서버에 접속 (DB 를 만들므로 sysadmin 권한 계정)
     2) 이 파일을 열고(Ctrl+O) → 실행(F5)
        · sqlcmd 모드 필요 없음. 다른 파일을 부르지 않는다(전부 이 안에 있다).
     3) 마지막 결과 그리드에서 개수를 확인한다.

   재실행해도 안전하다
     · 이미 있는 테이블·인덱스는 만들지 않는다.
     · 기본 데이터는 계정이 하나도 없을 때만 넣는다(운영 데이터를 덮지 않는다).
     · 운영 중인 DB 를 최신으로 올릴 때도 이 파일을 그대로 쓰면 된다([3] 이 없는 컬럼만 추가).

   주의
     · 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 에서
       [파일 → 열기 → 파일] 대화상자의 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.
     · ARIA 키(app.crypto.aria-key)는 이 기본 데이터를 만든 키와 같아야 한다.
       다르면 기본 계정의 성명·비밀번호가 복호화되지 않아 로그인할 수 없다.
   ============================================================================ */
SET NOCOUNT ON;
GO

/* ===========================================================================
   [1] 데이터베이스
   =========================================================================== */
IF DB_ID('CJ_AIRPORT') IS NULL
BEGIN
  PRINT '[1] DB 생성: CJ_AIRPORT';
  EXEC('CREATE DATABASE CJ_AIRPORT COLLATE Korean_Wansung_CI_AS');
END
ELSE
  PRINT '[1] DB 이미 있음: CJ_AIRPORT (그대로 사용)';
GO

USE CJ_AIRPORT;
GO

/* ===========================================================================
   [2] 테이블·인덱스 — 없는 것만 만든다
   =========================================================================== */
PRINT '[2] 테이블/인덱스 확인';
GO

/* CJAirPort DDL — MSSQL 단일. 스키마 원천: docs/database.md
   실행 전제: 대상 DB(CJ_AIRPORT) 생성 후 이 스크립트 실행. */

/* 사용자/로그인 계정 */
IF OBJECT_ID('dbo.tb_login_user', 'U') IS NULL
BEGIN
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
END
GO


/* 메뉴 (트리) — menu_id 는 고정값 부여(비 IDENTITY) */
IF OBJECT_ID('dbo.tb_menu', 'U') IS NULL
BEGIN
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
END
GO


/* 권한(그룹) */
IF OBJECT_ID('dbo.tb_menu_auth', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_menu_auth (
    auth_id   int IDENTITY(1,1) NOT NULL,
    auth_name nvarchar(100) NULL,
    reg_dt    datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt    datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_menu_auth PRIMARY KEY (auth_id)
  );
END
GO


/* 권한별 메뉴 CRUD 권한 */
IF OBJECT_ID('dbo.tb_menu_auth_detail', 'U') IS NULL
BEGIN
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
END
GO


/* 공통 코드 */
IF OBJECT_ID('dbo.tb_common', 'U') IS NULL
BEGIN
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
END
GO


/* 시스템 설정 (BiostarX 연동정보, 단일 행) */
IF OBJECT_ID('dbo.tb_system', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_system (
    biostar_ip nvarchar(50)  NULL,
    biostar_id nvarchar(100) NULL,
    biostar_pw nvarchar(255) NULL,
    reg_dt     datetime2(0)  NOT NULL DEFAULT getdate(),
    mod_dt     datetime2(0)  NOT NULL DEFAULT getdate()
  );
END
GO


/* 출입권한 그룹 (BiostarX 매핑) */
IF OBJECT_ID('dbo.tb_ac_group', 'U') IS NULL
BEGIN
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
END
GO


/* 차량 (1:1 — 차량마다 관리자 1명. FK 미강제: 기존 테이블과 동일하게 논리적 관계만 둔다) */
IF OBJECT_ID('dbo.tb_car', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_car (
    car_id         int IDENTITY(1,1) NOT NULL,
    car_no         nvarchar(20)  NOT NULL,               -- 차량번호 (예: 12가3456)
    car_name       nvarchar(50)  NULL,                   -- 차량명칭
    car_type       nvarchar(30)  NULL,                   -- 차종
    car_manager_id nvarchar(30)  NULL,                   -- 차량관리자 (→ tb_person.person_id, 소속 기관의 정규인원. FK 미강제)
    company_code   nvarchar(30)  NULL,                   -- 소속 기관 (→ tb_company.company_code, FK 미강제). 기관차량등록에서 채운다
    del_yn         nchar(1)      NOT NULL DEFAULT 'N',   -- 삭제여부(소프트 삭제): 삭제 시 'Y', 조회는 'N'
    reg_dt         datetime2(0)  NOT NULL DEFAULT getdate(),
    mod_dt         datetime2(0)  NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_car PRIMARY KEY (car_id),
    CONSTRAINT CHK_tb_car_del_yn CHECK (del_yn IN ('Y','N'))
  );
END
GO


/* 차량 출입구역 (차량 1 : 구역 N) — 인원의 tb_person_ac_group 과 같은 역할.
   차량은 BiostarX 출입그룹이 아니라 공통코드(cmm_id='CAR') 구역으로 관리한다 */
IF OBJECT_ID('dbo.tb_car_ac_group', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_car_ac_group (
    car_id  int          NOT NULL,                            -- → tb_car.car_id
    code_id nvarchar(50) NOT NULL,                            -- 출입구역 → tb_common(cmm_id='CAR').code_id
    reg_dt  datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt  datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_car_ac_group PRIMARY KEY (car_id, code_id)
  );
END
GO


/* 기관 (기관관리) — PK 업무코드. 삭제=del_yn 소프트 삭제, 활성/비활성=use_yn */
IF OBJECT_ID('dbo.tb_company', 'U') IS NULL
BEGIN
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
END
GO


/* 인원 (출입 대상자) — tb_login_user(로그인 계정)와 다른 개체.
   성명·생년월일·연락처는 ARIA 암호문이라 부분검색·정렬 불가(검색은 인원ID/기관/직위 등으로) */
IF OBJECT_ID('dbo.tb_person', 'U') IS NULL
BEGIN
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
    affiliation        nvarchar(100)  NULL,                   -- 소속 (방문객 자유입력; 정규는 tb_company 사용)
    id_check_dt        datetime2(0)   NULL,                   -- 신원조회 회보일
    id_check_file      nvarchar(500)  NULL,                   -- 회보근거문서 파일명 (실체는 tb_person_file)
    security_edu_dt    datetime2(0)   NULL,                   -- 보안교육 합격일
    security_edu_score int            NULL,                   -- 보안교육 점수
    final_approve_dt   datetime2(0)   NULL,                   -- 최종승인일
    approve_file       nvarchar(500)  NULL,                   -- 승인근거문서 파일명 (실체는 tb_person_file)
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
END
GO


/* 인원 등록사진 — 본 테이블에서 분리(행 크기·목록 성능·생체정보 보호). tb_person 과 1:1 */
IF OBJECT_ID('dbo.tb_person_photo', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_person_photo (
    person_id  nvarchar(30)  NOT NULL,                        -- → tb_person.person_id
    photo_data nvarchar(max) NULL,                            -- BiostarX 등록사진 (BASE64)
    reg_dt     datetime2(0)  NOT NULL DEFAULT getdate(),
    mod_dt     datetime2(0)  NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_person_photo PRIMARY KEY (person_id)
  );
END
GO


/* 인원 증빙문서 (회보근거·승인근거) — 종류별 1건. 파일 실체를 DB 에 보관해 백업을 일원화하고
   업로드 경로 설정·고아파일 문제를 없앤다. tb_person.id_check_file/approve_file 은 표시용 원본 파일명 */
IF OBJECT_ID('dbo.tb_person_file', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_person_file (
    person_id nvarchar(30)   NOT NULL,                          -- → tb_person.person_id
    file_type nvarchar(20)   NOT NULL,                          -- 문서구분: ID_CHECK(회보근거) / APPROVE(승인근거)
    file_name nvarchar(260)  NOT NULL,                          -- 원본 파일명
    file_size int            NOT NULL,                          -- 파일 크기(byte)
    file_data varbinary(max) NOT NULL,                          -- 파일 실체
    reg_dt    datetime2(0)   NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_person_file PRIMARY KEY (person_id, file_type)
  );
END
GO


/* 카드 (인원 1 : 카드 N). 카드 상태는 card_status 단일 컬럼이 진실의 원천 —
   분실/반납 등 '언제' 는 아래 날짜 컬럼이 기록한다 */
IF OBJECT_ID('dbo.tb_card', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_card (
    card_id            int IDENTITY(1,1) NOT NULL,            -- 카드ID (PK)
    card_type          nvarchar(50)   NULL,                   -- 카드구분 → tb_common(cmm_id='CDT')
    card_name          nvarchar(100)  NULL,                   -- 카드명칭
    card_status        nvarchar(50)   NULL,                   -- 카드상태 → tb_common(cmm_id='CS')
    pass_type          nvarchar(50)   NULL,                   -- 패스구분 → tb_common(cmm_id='PT')
    fee_paid_dt        datetime2(0)   NULL,                   -- 발급료 납부일
    issue_dt           datetime2(0)   NULL,                   -- 카드발급일
    issue_type         nvarchar(50)   NULL,                   -- 발급구분 → tb_common(cmm_id='IS')
    issue_reason       nvarchar(500)  NULL,                   -- 발급근거
    lost_dt            datetime2(0)   NULL,                   -- 카드분실일
    return_dt          datetime2(0)   NULL,                   -- 카드반납일
    remark             nvarchar(1000) NULL,                   -- 메모
    person_id          nvarchar(30)   NULL,                   -- 인원ID → tb_person (FK 미강제). 인원 카드일 때
    car_id             int            NULL,                   -- 차량ID → tb_car (FK 미강제). 차량 카드일 때
    biostar_card_id    nvarchar(50)   NULL,                   -- BiostarX 카드ID
    biostar_card_value nvarchar(255)  NULL,                   -- BiostarX 카드값 (암호화 안 함)
    use_yn             nchar(1)       NOT NULL DEFAULT 'Y',
    del_yn             nchar(1)       NOT NULL DEFAULT 'N',
    reg_dt             datetime2(0)   NOT NULL DEFAULT getdate(),
    mod_dt             datetime2(0)   NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_card PRIMARY KEY (card_id),
    CONSTRAINT CHK_tb_card_use_yn CHECK (use_yn IN ('Y','N')),
    CONSTRAINT CHK_tb_card_del_yn CHECK (del_yn IN ('Y','N')),
    -- 한 카드는 인원 또는 차량 중 한쪽에만 귀속(둘 다 채워질 수 없다)
    CONSTRAINT CHK_tb_card_holder CHECK (person_id IS NULL OR car_id IS NULL)
  );
END
GO


/* 카드번호는 실물 카드와 1:1 — 살아 있는 행끼리 중복될 수 없다(회수/재사용도 같은 행을 쓴다).
   필터 인덱스는 QUOTED_IDENTIFIER ON 이어야 만들어진다(sqlcmd 는 기본 OFF) */
SET QUOTED_IDENTIFIER ON;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_tb_card_value' AND object_id = OBJECT_ID('dbo.tb_card'))
  CREATE UNIQUE INDEX UX_tb_card_value ON dbo.tb_card (biostar_card_value)
  WHERE del_yn = 'N' AND biostar_card_value IS NOT NULL;

/* 인원 출입그룹 (인원 N : 출입그룹 M 매핑).
   복합 PK 가 동일 인원-그룹 중복 부여를 막는다. 부여/회수 이력은 tb_system_log 에 스냅샷으로 남긴다 */
IF OBJECT_ID('dbo.tb_person_ac_group', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_person_ac_group (
    person_id   nvarchar(30) NOT NULL,                        -- → tb_person.person_id
    ac_group_id int          NOT NULL,                        -- → tb_ac_group.ac_group_id
    reg_dt      datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt      datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_person_ac_group PRIMARY KEY (person_id, ac_group_id)
  );
END
GO


/* 방문/작업 그룹 (임시·장기 출입). 정규(tb_company 기반)와 달리 BiostarX 기관 그룹을
   만들지 않고 PT(임시/장기) 부모 그룹 아래로 편입한다(integration.md). 업체는 자유입력 text */
IF OBJECT_ID('dbo.tb_visit', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_visit (
    visit_no      int IDENTITY(1,1) NOT NULL,           -- 그룹번호 (PK)
    visit_type    nvarchar(50)   NULL,                  -- 유형(임시/장기 등) → tb_common(cmm_id='PT'). 소속 인원 person_type·카드 pass_type 결정
    status_code   nvarchar(50)   NULL,                  -- 방문상태 → tb_common(cmm_id='VS')
    work_purpose  nvarchar(500)  NULL,                  -- 작업목적
    permit_dt     datetime2(0)   NULL,                  -- 작업 허가일자
    work_start_dt datetime2(0)   NULL,                  -- 작업기간 시작
    work_end_dt   datetime2(0)   NULL,                  -- 작업기간 종료
    company_type  nvarchar(100)  NULL,                  -- 업체구분 (자유입력 text)
    company_name  nvarchar(100)  NULL,                  -- 업체명 (자유입력 text)
    receiver      nvarchar(100)  NULL,                  -- 수령자 (방문객, text)
    returner      nvarchar(100)  NULL,                  -- 반납자 (방문객, text)
    evidence_file nvarchar(260)  NULL,                  -- 근거문서 파일명 (text)
    checkout_dt   datetime2(0)   NULL,                  -- 퇴실 완료 시각 (파기 기준)
    remark        nvarchar(1000) NULL,                  -- 메모
    del_yn        nchar(1)       NOT NULL DEFAULT 'N',  -- 삭제여부 (소프트 삭제)
    reg_dt        datetime2(0)   NOT NULL DEFAULT getdate(),
    mod_dt        datetime2(0)   NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_visit PRIMARY KEY (visit_no),
    CONSTRAINT CHK_tb_visit_del_yn CHECK (del_yn IN ('Y','N'))
  );
END
GO


/* 인솔자 (1 visit : N) — 정규인원(tb_person, person_type='PT01').
   같은 인솔자가 여러 visit 에 지정될 수 있어 seq 로 구분(임시 중복금지 등은 visit_type 별 서비스 검증) */
IF OBJECT_ID('dbo.tb_visit_manager', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_visit_manager (
    visit_no  int          NOT NULL,                    -- → tb_visit.visit_no
    seq       int          NOT NULL,                    -- 순번
    person_id nvarchar(30) NOT NULL,                    -- 정규사용자ID → tb_person.person_id (PT01)
    reg_dt    datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt    datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_visit_manager PRIMARY KEY (visit_no, seq)
  );
END
GO


/* 방문 인원 명단 (visit 1 : 인원 N) — 인원은 tb_person(person_type=방문유형) */
IF OBJECT_ID('dbo.tb_visit_person', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_visit_person (
    visit_no     int          NOT NULL,                 -- → tb_visit.visit_no
    person_id    nvarchar(30) NOT NULL,                 -- → tb_person.person_id
    last_card_no nvarchar(255) NULL,                    -- 마지막 배정 카드번호 스냅샷(회수/재사용 후에도 보존)
    checkout_dt  datetime2(0) NULL,                    -- 개별 퇴실 일시(NULL=재실). 퇴실하면 카드 재발급 불가
    reg_dt       datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt       datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_visit_person PRIMARY KEY (visit_no, person_id)
  );
END
GO


/* 방문 차량 명단 (visit 1 : 차량 N) */
IF OBJECT_ID('dbo.tb_visit_car', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_visit_car (
    visit_no int          NOT NULL,                     -- → tb_visit.visit_no
    car_id   int          NOT NULL,                     -- → tb_car.car_id
    reg_dt   datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt   datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_visit_car PRIMARY KEY (visit_no, car_id)
  );
END
GO


/* 공통 인원구역 — tb_ac_group 최상위 노드 선택. 방문유형(PT).code_remark='Y' 면 하위 세부 트리도
   선택 가능, 아니면 최상위만(서비스에서 강제). 승인 시 각 방문 인원에게 최상위→하위 biostar 매핑
   그룹으로 확장해 tb_person_ac_group 에 기록한다(integration.md) */
IF OBJECT_ID('dbo.tb_visit_ac_group', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_visit_ac_group (
    visit_no    int          NOT NULL,                  -- → tb_visit.visit_no
    ac_group_id int          NOT NULL,                  -- → tb_ac_group.ac_group_id
    reg_dt      datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt      datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_visit_ac_group PRIMARY KEY (visit_no, ac_group_id)
  );
END
GO


/* 공통 차량구역 — 차량은 BiostarX 대상이 아니므로 CAR 공통코드(tb_car_ac_group 과 동일).
   승인 시 각 방문 차량에게 tb_car_ac_group 으로 복제 */
IF OBJECT_ID('dbo.tb_visit_car_ac_group', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_visit_car_ac_group (
    visit_no int          NOT NULL,                     -- → tb_visit.visit_no
    code_id  nvarchar(50) NOT NULL,                     -- 출입구역 → tb_common(cmm_id='CAR').code_id
    reg_dt   datetime2(0) NOT NULL DEFAULT getdate(),
    mod_dt   datetime2(0) NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_visit_car_ac_group PRIMARY KEY (visit_no, code_id)
  );
END
GO


/* 감사추적 (이력) */
IF OBJECT_ID('dbo.tb_system_log', 'U') IS NULL
BEGIN
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
END
GO

/* ===========================================================================
   [3] 기본 데이터 — 계정이 하나도 없을 때만(= 최초 설치) 넣는다
       공통코드·메뉴·권한·계정은 서로 참조하므로 통째로 한 번에 넣어야 한다.
   =========================================================================== */
IF NOT EXISTS (SELECT 1 FROM dbo.tb_login_user)
BEGIN
  PRINT '[3] 기본 데이터 입력(공통코드·메뉴·권한·계정)';

  /* CJAirPort seed — 최소 운영 데이터. 01_tables.sql 실행 후 수행.
     비밀번호/성명은 ARIA-256(개발키 01234567890123456789012345678901)로 암호화된 값.
     ⚠️ 운영 배포 시 실제 키로 재생성한 값으로 교체할 것. (docs/security.md)
     한글 리터럴은 nvarchar 안전을 위해 N'' 접두 사용. */

  /* 공통코드: 감사유형(AT) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('AT', N'감사유형', 'MENU',   N'메뉴접속', 'Y'),
    ('AT', N'감사유형', 'READ',   N'조회',     'Y'),
    ('AT', N'감사유형', 'CREATE', N'등록',     'Y'),
    ('AT', N'감사유형', 'UPDATE', N'수정',     'Y'),
    ('AT', N'감사유형', 'DELETE', N'삭제',     'Y'),
    ('AT', N'감사유형', 'DOWNLOAD', N'다운로드', 'Y'),
    ('AT', N'감사유형', 'LOGIN',  N'로그인',  'Y'),
    ('AT', N'감사유형', 'LOGOUT', N'로그아웃', 'Y');

  /* 공통코드: 근무지역(LO) 예시 — 시스템 코드(user_input=N, 기본값) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('LO', N'근무지역', 'T1', N'여객터미널', 'Y'),
    ('LO', N'근무지역', 'T2', N'화물터미널', 'Y');

  /* 사용자 추가 허용 구분(방문사유 VR) — user_input='Y' 로 개설. 화면 등록 시 select 에 노출됨. */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, user_input, use_yn) VALUES
    ('VR', N'방문사유', 'MEETING', N'회의', 'Y', 'Y'),
    ('VR', N'방문사유', 'WORK',    N'공사', 'Y', 'Y');

  /* 출입구역(AR) — 출입권한관리 트리의 최상위(tb_ac_group 동기화 기준). code_id → ar_code */

  /* 출입구역(AR) — 출입권한관리 트리의 최상위(tb_ac_group 동기화 기준). code_id → ar_code */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, user_input, use_yn) VALUES
     ('AR','출입구역','AR01','인원구역1','N','Y'),
     ('AR','출입구역','AR02','인원구역2','N','Y'),
     ('AR','출입구역','AR03','인원구역3','N','Y'),
     ('AR','출입구역','AR04','인원구역4','N','Y'),
     ('AR','출입구역','AR05','인원구역5','N','Y'),
     ('AR','출입구역','AR06','인원구역6','N','Y'),
     ('AR','출입구역','AR07','인원구역7','N','Y');

INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, user_input, use_yn) VALUES
     ('CAR','차량출입구역','CAR01','차량구역1','N','Y'),
     ('CAR','차량출입구역','CAR02','차량구역2','N','Y');

INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, user_input, use_yn) VALUES
                                                                                         ('CDT','카드종류','CDT01','인원','N','Y'),
                                                                                         ('CDT','카드종류','CDT02','차량','N','Y');
  /* 공통코드: 차종(CT) — 차량등록관리에서 사용. 시스템 코드(user_input=N 기본) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('CT', N'차종', '01', N'승용차', 'Y'),
    ('CT', N'차종', '02', N'SUV',    'Y'),
    ('CT', N'차종', '03', N'화물차', 'Y'),
    ('CT', N'차종', '04', N'트럭',   'Y'),
    ('CT', N'차종', '05', N'기타',   'Y');

  /* 공통코드: 기관구분(CO) — 기관등록관리에서 사용. 시스템 코드(user_input=N 기본) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('CO', N'기관구분', '11', N'기관',   'Y'),
    ('CO', N'기관구분', '22', N'국영',   'Y'),
    ('CO', N'기관구분', '33', N'공사',   'Y'),
    ('CO', N'기관구분', '44', N'항공사', 'Y'),
    ('CO', N'기관구분', '55', N'업체',   'Y');

  /* 인원구분(PT) — code_tag = 대응하는 발급구분(PTD) 코드.
     code_remark = 방문 출입구역 선택 시 하위 세부 트리 노출 여부('Y'=하위트리 선택 가능, 그 외=최상위만).
     ⚠️ 값은 운영 정책에 맞게 조정: 임시=최상위만, 장기·상주=세부까지 (tb_visit_ac_group 규칙) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, code_remark, use_yn) VALUES
    ('PT', N'인원구분', 'PT01', N'정규', 'PTD01', 'N', 'Y'),
    ('PT', N'인원구분', 'PT02', N'임시', 'PTD02', 'N', 'Y'),
    ('PT', N'인원구분', 'PT03', N'장기', 'PTD03', 'Y', 'Y'),
    ('PT', N'인원구분', 'PT04', N'상주', 'PTD03', 'Y', 'Y'),
    ('PT', N'인원구분', 'PT05', N'순찰', 'PTD03', 'Y', 'Y'),
    ('PT', N'인원구분', 'PT06', N'대여', 'PTD03', 'Y', 'Y');

  /* 발급구분(PTD) — code_tag = BiostarX 부모 사용자그룹 ID.
     ⚠️ BiostarX 환경마다 다르므로 대상 서버의 실제 그룹 ID 로 교체할 것. (integration.md) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn) VALUES
    ('PTD', N'발급구분', 'PTD01', N'정규등록', '14227', 'Y'),
    ('PTD', N'발급구분', 'PTD02', N'임시등록', '14236', 'Y'),
    ('PTD', N'발급구분', 'PTD03', N'장기등록', '14231', 'Y');

  /* 인원상태(PS) — code_tag = BiostarX 사용자의 disabled 값(신규만 활성, 나머지는 비활성) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn) VALUES
    ('PS', N'인원상태', '01', N'신규', 'false', 'Y'),
    ('PS', N'인원상태', '02', N'정지', 'true',  'Y'),
    ('PS', N'인원상태', '03', N'퇴사', 'true',  'Y'),
    ('PS', N'인원상태', '04', N'회수', 'true',  'Y'),
    ('PS', N'인원상태', '05', N'재발급', 'false', 'Y'),
    ('PS', N'인원상태', '06', N'분실', 'true',  'Y');

  /* 직위(UT) — code_name 이 BiostarX 사용자의 user_title 로 그대로 전달된다.
     기본 5개만 넣고, 그 외 직위는 운영에서 공통코드관리로 추가한다 */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('UT', N'직위', 'UT01', N'사원', 'Y'),
    ('UT', N'직위', 'UT02', N'대리', 'Y'),
    ('UT', N'직위', 'UT03', N'과장', 'Y'),
    ('UT', N'직위', 'UT04', N'차장', 'Y'),
    ('UT', N'직위', 'UT05', N'부장', 'Y');

  /* 카드상태(CS) — 카드 상태의 진실의 원천(tb_card.card_status 단일 컬럼).
     카드구분(CDT)은 운영에서 직접 등록하므로 시드하지 않는다 (공통코드관리) */
  /* code_tag='Y' = 정상이 아닌 상태. BiostarX 블랙리스트 차단과 신규 발급 차단을 함께 결정한다 */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn) VALUES
    ('CS', N'카드상태', 'CS01', N'정상', 'N', 'Y'),
    ('CS', N'카드상태', 'CS02', N'분실', 'Y', 'Y'),
    ('CS', N'카드상태', 'CS03', N'반납', 'Y', 'Y'),
    ('CS', N'카드상태', 'CS04', N'정지', 'Y', 'Y'),
    ('CS', N'카드상태', 'CS05', N'폐기', 'Y', 'Y');

  /* 발급구분(IS) — 카드 발급 사유 */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('IS', N'발급구분', 'IS01', N'신규', 'Y'),
    ('IS', N'발급구분', 'IS02', N'재발급', 'Y'),
    ('IS', N'발급구분', 'IS03', N'분실재발급', 'Y');

  /* 방문상태(VS) — tb_visit.status_code. 신청→입실 중→퇴실 완료 흐름 (취소 상태는 쓰지 않는다: 신청 단계면 삭제) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn) VALUES
    ('VS', N'방문상태', 'VS01', N'신청',     'Y'),
    ('VS', N'방문상태', 'VS03', N'입실 중',   'Y'),
    ('VS', N'방문상태', 'VS04', N'퇴실 완료', 'Y'),
    ('VS', N'방문상태', 'VS05', N'미반납',   'Y');

  /* 메뉴 (level 1 그룹은 menu_icon 지정 — 사이드바 아이콘) */
  INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn) VALUES
    (100, N'임시인원관리', NULL, NULL,                 1, 1, 'guard',    'Y'),
    (101, N'임시인원등록', 100,  '/visitor/visitor',    2, 1, NULL,       'Y'),
    (102, N'장기출입등록', 100,  '/visitor/longterm',   2, 2, NULL,       'Y'),
    (200, N'정규인원관리', NULL, NULL,                 1, 2, 'card',     'Y'),
    (201, N'정규인원등록', 200,  '/person/person',      2, 1, NULL,      'Y'),
    (300, N'시스템관리',   NULL, NULL,                 1, 2, 'settings', 'Y'),
    (302, N'설정관리',     300,  '/system/system',     2, 1, NULL,       'Y'),
    (301, N'공통코드관리', 300,  '/system/common',     2, 2, NULL,       'Y'),
    (304, N'권한메뉴관리', 300,  '/system/menuAuth',   2, 3, NULL,       'Y'),
    (303, N'사용자관리',   300,  '/system/loginUser',  2, 4, NULL,       'Y'),
    (500, N'보안관리',     NULL, NULL,                 1, 3, 'guard',    'Y'),
    (501, N'감사추적',     500,  '/security/systemLog', 2, 1, NULL,      'Y'),
    (502, N'출입권한관리', 500,  '/security/acGroup',   2, 2, NULL,      'Y'),
    (600, N'차량관리',     NULL, NULL,                 1, 4, 'car',      'Y'),
    (601, N'차량등록관리', 600,  '/carInfo/car',        2, 1, NULL,      'Y'),
    (700, N'기관관리',     NULL, NULL,                 1, 5, 'company',  'Y'),
    (701, N'기관등록관리', 700,  '/company/company',    2, 1, NULL,      'Y'),
    (702, N'기관차량등록', 700,  '/company/companyCar',        2, 2, NULL,      'Y'),
    (800, N'카드관리',     NULL, NULL,                 1, 6, 'card',     'Y'),
    (801, N'카드등록관리', 800,  '/card/card',          2, 1, NULL,      'Y');

  /* 관리자 권한 + 공통코드관리 전권 + 관리자 계정 */
  INSERT INTO dbo.tb_menu_auth (auth_name) VALUES (N'관리자');
  DECLARE @authId int = SCOPE_IDENTITY();

  INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
  VALUES (@authId, 301, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 303, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 304, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 501, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 502, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 601, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 701, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 201, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 801, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 702, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 101, 'Y', 'Y', 'Y', 'Y'),
         (@authId, 102, 'Y', 'Y', 'Y', 'Y');

  /* 관리자 계정: 아이디 admin / 비밀번호 admin123 (ARIA 암호문) */
  INSERT INTO dbo.tb_login_user
    (user_id, user_name, password, dept_name, use_yn, root_yn, auth_id, start_menu_id, work_location_code)
  VALUES
    ('admin',
     '3F04A75824FA503043F56A4A78370B23',   -- ARIA('관리자')
     'CADF8C82EC0394005E5F4DA4520BBFE1',   -- ARIA('admin123')
     N'운영팀', 'Y', 'Y', @authId, 301, 'T1');

  /* 조회전용 권한 + 계정: viewer / viewer123 — 메뉴 권한 CRUD 통제 확인용 (read Y, create/delete N) */
  INSERT INTO dbo.tb_menu_auth (auth_name) VALUES (N'조회전용');
  DECLARE @viewerAuthId int = SCOPE_IDENTITY();

  INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
  VALUES (@viewerAuthId, 301, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 303, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 304, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 501, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 502, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 601, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 701, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 201, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 801, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 702, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 101, 'Y', 'N', 'N', 'N'),
         (@viewerAuthId, 102, 'Y', 'N', 'N', 'N');

  INSERT INTO dbo.tb_login_user
    (user_id, user_name, password, dept_name, use_yn, root_yn, auth_id, start_menu_id, work_location_code)
  VALUES
    ('viewer',
     '90CC915CE3C405B614B53104CEECEB65',   -- ARIA('조회자')
     '61C20F44FC56313D845AD7B760D15F09',   -- ARIA('viewer123')
     N'운영팀', 'Y', 'N', @viewerAuthId, 301, 'T1');

END
ELSE
  PRINT '[3] 기본 데이터 이미 있음 — 건너뜀';
GO

/* ===========================================================================
   [4] 보정 — 없는 컬럼 추가 + 데이터 정합성 복구 (신규 설치면 대상 없음)
   =========================================================================== */
PRINT '[4] 스키마/데이터 보정';
GO

IF COL_LENGTH('dbo.tb_visit_person', 'last_card_no') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit_person ADD last_card_no nvarchar(255) NULL;
  PRINT '  + tb_visit_person.last_card_no 추가';
END
GO
IF COL_LENGTH('dbo.tb_visit_person', 'checkout_dt') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit_person ADD checkout_dt datetime2(0) NULL;
  PRINT '  + tb_visit_person.checkout_dt 추가';
END
GO

IF COL_LENGTH('dbo.tb_visit', 'checkout_dt') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit ADD checkout_dt datetime2(0) NULL;
  PRINT '  + tb_visit.checkout_dt 추가';
END
GO
/* 이미 퇴실 완료된 방문에는 시각이 없다 — 파기 기준이 되어야 하므로 mod_dt 로 채운다.
   근사치지만 유일한 단서이고, 1년 뒤 파기 대상 판정에는 충분하다. */
IF EXISTS (SELECT 1 FROM dbo.tb_visit WHERE status_code = 'VS04' AND checkout_dt IS NULL)
BEGIN
  UPDATE dbo.tb_visit SET checkout_dt = mod_dt WHERE status_code = 'VS04' AND checkout_dt IS NULL;
  PRINT '  + tb_visit.checkout_dt 백필(mod_dt 기준) ' + CAST(@@ROWCOUNT AS varchar(10)) + '건';
END
GO

/* 직위(UT) 기본 코드 — 없는 코드만 넣는다(운영에서 추가한 직위는 보존) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, 'Y'
FROM (VALUES
  ('UT', N'직위', 'UT01', N'사원'),
  ('UT', N'직위', 'UT02', N'대리'),
  ('UT', N'직위', 'UT03', N'과장'),
  ('UT', N'직위', 'UT04', N'차장'),
  ('UT', N'직위', 'UT05', N'부장')
) AS v(cmm_id, cmm_name, code_id, code_name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 직위(UT) 코드 추가';
GO

/* 방문객 인원ID 접두(PIP) — 없는 것만 넣는다(임시 IS / 장기 LT / 상주 RS / 순찰 PL / 대여 RT) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_remark, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, v.code_remark, 'Y'
FROM (VALUES
  ('PIP', N'인원ID 접두', 'PT02', 'IS', N'임시'),
  ('PIP', N'인원ID 접두', 'PT03', 'LT', N'장기'),
  ('PIP', N'인원ID 접두', 'PT04', 'RS', N'상주'),
  ('PIP', N'인원ID 접두', 'PT05', 'PL', N'순찰'),
  ('PIP', N'인원ID 접두', 'PT06', 'RT', N'대여')
) AS v(cmm_id, cmm_name, code_id, code_name, code_remark)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 인원ID 접두(PIP) 추가';
GO

/* 카드상태(CS) code_tag 복구 — 이 값이 비어 있으면 분실·정지 카드를 BiostarX 에서 차단하지 못하고
   비정상 카드의 신규 발급도 막지 못한다(둘 다 조용히 통과). 과거 시드는 이 컬럼을 채우지 않았다. */
UPDATE dbo.tb_common SET code_tag = 'N'
 WHERE cmm_id = 'CS' AND code_id = 'CS01' AND ISNULL(code_tag, '') <> 'N';
IF @@ROWCOUNT > 0 PRINT '  + 카드상태 CS01 code_tag 복구';
UPDATE dbo.tb_common SET code_tag = 'Y'
 WHERE cmm_id = 'CS' AND code_id IN ('CS02', 'CS03', 'CS04', 'CS05') AND ISNULL(code_tag, '') <> 'Y';
IF @@ROWCOUNT > 0 PRINT '  + 카드상태 차단코드 code_tag 복구';
GO

/* 시스템 공통코드는 '사용' 고정 — 미사용으로 바뀌어 있으면 되돌린다
   (업무 화면이 그 코드의 존재를 전제하므로 미사용이면 선택 팝업에서 사라진다) */
UPDATE dbo.tb_common SET use_yn = 'Y'
 WHERE ISNULL(user_input, 'N') <> 'Y' AND use_yn = 'N';
IF @@ROWCOUNT > 0 PRINT '  + 시스템 코드 사용유무 복구';
GO

/* 삭제된 인원에게 물려 남은 카드 회수 — 목록에 '발급중'으로 남아 재발급이 막힌다
   (카드 행은 지우지 않고 배정만 해제) */
UPDATE c
   SET c.person_id = NULL, c.use_yn = 'Y', c.del_yn = 'N', c.mod_dt = getdate()
  FROM dbo.tb_card c
  JOIN dbo.tb_person p ON p.person_id = c.person_id
 WHERE c.del_yn = 'N' AND p.del_yn = 'Y';
IF @@ROWCOUNT > 0 PRINT '  + 삭제 인원에 남아 있던 카드 회수';
GO

/* 방문상태 '신청 취소'(VS02) 제거 — 설정하는 경로가 없어 쓰이지 않는 상태다(신청 단계면 삭제).
   혹시 참조하는 방문이 남아 있으면 지우지 않는다(코드 없는 상태값이 목록에 남는 것을 막는다) */
IF NOT EXISTS (SELECT 1 FROM dbo.tb_visit WHERE status_code = 'VS02')
BEGIN
  DELETE FROM dbo.tb_common WHERE cmm_id = 'VS' AND code_id = 'VS02';
  -- 미반납(VS05) — 기존 설치본에도 보충한다(없으면 상태 표시가 코드값 그대로 나온다)
  IF NOT EXISTS (SELECT 1 FROM dbo.tb_common WHERE cmm_id = 'VS' AND code_id = 'VS05')
    INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn)
    VALUES ('VS', N'방문상태', 'VS05', N'미반납', 'Y');

  /* 감사유형(AT) 시스템 사건 정리 — 잠시 코드로 넣었다가 뺐다.
     유형 검색 목록에 섞이면 메뉴 [시스템] 과 골라야 할 곳이 두 군데가 된다.
     기록은 그대로 두고 코드만 지운다 — 이름은 mapper 가 붙인다. */
  DELETE FROM dbo.tb_common WHERE cmm_id = 'AT' AND code_id IN ('PURGE', 'STARTUP', 'SHUTDOWN');
  IF @@ROWCOUNT > 0 PRINT '  - 감사유형(AT) 시스템 사건 코드 제거(유형 목록에서 숨김)';

  /* 인원상태(PS) 재발급·분실 — code_tag 는 BiostarX 사용자의 disabled 값('false'=활성) */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn)
  SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, v.code_tag, 'Y'
  FROM (VALUES
    ('PS', N'인원상태', '05', N'재발급', 'false'),
    ('PS', N'인원상태', '06', N'분실',   'true')
  ) AS v(cmm_id, cmm_name, code_id, code_name, code_tag)
  WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
  IF @@ROWCOUNT > 0 PRINT '  + 인원상태(PS) 재발급·분실 추가';

  /* 인원구분(PT) 순찰·대여 — 장기·상주와 같은 발급구분(PTD03) + 세부 출입구역 선택('Y') */
  INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, code_remark, use_yn)
  SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, v.code_tag, v.code_remark, 'Y'
  FROM (VALUES
    ('PT', N'인원구분', 'PT05', N'순찰', 'PTD03', 'Y'),
    ('PT', N'인원구분', 'PT06', N'대여', 'PTD03', 'Y')
  ) AS v(cmm_id, cmm_name, code_id, code_name, code_tag, code_remark)
  WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
  IF @@ROWCOUNT > 0 PRINT '  + 인원구분(PT) 순찰·대여 추가';
  IF @@ROWCOUNT > 0 PRINT '  + 방문상태 VS02(신청 취소) 제거';
END
ELSE PRINT '  ! VS02 를 쓰는 방문이 있어 코드를 남겨 둠';
GO

/* ===========================================================================
   [4-1] 검색·정렬 보조 인덱스 (없으면 생성)

   PK(클러스터드)만으로는 아래 질의가 전부 전건 스캔이다. 데이터가 쌓이기 전에 만들어 둔다.
   특히 tb_system_log 는 메뉴접속·조회·입력·수정·삭제를 모두 남겨 가장 빨리 커지는 테이블이다.
   =========================================================================== */
PRINT '[4-1] 검색·정렬 보조 인덱스';

/* 감사추적: WHERE reg_dt BETWEEN ... ORDER BY reg_dt DESC, log_id DESC (+ 유형·메뉴·사용자 필터)
   INCLUDE 로 목록 화면이 쓰는 컬럼을 덮어 키 조회(lookup)를 줄인다. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_system_log_reg_dt' AND object_id = OBJECT_ID('dbo.tb_system_log'))
BEGIN
  CREATE INDEX IX_tb_system_log_reg_dt ON dbo.tb_system_log (reg_dt DESC, log_id DESC)
    INCLUDE (user_id, user_name, action_type, menu_id);
  PRINT '  + IX_tb_system_log_reg_dt';
END
GO

/* 카드: 인원/차량별 보유 카드 조회 — 정규인원 목록의 카드 장수(행마다), 카드번호 EXISTS 검색,
   회수(releaseByPerson/releaseByCar)가 전부 이 조건을 쓴다. 살아 있는 행만 색인(필터 인덱스). */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_card_person' AND object_id = OBJECT_ID('dbo.tb_card'))
BEGIN
  CREATE INDEX IX_tb_card_person ON dbo.tb_card (person_id) INCLUDE (biostar_card_value, card_status)
    WHERE del_yn = 'N' AND person_id IS NOT NULL;
  PRINT '  + IX_tb_card_person';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_card_car' AND object_id = OBJECT_ID('dbo.tb_card'))
BEGIN
  CREATE INDEX IX_tb_card_car ON dbo.tb_card (car_id) INCLUDE (biostar_card_value, card_status)
    WHERE del_yn = 'N' AND car_id IS NOT NULL;
  PRINT '  + IX_tb_card_car';
END
GO

/* 인원: 목록·인솔자 후보가 del_yn + person_type 으로 먼저 걸러진다(기관 필터는 그 다음). */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_person_type' AND object_id = OBJECT_ID('dbo.tb_person'))
BEGIN
  CREATE INDEX IX_tb_person_type ON dbo.tb_person (del_yn, person_type, company_code) INCLUDE (person_id);
  PRINT '  + IX_tb_person_type';
END
GO

/* 방문: 임시/장기 목록 — del_yn + visit_type 필터에 출입시작 기간(work_start_dt) 범위·정렬. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_visit_search' AND object_id = OBJECT_ID('dbo.tb_visit'))
BEGIN
  CREATE INDEX IX_tb_visit_search ON dbo.tb_visit (del_yn, visit_type, work_start_dt)
    INCLUDE (status_code, company_name);
  PRINT '  + IX_tb_visit_search';
END
GO

/* 방문객 명단: 인원ID 로 역방향 조회(방문객명 검색·퇴실 조회). PK 는 (visit_no, person_id) 라
   person_id 단독 조건은 탐색이 안 된다. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_visit_person_person' AND object_id = OBJECT_ID('dbo.tb_visit_person'))
BEGIN
  CREATE INDEX IX_tb_visit_person_person ON dbo.tb_visit_person (person_id) INCLUDE (visit_no);
  PRINT '  + IX_tb_visit_person_person';
END
GO

/* ===========================================================================
   [5] 결과 확인
   =========================================================================== */
PRINT '';
PRINT '== 설치 결과 ==';
SELECT '테이블'     AS 항목, CAST(COUNT(*) AS varchar(20)) AS 값 FROM sys.tables
UNION ALL SELECT '공통코드',   CAST(COUNT(*) AS varchar(20)) FROM dbo.tb_common
UNION ALL SELECT '메뉴',       CAST(COUNT(*) AS varchar(20)) FROM dbo.tb_menu
UNION ALL SELECT '권한',       CAST(COUNT(*) AS varchar(20)) FROM dbo.tb_menu_auth
UNION ALL SELECT '로그인계정', CAST(COUNT(*) AS varchar(20)) FROM dbo.tb_login_user
UNION ALL SELECT '직위(UT)',   CAST(COUNT(*) AS varchar(20)) FROM dbo.tb_common WHERE cmm_id = 'UT'
UNION ALL SELECT 'checkout_dt',
       CASE WHEN COL_LENGTH('dbo.tb_visit_person','checkout_dt') IS NULL THEN '없음' ELSE 'OK' END;
GO

PRINT '';
PRINT '기본 계정: admin / admin123   (조회 전용: viewer / viewer123)';
PRINT '설치 후: 설정관리에서 BiostarX 접속정보 확인 → 기관에 사용자그룹 연결 → 출입그룹 동기화';
GO
