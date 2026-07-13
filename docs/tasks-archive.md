# 작업 기록 (완료 이력)

> **완료한 작업만** 시간순(최신이 위)으로 기록한다. 진행중/백로그 같은 능동 작업관리는 하지 않는다
> (계획·논의는 대화/이슈/PR 로). 작업을 마치면 `/commit` 단계에서 아래에 한 줄 추가한다.
> 형식: `- [x] 요약 — 담당, 완료일, 커밋`

- [x] 하네스 점검 반영(OpenAI 원칙 대조): 강제공백 메움(code-lint에 MENU_ID금지·수동페이징금지·@RequestMapping↔menu_url), AGENTS §4 메뉴접근통제 불변식, new-screen 갱신, UI규칙 conventions→frontend 이관, 순수로직 단위테스트(MenuService/MenuNode)+smoke 격리 — yg-kwak, 2026-07-13
- [x] menu_id 하드코딩 제거 — 요청 URL(=menu_url) 서버 역조회로 menu_id 결정(MenuAccessInterceptor+CurrentMenu). 컨트롤러 MENU_ID 상수 삭제, 메뉴 접속(MENU) 감사 자동화(누락분 해결) — yg-kwak, 2026-07-13
- [x] 공통화: 윈도우형 페이징(core/pager.js)·기간 프리셋(core/period.js)을 core 컴포넌트로 추출, 전 목록화면(공통코드·사용자·감사추적) 적용 + conventions/frontend 규칙 등록 — yg-kwak, 2026-07-13
- [x] 감사추적(tb_system_log) 조회 전용 페이지 — 기간·유형·검색어 필터, 유형/메뉴 조인 표시, 엑셀(목적 감사), 최신순 기본 정렬. 조회/엑셀도 자체 감사 기록 — yg-kwak, 2026-07-13
- [x] 권한메뉴관리 트리에 메뉴별 조회/입력·수정/삭제 체크 추가 — 선택=조회, 미선택 시 입력·수정/삭제 해제·비활성. smoke 가드(시드 권한 auth_id≤2 update/delete 금지) — yg-kwak, 2026-07-13
- [x] 권한메뉴관리 UX 개편 — 좌 권한목록/우 설정(전체 메뉴 트리 체크, 캐스케이드·전체선택·신규/수정완료/삭제), 화면 엑셀·페이지크기 제거. MenuNode.buildTree 공용화, MenuService.fullTree — yg-kwak, 2026-07-13
- [x] 권한메뉴관리(tb_menu_auth) CRUD — 권한 그룹 + 메뉴별 CRUD 권한 매트릭스(조회/등록·수정/삭제), 사용중 권한 삭제 가드, 엑셀 + smoke — yg-kwak, 2026-07-13
- [x] 사이드바 권한 필터(read 권한 메뉴만, root 전체·빈 그룹 숨김) + 무권한 URL 직접접근 시 403 권한없음 페이지(error/forbidden) — yg-kwak, 2026-07-13
- [x] 필수(*) 표기 확대(공통코드·설정) + picker 필드 삭제버튼 공통화 + 하네스 규칙(신규메뉴 필수*·클릭선택 삭제버튼) — yg-kwak, 2026-07-13
- [x] 사용자 폼 개선 + 공통 컴포넌트: 필수(*) 표기, 비밀번호 표시토글(전 페이지), tb_common 코드 팝업(code-picker) — 근무지역을 팝업 선택으로. 시작메뉴/관리자여부 필드 제거(권한 필수화) — yg-kwak, 2026-07-13
- [x] 명명규칙 정비: 네이밍 앵커=테이블 어간(§1 명문화). 화면/라우트/컨트롤러/서비스를 stem 으로 통일 — commonCode→common, User*/user→LoginUser*/loginUser (Mapper/Model 은 Tb{Stem} 유지) — yg-kwak, 2026-07-13
- [x] 사용자관리(tb_login_user) CRUD — 골든 패턴 전개(성명/비번 ARIA, 비번 WRITE_ONLY·수정 시 유지, 권한/시작메뉴/근무지역 참조 select, root 부여 가드·본인삭제 차단, 엑셀) + smoke 36체크 — yg-kwak, 2026-07-13
- [x] UI 테마: Notion→Ant 대비 + 차콜 다크 프레임(헤더/사이드바), 헤더 블루 강조선, 사이드바 hover 파랑·클릭 전용 플라이아웃, 입력 자동완성 전역 차단 — yg-kwak, 2026-07-13
- [x] CSS 완전 토큰화 + Notion 테마 적용(팔레트→역할 2단계, 사이드바 라이트·토스트·헤더 토큰화) — yg-kwak, 2026-07-10
- [x] 사이드바 계층형 개편(그룹→하위, 접기/펼치기, 접힘 시 아이콘 플라이아웃, menu_icon) — yg-kwak, 2026-07-10, `54fd471`
- [x] 설정관리(tb_system) — BiostarX 연동정보 저장/연결테스트(adapter, pw ARIA 암호화) — yg-kwak, 2026-07-10, `b583328`
- [x] 공통 토스트 알림(success/error/warning, 서버 return 문구 자동) — yg-kwak, 2026-07-10, `22c8a8a`
- [x] 능동 작업보드(TASKS.md) 제거, 완료 기록만 유지 — yg-kwak, 2026-07-10, `f051ff0`
- [x] 공통코드 시스템코드(N) 조회 제외 + 구분 컬럼 제거 — yg-kwak, 2026-07-10, `36f2069`
- [x] 공통코드 코드구분 select(허용 구분만) + 코드구분명 자동 — yg-kwak, 2026-07-10, `4e1f6d9`
- [x] 공통코드 시스템코드(N) 화면 CRUD 차단 — yg-kwak, 2026-07-10, `41ab35e`
- [x] 하네스 자동화 보강(원문 격차 1~6: 스크립트/CI린트/ArchUnit/드리프트/MDC) — yg-kwak(+Claude), 2026-07-10, `0fa23fa` `c6860f5`
- [x] 리뷰 기록(docs/reviews) 도입 — yg-kwak, 2026-07-10, `6f87433`
- [x] 규칙 대장(conventions.md) 전면 확장 + 자동 등록 장치 — yg-kwak, 2026-07-10, `c7d7e23`
- [x] 조회 감사(READ 조건+건수) + LOGIN/LOGOUT + user_input 정정 — yg-kwak, 2026-07-10, `11b9ea3`
- [x] 엑셀 다운로드(목적→감사 remark, 전체 데이터) — yg-kwak, 2026-07-10, `a02b72d`
- [x] 행 클릭 수정 + 공통 확인/입력 모달(components) — yg-kwak, 2026-07-10, `0d10e50` `a02b72d`
- [x] 메뉴 권한 기반 CRUD 통제(화면+서버 이중 방어, viewer 계정) — yg-kwak, 2026-07-10, `2698234`
- [x] 목록 강화(정렬/페이지크기/검색조건/사용여부) + 레이아웃 — yg-kwak, 2026-07-10, `bc73d08` `419f2d9`
- [x] Gradle 저장소 루트 이동(project/ 제거) + IntelliJ 실행 정리 — yg-kwak, 2026-07-10, `3207b97`
- [x] 골든 샘플: 공통코드관리 CRUD — yg-kwak, 2026-07-09, `5747902`
- [x] 앱 스캐폴딩 + DB(DDL/seed) + 공통기반(인증/ARIA/감사/예외) — yg-kwak, 2026-07-09, `5747902`
- [x] 하네스 구축(AGENTS/docs/skills/hooks/CI) 및 정합화 — yg-kwak, ~2026-07-08, `1592fe3` 외
