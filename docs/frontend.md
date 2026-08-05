# 프론트엔드 (Thymeleaf 서버사이드 렌더링)

> 화면의 **구조·파일 위치·동작 관례**의 단일 출처. 스캐폴딩은 `/new-screen`.
> 화면의 **시각 규칙(색·타이포·컴포넌트 룩·토큰)** 은 `design.md` 가 담당한다(여기서 반복하지 않는다).
> **별도 SPA/번들러(webpack/vite) 없음.** 백엔드와 한 앱이다.

## 화면 구성 방식
- **Thymeleaf** 서버사이드 렌더링 + `thymeleaf-layout-dialect`.
- **탭/iframe 구조를 쓰지 않는다.** 각 화면은 **독립 페이지**이며, 공통 조각(head, main 레이아웃, sidebar)을 `th:replace`/`th:insert` 로 끼워 구성한다.
- 화면 = 페이지 템플릿(HTML) + 정적 자산(JS/CSS). Controller 가 모델을 담아 페이지를 반환.
- 정적 자산은 모두 **로컬 포함**(외부 CDN 미사용 — 운영 DMZ 대비).
- **web(관리자 웹)** 과 **kiosk(현장 키오스크)** 를 최상위에서 분리한다.

## 공통 조각 · 모달/팝업 위치 규칙
- **전역 레이아웃 조각**(head, main 레이아웃, sidebar, footer): `templates/fragments/`.
- **공통 모달/팝업 등 재사용 조각**: 한 곳에 모아 재사용한다.
  - web·kiosk **양쪽 공용**이면 → `templates/fragments/components/`
  - **web 전용**이면 → `templates/web/components/`, **kiosk 전용**이면 → `templates/kiosk/components/`
- 화면에서 모달을 새로 복제하지 말고 위 조각을 `th:replace` 로 불러 쓴다.
- **모달 조각 파일명은 `{이름}-modal.html`**, 짝이 되는 스크립트는 **`static/js/core/components/{이름}-modal.js`**(공용) 로 둔다. 예: `confirm-modal` · `prompt-modal` · `code-picker-modal`. 컴포넌트 스크립트는 `js/core/` 루트가 아니라 **`js/core/components/`** 아래에 모은다.

## 디렉터리 구조 (리소스 내부)
```
src/main/resources/
├── templates/
│   ├── fragments/            전역 레이아웃 조각: head, main(레이아웃), sidebar
│   │   └── components/       web·kiosk 공용 모달/팝업 — 파일명 {이름}-modal.html (confirm-modal, prompt-modal, code-picker-modal)
│   ├── login.html
│   ├── web/                  관리자 웹
│   │   ├── {도메인}/          system, visitor, ... 도메인별 화면
│   │   └── components/       web 전용 모달/팝업 등 공통 조각
│   └── kiosk/                현장 키오스크
│       ├── {도메인}/          kiosk 화면
│       └── components/       kiosk 전용 모달/팝업 등 공통 조각
└── static/
    ├── css/                  스타일 (토큰/컴포넌트 → design.md)
    ├── font/                 Pretendard woff2 (로컬)
    ├── ic/                   아이콘 PNG
    ├── images/
    └── js/
        ├── common.js, common/    공용 라이브러리
        ├── core/                 공통 뼈대: app.js(fetch), toast, sidebar(계층·접기·플라이아웃), pager(윈도우 페이징), period(기간 프리셋), no-autofill(입력이력 차단), password-toggle(비번 표시)
        │   └── components/       공용 모달/팝업 컴포넌트 스크립트(fragments/components 와 1:1): {이름}-modal.js (confirm-modal, prompt-modal, code-picker-modal)
        ├── web/
        │   ├── {도메인}/          화면별 스크립트
        │   └── components/       조각별 스크립트
        └── kiosk/
            ├── {도메인}/          kiosk 화면별 스크립트
            └── components/       kiosk 조각별 스크립트
```
- **templates 와 js 는 같은 트리(web/kiosk → 도메인/components)로 미러링**한다 — 화면과 스크립트를 1:1로 찾기 위함.

