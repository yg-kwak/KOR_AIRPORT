#!/usr/bin/env bash
# 심화 테스트 — 조회조건 / 문자셋 입력·수정 / 삭제(DB·BiostarX 대조) / 권한 매트릭스
#
# smoke-test.sh 가 "엔드포인트가 도는가"를 보는 빠른 관문이라면, 이 스크립트는
# "넣은 값이 그대로 살아남는가, 권한대로 막히는가"를 본다. 그래서 앱 응답만 믿지 않고
# DB 원본(sqlcmd)과 BiostarX(REST)를 직접 열어 대조한다.
#
# 사용법:
#   scripts/deep-test.sh                # 전체
#   scripts/deep-test.sh 조회 문자셋     # 지정 절만
# 전제: 앱이 떠 있을 것(부팅은 하지 않는다). 계정 admin/admin123.
# 환경변수:
#   BASE_URL     기본 https://localhost:8081
#   DB_HOST/DB_NAME/DB_USER/DB_PASSWORD   미지정 시 application-local.properties 에서 읽는다
#   BIOSTAR_IP/BIOSTAR_ID/BIOSTAR_PW      〃
#   SKIP_BIOSTAR=1  BiostarX 쓰기 구간(정규인원 전 구간)을 건너뛴다
set -uo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-https://localhost:8081}"
LOCAL_PROPS="src/main/resources/application-local.properties"

# ── 접속정보: 커밋된 스크립트에 비밀값을 넣지 않는다. git-ignored 파일에서 실행 시점에 읽는다.
prop() { [ -f "$LOCAL_PROPS" ] && grep "^$1=" "$LOCAL_PROPS" | head -1 | cut -d= -f2- || true; }
DB_URL="$(prop spring.datasource.url)"
DB_HOST="${DB_HOST:-$(printf '%s' "$DB_URL" | sed -n 's|.*//\([^:;]*\).*|\1|p')}"
DB_NAME="${DB_NAME:-$(printf '%s' "$DB_URL" | sed -n 's|.*databaseName=\([^;]*\).*|\1|p')}"
DB_USER="${DB_USER:-$(prop spring.datasource.username)}"
DB_PASSWORD="${DB_PASSWORD:-$(prop spring.datasource.password)}"
BIOSTAR_IP="${BIOSTAR_IP:-$(prop app.biostar.ip)}"
BIOSTAR_ID="${BIOSTAR_ID:-$(prop app.biostar.id)}"
BIOSTAR_PW="${BIOSTAR_PW:-$(prop app.biostar.pw)}"

SQLCMD="$(command -v sqlcmd || true)"
[ -z "$SQLCMD" ] && SQLCMD="$(ls "/c/Program Files/Microsoft SQL Server/Client SDK/ODBC/"*/Tools/Binn/sqlcmd 2>/dev/null | head -1 || true)"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
CK_A="$TMP/ck_admin"; PASS=0; FAIL=0; SKIP=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
skip() { echo "  ⚪ $1 (건너뜀)"; SKIP=$((SKIP+1)); }
# eq <설명> <기대> <실제>
eq() { if [ "$3" = "$2" ]; then ok "$1"; else bad "$1 — 기대 [$2], 실제 [$3]"; fi; }

