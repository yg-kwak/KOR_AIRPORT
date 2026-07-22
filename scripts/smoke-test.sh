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
check "공통코드 화면"  200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/system/common")"
# 사이드바 HTML 은 크고 매치가 앞쪽이라 `grep -q` 는 SIGPIPE(pipefail) 로 오탐 → 변수에 담아 case 로 판정
SB_A="$(A "$BASE_URL/system/common")"
check "admin(root) 사이드바 302 노출" 0 "$(case "$SB_A" in *'"/system/system"'*) echo 0;; *) echo 1;; esac)"
check "목록 조회"      200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/common/list?size=5")"
check "미인증 AJAX 401" 401 "$(curl -s -H 'X-Requested-With: XMLHttpRequest' -o /dev/null -w '%{http_code}' "$BASE_URL/system/common/list")"
# 전체(시스템 N + 사용자 Y) 노출 + 구분(user_input) 필드
check "시스템코드(AT) 목록 노출" 0 "$(A "$BASE_URL/system/common/list?searchType=cmmId&keyword=AT&size=200" | grep -q '"cmmId":"AT"' && echo 0 || echo 1)"
check "구분 필드 노출(AT=시스템 N)" 0 "$(A "$BASE_URL/system/common/list?searchType=cmmId&keyword=AT&size=5" | grep -q '"userInput":"N"' && echo 0 || echo 1)"

echo "== 코드구분 select (전체 구분) =="
check "구분 목록 200" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/common/groups")"
check "구분 목록에 VR 포함" 0 "$(A "$BASE_URL/system/common/groups" | grep -q '"cmmId":"VR"' && echo 0 || echo 1)"
check "구분 목록에 AT(시스템) 포함" 0 "$(A "$BASE_URL/system/common/groups" | grep -q '"cmmId":"AT"' && echo 0 || echo 1)"

echo "== CRUD (VR/SMKT1 임시행 — 사용자 코드) =="
# 이전 실행 잔여 정리(결과 무시)
A -X DELETE -o /dev/null "$BASE_URL/system/common?cmmId=VR&codeId=SMKT1" || true
check "등록(VR 그룹)" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"cmmId":"VR","codeId":"SMKT1","codeName":"smoke","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/common")"
check "없는 코드구분 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"cmmId":"ZZNOPE","codeId":"XX","codeName":"x","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/common")"
check "수정" 200 "$(A -H 'Content-Type: application/json' -X PUT  --data '{"cmmId":"VR","codeId":"SMKT1","codeName":"smoke2","useYn":"N"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/common")"
check "중복 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"cmmId":"VR","codeId":"SMKT1","codeName":"dup","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/common")"
check "시스템코드 삭제 차단(403)" 403 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/common?cmmId=AT&codeId=READ")"
check "엑셀 다운로드" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/common/excel?searchType=cmmId&keyword=VR&purpose=smoke-test")"
check "엑셀 purpose 누락 400" 400 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/common/excel?size=1")"
check "삭제" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/common?cmmId=VR&codeId=SMKT1")"

echo "== 설정관리(tb_system) =="
# 저장(POST /system/system) 체크는 일부러 제외한다 — tb_system 을 더미로 덮어쓰면
# 이후 IDE 로컬 실행 시 실제 BiostarX 접속정보가 사라진다(연동 메뉴 오작동).
# 실제 접속정보는 application-local.properties(app.biostar.*) → BiostarLocalSeeder 가
# 부팅 시 tb_system 에 시드한다. 화면(GET)·연결 테스트(POST /test)는 tb_system 을 변경하지 않아 유지.
check "설정 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/system/system")"
# 연결 테스트: 실제 BiostarX 없으면 실패(success=false, HTTP 200) — 엔드포인트 동작만 확인
check "연결 테스트 응답(HTTP 200)" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"biostarIp":"192.168.0.250","biostarId":"admin","biostarPw":"x"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/system/test")"

