#!/usr/bin/env bash
# 문서 린트 — 지식 베이스(md)가 최신·상호링크 상태인지 기계 검증한다. (CI docs 잡에서도 실행)
# 검사: ① md 상대링크 깨짐 ② AGENTS.md 200줄 제한
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0

echo "== [1] md 상대링크 검사 =="
# 대상: 루트 md + docs/**.md. http(s)·앵커(#) 링크는 제외.
while IFS= read -r file; do
  dir=$(dirname "$file")
  while IFS= read -r link; do
    [ -z "$link" ] && continue
    target="${link%%#*}"                       # 앵커 제거
    [ -z "$target" ] && continue
    case "$target" in http*|mailto:*) continue ;; esac
    if [ ! -e "$dir/$target" ] && [ ! -e "$target" ]; then
      echo "  ❌ $file → 깨진 링크: $link"
      echo "     → 대상 파일을 만들거나 링크를 수정하세요. 문서 이동 시 참조하는 모든 md 를 함께 고칩니다."
      FAIL=1
    fi
  done < <(grep -oE '\]\(([^)]+\.md[^)]*)\)' "$file" 2>/dev/null | sed -E 's/^\]\(//; s/\)$//')
done < <(find . -maxdepth 1 -name '*.md' ; find docs -name '*.md' 2>/dev/null)
[ "$FAIL" -eq 0 ] && echo "  ✅ 깨진 링크 없음"

echo "== [2] AGENTS.md 줄 수 (지도는 200줄 이내) =="
LINES=$(wc -l < AGENTS.md)
if [ "$LINES" -gt 200 ]; then
  echo "  ❌ AGENTS.md ${LINES}줄 > 200줄 — 상세 내용은 docs/ 로 내리고 지도는 목차만 유지하세요."
  FAIL=1
else
  echo "  ✅ AGENTS.md ${LINES}줄"
fi

exit $FAIL
