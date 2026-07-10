# 코드 관례 — 팀 공유 규칙 대장 (Rulebook)

> **목적**: 팀원 누가 어떤 프로젝트/페이지를 만들어도 **같은 구조·이름·흐름**이 되도록 한다.
> 이 하네스(AGENTS.md + docs/ + .claude/)를 다른 프로젝트에 복제해도 이 문서가 규칙의 원천이다.
> "불변식은 강제, 구현은 자유" — 강제 규칙은 `AGENTS.md §4`, 여기는 **모두가 따르는 관례**.
> 기준 구현(골든 샘플): 공통코드관리 — `CommonController`/`CommonService`/`TbCommonMapper`(+XML),
> `templates/web/system/commonCode.html`, `static/js/web/system/commonCode.js`.

## 0. 신규 패턴 등록 — 이 문서는 살아있는 대장이다 (자동 축적)
새 명명·패턴·경우의수가 생기면 **그 작업 안에서 이 문서에 등록하고 같이 커밋한다**. 등록을 빼먹지 않도록 3중 장치:
1. **작업 중**: 여기 없는 패턴을 새로 만들면 해당 섹션에 "규칙 한 줄 + 예시"를 즉시 추가한다(에이전트/개발자 공통 의무).
2. **커밋 시**: `/commit` 이 "새 패턴 → conventions.md 등록 여부"를 확인한다.
3. **리뷰 시**: `/review` 가 이 문서와의 일치를 점검한다. `/new-screen` 은 이 문서를 먼저 읽고 그대로 따른다.

> 애매하면 **골든 샘플의 지배적 패턴을 따른다** — 새 패턴 발명을 최소화한다.

---

## 1. 패키지/구조
- **역할별 평면 구조**: `controller / service / mapper / model / adapter / config / security / common / util`. 도메인별 하위 폴더 금지. (구조 원천: `architecture.md`)
- 도메인 구분은 **클래스명 접두사**: `Common*`, `Visitor*`, `AcGroup*`, `Menu*`, `System*` …
- 패키지명 소문자. 클래스만 PascalCase.
- 공통(범용) 코드 위치: 응답/페이징/예외=`common`, 인증·암호화=`security`, 엑셀 등 유틸=`util`.

## 2. HTML — id/class 명명 규칙
| 대상 | 규칙 | 예시(골든 샘플) |
|---|---|---|
| **id** | camelCase. JS 가 제어하는 요소에만 부여, 화면당 유일 | `keyword`, `gridBody`, `editModal` |
| 버튼 id | `btn` + 동작 | `btnSearch` `btnReset` `btnNew` `btnExcel` `btnSave` `btnCancel` |
| 필터 id | 필드명 + `Filter` | `useYnFilter` |
| 입력 id | **모델 필드명 그대로**(camelCase) — JS 수집이 1:1 | `cmmId` `cmmName` `codeId` `codeName` `useYn` |
| 목록 영역 id | 역할명 | `gridBody`(tbody) `paging` `totalInfo` `pageSize` `searchType` |
| 모달 id | `{용도}Modal` + 내부는 `modal*`/`{모달}*` 접두 | `editModal`/`modalTitle`/`modalClose`, 공통: `confirmModal*` `promptModal*` |
| **class** | kebab-case. 스타일/반복 요소용 | `search-section` `grid-toolbar` `row-click` |
| 레이아웃 class | `layout-*`, `lnb-*` | `layout-header` `layout-container` `layout-content` `lnb-sidebar` |
| 검색영역 class | `search-*` | `search-section` `search-field` `search-keyword` |
| 그리드 class | `grid`, `grid-*`, 상태는 단어 | `grid-toolbar` `grid-total` `grid-actions` `sortable` `sorted` `sort-ind` `empty` |
| 버튼/입력 class | `btn` + 변형 `btn-*`, `input`, 폭 유틸 `w-*` | `btn-primary` `btn-sm` `btn-danger` `btn-block` `w-160` |
| 모달 class | `modal-*` | `modal-overlay(.open)` `modal-container(.small/.confirm)` `modal-header/title/close/body/footer` |
| **data-\*** | 서버값/동작을 JS 로 전달 | `data-sort`(정렬키) `data-act`(동작) `data-json`(행 데이터) `data-page`, PK 는 `data-{필드}` |
- 서버 → JS 전역 전달은 `window.PAGE_*` (예: `PAGE_PERM`) 인라인 스크립트로.
- **의존 선택 패턴**(선택값에 따라 다른 필드 자동): 부모는 `<select>`(서버가 허용 목록 제공), 자식은 `readonly` 입력 + `change` 시 자동 채움. 서버가 파생값을 재검증·재설정한다(클라이언트 값 불신). 예: 공통코드 등록의 코드구분ID→코드구분명.
- 공통 모달 등 재사용 조각은 `fragments/components/`, 화면 전용은 `web|kiosk/components/`. (`frontend.md`)