echo "== 계정 자가서비스(헤더 사용자 메뉴) =="
# 비파괴 검증만: 목록 조회 + 가드(잘못된 값 400). 비밀번호/시작메뉴 실제 변경은 admin 계정을 훼손하므로 제외.
check "시작메뉴 후보 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/account/menus")"
check "시작메뉴 목록에 메뉴명 노출" 0 "$(A "$BASE_URL/account/menus" | grep -q '"menuName"' && echo 0 || echo 1)"
check "무권한 메뉴 시작메뉴 지정 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"startMenuId":999999}' -o /dev/null -w '%{http_code}' "$BASE_URL/account/startMenu")"
check "이전 비밀번호 불일치 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"oldPassword":"wrongwrong","newPassword":"newpw123","confirmPassword":"newpw123"}' -o /dev/null -w '%{http_code}' "$BASE_URL/account/password")"
check "변경 비밀번호 불일치 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"oldPassword":"admin123","newPassword":"newpw123","confirmPassword":"mismatch9"}' -o /dev/null -w '%{http_code}' "$BASE_URL/account/password")"
check "미인증 계정 API 401" 401 "$(curl -s -H 'X-Requested-With: XMLHttpRequest' -o /dev/null -w '%{http_code}' "$BASE_URL/account/menus")"

echo "== 사용자관리(tb_login_user) =="
A -X DELETE -o /dev/null "$BASE_URL/system/loginUser?userId=smokeusr" || true
check "사용자 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser")"
check "사용자 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser/list?size=5")"
check "참조 데이터(refs)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser/refs")"
# BiostarX 장치 조회(장치ID 팝업): 실제 장치 없으면 success=false, HTTP 200 — 엔드포인트 동작만 확인
check "BiostarX 장치 응답(HTTP 200)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser/biostarDevices")"
# 본문에 비ASCII(한글)를 쓰지 않는다 — Windows Git Bash 가 인자를 CP949 로 넘겨 UTF-8 파싱이 깨짐(브라우저 UTF-8 요청은 정상)
check "등록" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"userId":"smokeusr","userName":"SmokeUser","password":"pw123","deptName":"OpsTeam","authId":1,"workLocationCode":"T1","useYn":"Y","rootYn":"N"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser")"
check "목록에 성명 노출(ARIA 복호화)" 0 "$(A "$BASE_URL/system/loginUser/list?searchType=userId&keyword=smokeusr&size=5" | grep -q '"userName":"SmokeUser"' && echo 0 || echo 1)"
check "근무지역명 조인(코드명 표시)" 0 "$(A "$BASE_URL/system/loginUser/list?searchType=userId&keyword=smokeusr&size=5" | grep -q '"workLocationName":"' && echo 0 || echo 1)"
check "비밀번호 미노출(응답에 password 키 없음)" 0 "$(A "$BASE_URL/system/loginUser/list?searchType=userId&keyword=smokeusr&size=5" | grep -q '"password"' && echo 1 || echo 0)"
check "중복 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"userId":"smokeusr","userName":"dup","password":"x","useYn":"Y","rootYn":"N"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser")"
check "수정(비번 빈값=유지)" 200 "$(A -H 'Content-Type: application/json' -X PUT --data '{"userId":"smokeusr","userName":"SmokeUser2","password":"","deptName":"SecTeam","authId":1,"useYn":"N"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser")"
check "권한 누락 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"userId":"noauth","userName":"NoAuth","password":"pw"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser")"
check "코드 팝업 조회(LO)" 0 "$(A "$BASE_URL/system/common/picker?cmmId=LO" | grep -q '"codeId":"T1"' && echo 0 || echo 1)"
check "엑셀 다운로드" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser/excel?searchType=userId&keyword=smokeusr&purpose=smoke-test")"
check "본인 삭제 차단(400)" 400 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser?userId=admin")"
check "삭제" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser?userId=smokeusr")"

echo "== 권한메뉴관리(tb_menu_auth) =="
check "권한 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth")"
check "권한 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth/list?size=5")"
check "권한 메뉴 트리" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth/menus")"
check "권한 상세(admin auth=1)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth/detail?authId=1")"
# 이전 실행에서 남은 SmokeAuth 정리(누적/오염 방지 — 시드 auth_id<=2 는 건드리지 않음)
for lid in $(A "$BASE_URL/system/menuAuth/list?keyword=SmokeAuth&size=50" | grep -oE '"authId":[0-9]+' | grep -oE '[0-9]+'); do
  [ "$lid" -gt 2 ] && A -X DELETE -o /dev/null "$BASE_URL/system/menuAuth?authId=$lid"
