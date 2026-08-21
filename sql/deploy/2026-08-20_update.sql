/* ============================================================================
   CJAirPort — 2026-08-20 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] 컬럼 — tb_visit_manager.manager_phone (인솔자 연락처)

   왜 필요한가
     인솔자 연락처를 정규인원 정보에서 당겨오지 않고 방문마다 손으로 적는다.
     같은 사람이라도 방문마다 연락 받을 번호가 다를 수 있어서다.
     개인정보라 ARIA 로 암호화해 넣으므로 평문 길이보다 넉넉한 255 로 잡는다.
   ========================================================================== */
SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_NAME = 'tb_visit_manager' AND COLUMN_NAME = 'manager_phone')
BEGIN
  ALTER TABLE dbo.tb_visit_manager ADD manager_phone nvarchar(255) NULL;
  PRINT '+ tb_visit_manager.manager_phone 추가';
END
ELSE
  PRINT '= tb_visit_manager.manager_phone 이미 있음';

/* 기존 방문의 연락처는 비어 있다. 신청서에는 빈 칸으로 나오고,
   그 방문을 다시 저장할 때 화면에서 입력하면 채워진다. */
