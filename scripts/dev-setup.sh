#!/usr/bin/env bash
# 개발환경 부트스트랩 — DB 생성 → DDL → seed → 로컬 설정 파일 준비. (Git Bash 에서 실행)
# 사용법:
#   DB_SA_PASSWORD=**** scripts/dev-setup.sh            # 최초 셋업(이미 있으면 건너뜀)
#   DB_SA_PASSWORD=**** scripts/dev-setup.sh --reset    # DB 삭제 후 재생성 (주의!)
# 환경변수: DB_HOST(기본 localhost), DB_NAME(기본 CJ_AIRPORT), DB_SA_USER(기본 sa), DB_SA_PASSWORD(필수)
set -euo pipefail
cd "$(dirname "$0")/.."

DB_HOST="${DB_HOST:-localhost}"
DB_NAME="${DB_NAME:-CJ_AIRPORT}"
DB_SA_USER="${DB_SA_USER:-sa}"
RESET="${1:-}"

if [ -z "${DB_SA_PASSWORD:-}" ]; then
  echo "❌ DB_SA_PASSWORD 환경변수가 필요합니다. 예: DB_SA_PASSWORD=**** scripts/dev-setup.sh"
  exit 1
fi

# sqlcmd 탐색 (PATH → 표준 설치 경로)
SQLCMD="$(command -v sqlcmd || true)"
if [ -z "$SQLCMD" ]; then
  SQLCMD="$(ls "/c/Program Files/Microsoft SQL Server/Client SDK/ODBC/"*/Tools/Binn/sqlcmd 2>/dev/null | head -1 || true)"
fi
[ -z "$SQLCMD" ] && { echo "❌ sqlcmd 를 찾을 수 없습니다. SQL Server Command Line Utilities 를 설치하세요."; exit 1; }

run_sql() { "$SQLCMD" -S "$DB_HOST" -U "$DB_SA_USER" -P "$DB_SA_PASSWORD" -C -l 5 "$@"; }

echo "== [1/4] 접속 확인 ($DB_HOST) =="
run_sql -Q "SELECT 1" >/dev/null || { echo "❌ MSSQL 접속 실패"; exit 1; }

if [ "$RESET" = "--reset" ]; then
  echo "== [!] --reset: $DB_NAME 삭제 =="
  run_sql -Q "IF DB_ID('$DB_NAME') IS NOT NULL BEGIN ALTER DATABASE [$DB_NAME] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$DB_NAME]; END"
fi

echo "== [2/4] DB/스키마 =="
run_sql -Q "IF DB_ID('$DB_NAME') IS NULL CREATE DATABASE [$DB_NAME];"
HAS_TABLES=$(run_sql -d "$DB_NAME" -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables WHERE name='tb_login_user';" | tr -d '[:space:]')
if [ "$HAS_TABLES" = "0" ]; then
  run_sql -d "$DB_NAME" -f 65001 -i sql/ddl/01_tables.sql
  echo "== [3/4] seed =="
  run_sql -d "$DB_NAME" -f 65001 -i sql/seed/02_seed.sql
else
  echo "   테이블이 이미 존재 → DDL/seed 건너뜀 (초기화하려면 --reset)"
fi

echo "== [4/4] 로컬 설정 파일 =="
LOCAL_PROPS="src/main/resources/application-local.properties"
if [ ! -f "$LOCAL_PROPS" ]; then
  cp "$LOCAL_PROPS.example" "$LOCAL_PROPS"
  sed -i "s/CHANGE_ME/$DB_SA_PASSWORD/" "$LOCAL_PROPS"
  echo "   $LOCAL_PROPS 생성(비밀번호 주입, gitignore 대상)"
else
  echo "   $LOCAL_PROPS 이미 존재 → 유지"
fi

echo ""
echo "✅ 셋업 완료. 실행: ./gradlew bootRun --args=\"--spring.profiles.active=local\""
echo "   로그인: http://localhost:8080/login  (admin/admin123, 조회전용 viewer/viewer123)"
echo "   검증:  scripts/smoke-test.sh"