done
# 등록: 권한명 + 메뉴 301(조회만). 시드 권한(auth_id<=2)은 절대 update/delete 하지 않는다.
SMOKE_AUTH="$(A -H 'Content-Type: application/json' -X POST --data '{"authName":"SmokeAuth","details":[{"menuId":301,"readAuth":"Y","createAuth":"N","deleteAuth":"N"}]}' "$BASE_URL/system/menuAuth")"
check "권한 등록" 0 "$(case "$SMOKE_AUTH" in *'"success":true'*) echo 0;; *) echo 1;; esac)"
# 방금 만든 SmokeAuth 의 authId = keyword 필터 결과 중 최대값(SIGPIPE 회피 위해 head 대신 sort|tail)
NEW_AUTH_ID="$(A "$BASE_URL/system/menuAuth/list?keyword=SmokeAuth&size=50" | grep -o '"authId":[0-9]*' | grep -o '[0-9]*' | sort -n | tail -1)"
[ -z "$NEW_AUTH_ID" ] && NEW_AUTH_ID=0
check "권한명 누락 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"authName":"","details":[]}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth")"
check "권한 엑셀" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth/excel?keyword=SmokeAuth&purpose=smoke-test")"
check "사용중 권한 삭제 차단(admin auth=1, 400)" 400 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth?authId=1")"
if [ "$NEW_AUTH_ID" -gt 2 ]; then
  check "권한 수정" 200 "$(A -H 'Content-Type: application/json' -X PUT --data "{\"authId\":${NEW_AUTH_ID},\"authName\":\"SmokeAuth2\",\"details\":[{\"menuId\":301,\"readAuth\":\"Y\",\"createAuth\":\"Y\",\"deleteAuth\":\"N\"}]}" -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth")"
  check "권한 삭제(미사용)" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth?authId=${NEW_AUTH_ID}")"
else
  bad "SmokeAuth authId 추출 실패($NEW_AUTH_ID) — 시드 권한 보호 위해 수정/삭제 건너뜀"
fi

echo "== 감사추적(tb_system_log, 조회 전용) =="
check "감사추적 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog")"
check "감사 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/list?size=5")"
check "유형 옵션" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/types")"
check "유형 옵션에 READ 포함" 0 "$(A "$BASE_URL/security/systemLog/types" | grep -q '"codeId":"READ"' && echo 0 || echo 1)"
# 메뉴 접속 감사(MENU): 페이지 GET 후 감사로그에 MENU 유형이 남는지(인터셉터가 menu_id 해석)
curl -s -b "$CK_A" -o /dev/null "$BASE_URL/system/common"  # 메뉴 접속 유발
check "메뉴 접속 감사(MENU) 기록" 0 "$(A "$BASE_URL/security/systemLog/list?actionType=MENU&size=5" | grep -q '"actionType":"MENU"' && echo 0 || echo 1)"
check "유형 필터 조회(READ)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/list?actionType=READ&size=5")"
check "메뉴 옵션(본인 권한)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/menus")"
check "메뉴 필터 조회(305)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/list?menuId=501&size=5")"
check "기간 필터 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/list?startDate=2000-01-01&endDate=2999-12-31&size=5")"
check "엑셀 다운로드" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/excel?actionType=READ&purpose=smoke-test")"
check "엑셀 purpose 누락 400" 400 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/excel?size=1")"

