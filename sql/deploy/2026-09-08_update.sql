/* ============================================================================
   CJAirPort — 2026-09-08 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] 컬럼 — tb_car.affiliation (차량소속)
     [2] 테이블 — tb_blacklist (제재인원) + 메뉴 503 제재인원관리

   왜 필요한가
     신청서 차량표의 '출입자소속' 칸을 지금까지 방문(그룹)의 업체명으로 채웠다.
     임시·장기 등록에서 업체명을 더 이상 받지 않기로 하면서 그 칸의 출처가 없어졌다.
     차량마다 소속이 다를 수 있어 방문 단위 값보다 차량 단위 값이 정확하다.
     tb_car.company_code(기관 코드)와는 다른 값이다 — 이쪽은 신청서에 그대로 찍히는 문구다.
   ========================================================================== */
SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_NAME = 'tb_car' AND COLUMN_NAME = 'affiliation')
BEGIN
  ALTER TABLE dbo.tb_car ADD affiliation nvarchar(100) NULL;
  PRINT '+ tb_car.affiliation 추가';
END
ELSE
  PRINT '= tb_car.affiliation 이미 있음';

/* 기존 차량의 소속은 비어 있다. 신청서에는 빈 칸으로 나오고,
   그 차량을 다시 저장할 때 화면에서 입력하면 채워진다.
   업체명(tb_visit.company_name)은 컬럼을 그대로 두므로 과거 방문의 값은 남아 있다. */

/* ---------------------------------------------------------------------------
   [2] 제재인원 (보안관리 → 제재인원관리)

   성명+생년월일로 대조해 임시·장기·정규 등록을 막는다.
   둘 다 ARIA 암호문이고 결정적이라 완전일치 비교만 된다(부분검색·정렬 불가).
   --------------------------------------------------------------------------- */
IF OBJECT_ID('dbo.tb_blacklist','U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_blacklist (
    blacklist_id int IDENTITY(1,1) NOT NULL,
    person_name  nvarchar(255) NOT NULL,                 -- ARIA 암호화
    birth_date   nvarchar(255) NOT NULL,                 -- ARIA 암호화 (YYYY-MM-DD)
    affiliation  nvarchar(100) NULL,
    remark       nvarchar(1000) NULL,
    ban_start_dt datetime2(0)  NULL,                     -- 비면 즉시부터
    ban_end_dt   datetime2(0)  NULL,                     -- 비면 무기한
    del_yn       nchar(1)      NOT NULL DEFAULT 'N',
    reg_dt       datetime2(0)  NOT NULL DEFAULT getdate(),
    mod_dt       datetime2(0)  NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_blacklist PRIMARY KEY (blacklist_id),
    CONSTRAINT CHK_tb_blacklist_del_yn CHECK (del_yn IN ('Y','N'))
  );
  CREATE INDEX IX_tb_blacklist_person ON dbo.tb_blacklist (person_name, birth_date)
    INCLUDE (ban_start_dt, ban_end_dt, del_yn);
  PRINT '+ tb_blacklist 생성';
END
ELSE
  PRINT '= tb_blacklist 이미 있음';

/* 메뉴 503 제재인원관리 (보안관리 500 하위) */
IF NOT EXISTS (SELECT 1 FROM dbo.tb_menu WHERE menu_id = 503)
BEGIN
  INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn)
  VALUES (503, N'제재인원관리', 500, '/security/blacklist', 2, 3, NULL, 'Y');
  PRINT '+ 메뉴 503 제재인원관리 추가';
END
ELSE
  PRINT '= 메뉴 503 이미 있음';

/* 권한 — 감사추적(501) 권한을 그대로 물려받는다. 같은 보안관리 메뉴이고,
   권한별로 다시 정하는 것은 [권한메뉴관리] 화면에서 한다. */
INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
SELECT d.auth_id, 503, d.read_auth, d.create_auth, d.update_auth, d.delete_auth
FROM dbo.tb_menu_auth_detail d
WHERE d.menu_id = 501
  AND NOT EXISTS (SELECT 1 FROM dbo.tb_menu_auth_detail x
                  WHERE x.auth_id = d.auth_id AND x.menu_id = 503);
PRINT '+ 메뉴 503 권한 반영(감사추적 권한 기준)';
