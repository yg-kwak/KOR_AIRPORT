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

## 디렉터리 구조 (리소스 내부)
```
src/main/resources/
├── templates/
│   ├── fragments/            전역 레이아웃 조각: head, main(레이아웃), sidebar
│   │   └── components/       (선택) web·kiosk 공용 모달/팝업
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
        ├── core/                 app.js(fetch), toast, confirm/prompt-modal, code-picker(코드 팝업), sidebar(계층·접기·플라이아웃), no-autofill(입력이력 차단), password-toggle(비번 표시) 등 공통 뼈대
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
