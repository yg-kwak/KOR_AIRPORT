# 프론트엔드 (Thymeleaf 서버사이드 렌더링)

> 화면의 **구조·파일 위치·동작 관례**의 단일 출처. 스캐폴딩은 `/new-screen`.
> 화면의 **시각 규칙(색·타이포·컴포넌트 룩·토큰)** 은 `design.md` 가 담당한다(여기서 반복하지 않는다).
> **별도 SPA/번들러(webpack/vite) 없음.** 백엔드(project/)와 한 앱이다.

## 렌더링 방식
- **Thymeleaf** 서버사이드 렌더링 + `thymeleaf-layout-dialect`.
- 화면 = 템플릿(HTML) + 정적 자산(JS/CSS). Controller 가 모델을 담아 템플릿을 반환.
- 정적 자산은 모두 **로컬 포함**(외부 CDN 미사용 — 운영 DMZ 대비).

## 디렉터리 구조 (리소스 내부)
```
src/main/resources/
├── templates/
│   ├── fragments/        head, main(레이아웃), sidebar 등 공통 조각
│   ├── login.html
│   └── {도메인}/          system, visitor, ... 도메인별 화면
└── static/
    ├── css/              스타일 (토큰/컴포넌트 → design.md)
    ├── font/             Pretendard woff2 (로컬)
    ├── ic/               아이콘 PNG
    ├── js/
    │   ├── common.js, common/     공용 라이브러리
    │   ├── core/                  app.js, page-factory, page-registry, tab-manager 등 공통 뼈대
    │   └── pages/{도메인}/         화면별 스크립트
    └── images/
```

## 화면 작성 관례
- 새 화면: `templates/{도메인}/{화면}.html` + `static/js/pages/{도메인}/{화면}.js`.
- 공통 레이아웃은 `fragments/` 조각을 재사용(복제 금지). 시각 컴포넌트 룩은 `design.md` 를 따른다.
- 서버 통신은 core JS 규약(fetch 래퍼 등)을 따른다. TODO: AJAX 응답 표준 포맷(→ `backend.md` 에러 규격과 정합).
- 권한별 메뉴/버튼 노출은 서버가 내려준 권한(`tb_menu_auth_detail`)에 따른다. (`security.md`)
- 감사 대상 화면(조회/입력/수정/삭제)은 서버에서 이력을 남긴다. (`security.md`)

## TODO
- TODO: core JS(page-factory/registry/tab-manager) 채택 범위 확정.
- TODO: 공통 헤더/사이드바 fragment 확정.
- TODO: 에러/로딩/빈 상태 표준 처리(시각은 `design.md`).

## 관련 문서
[design.md](design.md) · [backend.md](backend.md) · [conventions.md](conventions.md) · [security.md](security.md)
