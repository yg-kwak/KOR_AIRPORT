# 아키텍처

> 전체 구조와 계층 경계의 진실 원천. **패키지 구조의 단일 출처**. 불변식 요약은 `AGENTS.md §4`.

## 1. 시스템 개요
공항 출입관리·방문자 통제 시스템. 웹 백엔드(Spring Boot)가 도메인/데이터를 담당하고, Thymeleaf 로 화면을 서버사이드 렌더링하며, 메인 목적은 출입통제 플랫폼 **Suprema BiostarX** 와 연동해 사용자의 출입을 제어하는 Spring Boot 웹 애플리케이션이다.

- 사용자 네트워크 구성: **운영(적용) 환경은 DMZ — 외부 통신 불가** (개발 환경은 해당 없음)
- 루트 패키지: `AirPort`
- 빌드/실행: `gradlew.bat build`, `gradlew.bat bootRun` (저장소 루트)
- 서비스 포트, DB설정, Mapper 경로, 세션 타임아웃, SSL 활성화, 로그 저장 등 환경설정은 `application.properties` 에 등록
- eGovFrame 은 **의존성만 포함하고 코드에서는 사용하지 않는다** (`backend.md`)

```
[Thymeleaf 화면(templates/ + static/)]  ── 같은 앱 내 서버사이드 렌더링
                 │
        Controller → Service → Mapper(MyBatis)
                 │                    │
             [adapter]             [MSSQL]
                 │
          [Suprema BiostarX]
```

## 2. 레이어 구조
패키지는 도메인별 하위 폴더 **없이 역할별 평면 구조**로 구성하고, 도메인 구분은 **클래스명 접두사**(`Visitor*`, `AcGroup*`, `Menu*` 등)로 한다.

```
AirPort
├── controller   MVC 컨트롤러 — 요청 처리, 세션 체크는 AuthInterceptor 위임
├── service      비즈니스 로직 — 인터페이스 없이 @Service 구체 클래스 단독
├── mapper       MyBatis 매퍼 인터페이스 (Tb*Mapper)
├── model        DTO/VO (Tb* — 테이블 1:1 매핑)
├── adapter      Biostar, 카드프린터, 주차 등 외부 API 연동
├── config       WebConfig, AuthInterceptor, MenuAccessInterceptor(요청 URL→menu_id 해석·메뉴접속 감사) 등
├── common       공통: ApiResponse, PageParam/PageResult, SessionKeys, CurrentMenu(요청 스코프 menu_id), exception
├── security     ARIA 암호화 유틸 (ARIAEngine, ARIAUtil)
└── util         엑셀 다운로드, IP 조회, 페이징, 로그 유틸
```

## 3. 계층 경계 (불변식)
- **Controller**: 요청/응답 매핑, 검증, 인증 컨텍스트. 비즈니스 로직·SQL 금지.
- **Service**: 도메인 로직, 트랜잭션 경계. 외부 SDK 직접 호출 금지(어댑터 경유).
- **Mapper(MyBatis)**: SQL 전담. XML 에만 SQL.
- **adapter**: Biostar, 카드프린터, 주차 등 외부 연동. 실패/재시도/매핑 담당.
- 의존 방향은 **단방향**: controller → service → mapper/adapter.

## 4. 주요 도메인 (메뉴 기준)
- 임시카드관리(Visitor): 방문신청 → 임시/장기 출입등록.
- 정규카드관리: 정규카드 등록.
- 공통관리: 사용자등록(`tb_login_user`), 권한메뉴관리(`tb_menu_auth`/`tb_menu_auth_detail`), 코드등록관리(`tb_common`), 출입권한매핑관리(`tb_ac_group`), 설정관리(`tb_system`).
- 기관관리: 출입기관 등록, 기관차량 등록.
- 보안관리: 감사추적(`tb_system_log`).
- 카드관리 / 차량관리(주차조회).
- 기타: 메뉴(`tb_menu`).

> 테이블 상세는 `database.md`, 감사/권한 정책은 `security.md`.

## 5. 라우팅/URL 규칙
- URL 경로는 **각 Controller 파일에** `@RequestMapping`/`@GetMapping` 등으로 정의한다(중앙 라우팅 테이블 없음).
- 경로 프리픽스는 도메인 기준으로 Controller 클래스 상단에 둔다(예: `@RequestMapping("/system/loginUser")`).
- **Controller 프리픽스 == `tb_menu.menu_url`.** 이 일치가 메뉴 해석(§6)의 근거다.
- 화면 반환(Thymeleaf 뷰)과 데이터 응답(`@ResponseBody`)을 한 Controller 안에서 구분해 명확히 한다.

## 6. 요청 처리 흐름 (인터셉터 → menu_id 해석)
`WebConfig` 가 인터셉터를 **순서대로** 건다(등록 누락 = 보안 회귀).

```
요청 → [AuthInterceptor] 세션 인증(미인증: AJAX 401 / 화면 login 리다이렉트)
     → [MenuAccessInterceptor] 요청 URI(=menu_url) → menu_id 역조회 → 요청 스코프 CurrentMenu 주입
                                (정상 200 페이지 GET 이면 "메뉴 접속(MENU)" 감사 자동 기록)
     → Controller  menuId() = currentMenu.getMenuId()  → 권한 판정·감사에 사용(하드코딩 없음)
     → Service → Mapper
```
- **menu_id 는 서버가 URL 로 결정**한다(클라이언트가 보내지 않음 = 권한 우회 방지). 역조회는 `MenuService.resolveMenuId`(menu_url 이 경로 경계로 최장 접두사인 것).
- 효과: **메뉴 번호가 순수 데이터** → `tb_menu` 만 바꾸면 사이드바·권한·감사가 따라옴(컨트롤러 상수 수정 불필요). 단, `@RequestMapping` 은 `menu_url` 과 계속 일치해야 하고, 새 화면은 여전히 컨트롤러가 필요. (상세: `security.md`, `conventions.md §4`)

## 7. TODO (채워야 할 결정)
- TODO: 트랜잭션 전파 정책.
- TODO: 미설계 도메인(임시/정규카드, 기관, 차량) 테이블 확정.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [integration.md](integration.md) · [security.md](security.md) · [frontend.md](frontend.md)
