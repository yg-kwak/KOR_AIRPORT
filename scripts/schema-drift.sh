#!/usr/bin/env bash
# 스키마 드리프트 검사 — DDL(sql/ddl) ↔ docs/database.md 가 어긋나면 실패한다.
# 검사: ① DDL 의 모든 테이블이 문서에 '### tb_...' 섹션으로 존재 ② DDL 의 모든 컬럼명이 문서에 언급
#      ③ 문서의 '### tb_...' 섹션이 DDL 에 존재 (양방향)
# 구현 노트: Windows(Git Bash) 프로세스 스폰이 느려 awk 1회 + 순수 bash 문자열 검사로 구성.
set -uo pipefail
cd "$(dirname "$0")/.."
DDL="sql/ddl/01_tables.sql"; DOC="docs/database.md"; FAIL=0

DOC_CONTENT=$'\n'"$(cat "$DOC")"
in_doc() { [[ "$DOC_CONTENT" == *"$1"* ]]; }

# DDL 에서 "테이블 컬럼" 쌍과 테이블 목록을 한 번에 추출
PAIRS=$(awk '
  /^CREATE TABLE dbo\./ { t=$3; sub(/^dbo\./,"",t); sub(/\(.*/,"",t); intab=1; print "TABLE", t; next }
  intab && /^\);/ { intab=0; next }
  intab && /^  [a-z_]+/ { if ($1!="CONSTRAINT") print t, $1 }
' "$DDL")

echo "== [1] DDL 테이블 → 문서 섹션 =="
while read -r kind name; do
  [ "$kind" = "TABLE" ] || continue
  if in_doc $'\n'"### $name"; then echo "  ✅ $name"; else
    echo "  ❌ $name — docs/database.md 에 '### $name' 섹션이 없습니다. 테이블 추가 시 문서 사전도 갱신하세요."
    FAIL=1
  fi
done <<< "$PAIRS"

echo "== [2] DDL 컬럼 → 문서 언급 =="
COL_FAIL=0
while read -r t c; do
  [ "$t" = "TABLE" ] && continue
  if ! in_doc "$c"; then
    echo "  ❌ $t.$c — 문서에 없음. DDL 변경 시 docs/database.md 표를 함께 수정하세요."
    FAIL=1; COL_FAIL=1
  fi
done <<< "$PAIRS"
[ "$COL_FAIL" -eq 0 ] && echo "  ✅ 모든 컬럼 문서화됨"

echo "== [3] 문서 섹션 → DDL 존재 (역방향) =="
DDL_TABLES=$'\n'"$(awk '/^CREATE TABLE dbo\./ { t=$3; sub(/^dbo\./,"",t); sub(/\(.*/,"",t); print t }' "$DDL")"$'\n'
REV_FAIL=0
while read -r t; do
  [ -z "$t" ] && continue
  if [[ "$DDL_TABLES" != *$'\n'"$t"$'\n'* ]]; then
    echo "  ❌ 문서의 $t 가 DDL 에 없습니다 — 삭제된 테이블이면 문서에서 제거하세요."
    FAIL=1; REV_FAIL=1
  fi
done <<< "$(grep -oE '^### tb_[a-z_]+' "$DOC" | sed 's/^### //')"
[ "$REV_FAIL" -eq 0 ] && echo "  ✅ 역방향 일치"

exit $FAIL