# ── UTF-8 본문 ────────────────────────────────────────────────
# Git Bash 는 curl 인자를 CP949 로 넘겨 한글이 깨진다(실측: 인자 한글 → HTTP 400).
# 본문을 파일에 UTF-8 로 쓰고 --data @파일 로 넘기면 그대로 간다. 문자셋 테스트의 전제.
BODY="$TMP/body.json"
J() { printf '%s' "$1" > "$BODY"; printf '%s' "$BODY"; }
# JSON 문자열 이스케이프 — 역슬래시와 큰따옴표만 처리하면 이 테스트 값들은 충분하다
# JSON 문자열 이스케이프 — 역슬래시와 큰따옴표.
# ${s//\/\\} 형태는 bash 가 치환문의 역슬래시를 한 번 더 먹어 역슬래시가 안 늘어난다.
# 변수에 담은 역슬래시로 치환해야 제대로 두 개가 된다.
BS=$(printf '\')
jesc() { local s=$1; s=${s//"$BS"/"$BS$BS"}; s=${s//'"'/"$BS\""}; printf '%s' "$s"; }

# ── HTTP ─────────────────────────────────────────────────────
# -k: local 프로파일은 self-signed 인증서로 HTTPS 를 연다
A()  { curl -sk -b "$CK_A" -H "X-Requested-With: XMLHttpRequest" "$@"; }
AC() { A -o /dev/null -w '%{http_code}' "$@"; }                       # 코드만
AJ() { A -H 'Content-Type: application/json' -X "$1" --data @"$(J "$3")" \
         -o /dev/null -w '%{http_code}' "$2"; }                        # AJ <메서드> <URL> <JSON>
AJB(){ A -H 'Content-Type: application/json' -X "$1" --data @"$(J "$3")" "$2"; }  # 본문까지

# ── DB 직접 조회 ──────────────────────────────────────────────
# 앱 응답만 보면 "조회 SQL 과 저장 SQL 이 나란히 틀린" 경우를 못 잡는다. 원본을 연다.
db() {
  [ -z "$SQLCMD" ] && { printf '(sqlcmd없음)'; return; }
  "$SQLCMD" -S "$DB_HOST" -U "$DB_USER" -P "$DB_PASSWORD" -d "$DB_NAME" -C -I -l 5 \
    -h -1 -W -Q "SET NOCOUNT ON; $1" 2>/dev/null | sed '/^$/d' | tr -d '\r' | head -1
}
# nvarchar 를 그대로 읽으면 콘솔 코드페이지에 깨진다. 16진수로 받아 바이트로 비교한다.
# MSSQL 의 nvarchar → varbinary 는 UTF-16LE 다(UTF-8 아님). 기대값도 iconv 로 UTF-16LE 로 맞춘다.
# db_hex <컬럼식> <FROM..WHERE절>
db_hex() { db "SELECT CONVERT(varchar(max), CONVERT(varbinary(max), $1), 2) $2;"; }
hex_of() { printf '%s' "$1" | iconv -f UTF-8 -t UTF-16LE | od -An -tx1 | tr -d ' \n' | tr '[:lower:]' '[:upper:]'; }

# ── BiostarX 직접 조회 ────────────────────────────────────────
BS_SID=""
bs_login() {
  [ -z "$BIOSTAR_IP" ] && return 1
  BS_SID=$(curl -sk -m 10 -D - -o /dev/null -X POST -H 'Content-Type: application/json' \
    --data @"$(J "{\"User\":{\"login_id\":\"$(jesc "$BIOSTAR_ID")\",\"password\":\"$(jesc "$BIOSTAR_PW")\"}}")" \
    "https://$BIOSTAR_IP/api/login" | grep -i '^bs-session-id:' | tr -d '\r' | awk '{print $2}')
  [ -n "$BS_SID" ]
}
# 존재하면 200, 없으면 400 (실측 확인)
bs_user_code() { curl -sk -m 10 -o /dev/null -w '%{http_code}' -H "bs-session-id: $BS_SID" \
                   "https://$BIOSTAR_IP/api/users/$1"; }

# 요청 JSON 의 문자열은 들어오는 문에서 앞뒤 공백을 뗀다(AirPort/config/JsonConfig).
# ARIA 복호화가 패딩을 벗기며 trim 하는 것과 맞춘 정책이라, 기대값도 같은 규칙으로 만든다.
trimv() { local s=$1; s="${s#"${s%%[![:space:]]*}"}"; printf '%s' "${s%"${s##*[![:space:]]}"}"; }

# 500(서버 오류)만 아니면 통과 — 200 보정이든 400 거절이든 서버의 판단이다
notfive() { case "$1" in 500) echo "500";; *) echo "ok";; esac; }

want() { case " ${WANT:-} " in *" $1 "*|"  ") return 0;; *) return 1;; esac; }
WANT="$*"

# GET 쿼리스트링의 한글도 인자로 넘기면 깨진다(실측: 검색 0건). 바이트를 전부 %XX 로 인코딩해 보낸다.
urlenc() { printf '%s' "$1" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g'; }

# ══════════════════════════════════════════════════════════════
echo "═══ §0 준비 ═══"
LOGIN_CODE=$(curl -sk -m 10 -c "$CK_A" -o /dev/null -w '%{http_code}' \
  --data "userId=admin&password=admin123" "$BASE_URL/login")
eq "관리자 로그인" 302 "$LOGIN_CODE"
[ "$LOGIN_CODE" != "302" ] && { echo "앱이 안 떠 있습니다 — BASE_URL=$BASE_URL"; exit 1; }
eq "DB 직접 연결" "$DB_NAME" "$(db "SELECT DB_NAME();")"
if bs_login; then ok "BiostarX 세션 발급"; else skip "BiostarX 세션 발급 실패 — 장비 대조 구간"; fi

# 이전 실행 잔여 정리 — 실패로 중단돼도 다음 실행이 깨끗한 상태에서 시작한다
# 시험 잔여물 전수 정리 — 시작과 끝 양쪽에서 부른다.
# 앱의 DELETE 는 상당수가 소프트 삭제라 행이 남는다. 공유 개발 DB 에 매 실행마다 쌓이면
# 다음 실행의 조회 건수·중복 판정이 흔들린다. 시험 데이터는 ZZ 접두로 격리해 하드 삭제한다.
purge_all() {
  db "DELETE FROM tb_person_ac_group WHERE person_id LIKE 'ZZ%';" >/dev/null
  db "DELETE FROM tb_person_photo    WHERE person_id LIKE 'ZZ%';" >/dev/null
  db "UPDATE tb_card SET person_id=NULL, car_id=NULL
        WHERE person_id LIKE 'ZZ%'
           OR car_id IN (SELECT car_id FROM tb_car WHERE car_no LIKE 'ZZ%');" >/dev/null
  db "DELETE FROM tb_person   WHERE person_id LIKE 'ZZ%';" >/dev/null
  db "DELETE FROM tb_car_ac_group WHERE car_id IN (SELECT car_id FROM tb_car WHERE car_no LIKE 'ZZ%');" >/dev/null
  db "DELETE FROM tb_car      WHERE car_no LIKE 'ZZ%';" >/dev/null
  db "DELETE FROM tb_card     WHERE biostar_card_value LIKE 'ZZ%';" >/dev/null
  db "DELETE FROM tb_company  WHERE company_code LIKE 'ZZ%';" >/dev/null
  db "DELETE FROM tb_common   WHERE code_id LIKE 'ZZ%';" >/dev/null
  db "DELETE FROM tb_login_user WHERE user_id LIKE 'zzu%' OR user_id LIKE 'zzrole%' OR user_id LIKE 'zzdel%';" >/dev/null
  db "DELETE FROM tb_menu_auth_detail WHERE auth_id IN (SELECT auth_id FROM tb_menu_auth WHERE auth_name LIKE N'ZZ%');" >/dev/null
  db "DELETE FROM tb_menu_auth WHERE auth_name LIKE N'ZZ%' AND auth_id > 2;" >/dev/null
}
purge_all

# ══════════════════════════════════════════════════════════════
if want 조회; then
echo ""
echo "═══ §1 조회 — 조건별 ═══"

# ── 페이징 ──────────────────────────────────────────────────
CNT1=$(A "$BASE_URL/system/common/list?page=1&size=3" | grep -o '"codeId"' | wc -l | tr -d ' ')
eq "size=3 이면 3건" 3 "$CNT1"
P1=$(A "$BASE_URL/system/common/list?page=1&size=3&sort=codeId&dir=asc" | grep -o '"codeId":"[^"]*"' | tr '\n' ',')
P2=$(A "$BASE_URL/system/common/list?page=2&size=3&sort=codeId&dir=asc" | grep -o '"codeId":"[^"]*"' | tr '\n' ',')
if [ -n "$P1" ] && [ "$P1" != "$P2" ]; then ok "2페이지는 1페이지와 다른 행"; else bad "페이징이 안 먹는다 — 1p[$P1] 2p[$P2]"; fi
# 사용자가 URL 을 직접 만지면 들어오는 값들. 어느 것도 500(서버 오류)이면 안 된다 —
# 200(보정) 이든 400(거절) 이든 서버가 판단해서 답해야 한다.
eq "size=0 이 500 아님"        ok "$(notfive "$(AC "$BASE_URL/system/common/list?size=0")")"
eq "size 음수가 500 아님"      ok "$(notfive "$(AC "$BASE_URL/system/common/list?size=-5")")"
eq "size 비숫자가 500 아님"    ok "$(notfive "$(AC "$BASE_URL/system/common/list?size=xyz")")"
eq "page 비숫자가 500 아님"    ok "$(notfive "$(AC "$BASE_URL/system/common/list?page=abc")")"
eq "size 과대(99999) 정상"     200 "$(AC "$BASE_URL/system/common/list?size=99999")"
eq "page 과대(999999) 는 0건"  0 "$(A "$BASE_URL/system/common/list?page=999999&size=10" | grep -o '"codeId"' | wc -l | tr -d ' ')"
eq "page 음수 정상 보정"       200 "$(AC "$BASE_URL/system/common/list?page=-1&size=10")"
# PageParam 은 모든 목록이 공유한다 — 한 곳이 깨지면 전 화면이 같이 깨진다
for u in /person/person /company/company /card/card /carInfo/car /system/loginUser; do
  eq "size=0 이 500 아님 ($u)" ok "$(notfive "$(AC "$BASE_URL$u/list?size=0")")"
done

# ── 정렬 ────────────────────────────────────────────────────
ASC=$(A "$BASE_URL/system/common/list?size=10&sort=codeId&dir=asc"  | grep -o '"codeId":"[^"]*"' | tr '\n' ',')
DESC=$(A "$BASE_URL/system/common/list?size=10&sort=codeId&dir=desc" | grep -o '"codeId":"[^"]*"' | tr '\n' ',')
if [ -n "$ASC" ] && [ "$ASC" != "$DESC" ]; then ok "asc/desc 결과가 다르다"; else bad "정렬 방향이 안 먹는다"; fi
# sort 는 화이트리스트다 — 밖의 값이 SQL 로 새면 여기서 500 이 난다
eq "정렬 화이트리스트 밖 무시(500 아님)" 200 "$(AC "$BASE_URL/system/common/list?size=5&sort=code_id;DROP%20TABLE%20tb_common--")"
eq "dir 이상값 무시(500 아님)"          200 "$(AC "$BASE_URL/system/common/list?size=5&sort=codeId&dir=sideways")"

# ── 검색어 ──────────────────────────────────────────────────
eq "부분일치 검색"   200 "$(AC "$BASE_URL/system/common/list?searchType=codeName&keyword=$(urlenc '코드')&size=5")"
eq "0건 검색은 빈 목록" 0 "$(A "$BASE_URL/system/common/list?searchType=codeId&keyword=$(urlenc 'ZZ없는값ZZ')&size=5" | grep -o '"codeId"' | wc -l | tr -d ' ')"
eq "빈 검색어는 전체"  200 "$(AC "$BASE_URL/system/common/list?searchType=codeName&keyword=&size=5")"
eq "검색어에 %와 _ (LIKE 와일드카드) 500 아님" 200 "$(AC "$BASE_URL/system/common/list?searchType=codeName&keyword=$(urlenc '%_%')&size=5")"
eq "검색어에 홑따옴표 500 아님"   200 "$(AC "$BASE_URL/system/common/list?searchType=codeName&keyword=$(urlenc "a'b")&size=5")"
eq "검색어 SQL 주입 500 아님"     200 "$(AC "$BASE_URL/system/common/list?searchType=codeName&keyword=$(urlenc "'; DROP TABLE tb_common; --")&size=5")"
eq "SQL 주입 후 테이블 살아있음"  1 "$(db "SELECT COUNT(*) FROM sys.tables WHERE name='tb_common';")"
eq "없는 searchType 500 아님"     200 "$(AC "$BASE_URL/system/common/list?searchType=nosuchfield&keyword=x&size=5")"

# ── 도메인 필터 · 복합조건 ──────────────────────────────────
eq "사용여부 Y 필터" 200 "$(AC "$BASE_URL/system/common/list?useYn=Y&size=5")"
eq "사용여부 N 필터" 200 "$(AC "$BASE_URL/system/common/list?useYn=N&size=5")"
eq "복합(구분+사용여부+정렬+페이징)" 200 "$(AC "$BASE_URL/system/common/list?searchType=cmmId&keyword=AT&useYn=Y&sort=codeId&dir=desc&page=1&size=5")"
eq "카드 3중 필터"   200 "$(AC "$BASE_URL/card/card/list?cardType=CDT01&passType=PT01&assigned=N&size=5")"
eq "인원 기관+상태 필터" 200 "$(AC "$BASE_URL/person/person/list?statusCode=01&size=5")"
eq "차량 목록 정렬"  200 "$(AC "$BASE_URL/carInfo/car/list?sort=carNo&dir=desc&size=5")"
eq "기관 사용여부+검색어" 200 "$(AC "$BASE_URL/company/company/list?useYn=Y&searchType=companyName&keyword=$(urlenc '항공')&size=5")"

# ── 기간 필터 ───────────────────────────────────────────────
eq "감사 기간 정상"      200 "$(AC "$BASE_URL/security/systemLog/list?startDate=2026-01-01&endDate=2026-12-31&size=5")"
eq "감사 기간 역전(시작>종료) 0건" 0 "$(A "$BASE_URL/security/systemLog/list?startDate=2027-01-01&endDate=2026-01-01&size=5" | grep -o '"logId"' | wc -l | tr -d ' ')"
eq "감사 기간 형식 오류 500 아님" ok "$(notfive "$(AC "$BASE_URL/security/systemLog/list?startDate=2026-13-45&endDate=abc&size=5")")"
eq "감사 시작일만"       200 "$(AC "$BASE_URL/security/systemLog/list?startDate=2026-01-01&size=5")"
eq "감사 유형+메뉴+기간 복합" 200 "$(AC "$BASE_URL/security/systemLog/list?actionType=READ&menuId=301&startDate=2026-01-01&endDate=2026-12-31&size=5")"
eq "주차 기간+방향 필터" 200 "$(AC "$BASE_URL/carInfo/parkingEvent/list?direction=IN&startDate=2026-01-01&endDate=2026-12-31&size=5")"
eq "주차 미개방만 필터"  200 "$(AC "$BASE_URL/carInfo/parkingEvent/list?notOpenOnly=true&size=5")"

# ── 암호화 컬럼 검색(ARIA 결정적 암호화 → 완전일치) ─────────
eq "성명 검색(암호문 완전일치) 500 아님" 200 "$(AC "$BASE_URL/person/person/list?searchType=personName&keyword=$(urlenc '홍길동')&size=5")"
eq "사용자 성명 검색 500 아님"           200 "$(AC "$BASE_URL/system/loginUser/list?searchType=userName&keyword=$(urlenc '관리자')&size=5")"
fi

# ══════════════════════════════════════════════════════════════
# 문자셋 표 — 이름류 필드에 넣어 보는 값들.
# 판정은 세 곳에서 같아야 한다: ① 등록 성공 ② 앱 조회 응답 ③ DB 원본(바이트).
# 앱 응답만 보면 저장·조회가 나란히 틀린 경우(둘 다 같은 인코딩으로 깨짐)를 놓친다.
CS_LABEL=(한글 영문 숫자 한영혼합 특수문자 홑따옴표 큰따옴표 역슬래시 꺾쇠XSS SQL주입 한글특수 앞뒤공백 이모지)
CS_VALUE=(
  '홍길동'
  'John Smith'
  '0123456789'
  '김Kim-1'
  '!@#$%^&*()_+-=[]{};:,.?/'
  "O'Brien"
  '그는 "관리자" 다'
  'C:\경로\파일'
  '<script>alert(1)</script>'
  "'; DROP TABLE tb_common; --"
  '㈜대한항공·제1여객터미널'
  '  앞뒤공백  '
  '공항🛫게이트'
)

# rt <설명> <기대값> <앱이 돌려준 값> <DB 원본 hex>
# DB 는 hex 로 받아 원본 바이트와 비교한다 — 콘솔 코드페이지에 속지 않기 위해서다.
rt() {
  local what=$1 want=$2 got=$3 dbhex=$4
  if [ "$got" != "$want" ]; then bad "$what — 앱 조회가 다르다: 기대[$want] 실제[$got]"; return; fi
  if [ -n "$dbhex" ] && [ "$dbhex" != "(sqlcmd없음)" ]; then
    if [ "$dbhex" != "$(hex_of "$want")" ]; then
      bad "$what — DB 원본이 다르다 (hex 기대 $(hex_of "$want") / 실제 $dbhex)"; return
    fi
  fi
  ok "$what"
}
# 코드포인트 → UTF-8 문자. bash 5.2 의 printf 는 \U 를 지원하지 않아 바이트를 직접 조립한다.
b2c() { printf "\x$(printf '%02x' "$1")"; }
cp2utf8() {
  local cp=$1
  if   [ "$cp" -lt 128 ];   then b2c "$cp"
  elif [ "$cp" -lt 2048 ];  then b2c $((0xC0|cp>>6)); b2c $((0x80|cp&0x3F))
  elif [ "$cp" -lt 65536 ]; then b2c $((0xE0|cp>>12)); b2c $((0x80|(cp>>6&0x3F))); b2c $((0x80|cp&0x3F))
  else b2c $((0xF0|cp>>18)); b2c $((0x80|(cp>>12&0x3F))); b2c $((0x80|(cp>>6&0x3F))); b2c $((0x80|cp&0x3F))
  fi
}

# 앱 조회 응답(JSON)에서 문자열 필드 하나 뽑기 — jq 없이 순수 bash.
# sed 로 하면 값 안의 따옴표·역슬래시에 정규식이 부서진다. 한 글자씩 읽어 이스케이프를 되돌린다.
# \uXXXX 도 푼다 — Jackson 이 BMP 밖 문자(이모지)를 서로게이트 쌍으로 내보내기 때문이다.
jget() {
  local json=$1 key=$2 rest out= c n bs h cp lo
  bs=$(printf '\')
  rest=${json#*\"$key\":\"}
  [ "$rest" = "$json" ] && return          # 키가 없다
  while [ -n "$rest" ]; do
    c=${rest:0:1}
    if [ "$c" = '"' ]; then break; fi
    if [ "$c" != "$bs" ]; then out=$out$c; rest=${rest:1}; continue; fi
    n=${rest:1:1}
    if [ "$n" = "u" ]; then
      h=${rest:2:4}; cp=$((16#$h))
      if [ "$cp" -ge 55296 ] && [ "$cp" -le 56319 ] && [ "${rest:6:2}" = "${bs}u" ]; then
        lo=$((16#${rest:8:4}))
        cp=$(( 65536 + ((cp - 55296) << 10) + (lo - 56320) ))
        out=$out$(cp2utf8 "$cp"); rest=${rest:12}
      else
        out=$out$(cp2utf8 "$cp"); rest=${rest:6}
      fi
      continue
    fi
    case "$n" in
      '"')   out=$out'"' ;;
      "$bs") out=$out$bs ;;
      n)     out=$out$'\n' ;;
      t)     out=$out$'\t' ;;
      r)     out=$out$'\r' ;;
      /)     out=$out'/' ;;
      *)     out=$out$bs$n ;;
    esac
    rest=${rest:2}
  done
  printf '%s' "$out"
}

# ── 문자셋 공통 엔진 ─────────────────────────────────────────
# 화면마다 등록 본문·조회 방법·정리 방법이 달라, 호출 직전에 아래 4개를 정의해 두고 cs_suite 를 부른다.
#   cs_body <순번> <값>  등록 JSON      cs_upd <순번> <값>  수정 JSON (없으면 수정 절 생략)
#   cs_get  <순번>       앱이 돌려준 이름   cs_key <순번>       DB WHERE 절
#   cs_purge <순번>      정리
# cs_suite <제목> <DB테이블> <이름컬럼> <ENC|PLAIN>
cs_suite() {
  local title=$1 tbl=$2 col=$3 mode=$4 i L V C GOT DBH DBRAW
  echo "── $title ──"
  for i in "${!CS_LABEL[@]}"; do
    L="${CS_LABEL[$i]}"; V="${CS_VALUE[$i]}"
    cs_purge "$i"
    C=$(AJ POST "$CS_URL" "$(cs_body "$i" "$V")")
    if [ "$C" != "200" ]; then bad "등록[$L] HTTP $C"; cs_purge "$i"; continue; fi
    GOT=$(cs_get "$i")
    if [ "$mode" = "ENC" ]; then
      # 암호화 컬럼은 DB 에 평문이 있으면 안 된다(AGENTS §4 개인정보 암호화)
      DBRAW=$(db "SELECT $col $(cs_key "$i");")
      rt_enc "등록·왕복[$L]" "$(trimv "$V")" "$GOT" "$DBRAW"
    else
      DBH=$(db_hex "$col" "$(cs_key "$i")")
      rt "등록·왕복[$L]" "$(trimv "$V")" "$GOT" "$DBH"
    fi
    # 수정: 다른 문자셋 값으로 바꿔 다시 왕복시킨다(수정 경로가 등록과 다른 SQL 을 탄다)
    if declare -F cs_upd >/dev/null; then
      local V2="${CS_VALUE[$(( (i+5) % ${#CS_VALUE[@]} ))]}"
      C=$(AJ PUT "$CS_URL" "$(cs_upd "$i" "$V2")")
      if [ "$C" != "200" ]; then bad "수정[$L→] HTTP $C"; else
        GOT=$(cs_get "$i")
        if [ "$mode" = "ENC" ]; then rt_enc "수정·왕복[$L]" "$(trimv "$V2")" "$GOT" "$(db "SELECT $col $(cs_key "$i");")"
        else rt "수정·왕복[$L]" "$(trimv "$V2")" "$GOT" "$(db_hex "$col" "$(cs_key "$i")")"; fi
      fi
    fi
    cs_purge "$i"
  done
}
# 암호화 컬럼 판정 — 앱은 평문으로 돌려주되 DB 에는 평문이 없어야 한다
rt_enc() {
  local what=$1 want=$2 got=$3 dbraw=$4
  if [ "$got" != "$want" ]; then bad "$what — 앱 조회 기대[$want] 실제[$got]"; return; fi
  if [ "$dbraw" = "$want" ]; then bad "$what — DB 에 평문 저장(암호화 누락)"; return; fi
  ok "$what (앱 왕복 + DB 암호문)"
}

if want 문자셋; then
echo ""
echo "═══ §2 입력 — 문자셋별 ═══"

# ── 공통코드: 코드명(nvarchar 100, 평문) ─────────────────────
echo "── 공통코드 code_name (평문) ──"
for i in "${!CS_LABEL[@]}"; do
  L="${CS_LABEL[$i]}"; V="${CS_VALUE[$i]}"; ID="ZZCS$i"
  A -X DELETE -o /dev/null "$BASE_URL/system/common?cmmId=VR&codeId=$ID"
  C=$(AJ POST "$BASE_URL/system/common" "{\"cmmId\":\"VR\",\"codeId\":\"$ID\",\"codeName\":\"$(jesc "$V")\",\"useYn\":\"Y\"}")
  if [ "$C" != "200" ]; then bad "등록[$L] HTTP $C"; continue; fi
  # 값에 { } 가 들어가면 중괄호 매칭이 부서진다 — 코드ID 로 걸러 한 행만 받아 통째로 판다
  GOT=$(jget "$(A "$BASE_URL/system/common/list?searchType=codeId&keyword=$ID&size=5")" codeName)
  DBH=$(db_hex "code_name" "FROM tb_common WHERE cmm_id='VR' AND code_id='$ID'")
  rt "등록·왕복[$L]" "$(trimv "$V")" "$GOT" "$DBH"
done

echo "── 공통코드 code_name 길이 경계(nvarchar 100) ──"
LONG100=$(printf '가%.0s' $(seq 1 100)); LONG101=$(printf '가%.0s' $(seq 1 101))
A -X DELETE -o /dev/null "$BASE_URL/system/common?cmmId=VR&codeId=ZZLEN1"
eq "정확히 100자 저장 성공" 200 "$(AJ POST "$BASE_URL/system/common" "{\"cmmId\":\"VR\",\"codeId\":\"ZZLEN1\",\"codeName\":\"$LONG100\",\"useYn\":\"Y\"}")"
eq "100자가 DB 에 100자로"  100 "$(db "SELECT LEN(code_name) FROM tb_common WHERE cmm_id='VR' AND code_id='ZZLEN1';")"
# 넘치면 잘라 넣지 말고 거절해야 한다. 500(SQL 오류)이면 검증이 없다는 뜻.
A -X DELETE -o /dev/null "$BASE_URL/system/common?cmmId=VR&codeId=ZZLEN2"
eq "101자는 400 으로 거절(500 아님)" ok "$(notfive "$(AJ POST "$BASE_URL/system/common" "{\"cmmId\":\"VR\",\"codeId\":\"ZZLEN2\",\"codeName\":\"$LONG101\",\"useYn\":\"Y\"}")")"
fi

if want 문자셋; then
echo ""
echo "═══ §3 입력·수정 — 화면별 문자셋 ═══"

# ── 차량등록관리: car_name (평문 nvarchar 50) ────────────────
CS_URL="$BASE_URL/carInfo/car"
carid() { A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=ZZCAR$1&size=5" | grep -oE '"carId":[0-9]+' | head -1 | grep -oE '[0-9]+'; }
cs_body()  { printf '{"carNo":"ZZCAR%s","carName":"%s","carType":"01"}' "$1" "$(jesc "$2")"; }
cs_upd()   { printf '{"carId":%s,"carNo":"ZZCAR%s","carName":"%s","carType":"02"}' "$(carid "$1")" "$1" "$(jesc "$2")"; }
cs_get()   { jget "$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=ZZCAR$1&size=5")" carName; }
cs_key()   { printf "FROM tb_car WHERE car_no='ZZCAR%s' AND del_yn='N'" "$1"; }
cs_purge() { local id; id=$(carid "$1"); [ -n "$id" ] && A -X DELETE -o /dev/null "$BASE_URL/carInfo/car?carId=$id"; return 0; }
cs_suite "차량 car_name (평문)" tb_car car_name PLAIN

# ── 기관등록관리: company_name(평문) / ceo_name(ARIA 암호화) ─
unset -f cs_upd
CS_URL="$BASE_URL/company/company"
# biostarGroupId 를 지정해 BiostarX 사용자그룹 생성 경로를 피한다(외부 부작용 방지)
cs_body()  { printf '{"companyCode":"ZZC%s","companyName":"%s","companyType":"11","ceoName":"CEO","useYn":"Y","biostarGroupId":99999}' "$1" "$(jesc "$2")"; }
cs_upd()   { printf '{"companyCode":"ZZC%s","companyName":"%s","companyType":"44","ceoName":"CEO","useYn":"Y","biostarGroupId":99999}' "$1" "$(jesc "$2")"; }
cs_get()   { jget "$(A "$BASE_URL/company/company/list?searchType=companyCode&keyword=ZZC$1&size=5")" companyName; }
cs_key()   { printf "FROM tb_company WHERE company_code='ZZC%s'" "$1"; }
cs_purge() { A -X DELETE -o /dev/null "$BASE_URL/company/company?companyCode=ZZC$1"; \
             db "DELETE FROM tb_company WHERE company_code='ZZC$1';" >/dev/null; return 0; }
cs_suite "기관 company_name (평문)" tb_company company_name PLAIN

# 대표자명은 ARIA 암호화 대상 — 앱은 평문으로 보여주되 DB 에는 평문이 없어야 한다
cs_body()  { printf '{"companyCode":"ZZE%s","companyName":"기관","companyType":"11","ceoName":"%s","useYn":"Y","biostarGroupId":99999}' "$1" "$(jesc "$2")"; }
cs_upd()   { printf '{"companyCode":"ZZE%s","companyName":"기관","companyType":"44","ceoName":"%s","useYn":"Y","biostarGroupId":99999}' "$1" "$(jesc "$2")"; }
cs_get()   { jget "$(A "$BASE_URL/company/company/list?searchType=companyCode&keyword=ZZE$1&size=5")" ceoName; }
cs_key()   { printf "FROM tb_company WHERE company_code='ZZE%s'" "$1"; }
cs_purge() { A -X DELETE -o /dev/null "$BASE_URL/company/company?companyCode=ZZE$1"; \
             db "DELETE FROM tb_company WHERE company_code='ZZE$1';" >/dev/null; return 0; }
cs_suite "기관 ceo_name (ARIA 암호화)" tb_company ceo_name ENC

# ── 사용자관리: user_name (ARIA 암호화) ─────────────────────
CS_URL="$BASE_URL/system/loginUser"
cs_body()  { printf '{"userId":"zzu%s","userName":"%s","password":"pw12345","deptName":"운영팀","authId":1,"workLocationCode":"T1","useYn":"Y","rootYn":"N"}' "$1" "$(jesc "$2")"; }
cs_upd()   { printf '{"userId":"zzu%s","userName":"%s","password":"","deptName":"보안팀","authId":1,"useYn":"Y","rootYn":"N"}' "$1" "$(jesc "$2")"; }
cs_get()   { jget "$(A "$BASE_URL/system/loginUser/list?searchType=userId&keyword=zzu$1&size=5")" userName; }
cs_key()   { printf "FROM tb_login_user WHERE user_id='zzu%s'" "$1"; }
cs_purge() { A -X DELETE -o /dev/null "$BASE_URL/system/loginUser?userId=zzu$1"; return 0; }
cs_suite "사용자 user_name (ARIA 암호화)" tb_login_user user_name ENC

# ── 카드등록관리: card_name (평문 nvarchar 100) ──────────────
# 카드 '등록'은 BiostarX 에 카드를 만드는데(CardService:193) 삭제는 소프트 삭제뿐이라
# 장비에 고아 카드가 쌓인다. 그래서 행을 DB 에 직접 심고 앱의 수정·조회 경로만 검증한다.
CS_URL="$BASE_URL/card/card"
db "DELETE FROM tb_card WHERE biostar_card_value LIKE 'ZZCD%';" >/dev/null
db "INSERT INTO tb_card (biostar_card_value, card_type, pass_type, card_name, card_status, del_yn)
    VALUES ('ZZCD0', 'CDT01', 'PT01', N'초기값', 'CS01', 'N');" >/dev/null
ZZ_CARD_ID=$(db "SELECT card_id FROM tb_card WHERE biostar_card_value='ZZCD0';")
if [ -z "$ZZ_CARD_ID" ]; then
  skip "카드 card_name — 시험 행 생성 실패"
else
  echo "── 카드 card_name (평문, 수정 경로) ──"
  for i in "${!CS_LABEL[@]}"; do
    L="${CS_LABEL[$i]}"; V="${CS_VALUE[$i]}"
    C=$(AJ PUT "$CS_URL" "$(printf '{"cardId":%s,"biostarCardValue":"ZZCD0","cardType":"CDT01","passType":"PT01","cardName":"%s","cardStatus":"CS01"}' "$ZZ_CARD_ID" "$(jesc "$V")")")
    if [ "$C" != "200" ]; then bad "수정[$L] HTTP $C"; continue; fi
    GOT=$(jget "$(A "$BASE_URL/card/card/list?searchType=cardNo&keyword=ZZCD0&size=5")" cardName)
    rt "수정·왕복[$L]" "$(trimv "$V")" "$GOT" "$(db_hex "card_name" "FROM tb_card WHERE biostar_card_value='ZZCD0'")"
  done
  db "DELETE FROM tb_card WHERE biostar_card_value='ZZCD0';" >/dev/null
  eq "시험 카드 행 정리됨" 0 "$(db "SELECT COUNT(*) FROM tb_card WHERE biostar_card_value='ZZCD0';")"
fi
unset -f cs_upd cs_body cs_get cs_key cs_purge
fi

# ══════════════════════════════════════════════════════════════
if want 삭제; then
echo ""
echo "═══ §4 삭제 — DB·BiostarX 대조 ═══"

# ── 소프트/하드 삭제 구분 확인 ───────────────────────────────
# 화면마다 삭제의 의미가 다르다. "목록에서 사라졌다"만 보면 실제로 남았는지 지워졌는지 모른다.
echo "── 삭제 방식(소프트 vs 하드) ──"
A -X DELETE -o /dev/null "$BASE_URL/carInfo/car?carId=0"
AJ POST "$BASE_URL/carInfo/car" '{"carNo":"ZZDEL1","carName":"삭제시험","carType":"01"}' >/dev/null
DCAR=$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=ZZDEL1&size=5" | grep -oE '"carId":[0-9]+' | head -1 | grep -oE '[0-9]+')
eq "차량 삭제 200" 200 "$(AC -X DELETE "$BASE_URL/carInfo/car?carId=${DCAR:-0}")"
eq "차량은 소프트 삭제 — 행은 남고 del_yn='Y'" "Y" "$(db "SELECT del_yn FROM tb_car WHERE car_id=${DCAR:-0};")"
eq "차량 삭제 후 목록 미노출" 0 "$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=ZZDEL1&size=5" | grep -c '"carNo":"ZZDEL1"')"
db "DELETE FROM tb_car WHERE car_no='ZZDEL1';" >/dev/null

AJ POST "$BASE_URL/system/common" '{"cmmId":"VR","codeId":"ZZDEL2","codeName":"삭제시험","useYn":"Y"}' >/dev/null
eq "공통코드 삭제 200" 200 "$(AC -X DELETE "$BASE_URL/system/common?cmmId=VR&codeId=ZZDEL2")"
eq "공통코드는 하드 삭제 — 행이 없다" 0 "$(db "SELECT COUNT(*) FROM tb_common WHERE cmm_id='VR' AND code_id='ZZDEL2';")"

AJ POST "$BASE_URL/system/loginUser" '{"userId":"zzdel3","userName":"삭제시험","password":"pw12345","authId":1,"useYn":"Y","rootYn":"N"}' >/dev/null
eq "사용자 삭제 200" 200 "$(AC -X DELETE "$BASE_URL/system/loginUser?userId=zzdel3")"
eq "사용자는 하드 삭제 — 행이 없다" 0 "$(db "SELECT COUNT(*) FROM tb_login_user WHERE user_id='zzdel3';")"

# ── 정규인원 전 구간: 등록 → 수정 → 삭제 (BiostarX 대조) ────
# 이 구간만 실제 장비에 사용자를 만든다. 전용 ID(ZZSMK*)로 격리하고 끝나면 지운다.
echo "── 정규인원 등록·수정·삭제 (BiostarX 대조) ──"
PID="ZZSMK01"
PCO=$(db "SELECT TOP 1 company_code FROM tb_company WHERE biostar_group_id IS NOT NULL AND ISNULL(del_yn,'N')='N' ORDER BY company_code;")
purge_person() {                      # 실행 첫머리·끝 양쪽에서 부른다
  A -X DELETE -o /dev/null "$BASE_URL/person/person?personId=$PID"
  db "DELETE FROM tb_person_ac_group WHERE person_id='$PID';" >/dev/null
  db "DELETE FROM tb_person WHERE person_id='$PID';" >/dev/null
}

if [ -z "$BS_SID" ] || [ "${SKIP_BIOSTAR:-}" = "1" ] || [ -z "$PCO" ]; then
  skip "정규인원 전 구간 — BiostarX 세션/기관 없음(SKIP_BIOSTAR=$SKIP_BIOSTAR, 기관=$PCO)"
else
  purge_person
  eq "시작 전 장비에 잔여 없음" 400 "$(bs_user_code "$PID")"

  P_NAME='김한글'
  C=$(AJ POST "$BASE_URL/person/person" "$(printf '{"personId":"%s","personName":"%s","companyCode":"%s","statusCode":"01","accessStartDt":"2026-01-01T09:00","accessEndDt":"2028-05-31T23:59","useYn":"Y"}' "$PID" "$(jesc "$P_NAME")" "$PCO")")
  eq "인원 등록 200" 200 "$C"
  if [ "$C" = "200" ]; then
    eq "DB 에 행 생성"            1 "$(db "SELECT COUNT(*) FROM tb_person WHERE person_id='$PID' AND ISNULL(del_yn,'N')='N';")"
    eq "성명이 DB 에 평문이 아님" "ok" "$(case "$(db "SELECT person_name FROM tb_person WHERE person_id='$PID';")" in "$P_NAME") echo 평문노출;; *) echo ok;; esac)"
    eq "앱 조회는 평문 성명"      "$P_NAME" "$(jget "$(A "$BASE_URL/person/person/list?searchType=personId&keyword=$PID&size=5")" personName)"
    eq "BiostarX 에 사용자 생성됨" 200 "$(bs_user_code "$PID")"

    # 수정 — 한글 성명을 다른 값으로
    P_NAME2='이特殊·名'
    eq "인원 수정 200" 200 "$(AJ PUT "$BASE_URL/person/person" "$(printf '{"personId":"%s","personName":"%s","companyCode":"%s","statusCode":"01","accessStartDt":"2026-01-01T09:00","accessEndDt":"2028-05-31T23:59","useYn":"Y"}' "$PID" "$(jesc "$P_NAME2")" "$PCO")")"
    eq "수정된 성명이 앱에 반영"  "$P_NAME2" "$(jget "$(A "$BASE_URL/person/person/list?searchType=personId&keyword=$PID&size=5")" personName)"
    eq "수정 후에도 장비에 존재"  200 "$(bs_user_code "$PID")"

    # 삭제 — DB 와 장비 양쪽 확인
    eq "인원 삭제 200" 200 "$(AC -X DELETE "$BASE_URL/person/person?personId=$PID")"
    eq "DB 는 소프트 삭제(del_yn='Y')" "Y" "$(db "SELECT del_yn FROM tb_person WHERE person_id='$PID';")"
    eq "삭제 후 목록 미노출"      0 "$(A "$BASE_URL/person/person/list?searchType=personId&keyword=$PID&size=5" | grep -c "\"personId\":\"$PID\"")"
    eq "BiostarX 에서 사라짐"     400 "$(bs_user_code "$PID")"
    eq "출입권한 행도 정리됨"     0 "$(db "SELECT COUNT(*) FROM tb_person_ac_group WHERE person_id='$PID';")"

    # 같은 ID 재등록(되살리기) — 삭제 때 장비도 지웠으므로 충돌하지 않아야 한다
    eq "같은 ID 재등록 200" 200 "$(AJ POST "$BASE_URL/person/person" "$(printf '{"personId":"%s","personName":"%s","companyCode":"%s","statusCode":"01","accessStartDt":"2026-01-01T09:00","accessEndDt":"2028-05-31T23:59","useYn":"Y"}' "$PID" "$(jesc "$P_NAME")" "$PCO")")"
    eq "재등록 후 장비에도 생성"  200 "$(bs_user_code "$PID")"
  fi
  purge_person
  eq "정리 후 DB 잔여 없음"   0 "$(db "SELECT COUNT(*) FROM tb_person WHERE person_id='$PID';")"
  eq "정리 후 장비 잔여 없음" 400 "$(bs_user_code "$PID")"
fi
fi

# ══════════════════════════════════════════════════════════════
if want 권한; then
echo ""
echo "═══ §5 권한 — 역할별 허용/차단 ═══"

# 시드 권한(auth_id<=2)은 절대 건드리지 않는다. 시험용 권한·계정을 새로 만들어 쓰고 지운다.
ZZ_AUTHS=""
mkauth() { # mkauth <이름> <read> <create> <update> <delete> → authId
  local n=$1 r=$2 c=$3 u=$4 d=$5 id
  AJB POST "$BASE_URL/system/menuAuth" "$(printf '{"authName":"%s","details":[
      {"menuId":301,"readAuth":"%s","createAuth":"%s","updateAuth":"%s","deleteAuth":"%s"},
      {"menuId":601,"readAuth":"%s","createAuth":"%s","updateAuth":"%s","deleteAuth":"%s"}]}' \
      "$n" "$r" "$c" "$u" "$d" "$r" "$c" "$u" "$d")" >/dev/null
  id=$(A "$BASE_URL/system/menuAuth/list?keyword=$(urlenc "$n")&size=50" | grep -o '"authId":[0-9]*' | grep -o '[0-9]*' | sort -n | tail -1)
  ZZ_AUTHS="$ZZ_AUTHS $id"; printf '%s' "$id"
}
mkuser() { # mkuser <userId> <authId>
  A -X DELETE -o /dev/null "$BASE_URL/system/loginUser?userId=$1"
  AJ POST "$BASE_URL/system/loginUser" \
    "{\"userId\":\"$1\",\"userName\":\"권한시험\",\"password\":\"zzpw12345\",\"authId\":$2,\"useYn\":\"Y\",\"rootYn\":\"N\"}" >/dev/null
}
# 역할로 로그인해 쿠키를 잡는다
as() { local ck="$TMP/ck_$1"; curl -sk -m 10 -c "$ck" -o /dev/null -w '%{http_code}' \
         --data "userId=$1&password=zzpw12345" "$BASE_URL/login" >/dev/null; printf '%s' "$ck"; }