echo "== 출입권한관리(tb_ac_group) =="
check "출입권한 화면(동기화)" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup")"
check "트리 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup/tree")"
check "동기화 최상위(AR) 존재" 0 "$(A "$BASE_URL/security/acGroup/tree" | grep -q '"arCode":"AR01"' && echo 0 || echo 1)"
check "BiostarX 출입그룹 응답(HTTP 200)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup/biostarGroups")"
TOP_AC=$(A "$BASE_URL/security/acGroup/tree" | grep -oE '"acGroupId":[0-9]+' | head -1 | grep -oE '[0-9]+')
check "최상위 삭제 차단(403)" 403 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup?acGroupId=${TOP_AC:-0}")"
check "하위 그룹 추가" 200 "$(A -H 'Content-Type: application/json' -X POST --data "{\"parentId\":${TOP_AC:-0},\"groups\":[{\"biostarAcId\":99999,\"biostarAcName\":\"SMOKEAC\"}]}" -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup/children")"
check "하위 트리 노출(SMOKEAC)" 0 "$(A "$BASE_URL/security/acGroup/tree" | grep -q '"biostarAcName":"SMOKEAC"' && echo 0 || echo 1)"
CHILD_AC=$(A "$BASE_URL/security/acGroup/tree" | grep -oE '"acGroupId":[0-9]+,"acGroupName":"SMOKEAC"' | grep -oE '[0-9]+' | head -1)
check "하위 수정(200)" 200 "$(A -H 'Content-Type: application/json' -X PUT --data "{\"acGroupId\":${CHILD_AC:-0},\"acGroupName\":\"SMOKEAC2\",\"biostarAcId\":99999,\"biostarAcName\":\"SMOKEAC\"}" -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup")"
check "하위 삭제(200)" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup?acGroupId=${CHILD_AC:-0}")"

echo "== 차량등록관리(tb_car) =="
check "차량 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
check "차량 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car/list?size=5")"
check "차종 코드팝업(CT)" 0 "$(A "$BASE_URL/system/common/picker?cmmId=CT" | grep -q '"codeId":"02"' && echo 0 || echo 1)"
# 본문 비ASCII 금지(Git Bash CP949 이슈) — 차량번호는 ASCII 로 검증
check "차량 등록" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"carNo":"SMOKE-CAR-1","carName":"SmokeCar","carType":"01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
check "차량번호 중복 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"carNo":"SMOKE-CAR-1","carName":"dup"}' -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
check "차량번호 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"carName":"nono"}' -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
check "목록 노출(SMOKE-CAR-1)" 0 "$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=SMOKE-CAR-1&size=5" | grep -q '"carNo":"SMOKE-CAR-1"' && echo 0 || echo 1)"
check "차종명 조인 노출(SUV)" 0 "$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=SMOKE-CAR-1&size=5" | grep -q '"carTypeName"' && echo 0 || echo 1)"
CAR_ID=$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=SMOKE-CAR-1&size=5" | grep -oE '"carId":[0-9]+' | head -1 | grep -oE '[0-9]+')
check "차량 수정(200)" 200 "$(A -H 'Content-Type: application/json' -X PUT --data "{\"carId\":${CAR_ID:-0},\"carNo\":\"SMOKE-CAR-1\",\"carName\":\"SmokeCar2\",\"carType\":\"02\"}" -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
check "엑셀 다운로드" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car/excel?searchType=carNo&keyword=SMOKE-CAR-1&purpose=smoke-test")"
check "차량 삭제(소프트,200)" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car?carId=${CAR_ID:-0}")"
check "삭제 후 목록 미노출" 0 "$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=SMOKE-CAR-1&size=5" | grep -q '"carNo":"SMOKE-CAR-1"' && echo 1 || echo 0)"
check "삭제된 차량번호 재등록 허용(200)" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"carNo":"SMOKE-CAR-1","carName":"reuse"}' -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
NEW_CAR_ID=$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=SMOKE-CAR-1&size=5" | grep -oE '"carId":[0-9]+' | head -1 | grep -oE '[0-9]+')
A -X DELETE -o /dev/null "$BASE_URL/carInfo/car?carId=${NEW_CAR_ID:-0}" || true