## 화면 작성 관례
- **HTML id/class·JS 함수/변수 명명과 호출 흐름은 `conventions.md` §2·§3 이 원천**(여기서 반복하지 않는다).
- 새 화면: `templates/web/{도메인}/{화면}.html` + `static/js/web/{도메인}/{화면}.js` (kiosk 는 `kiosk/` 하위).
- 페이지는 `fragments/`(head/main/sidebar)를 조합해 구성. 모달/팝업은 `components/` 조각을 재사용(복제 금지).
- 시각 컴포넌트 룩·토큰은 `design.md` 를 따른다.
- 서버 통신은 core JS 규약을 따른다: `static/js/core/app.js` 의 `api.get/post/put/del`. 응답은 표준 `ApiResponse{success,code,message,data}` 로 처리(`backend.md`).
- 권한별 메뉴/버튼 노출은 서버가 내려준 권한(`tb_menu_auth_detail`)에 따른다. (`security.md`)
- 감사 대상 화면(조회/입력/수정/삭제)은 서버에서 이력을 남긴다. (`security.md`)

## 공통 UI 컴포넌트·동작 (전 화면 공통)
- **처리 중 표시(`js/core/busy.js`)**: 저장·삭제 등 서버 왕복 동안 화면을 덮어 진행 중임을 알리고 중복 클릭을 막는다. `api` 래퍼가 자동으로 켜고 끄며(중첩 요청은 카운터), 0.25초 안에 끝나면 표시하지 않는다. 직접 `fetch` 하는 코드는 `busy.wrap(promise)`.
> 아래는 `head` fragment 가 로드하는 core 컴포넌트로 **전 화면에 자동/공통** 적용된다. 화면마다 새로 만들지 않는다. (명명 규칙은 `conventions.md`)
- **페이징**: 공통 `pager`(core/pager.js)만 사용 — `pager.render($('paging'), page, totalPages, (p)=>{ state.page=p; load(); })`. 처음«/이전‹/번호(최대5)/다음›/마지막» + 양끝 비활성·클릭 위임 내장. **페이지 번호를 직접 그리지 않는다**(code-lint 강제).
- **기간(날짜 범위) 필터**: 공통 `period`(core/period.js) — 프리셋 select(`all/1m/3m/6m/1y/custom`) + 직접입력 시에만 date input 노출. `const ctl = period.attach(sel, rangeBox, startEl, endEl)` → `ctl.value()`(=`{start,end}`), `ctl.reset('1m')`. (마크업: `#periodType` + `#dateRange`(hidden) 안에 `#startDate`~`#endDate`)
  - `all` 은 **빈 문자열**을 돌려줘 서버가 기간 조건을 걸지 않는다(사용자가 명시적으로 고를 때만 쓴다).
  - **기본값은 `1m`(1개월)** — 감사추적도, 임시·장기 방문 목록도 같다. 화면 진입 시 `attach()` 직후 한 번 `value()` 를 읽어 state 에 넣어야 **첫 조회부터** 기간이 걸린다(안 하면 첫 화면만 전건이 나온다). `[초기화]` 도 `1m` 으로 되돌린다.
