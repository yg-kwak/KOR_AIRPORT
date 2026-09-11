/* ============================================================================
   CJAirPort — 2026-09-10 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] 메뉴 902 이벤트 로그 (모니터링 900 하위) + 권한

   왜 필요한가
     실시간 이벤트(901)와 같은 인증 이벤트를 다른 배치로 보는 화면이다.
     단말기를 여러 대 한 화면에서 보고, 지난 인증을 세로로 쌓아 스크롤한다.
     테이블 변경은 없다 — 메뉴 한 줄과 권한뿐이다.
   ========================================================================== */
SET NOCOUNT ON;

/* [1] 메뉴 902 이벤트 로그 (모니터링 900 하위) */
IF NOT EXISTS (SELECT 1 FROM dbo.tb_menu WHERE menu_id = 902)
BEGIN
  INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn)
  VALUES (902, N'이벤트 로그', 900, '/monitor/eventLog', 2, 2, NULL, 'Y');
  PRINT '+ 메뉴 902 이벤트 로그 추가';
END
ELSE
  PRINT '= 메뉴 902 이미 있음';

/* 권한 — 실시간 이벤트(901) 권한을 그대로 물려받는다. 같은 모니터링 메뉴이고,
   권한별로 다시 정하는 것은 [권한메뉴관리] 화면에서 한다. */
INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
SELECT d.auth_id, 902, d.read_auth, d.create_auth, d.update_auth, d.delete_auth
FROM dbo.tb_menu_auth_detail d
WHERE d.menu_id = 901
  AND NOT EXISTS (SELECT 1 FROM dbo.tb_menu_auth_detail x
                  WHERE x.auth_id = d.auth_id AND x.menu_id = 902);
PRINT '+ 메뉴 902 권한 = 901 과 동일하게 부여(이미 있으면 건너뜀)';
