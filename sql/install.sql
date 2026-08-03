/* ============================================================================
   CJAirPort — 신규 설치용 통합 스크립트 (DB 생성 → 테이블 → 기본 데이터)
   ----------------------------------------------------------------------------
   실행 (sqlcmd, Windows):
     sqlcmd -S localhost -U sa -P "<비밀번호>" -C -I -f 65001 -i sql\install.sql

     -C : 서버 인증서 신뢰   -I : QUOTED_IDENTIFIER ON(필터 인덱스에 필요)
     -f 65001 : 입력 파일 UTF-8 (한글 코드명이 깨지지 않게 반드시 지정)

   ※ 이 스크립트는 :r 로 DDL/seed 를 불러오므로 **저장소 루트에서** 실행해야 한다.
   ※ 이미 CJ_AIRPORT 가 있으면 만들지 않고 그대로 쓴다(기존 데이터 보존).
      기존 DB 를 최신 스키마로 올리려면 install 이 아니라 sql/migration/ 을 쓴다.
   ============================================================================ */
:on error exit
SET NOCOUNT ON;
GO

IF DB_ID('CJ_AIRPORT') IS NULL
BEGIN
  PRINT '== DB 생성: CJ_AIRPORT ==';
  EXEC('CREATE DATABASE CJ_AIRPORT COLLATE Korean_Wansung_CI_AS');
END
ELSE
  PRINT '== DB 이미 존재: CJ_AIRPORT (그대로 사용) ==';
GO

USE CJ_AIRPORT;
GO

PRINT '== 테이블(DDL) ==';
:r sql\ddl\01_tables.sql
GO

PRINT '== 기본 데이터(공통코드·메뉴·관리자 계정) ==';
:r sql\seed\02_seed.sql
GO

PRINT '';
PRINT '== 완료 — 확인 ==';
SELECT '테이블' AS 항목, CAST(COUNT(*) AS varchar) AS 개수 FROM sys.tables
UNION ALL SELECT '공통코드', CAST(COUNT(*) AS varchar) FROM tb_common
UNION ALL SELECT '메뉴',    CAST(COUNT(*) AS varchar) FROM tb_menu
UNION ALL SELECT '로그인계정', CAST(COUNT(*) AS varchar) FROM tb_login_user;
GO

PRINT '';
PRINT '기본 계정: admin / admin123 (조회전용 viewer / viewer123)';
PRINT '주의: 운영 반입 시 ARIA 키(app.crypto.aria-key)는 이 seed 를 만든 키와 같아야 한다.';
PRINT '      다른 키를 쓰려면 운영 키로 재암호화한 seed 를 넣어야 성명·비밀번호가 정상 복호화된다.';
GO