## 3. JavaScript — 명명 규칙 · 호출 흐름
**파일**: 화면당 1개, 템플릿과 **미러 경로**(`templates/web/system/commonCode.html` ↔ `static/js/web/system/commonCode.js`). 전체를 IIFE `(function(){ ... })()` 로 감싼다(전역 오염 방지).

**명명**
| 대상 | 규칙 | 예시 |
|---|---|---|
| 상수 | UPPER_SNAKE | `BASE`(엔드포인트 프리픽스), `PERM` |
| 화면 상태 | 단일 객체 `state` | `{ page, size, keyword, searchType, useYn, sort, dir }` |
| 함수 | 동사 camelCase | `load` `search` `reset` `save` `remove` `bind` |
| 그리기 함수 | `render` 접두 | `renderRows` `renderPaging` `renderTotal` `renderSortIndicators` |
| 모달 함수 | `openModal` / `closeModal` | 모드 인자: `openModal('create'|'edit', row)` |
| 헬퍼 | `$`(getElementById), `esc`(XSS 이스케이프) | 서버 데이터를 innerHTML 에 넣을 땐 **반드시 `esc()`** |

**호출 흐름 (표준)**
```
DOMContentLoaded → bind()  → load()
                              └ api.get(BASE+'/list'+query)
                                └ renderRows / renderPaging / renderTotal / renderSortIndicators
사용자 액션(검색/정렬/페이지/크기) → state 변경 → load() 재호출
등록/수정: openModal → save() → api.post|put → closeModal() → load()
삭제:      confirmModal.open(...) → api.del → load()
다운로드:  promptModal.open(목적) → location.href = BASE+'/excel?...&purpose='
```
**공통 규약**
- 서버 통신은 **`api.get/post/put/del`**(core/app.js)만 사용 — `ApiResponse` 해석·401 리다이렉트·오류 alert 내장. fetch 직접 호출 금지.
- `alert/confirm/prompt` 브라우저 기본창 금지 — **공통 컴포넌트** 사용: 확인=`confirmModal`, 입력=`promptModal`, 안내=`toast`(success/error/warning).
- **안내 메시지는 서버 return 우선**: 성공은 컨트롤러가 `ApiResponse.okMessage("...")` 로 내려주면 `api` 래퍼가 자동 `toast.success`. 서버 오류는 `api` 래퍼가 자동 `toast.error`. 클라 검증 실패만 화면에서 `toast.warning`.
- 이벤트 바인딩은 `bind()` 한 곳에. 권한 없는 버튼은 서버 렌더에서 빠지므로 `if ($('btnNew'))` 가드.
- 목록 쿼리 파라미터 이름은 백엔드 `PageParam` 과 동일: `page/size/keyword/searchType/sort/dir`(+도메인 필터).

## 4. Controller — 명명 · endpoint 규칙
- 클래스: `{도메인}Controller`, 라우팅은 **클래스 상단** `@RequestMapping("/{영역}/{기능}")` (예: `/system/commonCode`). 영역=메뉴 상위(system 등).
- 상수: `MENU_ID` — 화면의 tb_menu id(권한·감사에 사용).
- 메소드(표준 세트):

