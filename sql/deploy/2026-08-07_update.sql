/* ============================================================================
   CJAirPort — 2026-08-07 변경분 (운영 DB 적용용, SSMS 에서 그대로 실행)
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
     [1] 방문상태(VS) — 미반납 추가
     [2] 인원상태(PS) — 재발급 · 분실 추가
     [3] 인원구분(PT) — 순찰 · 대여 추가
     [4] 인원ID 접두(PIP) — 순찰 PL · 대여 RT 추가
     [5] tb_visit.checkout_dt 컬럼 추가 + 기존 퇴실완료분 백필
     [6] 감사유형(AT) — 자동 파기 추가
   ============================================================================ */
SET NOCOUNT ON;
PRINT '=== CJAirPort 2026-08-07 변경분 적용 시작 ===';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [1] 방문상태(VS) — 미반납(VS05)
   작업기간이 끝났는데 카드를 반납하지 않은 방문. 상태값 자체는 저장하지 않고
   조회할 때 계산하지만, 이름을 보여주려면 코드가 있어야 한다.
   ──────────────────────────────────────────────────────────────────────────── */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, 'Y'
FROM (VALUES
  ('VS', N'방문상태', 'VS05', N'미반납')
) AS v(cmm_id, cmm_name, code_id, code_name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 방문상태(VS) 미반납 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [2] 인원상태(PS) — 재발급 · 분실
   code_tag 는 BiostarX 사용자의 disabled 값이다('false' = 활성).
   재발급은 정상이라 활성, 분실은 비활성으로 넣는다.
   ──────────────────────────────────────────────────────────────────────────── */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, v.code_tag, 'Y'
FROM (VALUES
  ('PS', N'인원상태', '05', N'재발급', 'false'),
  ('PS', N'인원상태', '06', N'분실',   'true')
) AS v(cmm_id, cmm_name, code_id, code_name, code_tag)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 인원상태(PS) 재발급·분실 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [3] 인원구분(PT) — 순찰 · 대여
   장기·상주와 같게 발급구분 PTD03, 세부 출입구역 선택 'Y'.
   장기출입등록의 방문유형 목록은 code_tag='PTD03' 계열을 뽑으므로
   이 코드만 들어가면 화면에 자동으로 나타난다.
   ──────────────────────────────────────────────────────────────────────────── */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_tag, code_remark, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, v.code_tag, v.code_remark, 'Y'
FROM (VALUES
  ('PT', N'인원구분', 'PT05', N'순찰', 'PTD03', 'Y'),
  ('PT', N'인원구분', 'PT06', N'대여', 'PTD03', 'Y')
) AS v(cmm_id, cmm_name, code_id, code_name, code_tag, code_remark)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 인원구분(PT) 순찰·대여 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [4] 인원ID 접두(PIP) — 순찰 PL · 대여 RT
   유형별로 독립 시퀀스로 채번한다(PL000001 / RT000001).
   ⚠️ 인원ID 는 BiostarX 사용자ID 와 같은 키라 발급 후 바꿀 수 없다.
      접두를 다르게 쓰려면 이 스크립트를 돌리기 전에 아래 값을 고친다.
   ──────────────────────────────────────────────────────────────────────────── */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, code_remark, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, v.code_remark, 'Y'
FROM (VALUES
  ('PIP', N'인원ID 접두', 'PT05', 'PL', N'순찰'),
  ('PIP', N'인원ID 접두', 'PT06', 'RT', N'대여')
) AS v(cmm_id, cmm_name, code_id, code_name, code_remark)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 인원ID 접두(PIP) 순찰·대여 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   [5] tb_visit.checkout_dt — 퇴실 완료 시각
   정기 파기(퇴실 후 1년)의 기준이 되는 컬럼이다.
   mod_dt 는 퇴실 뒤 아무 수정에도 바뀌어 기준으로 쓸 수 없다.
   ──────────────────────────────────────────────────────────────────────────── */
IF COL_LENGTH('dbo.tb_visit', 'checkout_dt') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit ADD checkout_dt datetime2(0) NULL;
  PRINT '  + tb_visit.checkout_dt 추가';
END
GO

/* 이미 퇴실 완료된 방문에는 시각이 없다 — mod_dt 로 채운다.
   근사치지만 유일한 단서이고, 1년 뒤 파기 대상 판정에는 충분하다. */
IF EXISTS (SELECT 1 FROM dbo.tb_visit WHERE status_code = 'VS04' AND checkout_dt IS NULL)
BEGIN
  UPDATE dbo.tb_visit SET checkout_dt = mod_dt WHERE status_code = 'VS04' AND checkout_dt IS NULL;
  PRINT '  + tb_visit.checkout_dt 백필(mod_dt 기준) ' + CAST(@@ROWCOUNT AS varchar(10)) + '건';
END
GO

/* ────────────────────────────────────────────────────────────────────────────
   [6] 감사유형(AT) — 자동 파기
   정기 파기 배치가 남기는 기록의 유형이다. 사람이 지운 '삭제'와 섞이지 않아
   감사추적에서 유형을 '자동 파기'로 고르면 배치 이력만 볼 수 있다.
   ──────────────────────────────────────────────────────────────────────────── */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, 'Y'
FROM (VALUES
  ('AT', N'감사유형', 'PURGE', N'자동 파기')
) AS v(cmm_id, cmm_name, code_id, code_name)
WHERE NOT EXISTS (SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id);
IF @@ROWCOUNT > 0 PRINT '  + 감사유형(AT) 자동 파기 추가';
GO

/* ────────────────────────────────────────────────────────────────────────────
   확인 — 아래 결과가 기대와 같은지 본다
   ──────────────────────────────────────────────────────────────────────────── */
PRINT '=== 적용 결과 ===';
GO
SELECT N'[1] 방문상태(VS)' AS 항목, code_id, code_name, code_tag
FROM dbo.tb_common WHERE cmm_id = 'VS' ORDER BY code_id;

SELECT N'[2] 인원상태(PS)' AS 항목, code_id, code_name, code_tag AS [disabled]
FROM dbo.tb_common WHERE cmm_id = 'PS' ORDER BY code_id;

SELECT N'[3] 인원구분(PT)' AS 항목, code_id, code_name, code_tag AS 발급구분, code_remark AS 세부구역
FROM dbo.tb_common WHERE cmm_id = 'PT' ORDER BY code_id;

SELECT N'[4] 인원ID 접두(PIP)' AS 항목, code_id, code_name AS 접두, code_remark AS 유형
FROM dbo.tb_common WHERE cmm_id = 'PIP' ORDER BY code_id;

SELECT N'[5] 퇴실완료 방문' AS 항목,
       COUNT(*) AS 전체,
       SUM(CASE WHEN checkout_dt IS NULL THEN 1 ELSE 0 END) AS 시각없음
FROM dbo.tb_visit WHERE status_code = 'VS04';

SELECT N'[6] 감사유형(AT)' AS 항목, code_id, code_name
FROM dbo.tb_common WHERE cmm_id = 'AT' ORDER BY code_id;
GO

PRINT '=== 완료 ===';
PRINT '  · [5] 시각없음 이 0 이어야 정상이다.';
PRINT '  · 적용 후 새 jar 로 교체하고 서비스를 재시작한다.';
GO