R()  { curl -sk -b "$1" -H "X-Requested-With: XMLHttpRequest" "${@:2}"; }
RC() { R "$1" -o /dev/null -w '%{http_code}' "${@:2}"; }
RJ() { R "$1" -H 'Content-Type: application/json' -X "$2" --data @"$(J "$4")" -o /dev/null -w '%{http_code}' "$3"; }

# 이전 실행 잔여 정리
for lid in $(A "$BASE_URL/system/menuAuth/list?keyword=$(urlenc 'ZZ권한')&size=50" | grep -o '"authId":[0-9]*' | grep -o '[0-9]*'); do
  [ "$lid" -gt 2 ] && A -X DELETE -o /dev/null "$BASE_URL/system/menuAuth?authId=$lid"
done

A_READ=$(mkauth 'ZZ권한조회만' Y N N N)
A_CREA=$(mkauth 'ZZ권한등록만' Y Y N N)
A_UPD=$(mkauth  'ZZ권한수정만' Y N Y N)
A_DEL=$(mkauth  'ZZ권한삭제만' Y N N Y)
A_NONE=$(mkauth 'ZZ권한없음'   N N N N)
mkuser zzrole1 "$A_READ"; mkuser zzrole2 "$A_CREA"; mkuser zzrole3 "$A_UPD"
mkuser zzrole4 "$A_DEL";  mkuser zzrole5 "$A_NONE"

