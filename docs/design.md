# 디자인 가이드 (UI/UX · 시각 가이드라인)

> 화면의 **시각 규칙**(색·타이포·간격·컴포넌트 룩)의 단일 출처.
> 화면의 **구조·파일 위치·동작 관례**는 `frontend.md` 가 담당한다(중복 금지).
> **현재 테마: Notion** (getdesign.md 참고). 웜 블랙 텍스트·라이트 웜그레이 서피스·블루 액센트·옅은 보더·낮은 라운드.

## 1. 원칙
- 업무용 관리 시스템 — **정보 밀도·가독성·일관성** 우선. 화려함보다 명료함.
- **모든 시각값은 CSS 변수(디자인 토큰)로만.** 컴포넌트에 HEX/rgb 직접 사용 금지 → **스타일 교체 = `:root` 값만 변경**.
- 토큰은 2단계: **팔레트(`--nt-*` 원천값)** → **역할 토큰(`--color-*`, `--sidebar-*`, `--toast-*`, `--radius-*` …)**. 컴포넌트는 역할 토큰만 참조.
- 컴포넌트는 재사용한다. 같은 요소(버튼/입력/테이블)를 화면마다 새로 만들지 않는다.

## 2. 컬러 토큰 (`static/css` `:root`, 현재=Notion)
```css
/* 팔레트(원천) */
--nt-blue:#2383e2; --nt-blue-hover:#1a72c4; --nt-blue-active:#1667b3;
--nt-red:#eb5757;  --nt-green:#4dab9a; --nt-amber:#d9a94e;
--nt-text:#37352f;      /* 웜 블랙 본문 */
--nt-text-2:#787774;    /* 보조 */   --nt-text-3:#9b9a97; /* 흐림 */
--nt-bg:#ffffff;        /* 페이지 */  --nt-bg-gray:#f7f7f5; /* hover/thead */
--nt-sidebar:#fbfbfa;   --nt-border:#e9e9e7; --nt-hover:#efefee;

/* 역할(컴포넌트는 이것만 사용) */
--color-primary-*  → 블루 액센트(주요 버튼/링크/포커스)
--color-tertiary-default(페이지) / -hover(옅은 서피스) / -active(보더)
--neutral-900/700/500 → 본문/보조/흐림 텍스트
--color-error → --nt-red
--color-black → 웜 블랙(#37352f)   --color-white → #fff
--sidebar-*  라이트 사이드바   --toast-*  다크 토스트   --radius-sm/-/-lg: 4/6/8px
--header-bg 흰색 + 하단 보더
```
- 본문 텍스트: `--color-black`(웜 블랙) / 보조: `--neutral-700~500` / 위험·실패: `--color-error`.
- 상태색(토스트): success=녹, error=적, warning=황.

## 🎨 다른 테마로 교체하려면
1. getdesign.md 등에서 팔레트를 골라 **`--nt-*` 원천값**을 교체(그리고 필요 시 역할 토큰 매핑 조정).
2. `docs/design.md`(이 문서) 값도 함께 갱신(단일 출처).
3. 폰트 교체 시 **`.woff2`를 `static/font/`에 로컬 번들**(운영 DMZ — 외부 CDN 불가).
4. `scripts/smoke-test.sh`로 회귀 확인 후 커밋. — 컴포넌트가 토큰만 쓰므로 대부분 자동 반영된다.

## 3. 타이포그래피
- 폰트: **Pretendard** (Regular 400 / Medium 500 / Bold 700), `static/font/*.woff2` 로 로컬 포함(DMZ 대비). 폴백 `sans-serif`.
- 스케일:

| 용도 | size / line-height | weight |
|------|--------------------|--------|
| h1 | 28 / 36 | 500 |
| h2 | 20 / 26 | 700 |
| body1 | 18 / 24 | 700 |
| body2 | 16 / 22 | 500 |
| body3 (기본 본문) | 14 / 18 | 500 |

## 4. 간격·레이아웃·라운드
- 레이아웃: 상단 **헤더 60px 고정**(흰색 배경 + 하단 보더, `--header-bg`) + 좌측 **LNB 사이드바 240px**(라이트, 접힘 64px) + 본문 영역.
- 라운드(토큰): 입력/버튼 `--radius-sm`(4px), 카드/검색영역 `--radius`(6px), 모달 `--radius-lg`(8px). *(Notion 낮은 라운드)*
- 보더: `1px solid var(--color-tertiary-active)` (=`--nt-border`, 옅음).
- 폭 유틸리티: `.w-80 .w-100 .w-120 .w-160 .w-200 .w-240 .w-300 .w-500 .w-full`.
- 반응형: `max-width:1024px` 에서 LNB 오프캔버스, `768px` 에서 2컬럼→1컬럼.

## 5. 컴포넌트 룩
- **버튼**: 기본(primary) 파랑 배경/흰 글자. 보조 버튼은 흰 배경+보더(웜 블랙 글자). 라운드 `--radius-sm`.
- **입력(input)**: 보더 `--color-tertiary-active`, 포커스 시 파랑 보더 + 포커스 링(`--nt-focus-ring`).
- **토스트/모달/확인·입력 모달**: 공통 컴포넌트(`js/core/*`). 토스트는 다크 서피스(`--toast-*`).
- **테이블**: 헤더 배경 `--color-tertiary-hover`, 행 구분선 `--color-tertiary-active`, hover 강조. 셀 정렬·폭은 `.w-*` 유틸.
- **모달(modal)**: 오버레이 + `.modal-container`(small/기본), 헤더(title+닫기 아이콘)/바디/푸터 구조. 공통 조각으로 재사용(`frontend.md`).
- **페이지 레이아웃**: 탭/iframe 없이 **독립 페이지**. 고정 헤더 + 좌측 사이드바 + 본문 영역을 공통 조각(head/main/sidebar)으로 조합.
- **사이드바(LNB)**: 트리 메뉴, 권한(`tb_menu_auth_detail`)에 따라 노출. 현재 메뉴 활성 강조.

## 6. 아이콘
- 로컬 PNG 아이콘 세트 `static/ic/*` (예: `ic_close_24`, `ic_view_off_24`) — 24px 기준. 외부 CDN 미사용(DMZ).
- TODO: 아이콘 네이밍/사이즈 규격 통일(SVG 스프라이트 검토).

## 7. 접근성·i18n
- 라벨/`alt`/키보드 포커스/대비(AA) 준수.
- 문구는 `messages.properties` 로 관리(하드코딩 지양). (`backend.md`)

## 8. 검토 제안 (정할 것)
- **Tailwind 도입 여부**: 현재 스택은 번들러 없는 Thymeleaf SSR이라, 플레인 CSS 변수(위 토큰) 방식을 권장. Tailwind 는 빌드 파이프라인이 필요하므로 도입 시 비용 대비 효과를 먼저 판단.
- 다크모드 지원 여부.

## 관련 문서
[frontend.md](frontend.md) · [conventions.md](conventions.md) · [security.md](security.md)