echo "== 기관등록관리(tb_company) =="
A -X DELETE -o /dev/null "$BASE_URL/company/company?companyCode=SMKCO1" || true
check "기관 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
check "기관 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/company/list?size=5")"
check "기관구분 코드팝업(CO)" 0 "$(A "$BASE_URL/system/common/picker?cmmId=CO" | grep -q '"codeId":"44"' && echo 0 || echo 1)"
# BiostarX 사용자그룹 조회(PTD01 하위): 실기기 없으면 success=false, HTTP 200 — 엔드포인트 동작만 확인(읽기 전용)
check "BiostarX 사용자그룹 응답(HTTP 200)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/company/biostarGroups")"
# 아래 등록/수정은 biostarGroupId 를 지정(선택 경로) — BiostarX 에 실제 그룹을 만들지 않는다(외부 부작용 방지)
check "기관 등록(대표자 ARIA)" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"companyCode":"SMKCO1","companyName":"SmokeOrg","companyType":"11","ceoName":"SmokeCeo","tel":"010","serviceStartDt":"2026-07-01","useYn":"Y","biostarGroupId":99999}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
check "기관코드 중복 등록 거절(400)" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"companyCode":"SMKCO1","companyName":"dup"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
check "기관명 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"companyCode":"SMKCO2"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
check "대표자 복호화 노출(SmokeCeo)" 0 "$(A "$BASE_URL/company/company/list?searchType=companyCode&keyword=SMKCO1&size=5" | grep -q '"ceoName":"SmokeCeo"' && echo 0 || echo 1)"
check "기관구분명 조인 노출" 0 "$(A "$BASE_URL/company/company/list?searchType=companyCode&keyword=SMKCO1&size=5" | grep -q '"companyTypeName"' && echo 0 || echo 1)"
check "기관 수정(200)" 200 "$(A -H 'Content-Type: application/json' -X PUT --data '{"companyCode":"SMKCO1","companyName":"SmokeOrg","companyType":"44","useYn":"N","biostarGroupId":99999}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
check "엑셀 다운로드" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/company/excel?searchType=companyCode&keyword=SMKCO1&purpose=smoke-test")"
check "기관 삭제(소프트,200)" 200 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/company/company?companyCode=SMKCO1")"
check "삭제 후 목록 미노출" 0 "$(A "$BASE_URL/company/company/list?searchType=companyCode&keyword=SMKCO1&size=5" | grep -q '"companyCode":"SMKCO1"' && echo 1 || echo 0)"
check "삭제된 기관코드 재등록 허용(200,되살리기)" 200 "$(A -H 'Content-Type: application/json' -X POST --data '{"companyCode":"SMKCO1","companyName":"reuse"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
A -X DELETE -o /dev/null "$BASE_URL/company/company?companyCode=SMKCO1" || true

echo "== 정규인원등록(tb_person) =="
# 등록 성공 경로는 BiostarX 에 실제 사용자를 생성하므로 smoke 에서 제외(외부 부작용 방지).
# 읽기 전용 경로 + 입력 검증(외부 호출 전에 실패)만 확인한다.
check "인원 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "인원 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/list?size=5")"
check "기관 선택 팝업(공용)" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/company/picker")"
check "출입권한 트리" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/acGroups")"
check "인원ID 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personName":"x"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "성명 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"SMKP1"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
# 수정/삭제도 존재 확인이 BiostarX 호출보다 먼저라 없는 인원은 외부 호출 없이 404
check "기관 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"SMKP2","personName":"x"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "출입종료일 상한 초과 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"SMKP3","personName":"x","companyCode":"SMKCO1","statusCode":"01","accessStartDt":"2026-01-01","accessEndDt":"2038-01-01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "출입시작일>종료일 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"SMKP4","personName":"x","companyCode":"SMKCO1","statusCode":"01","accessStartDt":"2027-01-01","accessEndDt":"2026-01-01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "없는 인원 수정 404" 404 "$(A -H 'Content-Type: application/json' -X PUT --data '{"personId":"NOPESMK","personName":"x","companyCode":"SMKCO1","statusCode":"01","accessStartDt":"2026-01-01","accessEndDt":"2026-12-31"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "없는 인원 삭제 404" 404 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/person/person?personId=NOPESMK")"
check "인원ID 형식 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"BAD_ID!","personName":"x","companyCode":"SMKCO1","statusCode":"01","accessStartDt":"2026-01-01","accessEndDt":"2026-12-31"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "생년월일 형식 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"SMKFMT","personName":"x","birthDate":"1990/01/01","companyCode":"SMKCO1","statusCode":"01","accessStartDt":"2026-01-01","accessEndDt":"2026-12-31"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "카드 필수값(패스구분) 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"personId":"SMKCARD","personName":"x","companyCode":"SMKCO1","statusCode":"01","accessStartDt":"2026-01-01","accessEndDt":"2026-12-31","cards":[{"cardNo":"1","cardName":"c","cardStatus":"CS01"}]}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
check "미할당 카드 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/card/unassigned?keyword=")"
check "인원 카드목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/cards?personId=NOPESMK")"
check "카드번호 없이 등록 실패" 0 "$(A -H 'Content-Type: application/json' -X POST --data '{}' "$BASE_URL/person/person/card/register" | grep -q '"success":false' && echo 0 || echo 1)"
check "없는 증빙문서 404" 404 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/file?personId=NOPESMK&fileType=ID_CHECK")"
check "일괄삭제 빈 목록 400" 400 "$(A -H 'Content-Type: application/json' -X DELETE --data '[]' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/bulk")"