# 수정 대상으로 쓸 차량 하나(관리자로 만들어 둔다)
A -X DELETE -o /dev/null "$BASE_URL/carInfo/car?carId=0"
db "DELETE FROM tb_car WHERE car_no='ZZROLE';" >/dev/null
AJ POST "$BASE_URL/carInfo/car" '{"carNo":"ZZROLE","carName":"권한시험","carType":"01"}' >/dev/null
RCAR=$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=ZZROLE&size=5" | grep -oE '"carId":[0-9]+' | head -1 | grep -oE '[0-9]+')

# role <계정> <설명> <조회기대> <등록기대> <수정기대> <삭제기대>
role() {
  local u=$1 name=$2 er=$3 ec=$4 eu=$5 ed=$6 ck
  ck=$(as "$u")
  echo "── $name ──"
  eq "  조회" "$er" "$(RC "$ck" "$BASE_URL/carInfo/car/list?size=1")"
  eq "  등록" "$ec" "$(RJ "$ck" POST "$BASE_URL/carInfo/car" '{"carNo":"ZZROLE9","carName":"x","carType":"01"}')"
  eq "  수정" "$eu" "$(RJ "$ck" PUT "$BASE_URL/carInfo/car" "{\"carId\":${RCAR:-0},\"carNo\":\"ZZROLE\",\"carName\":\"바뀜\",\"carType\":\"02\"}")"
  eq "  삭제" "$ed" "$(RC "$ck" -X DELETE "$BASE_URL/carInfo/car?carId=999999")"
  # 등록이 성공했으면 치운다
  local nid; nid=$(A "$BASE_URL/carInfo/car/list?searchType=carNo&keyword=ZZROLE9&size=5" | grep -oE '"carId":[0-9]+' | head -1 | grep -oE '[0-9]+')
  [ -n "$nid" ] && A -X DELETE -o /dev/null "$BASE_URL/carInfo/car?carId=$nid"
  db "DELETE FROM tb_car WHERE car_no='ZZROLE9';" >/dev/null
}

