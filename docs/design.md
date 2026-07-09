# 디자인 가이드 (UI/UX · 시각 가이드라인)

> 화면의 **시각 규칙**(색·타이포·간격·컴포넌트 룩)의 단일 출처.
> 화면의 **구조·파일 위치·동작 관례**는 `frontend.md` 가 담당한다(중복 금지).
> ⚠️ 아래는 **ROKA 프로젝트를 기반으로 한 초기 예시**다. 확정 후 직접 수정한다.

## 1. 원칙
- 업무용 관리 시스템 — **정보 밀도·가독성·일관성** 우선. 화려함보다 명료함.
- 모든 시각값은 **CSS 변수(디자인 토큰)** 로만 쓴다. 화면에 색상 HEX 를 직접 박지 않는다.
- 컴포넌트는 재사용한다. 같은 요소(버튼/입력/테이블)를 화면마다 새로 만들지 않는다.

## 2. 컬러 토큰 (`static/css` `:root`)
```css
:root {
  /* Primary — 헤더/주요 액션 */
  --color-primary-default: #2D5273;
  --color-primary-hover:   #396892;
  --color-primary-active:  #4075A3;
  --color-primary-disabled:#ADB7BF;
  /* Secondary — 보조 액션/강조 */
  --color-secondary-default:#68B2F4;
  --color-secondary-hover:  #579BDA;
  --color-secondary-active: #4C8AC3;
  --color-secondary-disabled:#B4BFCA;
  /* Tertiary — 배경/카드/보더 */
  --color-tertiary-default: #FAFCFD;  /* 페이지 배경 */
  --color-tertiary-hover:   #F1F4F8;
  --color-tertiary-active:  #DDE6EE;  /* 보더 */
  --color-tertiary-disabled:#BFC8CF;
  /* Neutral — 텍스트/보조 */
  --neutral-100:#f2f2f2; --neutral-300:#dcdcdc; --neutral-500:#b7b7b7;
  --neutral-700:#858585; --neutral-900:#666666;
  /* 상태 */
  --color-error:#DE2B2B;
  --color-black:#000; --color-white:#fff;
}
```
- 본문 텍스트: `--color-black` / 보조 텍스트: `--neutral-700~900` / 비활성: `*-disabled`.
- 위험·삭제·검증실패: `--color-error`.
- TODO: 성공/경고(success/warning) 토큰 추가 여부.

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
- 레이아웃: 상단 **헤더 60px 고정**(primary 배경) + 좌측 **LNB 사이드바 ~220px 고정** + 본문 영역(패딩 `30px 40px`).
- 카드/영역 라운드: 검색영역 `12px`, 콘텐츠 카드 `16px`, 버튼 `8px`.
- 보더: `1px solid var(--color-tertiary-active)` 또는 `#D6DFEB`.
- 폭 유틸리티: `.w-80 .w-100 .w-120 .w-160 .w-200 .w-240 .w-300 .w-500 .w-full`.
- 반응형: `max-width:1024px` 에서 LNB 오프캔버스, `768px` 에서 2컬럼→1컬럼.

## 5. 컴포넌트 룩
- **버튼**: 기본 primary 배경/흰 글자, hover 시 `*-hover`. 보조 버튼은 흰 배경+primary 글자+보더. 비활성 `*-disabled`. 라운드 8px.
- **입력(input)**: 보더 `--color-tertiary-active`, 포커스 시 primary 보더. 비밀번호는 표시/숨김 토글 아이콘.
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