| 메소드 | HTTP·경로 | 역할 |
|---|---|---|
| `page` | `GET ""` | 화면 반환(뷰). 권한 read 확인 + `perm`/`menus` 모델 |
| `list` | `GET /list` `@ResponseBody` | 목록 AJAX. `{도메인}SearchParam` 바인딩 |
| `excel` | `GET /excel` | 엑셀 다운로드(전체). `purpose` 필수 |
| `create` | `POST ""` `@ResponseBody` | 등록. `@RequestBody` 모델 |
| `update` | `PUT ""` `@ResponseBody` | 수정 |
| `delete` | `DELETE ""` `@ResponseBody` | 삭제. PK 는 `@RequestParam` |
- 변수: 검색=`param`, 단건=`row`, 세션 사용자 추출은 `actor(session)` private 헬퍼.
- Controller 는 얇게: **read 권한 확인·모델 구성·서비스 위임만**. 쓰기 권한 검증/비즈니스/감사는 Service. SQL 금지(불변식).
- 응답: 데이터는 `ApiResponse.ok(...)`/`fail`, 예외는 `GlobalExceptionHandler` 가 표준 바디+상태(403/401/404/400/500)로 변환.

## 5. Service — 명명 · 암복호화 · 감사 규칙
- 클래스: `{도메인}Service`(@Service 구체 클래스, 인터페이스 없음). 생성자 주입.
- 메소드(표준 세트): `list`(READ 감사 포함) / `listAllForExcel`(DOWNLOAD 감사+remark) / `get` / `create` / `update` / `delete`. 쓰기 3종은 `@Transactional`.
- **쓰기 흐름(순서 고정)**: ① `menuAuthService.requireCreate|requireDelete(actor, menuId)` → ② 업무 검증(중복 등) → ③ mapper 호출 → ④ `auditService.log(actor, TYPE, menuId, detail[, remark])`.
- 권한: 판정/강제는 `MenuAuthService`(`permissionFor`/`requireRead·Create·Delete`)만 사용. 등록/수정=create_auth 하나로 판정.
- 감사: action_type 상수는 `AuditService`(MENU/READ/CREATE/UPDATE/DELETE/DOWNLOAD/LOGIN/LOGOUT). 목록 조회는 검색조건+건수를 detail 에 남긴다. (`security.md`)
- **암복호화(ARIA) 규칙**:
  - 호출은 **Service 계층에서만**. Controller/Mapper 에서 금지.
  - 저장 직전 `ARIAUtil.ariaEncrypt()`, 표시용 조회 직후 `ariaDecrypt()`. 대상 컬럼=`database.md` Enc=Y.
  - 비밀번호는 복호화하지 않는다 — **암호문끼리 비교**(LoginService 참조). 세션에 비밀번호 저장 금지(`setPassword(null)`).
  - 키는 프로퍼티(`app.crypto.aria-key`) 주입 — 하드코딩 금지(불변식).
- 변수: 행위자=`actor`, 메뉴=`menuId`, 검색=`param`, 단건=`row`, 건수=`total`.

## 6. Mapper — 인터페이스 · XML · SQL 규칙
**인터페이스**: `Tb{Table}Mapper` (테이블 1:1). 다건 파라미터는 `@Param` 명시.

**메소드 명명(표준 세트)**
| 메소드 | 용도 |
|---|---|
| `selectList(param)` | 목록(페이징) |
| `selectListAll(param)` | 엑셀용 전체(페이징 없음, 동일 where/orderBy) |
| `selectCount(param)` | 총 건수 |
| `selectOne(pk...)` / `selectById(id)` | 단건 |
| `insert(row)` / `update(row)` / `delete(pk...)` | 쓰기 |
| 도메인 특화 | `select{대상}By{조건}` (예: `selectUseList`) |

