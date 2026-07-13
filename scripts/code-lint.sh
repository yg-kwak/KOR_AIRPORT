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

# ── 패턴 강제(문서만 두지 않고 grep 으로 검사) ────────────────────────────
echo "== [2] menu_id 하드코딩 금지 (MenuAccessInterceptor 가 URL 로 해석) =="
if grep -rnE 'static final int MENU_ID|int +MENU_ID *=' src/main/java/AirPort/controller >/dev/null 2>&1; then
  grep -rnE 'static final int MENU_ID|int +MENU_ID *=' src/main/java/AirPort/controller
  echo "  ❌ 컨트롤러에 MENU_ID 상수 금지 → currentMenu.getMenuId() 사용. (docs/security.md, conventions §4)"
  FAIL=1
else echo "  ✅ MENU_ID 하드코딩 없음"; fi

echo "== [3] 목록 페이징은 공통 pager 사용 (수동 번호 렌더 금지) =="
if grep -rn 'for (let i = 1; i <= totalPages' src/main/resources/static/js >/dev/null 2>&1; then
  grep -rn 'for (let i = 1; i <= totalPages' src/main/resources/static/js
  echo "  ❌ 수동 페이징 루프 금지 → pager.render(\$('paging'), page, totalPages, onGo). (docs/frontend.md)"
  FAIL=1
else echo "  ✅ 수동 페이징 없음"; fi

echo "== [4] 컨트롤러 @RequestMapping(/system/*) ↔ tb_menu.menu_url 일치 =="
SEED="sql/seed/02_seed.sql"; MAP_FAIL=0
while IFS= read -r path; do
  [ -z "$path" ] && continue
  if ! grep -q "'$path'" "$SEED"; then
    echo "  ❌ $path — seed 의 menu_url 에 없음. tb_menu 에 등록하거나 @RequestMapping 을 menu_url 과 일치시키세요(메뉴 해석·권한 근거). (docs/architecture.md §5)"
    FAIL=1; MAP_FAIL=1
  fi
done < <(grep -rhoE '@RequestMapping\("/system/[a-zA-Z]+"\)' src/main/java/AirPort/controller | grep -oE '/system/[a-zA-Z]+')
[ "$MAP_FAIL" -eq 0 ] && echo "  ✅ 매핑↔menu_url 일치"

exit $FAIL