echo "== 카드등록관리(tb_card) ==" 
check "카드 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/card/card")"
check "카드 목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/card/card/list?size=5")"
check "카드 미할당 필터" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/card/card/list?assigned=N&size=5")"
check "카드번호 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"cardName":"x"}' -o /dev/null -w '%{http_code}' "$BASE_URL/card/card")"
check "카드구분 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"biostarCardValue":"1","cardName":"x","passType":"PT01","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/card/card")"
check "카드 카드구분 필터" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/card/card/list?cardType=CDT01&size=5")"
check "카드 패스구분 필터" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/card/card/list?passType=PT01&size=5")"
check "인원카드 패스구분 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"biostarCardValue":"1","cardType":"CDT01","cardName":"x","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/card/card")"
check "없는 카드 수정 404" 404 "$(A -H 'Content-Type: application/json' -X PUT --data '{"cardId":999999,"biostarCardValue":"1","cardType":"CDT01","passType":"PT01","cardName":"x","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/card/card")"
check "없는 카드 삭제 404" 404 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/card/card?cardId=999999")"

echo "== 기관차량등록(tb_car + tb_card) ==" 
check "기관차량 화면" 200 "$(curl -s -b "$CK_A" -o /dev/null -w '%{http_code}' "$BASE_URL/company/car")"
check "기관차량 기관목록 조회" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/list?size=5")"
check "기관차량 사용유무 필터" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/list?useYn=Y&size=5")"
check "기관의 차량 목록" 200 "$(A -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/cars?companyCode=SMKCO1")"
check "기관 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"carNo":"99A9999","carName":"x","carType":"CT01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car")"
check "차량번호 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"companyCode":"SMKCO1","carName":"x","carType":"CT01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car")"
check "깨진 JSON 본문 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"carNo":' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car")"
check "없는 차량 삭제 404" 404 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/company/car?carId=999999")"
check "카드발급 카드번호 필수 400" 400 "$(A -H 'Content-Type: application/json' -X POST --data '{"carId":999999,"cardName":"x","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/card")"
check "없는 차량 카드발급 404" 404 "$(A -H 'Content-Type: application/json' -X POST --data '{"carId":999999,"cardNo":"1","cardName":"x","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/card")"
check "없는 카드 회수 404" 404 "$(A -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/card?cardId=999999")"

