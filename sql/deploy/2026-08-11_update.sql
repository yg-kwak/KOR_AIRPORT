/* ============================================================================
   CJAirPort — 2026-08-11 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
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
     [1] 메뉴 — 모니터링(900) / 실시간 이벤트(901, /monitor/event)
     [2] 실시간 이벤트 메뉴 권한 — 기존 권한그룹에 부여

   ※ [2] 는 '관리자' 권한그룹에만 자동으로 준다. 다른 권한그룹에도 필요하면
     시스템관리 → 권한메뉴관리 화면에서 체크하거나, 아래 [2] 의 주석을 참고해
     WHERE 조건을 바꿔 실행한다.
   ============================================================================ */
SET NOCOUNT ON;
PRINT '=== CJAirPort 2026-08-11 변경분 적용 시작 ===';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [1] 메뉴 — 모니터링(900) · 실시간 이벤트(901)
       menu_url 은 컨트롤러의 @RequestMapping 과 반드시 같아야 한다.
       서버가 요청 URL 로 menu_id 를 정하므로, 다르면 권한 판정이 통째로 빗나간다.
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '[1] 메뉴 — 모니터링 / 실시간 이벤트';

INSERT INTO dbo.tb_menu (menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon, use_yn)
SELECT v.menu_id, v.menu_name, v.parent_menu_id, v.menu_url, v.menu_level, v.menu_order, v.menu_icon, 'Y'
FROM (VALUES
  (900, N'모니터링',      CAST(NULL AS int), CAST(NULL AS nvarchar(255)), 1, 7, 'monitor'),
  (901, N'실시간 이벤트', 900,               '/monitor/event',            2, 1, CAST(NULL AS nvarchar(30)))
) AS v(menu_id, menu_name, parent_menu_id, menu_url, menu_level, menu_order, menu_icon)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_menu m WHERE m.menu_id = v.menu_id);
PRINT '  + 메뉴 ' + CAST(@@ROWCOUNT AS varchar(10)) + '건 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [2] 권한 — '관리자' 권한그룹에 실시간 이벤트(901) 전권
       다른 권한그룹에도 주려면 아래 AND a.auth_name = N'관리자' 줄을 지운다
       (그러면 모든 권한그룹에 조회 권한만 주도록 값을 바꿔 쓰는 편이 낫다).
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '[2] 권한 — 실시간 이벤트(901)';

INSERT INTO dbo.tb_menu_auth_detail (auth_id, menu_id, read_auth, create_auth, update_auth, delete_auth)
SELECT a.auth_id, 901, 'Y', 'Y', 'Y', 'Y'
FROM dbo.tb_menu_auth a
WHERE a.auth_name = N'관리자'
  AND NOT EXISTS (SELECT 1 FROM dbo.tb_menu_auth_detail d
                  WHERE d.auth_id = a.auth_id AND d.menu_id = 901);
PRINT '  + 권한 ' + CAST(@@ROWCOUNT AS varchar(10)) + '건 부여';
GO

/* 차량구역3 공통코드 — 이 파일의 다른 절과 같이 재실행해도 안전해야 한다(그냥 INSERT 면 돌릴 때마다 늘어난다) */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, code_remark, user_input, use_yn)
SELECT 'CAR', N'차량출입구역', 'CAR03', N'차량구역3', NULL, NULL, 'N', 'Y'
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common WHERE cmm_id = 'CAR' AND code_id = 'CAR03');
GO

/* ────────────────────────────────────────────────────────────────────────────
   적용 결과 확인
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '=== 적용 결과 ===';
SELECT N'메뉴 900 모니터링'      AS 항목, CAST(COUNT(*) AS varchar(10)) AS 건수 FROM dbo.tb_menu WHERE menu_id = 900
UNION ALL SELECT N'메뉴 901 실시간 이벤트', CAST(COUNT(*) AS varchar(10)) FROM dbo.tb_menu WHERE menu_id = 901
UNION ALL SELECT N'901 권한 부여 권한그룹',  CAST(COUNT(*) AS varchar(10)) FROM dbo.tb_menu_auth_detail WHERE menu_id = 901;
GO

PRINT '=== CJAirPort 2026-08-11 변경분 적용 완료 ===';
GO
