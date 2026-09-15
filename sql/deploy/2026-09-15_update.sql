/* ============================================================================
   CJAirPort — 2026-09-15 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
   ----------------------------------------------------------------------------
   실행 방법
     1) SSMS 로 운영 DB 에 접속
     2) 이 파일을 열고(Ctrl+O) → 대상 DB 를 선택한 뒤 실행(F5)

   재실행해도 안전하다 — 이미 있으면 건너뛴다.

   ※ 파일 인코딩은 UTF-8 이다. 한글이 깨져 보이면 SSMS 의
     [파일 → 열기 → 파일] 대화상자에서 '인코딩' 을 'UTF-8' 로 지정해 다시 연다.

   담는 내용
     [1] tb_visit.visit_kind — 방문구분(인원/차량/인원+차량) + 기존 방문 백필

   왜 필요한가
     임시인원등록·장기출입등록·키오스크가 그룹정보 맨 위에서 방문구분을 고르고,
     고른 쪽(방문객·사용자 출입그룹 / 차량·차량 출입그룹)만 입력받는다.
     기존 방문은 명단으로 되짚어 채운다 — 방문객만 있으면 인원, 차량만 있으면 차량,
     그 밖(둘 다 있거나 둘 다 없음)은 인원+차량(모든 칸을 연다).
   ========================================================================== */
SET NOCOUNT ON;

/* [1] 컬럼 */
IF COL_LENGTH('dbo.tb_visit', 'visit_kind') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit ADD visit_kind nvarchar(10) NULL;
  PRINT '+ tb_visit.visit_kind 추가';
END
ELSE
  PRINT '= tb_visit.visit_kind 이미 있음';
GO

IF OBJECT_ID('dbo.CHK_tb_visit_kind', 'C') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit ADD CONSTRAINT CHK_tb_visit_kind
    CHECK (visit_kind IS NULL OR visit_kind IN ('PERSON','CAR','BOTH'));
  PRINT '+ CHK_tb_visit_kind 추가';
END
ELSE
  PRINT '= CHK_tb_visit_kind 이미 있음';
GO

/* 백필 — 값이 없는 방문만 */
UPDATE v SET visit_kind =
  CASE WHEN EXISTS (SELECT 1 FROM dbo.tb_visit_person p WHERE p.visit_no = v.visit_no)
        AND NOT EXISTS (SELECT 1 FROM dbo.tb_visit_car c WHERE c.visit_no = v.visit_no) THEN 'PERSON'
       WHEN EXISTS (SELECT 1 FROM dbo.tb_visit_car c WHERE c.visit_no = v.visit_no)
        AND NOT EXISTS (SELECT 1 FROM dbo.tb_visit_person p WHERE p.visit_no = v.visit_no) THEN 'CAR'
       ELSE 'BOTH' END
FROM dbo.tb_visit v WHERE v.visit_kind IS NULL;
PRINT '+ tb_visit.visit_kind 백필 ' + CAST(@@ROWCOUNT AS varchar(10)) + '건';
GO
