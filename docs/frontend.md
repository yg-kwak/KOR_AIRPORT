# 프론트엔드 (shadcn/ui)

> 화면 추가·수정 시 읽는다. 스캐폴딩은 `/new-screen`.

## 디자인 시스템
- shadcn/ui (https://ui.shadcn.com). 컴포넌트는 복사-소유 방식.
- TODO: 프레임워크 확정(Next.js / Vite+React 등)과 `web/` 실제 구조.

## 화면 관례
- 컴포넌트/페이지 배치: TODO.
- 폼·테이블·다이얼로그는 shadcn 컴포넌트를 재사용(직접 재구현 금지).
- 접근성: 라벨/키보드 포커스/대비 준수.
- API 호출부는 화면 컴포넌트와 분리. TODO: 데이터 패칭 규약.

## 백엔드 연동
- REST 규격은 `backend.md` / `architecture.md` 를 따른다.
- 인증 토큰/세션 처리: TODO. (`security.md`)

## TODO
- TODO: 라우팅/상태관리 선택.
- TODO: 공통 레이아웃(헤더/사이드바), 권한별 메뉴 노출.
- TODO: 에러/로딩/빈 상태 표준 컴포넌트.

## 관련 문서
[backend.md](backend.md) · [conventions.md](conventions.md) · [security.md](security.md)
