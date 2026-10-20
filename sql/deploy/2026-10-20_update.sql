/* ============================================================================
   CJAirPort — 2026-10-20 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] 메뉴 504 에스코트 인원 관리 (보안관리 500 하위) + 권한

   왜 필요한가
     BiostarX 출입문의 에스코트(auth_door_control) 사용자를 이 시스템에서 조회하고 추가한다.
     데이터는 BiostarX 에만 있고 이 시스템에는 저장하지 않으므로 테이블은 없다.
   ========================================================================== */
SET NOCOUNT ON;

/* [1] 메뉴 504 에스코트 인원 관리 */
IF NOT EXISTS (SELECT 1 FROM dbo.tb_menu WHERE menu_id = 504)
BEGIN
  INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn)
  VALUES (504, N'에스코트 인원 관리', 500, '/security/escort', 2, 4, NULL, 'Y');
  PRINT '+ 메뉴 504 에스코트 인원 관리 추가';
END
ELSE
  PRINT '= 메뉴 504 이미 있음';

/* 권한 — 감사추적(501) 권한을 그대로 물려받는다. 같은 보안관리 메뉴이고,
   권한별로 다시 정하는 것은 [권한메뉴관리] 화면에서 한다.
   ※ 등록 권한이 있어야 사용자를 추가할 수 있다(BiostarX 출입문에 바로 반영된다). */
INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
SELECT d.auth_id, 504, d.read_auth, d.create_auth, d.update_auth, d.delete_auth
FROM dbo.tb_menu_auth_detail d
WHERE d.menu_id = 501
  AND NOT EXISTS (SELECT 1 FROM dbo.tb_menu_auth_detail x
                  WHERE x.auth_id = d.auth_id AND x.menu_id = 504);
PRINT '+ 메뉴 504 권한 반영(감사추적 권한 기준)';
