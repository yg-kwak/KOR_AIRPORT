# 프론트엔드 (Thymeleaf 서버사이드 렌더링)

> 화면 추가·수정 시 읽는다. 스캐폴딩은 `/new-screen`.
> **별도 SPA/번들러(webpack/vite) 없음.** 백엔드(project/)와 한 앱이다.

## 렌더링 방식
- **Thymeleaf** 서버사이드 렌더링 + `thymeleaf-layout-dialect`.
- 화면 = 템플릿(HTML) + 정적 자산(JS/CSS). Controller 가 모델을 담아 템플릿을 반환.

## 디자인 시스템 (shadcn = 시각 레퍼런스)
- shadcn/ui (https://ui.shadcn.com) 는 **React 전용**이라 컴포넌트를 그대로 쓰지 않는다.
- shadcn 의 **비주얼 언어(색/간격/라운드/그림자/폼·테이블·다이얼로그 룩)** 를 참고해 `static/css` 로 재현한다.
- 목표: 화면 간 일관된 디자인. TODO: 디자인 토큰(CSS 변수) 정리, Tailwind 도입 여부.

## 디렉터리 구조 (리소스 내부)
```
src/main/resources/
├── templates/
│   ├── fragments/        head, main(레이아웃), sidebar 등 공통 조각
│   ├── login.html
│   └── {도메인}/          system, visitor, ... 도메인별 화면
└── static/
    ├── css/
    ├── js/
    │   ├── common.js, common/     공용 라이브러리
    │   ├── core/                  app.js, page-factory, page-registry, tab-manager 등 공통 뼈대
    │   └── pages/{도메인}/         화면별 스크립트
    ├── font/  images/  ...
```

## 화면 관례
- 새 화면은 `templates/{도메인}/` 에 템플릿, `static/js/pages/{도메인}/` 에 스크립트를 둔다.
- 공통 레이아웃은 `fragments/` 조각을 재사용(직접 복제 금지).
- 폼/테이블/다이얼로그는 공통 컴포넌트·패턴(core JS)을 재사용.
- 서버 통신은 core 규약(fetch 래퍼 등)을 따른다. TODO: AJAX 응답 표준 포맷.
- 권한별 메뉴 노출은 서버가 내려준 권한(`tb_menu_auth_detail`)에 따른다. (`security.md`)
- 접근성: 라벨/키보드 포커스/대비 준수.

## TODO
- TODO: core JS(page-factory/registry/tab-manager) 채택 범위 확정.
- TODO: 공통 헤더/사이드바 fragment 확정.
- TODO: 에러/로딩/빈 상태 표준 처리.

## 관련 문서
[backend.md](backend.md) · [conventions.md](conventions.md) · [security.md](security.md)