echo "== 권한 통제 (viewer: read Y / create·delete N) =="
VCODE=$(curl -s -m 2 -c "$CK_V" -o /dev/null -w "%{http_code}" --data "userId=viewer&password=viewer123" "$BASE_URL/login" 2>/dev/null)
if [ "$VCODE" = "302" ]; then
  V() { curl -s -b "$CK_V" -H "X-Requested-With: XMLHttpRequest" "$@"; }
  check "viewer 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/system/common/list?size=1")"
  check "viewer 등록 403"  403 "$(V -H 'Content-Type: application/json' -X POST --data '{"cmmId":"HK","codeId":"X","useYn":"Y"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/common")"
  check "viewer 삭제 403"  403 "$(V -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/system/common?cmmId=AT&codeId=READ")"
  check "viewer 사용자 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser/list?size=1")"
  check "viewer 사용자 등록 403"  403 "$(V -H 'Content-Type: application/json' -X POST --data '{"userId":"x","userName":"x","password":"x","useYn":"Y","rootYn":"N"}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/loginUser")"
  # 사이드바 권한 필터: viewer 는 302(설정관리) 미노출, 303(사용자관리)은 노출 (case 매칭 — SIGPIPE 회피)
  SB_V="$(V "$BASE_URL/system/common")"
  check "viewer 사이드바 302 미노출" 0 "$(case "$SB_V" in *'"/system/system"'*) echo 1;; *) echo 0;; esac)"
  check "viewer 사이드바 303 노출"   0 "$(case "$SB_V" in *'"/system/loginUser"'*) echo 0;; *) echo 1;; esac)"
  # 무권한 URL 직접 접근 → 403 권한없음 페이지
  check "viewer 무권한 URL 403"      403 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/system/system")"
  FP_V="$(V "$BASE_URL/system/system")"
  check "무권한 페이지 렌더"          0 "$(case "$FP_V" in *'id="forbiddenPage"'*) echo 0;; *) echo 1;; esac)"
  check "viewer 권한목록 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth/list?size=1")"
  check "viewer 권한 등록 403"      403 "$(V -H 'Content-Type: application/json' -X POST --data '{"authName":"x","details":[]}' -o /dev/null -w '%{http_code}' "$BASE_URL/system/menuAuth")"
  check "viewer 감사추적 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/security/systemLog/list?size=1")"
  check "viewer 출입권한 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup/tree")"
  check "viewer 하위 추가 403"     403 "$(V -H 'Content-Type: application/json' -X POST --data '{"parentId":1,"groups":[]}' -o /dev/null -w '%{http_code}' "$BASE_URL/security/acGroup/children")"
  check "viewer 차량 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car/list?size=1")"
  check "viewer 차량 등록 403"  403 "$(V -H 'Content-Type: application/json' -X POST --data '{"carNo":"VX-1"}' -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
  check "viewer 기관 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/company/company/list?size=1")"
  check "viewer 인원 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/list?size=1")"
  check "viewer 인원 삭제 403" 403 "$(V -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/person/person?personId=NOPESMK")"
  check "viewer 미할당 카드 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/card/unassigned")"
  check "viewer 카드 등록 403" 403 "$(V -H 'Content-Type: application/json' -X POST --data '{"cardNo":"1"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/card/register")"
  check "viewer 카드 읽기 403" 403 "$(V -H 'Content-Type: application/json' -X POST --data '{}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/card/scan")"
  check "viewer 인원 일괄삭제 403" 403 "$(V -H 'Content-Type: application/json' -X DELETE --data '["NOPESMK"]' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person/bulk")"
  check "viewer 인원 등록 403"  403 "$(V -H 'Content-Type: application/json' -X POST --data '{"personId":"VXP","personName":"x"}' -o /dev/null -w '%{http_code}' "$BASE_URL/person/person")"
  check "viewer 기관차량 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/list?size=1")"
  check "viewer 기관차량 등록 403" 403 "$(V -H 'Content-Type: application/json' -X POST --data '{"companyCode":"SMKCO1","carNo":"1","carName":"x","carType":"CT01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car")"
  check "viewer 차량카드 발급 403" 403 "$(V -H 'Content-Type: application/json' -X POST --data '{"carId":1,"cardNo":"1","cardName":"x","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/car/card")"
  check "viewer 카드관리 조회 허용" 200 "$(V -o /dev/null -w '%{http_code}' "$BASE_URL/card/card/list?size=1")"
  check "viewer 카드관리 등록 403" 403 "$(V -H 'Content-Type: application/json' -X POST --data '{"biostarCardValue":"1","cardType":"CDT01","passType":"PT01","cardName":"x","cardStatus":"CS01"}' -o /dev/null -w '%{http_code}' "$BASE_URL/card/card")"
  check "viewer 카드관리 삭제 403" 403 "$(V -X DELETE -o /dev/null -w '%{http_code}' "$BASE_URL/card/card?cardId=999999")"
  check "viewer 기관 등록 403"  403 "$(V -H 'Content-Type: application/json' -X POST --data '{"companyCode":"VXCO","companyName":"x"}' -o /dev/null -w '%{http_code}' "$BASE_URL/company/company")"
else
  bad "viewer 로그인 실패($VCODE) — seed 확인 필요"
fi

echo ""
echo "===== 결과: ✅ $PASS / ❌ $FAIL ====="
[ "$FAIL" -eq 0 ]