**XML 규칙**
- 파일명 = 인터페이스명 `.xml`(`mapper/Tb{Table}Mapper.xml`), `namespace` = 인터페이스 FQCN, `id` = 메소드명과 일치.
- `resultType` 은 typeAlias(모델 클래스명, 예: `TbCommon`) — `mybatis-config` 의 `AirPort.model` 패키지 alias.
- 파라미터는 `#{}` 만(불변식). 컬럼 명시(`SELECT *` 금지). LIKE 는 `'%' + #{keyword} + '%'`.
- **검색 조건**은 `<sql id="searchWhere">` 조각으로 분리 → 목록/카운트/전체가 `<include>` 공유. `searchType` 분기는 `<choose>`.
- **정렬**은 `<sql id="orderBy">` + **화이트리스트 `<choose>`** 로만 컬럼 매핑(SQL 인젝션 방지). 각 분기에 tie-breaker(PK) 포함, `<otherwise>` 는 기본 정렬.
- **페이징**은 MSSQL `OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY` (offset 계산은 `PageParam.getOffset()`).
- null 기본값은 `ISNULL(#{x}, '기본')`. (jdbcTypeForNull=VARCHAR 설정 전제 — `mybatis-config`)

**검색 파라미터 모델**: 공통 `PageParam`(page/size/keyword/searchType/sort/dir) 을 상속한 `{도메인}SearchParam` 에 도메인 필터(useYn 등)를 추가한다.

## 7. 표준 CRUD 페이지 전개 흐름 (수직 슬라이스)
새 화면은 아래 순서로 골든 샘플을 복제한다 (`/new-screen`):
```
① database.md 에서 테이블 확인 → 모델 Tb{Table} (+{도메인}SearchParam)
② Tb{Table}Mapper (인터페이스+XML: searchWhere/orderBy/표준 세트)
③ {도메인}Service (권한 require* + 감사 log + 트랜잭션)
④ {도메인}Controller (page/list/excel/create/update/delete + MENU_ID)
⑤ templates/web/{영역}/{화면}.html (fragments 조합 + 공통 모달 include + PAGE_PERM 주입)
⑥ static/js/web/{영역}/{화면}.js (state/bind/load/render* 흐름)
⑦ seed: tb_menu 메뉴 + tb_menu_auth_detail 권한
⑧ 검증: 로그인→목록→CRUD→권한(viewer)→감사 이력 확인
```

## 8. 포맷팅 · 커밋 · 주석
- Java: **google-java-format** (spotless `googleJavaFormat()`, `/commit` 시 자동).
- 프론트(HTML/JS/CSS): prettier (PostToolUse 훅).
- 커밋: Conventional Commits(한국어). 타입 `feat|fix|refactor|docs|style|test|chore|perf`. scope 예: `common|visitor|acgroup|menu|biostar|auth|audit|ui|db|infra|deploy`.
- 주석: "무엇"이 아니라 "왜". 정책 결정(예: 등록/수정=create_auth)은 결정 지점에 주석으로 남긴다.

## 9. 강제 (Enforcement)
불변식은 4계층에서 강제한다. 새 규칙에는 가능한 한 자동 검사를 붙인다.
1. **로컬 훅** `.claude/hooks/` — 비밀값·생성물 차단(+교정 지침 출력).
2. **구조 테스트(ArchUnit)** `src/test/.../ArchitectureTest` — 계층 단방향·model 순수성·클래스 네이밍. `gradlew test` 로 실행.
3. **린트 스크립트** `scripts/` — `code-lint.sh`(파일 크기: Java≤500/JS≤400, 예외는 ALLOWLIST 에 사유와 함께), `docs-lint.sh`(md 링크·AGENTS 200줄), `schema-drift.sh`(DDL↔database.md 양방향 일치). 로컬·CI 동일 스크립트.
4. **CI** `.github/workflows/ci.yml` — 위 전부 + JPA·인라인SQL·MariaDB 가드 + gitleaks + 빌드/테스트. **주간 스케줄**로 문서·드리프트를 정기 재검(도큐 가드닝의 기계 파트, 나머지는 `/cleanup`).

> 검사 실패 메시지는 "무엇이 왜 막혔고 **어떻게 고쳐라**"까지 담는다.
> 검증 루프: 기능 작업 후 `scripts/smoke-test.sh`(E2E 13체크) → `/review` → `/commit`.

## 관련 문서
[architecture.md](architecture.md) · [backend.md](backend.md) · [frontend.md](frontend.md) · [database.md](database.md) · [security.md](security.md) · [design.md](design.md)
