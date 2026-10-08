/* ============================================================================
   CJAirPort — 2026-10-08 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] tb_menu.new_window_yn — 메뉴를 새 창으로 여는지(Y/N)
     [2] tb_graphic_map / tb_graphic_map_door — 그래픽맵 평면도와 그 위에 놓은 출입문(BiostarX 출입문 단위)
     [3] 메뉴 903 그래픽맵 (모니터링 900 하위, 새 창) + 권한

   왜 필요한가
     평면도를 올리고 그 위에 출입문(단말기)을 배치해, 인증이 일어난 문을 평면도에서 보고
     아래에 인증 로그, 오른쪽에 인증 사진을 띄우는 상황판이다. 늘 켜 두는 화면이라 새 창으로 연다.
   ========================================================================== */
SET NOCOUNT ON;

/* [1] 메뉴 새 창 여부 */
IF COL_LENGTH('dbo.tb_menu', 'new_window_yn') IS NULL
BEGIN
  ALTER TABLE dbo.tb_menu ADD new_window_yn nchar(1) NOT NULL
    CONSTRAINT DF_tb_menu_new_window_yn DEFAULT 'N';
  PRINT '+ tb_menu.new_window_yn 추가';
END
ELSE
  PRINT '= tb_menu.new_window_yn 이미 있음';
GO

/* [2] 그래픽맵 */
IF OBJECT_ID('dbo.tb_graphic_map', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_graphic_map (
    map_id      int IDENTITY(1,1) NOT NULL,           -- 맵번호 (PK)
    map_name    nvarchar(100)  NOT NULL,              -- 맵 이름 (예: B3F 남측)
    image_data  varbinary(max) NULL,                  -- 평면도 이미지 원본(PNG/JPG/GIF/WEBP)
    image_type  nvarchar(50)   NULL,                  -- 이미지 MIME (image/png ...)
    sort_order  int            NOT NULL DEFAULT 0,    -- 목록 순서
    del_yn      nchar(1)       NOT NULL DEFAULT 'N',  -- 삭제여부 (소프트 삭제)
    reg_dt      datetime2(0)   NOT NULL DEFAULT getdate(),
    mod_dt      datetime2(0)   NOT NULL DEFAULT getdate(),
    CONSTRAINT PK_tb_graphic_map PRIMARY KEY (map_id),
    CONSTRAINT CHK_tb_graphic_map_del_yn CHECK (del_yn IN ('Y','N'))
  );
  PRINT '+ tb_graphic_map 생성';
END
ELSE
  PRINT '= tb_graphic_map 이미 있음';
GO

/* 출입문 배치 — BiostarX 출입문(door) 단위. 같은 날 먼저 배포된 처음 모양(단말기 단위, door_id 없음)이
   이미 있으면: 비어 있으면 지우고, 배치가 있으면 tb_graphic_map_door_v1 로 이름만 바꿔 보관한다.
   단말기 → 출입문 짝은 BiostarX 에만 있어 SQL 로 옮길 수 없다 — 화면에서 다시 놓는다(보관본에 옛 위치가 있다). */
IF OBJECT_ID('dbo.tb_graphic_map_door', 'U') IS NOT NULL
   AND COL_LENGTH('dbo.tb_graphic_map_door', 'door_id') IS NULL
BEGIN
  IF NOT EXISTS (SELECT 1 FROM dbo.tb_graphic_map_door)
  BEGIN
    DROP TABLE dbo.tb_graphic_map_door;
    PRINT '~ tb_graphic_map_door 처음 모양(비어 있음) — 출입문 단위로 다시 만든다';
  END
  ELSE
  BEGIN
    EXEC sp_rename 'dbo.tb_graphic_map_door', 'tb_graphic_map_door_v1';
    EXEC sp_rename 'dbo.PK_tb_graphic_map_door', 'PK_tb_graphic_map_door_v1';
    PRINT '!! tb_graphic_map_door 처음 모양의 배치를 tb_graphic_map_door_v1 로 보관했다 — 그래픽맵에서 출입문을 다시 놓으세요';
  END
END
GO

IF OBJECT_ID('dbo.tb_graphic_map_door', 'U') IS NULL
BEGIN
  CREATE TABLE dbo.tb_graphic_map_door (
    map_id      int            NOT NULL,              -- → tb_graphic_map.map_id
    door_id     int            NOT NULL,              -- BiostarX 출입문ID (원격 개방·잠금·해제 대상)
    door_name   nvarchar(200)  NULL,                  -- 배치할 때의 출입문 이름(표시용 스냅샷)
    device_id   nvarchar(50)   NULL,                  -- 그 문의 입구 단말기 — 인증 이벤트(device_id)를 이 문에 잇는다
    pos_x       decimal(7,4)   NOT NULL,              -- 평면도 가로 위치 0~1 (비율 — 확대해도 같은 자리)
    pos_y       decimal(7,4)   NOT NULL,              -- 평면도 세로 위치 0~1
    CONSTRAINT PK_tb_graphic_map_door PRIMARY KEY (map_id, door_id)
  );
  PRINT '+ tb_graphic_map_door 생성';
END
ELSE
  PRINT '= tb_graphic_map_door 이미 있음';
GO

/* [3] 메뉴 903 그래픽맵 (모니터링 900 하위) — 새 창으로 연다 */
IF NOT EXISTS (SELECT 1 FROM dbo.tb_menu WHERE menu_id = 903)
BEGIN
  INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn, new_window_yn)
  VALUES (903, N'그래픽맵', 900, '/monitor/graphicMap', 2, 3, NULL, 'Y', 'Y');
  PRINT '+ 메뉴 903 그래픽맵 추가';
END
ELSE
  PRINT '= 메뉴 903 이미 있음';

/* 권한 — 이벤트 로그(902) 권한을 그대로 물려받는다. 권한별로 다시 정하는 것은 [권한메뉴관리] 화면에서 한다. */
INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
SELECT d.auth_id, 903, d.read_auth, d.create_auth, d.update_auth, d.delete_auth
FROM dbo.tb_menu_auth_detail d
WHERE d.menu_id = 902
  AND NOT EXISTS (SELECT 1 FROM dbo.tb_menu_auth_detail x
                  WHERE x.auth_id = d.auth_id AND x.menu_id = 903);
PRINT '+ 메뉴 903 권한 ' + CAST(@@ROWCOUNT AS varchar(10)) + '건 (902 와 같게)';
GO