# 삭제는 '없는 차량'을 지운다 — 권한이 있으면 404(권한 통과 후 존재 확인), 없으면 403
role zzrole1 "조회만 (read Y)"                 200 403 403 403
role zzrole2 "등록만 (create Y)"               200 200 200 403
# update_auth 는 어디서도 읽히지 않는다(정책: 등록/수정은 create_auth 로 판정).
# '수정 Y, 등록 N' 권한을 준 사람은 수정이 될 거라 믿지만 실제로는 막힌다 — 그 사실을 여기서 고정한다.
role zzrole3 "수정만 (update Y, create N)"     200 403 403 403
role zzrole4 "삭제만 (delete Y)"               200 403 403 404
role zzrole5 "무권한 (read N)"                 403 403 403 403

# ── 화면 접근·사이드바 ──────────────────────────────────────
echo "── 화면 접근 / 사이드바 ──"
CK5=$(as zzrole5); CK1=$(as zzrole1)
eq "무권한 URL 직접 접근 403"   403 "$(curl -sk -b "$CK5" -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
eq "무권한 페이지가 렌더됨"      0 "$(case "$(curl -sk -b "$CK5" "$BASE_URL/carInfo/car")" in *'id="forbiddenPage"'*) echo 0;; *) echo 1;; esac)"
eq "조회권한자는 화면 200"      200 "$(curl -sk -b "$CK1" -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car")"
SB1=$(curl -sk -b "$CK1" "$BASE_URL/system/common")
eq "사이드바에 권한 있는 메뉴 노출"  0 "$(case "$SB1" in *'"/carInfo/car"'*) echo 0;; *) echo 1;; esac)"
eq "사이드바에 권한 없는 메뉴 미노출" 0 "$(case "$SB1" in *'"/company/company"'*) echo 1;; *) echo 0;; esac)"
eq "권한 밖 메뉴 API 도 403"     403 "$(RC "$CK1" "$BASE_URL/company/company/list?size=1")"
eq "미인증 AJAX 401"            401 "$(curl -sk -H 'X-Requested-With: XMLHttpRequest' -o /dev/null -w '%{http_code}' "$BASE_URL/carInfo/car/list")"