- **입력 자동완성/입력이력 금지(전 페이지)**: `core/no-autofill.js` 가 모든 `input`/`textarea`(동적 추가분 포함)에 `autocomplete=off` 자동 적용. 예외로 자격증명은 템플릿에 `autocomplete` 명시(아이디 `off`, 비밀번호 `new-password`)하면 값 보존.
- **필수 입력 표시**: 필수 항목 라벨 뒤 `<span class="req">*</span>`(붉은 별). **신규 화면 필수값에는 빠짐없이** 붙이고(미지정 시 AI 가 도메인·검증으로 판단), `*` 항목은 **클라+서버 양쪽 검증**과 일치.
- **비밀번호 입력**: `type=password` 면 표시/숨김(눈) 토글 자동 부착(`core/password-toggle.js`). 별도 마크업 불필요.
- **코드(tb_common) 참조 = 코드 팝업**: `<select>` 대신 공통 코드 팝업. 마크업=코드ID(`type=hidden`)+코드명(`.input.picker-field` readonly, `data-target="{hidden id}"`). 조각 `fragments/components/code-picker-modal` 포함 후 `const sel = await codePicker.open({cmmId, cmmName})`. 서버 조회 `GET /system/common/picker?cmmId=&keyword=`(로그인만 필요). `.picker-field` 는 우측 '삭제' 버튼이 자동 부착됨.
- **공용 모달/팝업 컴포넌트**: 조각 `fragments/components/{이름}-modal.html`, 짝 스크립트 `static/js/core/components/{이름}-modal.js`. 예: `confirm-modal`·`prompt-modal`·`code-picker-modal`.
- **확인 모달 키보드 처리**: `confirmModal.open()` 은 열릴 때 **확인 버튼에 포커스**를 주고, 열려 있는 동안 **Enter = 확인**으로 처리한다(`keydown` 을 **캡처 단계**에서 가로채 뒤쪽 화면의 Enter 핸들러(검색 등)가 함께 돌지 않게 한다). 취소는 닫기(×)·오버레이 클릭. 입력 모달(`promptModal`)은 입력칸에서 Enter 로 확인한다.
- **기관(tb_company) 참조 = 기관 팝업**: 조각 `fragments/components/company-picker-modal` 포함 후 `const sel = await companyPicker.open()` → `{companyCode, companyName}`. 등록 폼과 검색조건 **양쪽 모두** 같은 팝업을 쓴다.
- **목록 다중선택 = 선택 삭제**: 첫 컬럼에 체크박스(`th` 에 `#checkAll`, 행은 `.row-chk[data-id]`), 1건 이상 선택되면 그리드 툴바의 **등록 왼쪽**에 `#btnDeleteSel`(`선택 삭제 (N)`)가 나타난다. 서버는 `DELETE /{영역}/{stem}/bulk` + JSON 배열 본문(`api.del(url, ids)`)으로 받아 건별 처리하고 실패분만 경고로 모아 돌려준다. 행 클릭(수정) 과 충돌하지 않도록 체크박스 클릭은 선택 토글만 한다.
- **모달 안 삭제 버튼**: 수정 모드에서만 노출하며 `modal-footer` **좌측**(`.modal-footer-left`)에 두어 취소/저장과 떨어뜨린다. 목록의 행별 삭제 버튼은 두지 않는다(선택 삭제 + 모달 삭제로 일원화).
- **카드 목록 = 카드 컴포넌트**: 조각 `fragments/components/card-list :: cardList('{컨테이너id}')` + 같은 파일의 `:: cardPanel`(입력 패널) + `core/components/card-list.js`. `cardList.init(id, {baseUrl, cardTypeName})` / `cardList.set(id, 카드목록)` / `cardList.get(id)`(저장 payload). '카드 추가' 확인 시 **BiostarX 에 즉시 등록**되고, tb_card 저장·사용자 부여는 인원 저장 때 이뤄진다(`integration.md`). **행을 클릭하면 같은 패널에 값이 채워져** 열리며(카드번호는 실물 카드라 읽기전용, SCAN·할당하기 숨김) 확인 시 목록 값만 갱신한다(외부 호출 없음). 카드번호는 직접 입력 · **SCAN**(장치 리더) · **할당하기**(`:: cardPicker` 팝업에서 회수된 미할당 카드 선택, 선택 시 패스구분·명칭·상태까지 채움) 세 가지로 넣는다. 카드종류는 화면에선 고정 표시(`cardTypeName`)만 하고 **실제 코드값은 서버가 정한다**(화면 값 불신).
- **편집 모달 옆 보조 패널**: 입력이 많아 별도 창이 필요하면 새 오버레이를 만들지 말고 **편집 오버레이 안에 `.modal-container` 형제**로 넣는다(`.card-side`처럼 `display:none` → `.open` 시 flex). 오버레이의 `gap`·`flex-wrap` 덕에 **우측에 나란히** 놓이고 좁은 화면에서는 아래로 접힌다 — z-index 다툼이 없다.
- **출입권한 선택 = 출입권한 트리 컴포넌트**: 조각 `fragments/components/ac-group-tree :: acGroupTree('{컨테이너id}')` + `core/components/ac-group-tree.js`. `acGroupTree.init(id, '{목록URL}')`(화면당 1회) / `acGroupTree.set(id, ids)`(모달 열 때) / `acGroupTree.get(id)`. `biostar_ac_id` 가 매핑된 노드만 선택 가능(최상위 구역은 라벨), **상위를 체크/해제하면 하위 전체에 같은 값**이 적용된다.
- **엑셀 일괄등록 = 공용 엑셀 등록 컴포넌트**: 조각 `fragments/components/excel-import-modal :: excelImportModal` + `core/components/excel-import.js`. 툴바 `엑셀 등록` 버튼에서 `excelImport.open({baseUrl, hint, onDone})`. 모달 안에서 양식 다운로드(`GET {baseUrl}/excel/template`) → 파일 선택(파일명 미리보기, 선택 즉시 전송 안 함) → 업로드(`POST {baseUrl}/excel/import`, FormData `file`). 서버는 행마다 기존 create 를 **행 단위 독립 트랜잭션**(self 프록시)으로 호출해 `{success,fail,errors[]}` 반환, 성공분 있으면 onDone(목록 갱신)+모달 닫기. 기관(`/company/company`)·정규인원(`/person/person`)이 공유한다. 업로드는 `FormData`(`file`)로 `POST .../excel/import`, 서버는 행마다 기존 `create` 를 **행 단위 독립 트랜잭션**으로 호출(한 행 실패가 나머지를 막지 않음)하고 `{success, fail, errors[]}` 를 돌려준다. 화면은 성공/실패 건수 + 행별 사유(앞 5건)를 토스트로 요약하고 성공분이 있으면 목록 갱신. 필수값·중복·암호화·외부연동은 create 규칙을 그대로 재사용한다.
- **첨부파일 = 파일 필드 컴포넌트**: 조각 `fragments/components/file-field :: fileField('{필드id}','{라벨}')` + `core/components/file-field.js`. `<input type=file>` 은 감추고 [파일 선택]·[다운로드]·[삭제] 버튼만 노출한다. 파일은 **BASE64 로 읽어 화면 폼과 함께 전송**(별도 업로드 요청 없음 → 저장 전 취소하면 서버에 남지 않는다). API: `fileField.set(id, 파일명, 다운로드URL)` / `fileField.get(id)` → `{name, data}`(`data=null` 이면 *기존 파일 유지*, `name=null` 이면 *삭제*). 상한 5MB. 다운로드 버튼은 **이미 저장된 파일**에만 보인다.
- **헤더 계정 메뉴(전 화면 공통)**: `fragments/main :: header`(th:block) 안에 사용자명 드롭다운 + 계정 모달(시작메뉴 변경/비밀번호 변경/로그아웃 확인)을 함께 담고, `core/header-user.js` 가 동작을 붙인다. 서버는 `AccountController`(`/account/menus`·`/startMenu`·`/password`, 자가서비스=로그인만). 시작메뉴 선택은 **메뉴 트리**(`/account/menus` 가 `MenuService.tree` 반환)로 렌더 — 그룹(menuUrl 없음)은 선택 불가 헤더, 화면(menuUrl 있음)만 라디오 선택. 선택값은 `tb_login_user.start_menu_id` 에 저장되어 다음 로그인 시 `MenuService.startMenuTarget` 이 그 화면으로 바로 진입시킨다. 모달 id 는 페이지 조각과 충돌하지 않도록 `hdr*` 접두사.

