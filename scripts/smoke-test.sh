#!/usr/bin/env bash
# 스모크 테스트 — 앱을 띄우고(HTTP만으로) 핵심 흐름을 검증한다. (Git Bash)
# 사용법:
#   scripts/smoke-test.sh              # 앱을 직접 부팅(local 프로파일) 후 검증하고 종료
#   scripts/smoke-test.sh --no-boot    # 이미 떠 있는 앱(8080)에 대해 검증만
# 전제: dev-setup 완료(DB+seed), 계정 admin/admin123 · viewer/viewer123
set -uo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
NO_BOOT="${1:-}"
BOOT_LOG="$(mktemp)"; CK_A="$(mktemp)"; CK_V="$(mktemp)"
PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
check() { # check <설명> <기대코드> <실제코드>
  [ "$3" = "$2" ] && ok "$1 ($3)" || bad "$1 — 기대 $2, 실제 $3"
}

stop_app() {
  if [ "$NO_BOOT" != "--no-boot" ]; then
    netstat -ano 2>/dev/null | grep ':8080' | grep LISTENING | awk '{print $5}' | sort -u \
      | while read -r pid; do taskkill //F //PID "$pid" >/dev/null 2>&1 || true; done
  fi
}
trap stop_app EXIT

if [ "$NO_BOOT" != "--no-boot" ]; then
  echo "== 앱 부팅(local) =="
  ./gradlew bootRun --console=plain \
    "--args=--spring.profiles.active=local --spring.devtools.restart.enabled=false" \
    > "$BOOT_LOG" 2>&1 &
fi

echo "== 기동 대기 =="
CODE=""
for _ in $(seq 1 60); do
  CODE=$(curl -s -m 2 -c "$CK_A" -o /dev/null -w "%{http_code}" \
    --data "userId=admin&password=admin123" "$BASE_URL/login" 2>/dev/null)
  [ "$CODE" = "302" ] && break; sleep 3
done
check "관리자 로그인(302 리다이렉트)" 302 "$CODE"
[ "$CODE" != "302" ] && { echo "기동 실패 — 로그: $BOOT_LOG"; tail -5 "$BOOT_LOG" 2>/dev/null; exit 1; }

A() { curl -s -b "$CK_A" -H "X-Requested-With: XMLHttpRequest" "$@"; }

echo "== 화면/조회 =="
check "공통코드 화면"  200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
check "목록 조회"      200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode/list?size=5")"
check "미인증 AJAX 401" 401 "$(curl -s -H 'X-Requested-With: XMLHttpRequest' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode/list")"
# 시스템 코드(AT=N)는 목록에 나오지 않아야 함
check "시스템코드(AT) 목록 제외" 0 "$(A "$BASE_URL/system/commonCode/list?searchType=cmmId&keyword=AT&size=200" | grep -q '"cmmId":"AT"' && echo 1 || echo 0)"

echo "== 코드구분 select (허용 구분만) =="
check "허용 구분 목록 200" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode/groups")"
check "허용 구분에 VR 포함" 0 "$(A "$BASE_URL/system/commonCode/groups" | grep -q '"cmmId":"VR"' && echo 0 || echo 1)"

echo "== CRUD (VR/SMKT1 임시행 — 허용 구분) =="
# 이전 실행 잔여 정리(결과 무시)
A -X DELETE -o /dev/null "$BASE_URL/system/commonCode?cmmId=VR&codeId=SMKT1" || true
check "등록(허용구분 VR)" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"cmmId":"VR","codeId":"SMKT1","codeName":"smoke","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
check "비허용 구분 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"cmmId":"AT","codeId":"XX","codeName":"x","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
check "수정" 200 "$(A -H 'Content-Type: application/json' -X PUT  --data '{"cmmId":"VR","codeId":"SMKT1","codeName":"smoke2","useYn":"N"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
check "중복 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"cmmId":"VR","codeId":"SMKT1","codeName":"dup","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
check "시스템코드 수정 차단(403)" 403 "$(A -H 'Content-Type: application/json' -X PUT --data '{"cmmId":"AT","codeId":"READ","codeName":"hack","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
check "시스템코드 삭제 차단(403)" 403 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode?cmmId=AT&codeId=READ")"
check "엑셀 다운로드" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode/excel?searchType=cmmId&keyword=VR&purpose=smoke-test")"
check "엑셀 purpose 누락 400" 400 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode/excel?size=1")"
check "삭제" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode?cmmId=VR&codeId=SMKT1")"

echo "== 권한 통제 (viewer: read Y / create·delete N) =="
VCODE=$(curl -s -m 2 -c "$CK_V" -o /dev/null -w "%{http_code}" --data "userId=viewer&password=viewer123" "$BASE_URL/login" 2>/dev/null)
if [ "$VCODE" = "302" ]; then
  V() { curl -s -b "$CK_V" -H "X-Requested-With: XMLHttpRequest" "$@"; }
  check "viewer 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode/list?size=1")"
  check "viewer 등록 403"  403 "$(V -H 'Content-Type: application/json' -X POST --data '{"cmmId":"HK","codeId":"X","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode")"
  check "viewer 삭제 403"  403 "$(V -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/commonCode?cmmId=AT&codeId=READ")"
else
  bad "viewer 로그인 실패($VCODE) — seed 확인 필요"
fi

echo ""
echo "===== 결과: ✅ $PASS / ❌ $FAIL ====="
[ "$FAIL" -eq 0 ]
