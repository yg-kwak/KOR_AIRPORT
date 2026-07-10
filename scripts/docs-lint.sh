#!/usr/bin/env bash
# 문서 린트 — 지식 베이스(md)가 최신·상호링크 상태인지 기계 검증한다. (CI docs 잡에서도 실행)
# 검사: ① md 상대링크 깨짐 ② AGENTS.md 200줄 제한
# 구현 노트: Windows(Git Bash) 프로세스 스폰이 느려 grep 1회 + 순수 bash 로 구성.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0

echo "== [1] md 상대링크 검사 =="
# 루트/docs 의 모든 md 에서 [..](x.md) 링크를 한 번에 수집 → bash 내장으로 존재 확인
LINKS=$(grep -rHoE '\]\([^)]+\.md[^)]*\)' --include='*.md' . 2>/dev/null | grep -v '^\./\.git' || true)
LINK_FAIL=0
while IFS= read -r line; do
  [ -z "$line" ] && continue
  file="${line%%:*}"
  rest="${line#*:}"          # ](docs/x.md#anchor)
  target="${rest#*](}"       # docs/x.md#anchor)
  target="${target%)}"       # 닫는 괄호 제거
  target="${target%%#*}"     # 앵커 제거
  case "$target" in http*|mailto:*|"") continue ;; esac
  dir="${file%/*}"
  if [ ! -e "$dir/$target" ] && [ ! -e "$target" ]; then
    echo "  ❌ $file → 깨진 링크: $target"
    echo "     → 대상 파일을 만들거나 링크를 수정하세요. 문서 이동 시 참조하는 모든 md 를 함께 고칩니다."
    FAIL=1; LINK_FAIL=1
  fi
done <<< "$LINKS"
[ "$LINK_FAIL" -eq 0 ] && echo "  ✅ 깨진 링크 없음"

echo "== [2] AGENTS.md 줄 수 (지도는 200줄 이내) =="
LINES=$(wc -l < AGENTS.md)
if [ "$LINES" -gt 200 ]; then
  echo "  ❌ AGENTS.md ${LINES}줄 > 200줄 — 상세 내용은 docs/ 로 내리고 지도는 목차만 유지하세요."
  FAIL=1
else
  echo "  ✅ AGENTS.md ${LINES}줄"
fi

exit $FAIL
