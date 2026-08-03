/* ============================================================================
   마이그레이션 — 이미 구축된 DB 를 최신 스키마로 올린다 (2026-07-30 기준)
   ----------------------------------------------------------------------------
   실행 (저장소 루트에서):
     sqlcmd -S <서버> -U sa -P "<비밀번호>" -d CJ_AIRPORT -C -I -f 65001 ^
            -i sql\migration\2026-07-30_visit-checkout.sql

   신규 설치라면 이 파일은 필요 없다(sql\install.sql 이 최신 DDL 을 만든다).
   모든 문장은 **멱등**이라 여러 번 실행해도 안전하다.
   ============================================================================ */
:on error exit
SET NOCOUNT ON;
GO

/* ── 1. 방문객 마지막 카드 스냅샷 (카드 회수·재사용 후에도 어떤 카드를 썼는지 보존) ── */
IF COL_LENGTH('dbo.tb_visit_person', 'last_card_no') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit_person ADD last_card_no nvarchar(255) NULL;
  PRINT '  + tb_visit_person.last_card_no 추가';
END
ELSE PRINT '  = tb_visit_person.last_card_no 이미 있음';
GO

/* ── 2. 방문객 개별 퇴실 일시 (NULL=재실. 값이 있으면 카드 재발급 불가) ── */
IF COL_LENGTH('dbo.tb_visit_person', 'checkout_dt') IS NULL
BEGIN
  ALTER TABLE dbo.tb_visit_person ADD checkout_dt datetime2(0) NULL;
  PRINT '  + tb_visit_person.checkout_dt 추가';
END
ELSE PRINT '  = tb_visit_person.checkout_dt 이미 있음';
GO

/* ── 3. 직위(UT) 기본 코드 — 없는 코드만 넣는다(운영에서 추가한 직위는 보존) ── */
INSERT INTO dbo.tb_common (cmm_id, cmm_name, code_id, code_name, use_yn)
SELECT v.cmm_id, v.cmm_name, v.code_id, v.code_name, 'Y'
FROM (VALUES
  ('UT', N'직위', 'UT01', N'사원'),
  ('UT', N'직위', 'UT02', N'대리'),
  ('UT', N'직위', 'UT03', N'과장'),
  ('UT', N'직위', 'UT04', N'차장'),
  ('UT', N'직위', 'UT05', N'부장')
) AS v(cmm_id, cmm_name, code_id, code_name)
WHERE NOT EXISTS (
  SELECT 1 FROM dbo.tb_common c WHERE c.cmm_id = v.cmm_id AND c.code_id = v.code_id
);
PRINT '  + 직위(UT) 코드 ' + CAST(@@ROWCOUNT AS varchar) + '건 추가';
GO

/* ── 4. 시스템 공통코드의 사용유무는 '사용' 고정 — 과거에 미사용으로 바뀐 값 복구 ──
       (업무 로직·화면이 그 코드의 존재를 전제하므로 미사용이면 선택 팝업에서 사라진다) */
UPDATE dbo.tb_common SET use_yn = 'Y'
 WHERE ISNULL(user_input, 'N') <> 'Y' AND use_yn = 'N';
PRINT '  + 시스템 코드 사용유무 복구 ' + CAST(@@ROWCOUNT AS varchar) + '건';
GO

/* ── 5. 삭제된 인원에 물려 남은 카드 회수 — 목록에 '발급중'으로 남아 재발급이 막힌다 ──
       (카드 행은 지우지 않고 배정만 해제한다) */
UPDATE c
   SET c.person_id = NULL, c.use_yn = 'Y', c.del_yn = 'N', c.mod_dt = getdate()
  FROM dbo.tb_card c
  JOIN dbo.tb_person p ON p.person_id = c.person_id
 WHERE c.del_yn = 'N' AND p.del_yn = 'Y';
PRINT '  + 삭제 인원에 남아 있던 카드 회수 ' + CAST(@@ROWCOUNT AS varchar) + '건';
GO

PRINT '';
PRINT '== 확인 ==';
SELECT 'checkout_dt' AS 항목,
       CASE WHEN COL_LENGTH('dbo.tb_visit_person','checkout_dt') IS NULL THEN '없음' ELSE 'OK' END AS 상태
UNION ALL SELECT 'last_card_no',
       CASE WHEN COL_LENGTH('dbo.tb_visit_person','last_card_no') IS NULL THEN '없음' ELSE 'OK' END
UNION ALL SELECT '직위(UT) 코드 수', CAST(COUNT(*) AS varchar) FROM dbo.tb_common WHERE cmm_id = 'UT'
UNION ALL SELECT '미사용 시스템 코드', CAST(COUNT(*) AS varchar) FROM dbo.tb_common
       WHERE ISNULL(user_input,'N') <> 'Y' AND use_yn = 'N'
UNION ALL SELECT '삭제 인원 보유 카드', CAST(COUNT(*) AS varchar) FROM dbo.tb_card c
       JOIN dbo.tb_person p ON p.person_id = c.person_id WHERE c.del_yn='N' AND p.del_yn='Y';
GO
