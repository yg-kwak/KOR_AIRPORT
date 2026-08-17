/* ============================================================================
   CJAirPort — 2026-08-13 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)
     3) 메시지 창의 '+' 줄로 무엇이 들어갔는지 확인한다.

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 이 내용은 CJAirPort_install.sql 에도 들어 있다. 신규 설치라면 그 파일만
     실행하면 되고, 이미 돌고 있는 DB 에는 이 파일이 더 짧고 안전하다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] 테이블 — tb_parking_event (주차 입·출차 이벤트 이력)
     [2] 인덱스 — 주차 조회 화면의 기간·차량번호 조회
     [3] 메뉴 — 주차 조회(602, /carInfo/parkingEvent)
     [4] 주차 조회 메뉴 권한 — '관리자' 권한그룹에 부여

   ※ [4] 는 '관리자' 권한그룹에만 자동으로 준다. 다른 권한그룹에도 필요하면
     시스템관리 → 권한메뉴관리 화면에서 체크한다.
   ============================================================================ */
SET NOCOUNT ON;
PRINT '=== CJAirPort 2026-08-13 변경분 적용 시작 ===';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [1] 주차 입·출차 이벤트 이력

       아마노 주차관제가 우리 쪽으로 밀어 준다(POST /api/InOutCar). 우리가 조회하러
       가지 않는다. 주차서버는 우리 응답을 못 받으면 같은 건을 다시 보내므로,
       (event_type, car_no, event_dt) 를 유일키로 두어 두 번 받아도 한 줄만 남긴다.
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '[1] 테이블 — tb_parking_event';

IF OBJECT_ID('dbo.tb_parking_event', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_parking_event (
    event_id      int IDENTITY(1,1) NOT NULL,
    event_type    nvarchar(30)  NOT NULL,   -- EnteredCar/ExitedCar/...NotOpen/...RearCar
    event_name    nvarchar(100) NULL,
    lot_area      int           NULL,       -- 주차장 번호
    eqpm_id       int           NULL,       -- 장치(차단기) 번호
    car_no        nvarchar(50)  NOT NULL,   -- 미인식은 'No_Detection'
    event_dt      datetime2(0)  NOT NULL,
    in_dt         datetime2(0)  NULL,       -- 입차 시각(체류시간 계산용)
    in_eqpm_id    int           NULL,
    user_name     nvarchar(100) NULL,
    pass_type     nvarchar(20)  NULL,       -- passType1~8 / normal / visitor
    is_cust_def   nchar(1)      NULL,       -- 정기차량 여부 Y/N
    parking_id    int           NULL,       -- iID. -1 이면 출입권한 없는 차량
    car_image_url nvarchar(500) NULL,
    history_id    int           NULL,
    lpr_trns_id   int           NULL,
    raw_json      nvarchar(max) NULL,       -- 원문 보존
    reg_dt        datetime2(0)  NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_parking_event PRIMARY KEY (event_id),
    CONSTRAINT UQ_tb_parking_event UNIQUE (event_type, car_no, event_dt)
  );
  PRINT '  + tb_parking_event 생성';
END
ELSE PRINT '  = tb_parking_event 이미 있음';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [2] 인덱스
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '[2] 인덱스';

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_parking_event_dt' AND object_id = OBJECT_ID('dbo.tb_parking_event'))
BEGIN
  CREATE INDEX IX_tb_parking_event_dt ON dbo.tb_parking_event (event_dt DESC, event_id DESC)
    INCLUDE (event_type, car_no, pass_type);
  PRINT '  + IX_tb_parking_event_dt';
END
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_tb_parking_event_car' AND object_id = OBJECT_ID('dbo.tb_parking_event'))
BEGIN
  CREATE INDEX IX_tb_parking_event_car ON dbo.tb_parking_event (car_no) INCLUDE (event_dt, event_type);
  PRINT '  + IX_tb_parking_event_car';
END
GO

/* ────────────────────────────────────────────────────────────────────────────
   [3] 메뉴 — 주차 조회(602)
       menu_url 은 컨트롤러의 @RequestMapping 과 반드시 같아야 한다.
       서버가 요청 URL 로 menu_id 를 정하므로, 다르면 권한 판정이 통째로 빗나간다.
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '[3] 메뉴 — 주차 조회';

INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn)
SELECT v.menu_id, v.menu_name, v.parent_menu_id, v.menu_url, v.menu_level, v.menu_order, v.menu_icon, 'Y'
FROM (VALUES
  (602, N'주차 조회', 600, '/carInfo/parkingEvent', 2, 2, CAST(NULL AS nvarchar(30)))
) AS v(menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_menu m WHERE m.menu_id = v.menu_id);
PRINT '  + 메뉴 ' + CAST(@@ROWCOUNT AS varchar(10)) + '건 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [4] 권한 — '관리자' 권한그룹에 주차 조회(602) 전권
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '[4] 권한 — 주차 조회(602)';

INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
SELECT a.auth_id, 602, 'Y', 'Y', 'Y', 'Y'
FROM dbo.tb_menu_auth a
WHERE a.auth_name = N'관리자'
  AND NOT EXISTS (SELECT 1 FROM dbo.tb_menu_auth_detail d
                  WHERE d.auth_id = a.auth_id AND d.menu_id = 602);
PRINT '  + 권한 ' + CAST(@@ROWCOUNT AS varchar(10)) + '건 부여';
GO

/* ────────────────────────────────────────────────────────────────────────────
   적용 결과 확인
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '=== 적용 결과 ===';
SELECT N'테이블 tb_parking_event' AS 항목,
       CASE WHEN OBJECT_ID('dbo.tb_parking_event', 'U') IS NULL THEN '0' ELSE '1' END AS 건수
UNION ALL SELECT N'메뉴 602 주차 조회', CAST(COUNT(*) AS varchar(10)) FROM dbo.tb_menu WHERE menu_id = 602
UNION ALL SELECT N'602 권한 부여 권한그룹', CAST(COUNT(*) AS varchar(10)) FROM dbo.tb_menu_auth_detail WHERE menu_id = 602;
GO

PRINT '=== CJAirPort 2026-08-13 변경분 적용 완료 ===';
GO
