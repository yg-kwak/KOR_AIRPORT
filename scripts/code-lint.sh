#!/usr/bin/env bash
# 코드 린트 — 파일 크기 제한(엔트로피 방지). 계층/네이밍 구조 검사는 ArchUnit(src/test)이 담당.
# Java 500줄 / JS 400줄 초과 시 실패. 예외는 ALLOWLIST 에 사유와 함께 등록.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0

# 예외 목록(파일명): 사유를 주석으로 남긴다
ALLOWLIST=(
  "ARIAEngine.java"   # 전자정부 표준 ARIA 구현 이식본(551줄) — 수정 금지 벤더 코드
)
allowed() { local f; for f in "${ALLOWLIST[@]}"; do [[ "$1" == *"$f" ]] && return 0; done; return 1; }

check_size() { # <glob-root> <ext> <limit>
  while IFS= read -r f; do
    lines=$(wc -l < "$f")
    if [ "$lines" -gt "$3" ] && ! allowed "$f"; then
      echo "  ❌ $f — ${lines}줄 > ${3}줄 제한"
      echo "     → 책임 단위로 분리하세요(예: 도메인별 클래스/모듈 분할). 불가피하면 scripts/code-lint.sh ALLOWLIST 에 사유와 함께 등록."
      FAIL=1
    fi
  done < <(find "$1" -name "*.$2" 2>/dev/null)
}

echo "== 파일 크기 제한 (Java≤500, JS≤400) =="
check_size src/main/java java 500
check_size src/main/resources/static/js js 400
[ "$FAIL" -eq 0 ] && echo "  ✅ 초과 파일 없음"

exit $FAIL