## 더 나은 구성 제안
- **플레이스홀더 이름 확정**: `{공통조각}` 대신 `components/`(또는 `_partials/`) 로 통일 — 도메인 폴더와 시각적으로 구분되고 예측 가능.
- **공용/전용 모달의 승격 규칙**: 처음엔 `web/components/` 에 두고, kiosk 와 공유가 생기면 `fragments/components/` 로 승격. "중복 발견 시 상위로 올린다" 를 관례로.
- **CSS 도 동일 트리로**: `static/css/web/**`, `static/css/kiosk/**`, 공용은 `static/css/common/**` — JS/템플릿과 미러링해 일관.
- **core JS 는 탭 제거에 맞춰 정리**: 탭 매니저/iframe 레지스트리 대신 `page-factory` + `head`/`sidebar` 초기화만 둔다.
- **fragment 파라미터화**: 모달은 `th:fragment="modal(title, ...)"` 처럼 인자를 받게 만들어 한 조각으로 여러 화면 재사용.

## TODO
- TODO: core JS(page-factory/head/sidebar) 채택 범위 확정.
- TODO: 공통 헤더/사이드바 fragment 확정, kiosk 레이아웃 별도 여부.
- TODO: 에러/로딩/빈 상태 표준 처리(시각은 `design.md`).

## 관련 문서
[design.md](design.md) · [backend.md](backend.md) · [conventions.md](conventions.md) · [security.md](security.md)