# ── 정리 ────────────────────────────────────────────────────
for u in zzrole1 zzrole2 zzrole3 zzrole4 zzrole5; do A -X DELETE -o /dev/null "$BASE_URL/system/loginUser?userId=$u"; done
# $(mkauth ...) 는 서브셸이라 그 안에서 담은 변수가 여기 남지 않는다 — 이름으로 다시 찾아 지운다
for id in $(A "$BASE_URL/system/menuAuth/list?keyword=$(urlenc 'ZZ권한')&size=50" | grep -o '"authId":[0-9]*' | grep -o '[0-9]*'); do
  [ "$id" -gt 2 ] 2>/dev/null && A -X DELETE -o /dev/null "$BASE_URL/system/menuAuth?authId=$id"
done
[ -n "$RCAR" ] && A -X DELETE -o /dev/null "$BASE_URL/carInfo/car?carId=$RCAR"
db "DELETE FROM tb_car WHERE car_no LIKE 'ZZROLE%';" >/dev/null
eq "시험 계정 정리됨" 0 "$(db "SELECT COUNT(*) FROM tb_login_user WHERE user_id LIKE 'zzrole%';")"
eq "시험 권한 정리됨" 0 "$(db "SELECT COUNT(*) FROM tb_menu_auth WHERE auth_name LIKE N'ZZ권한%';")"
fi

# 끝나고도 반드시 치운다 — 공유 개발 DB 라 다음 실행·사람 작업에 섞이면 안 된다
echo ""
echo "═══ 정리 ═══"
purge_all
LEFT=$(db "SELECT (SELECT COUNT(*) FROM tb_person WHERE person_id LIKE 'ZZ%')
            + (SELECT COUNT(*) FROM tb_car WHERE car_no LIKE 'ZZ%')
            + (SELECT COUNT(*) FROM tb_company WHERE company_code LIKE 'ZZ%')
            + (SELECT COUNT(*) FROM tb_card WHERE biostar_card_value LIKE 'ZZ%')
            + (SELECT COUNT(*) FROM tb_common WHERE code_id LIKE 'ZZ%')
            + (SELECT COUNT(*) FROM tb_menu_auth WHERE auth_name LIKE N'ZZ%');")
eq "시험 데이터 전수 정리됨" 0 "$LEFT"
if [ -n "$BS_SID" ]; then eq "장비에 시험 사용자 잔여 없음" 400 "$(bs_user_code ZZSMK01)"; fi

echo ""
echo "═════ 결과: ✅ $PASS / ❌ $FAIL / ⚪ $SKIP ═════"
[ "$FAIL" -eq 0 ]
