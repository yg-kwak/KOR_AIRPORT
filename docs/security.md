# 보안 · 권한 · 감사

> 출입관리 시스템의 핵심. 인증/권한/암호화/감사의 진실 원천.
> 이력/암호화 대상 **컬럼 스키마**는 여기서 정의하지 않고 `database.md` 를 링크한다.

## 비밀값 (불변식)
- `.env`, 키/키스토어, DB 접속정보를 **코드/커밋에 넣지 않는다** (PreToolUse 훅이 차단).
- `.env.example` 에는 **키 이름만**. 실제 값은 운영 환경에서 주입.
- 프로퍼티 내 민감값은 Jasypt(`jasypt-spring-boot-starter`)로 암호화하여 `application.properties` 에 저장.
- TODO: Jasypt 마스터 비밀번호 주입 방식(기동 인자/환경변수) 확정.

## 인증 (Authentication)
- **세션 기반 커스텀 인증**. Spring Security 미사용, eGov 보안 모듈 미사용.
- 로그인/정적/공개 경로를 제외한 요청은 `AuthInterceptor`(HandlerInterceptor)가 세션을 검증.
- ⚠️ `WebConfig.addInterceptors()` 에 인터셉터가 **실제 등록**되어야 보안 경계가 성립한다. 등록 누락은 보안 회귀 — `/review` 시 최우선 확인 항목.
- 계정 잠금: `tb_login_user.login_fail_cnt` 로 실패 횟수 관리. 비밀번호 변경주기: `password_change_dt`.

## 권한 (Authorization)
- 메뉴 단위 CRUD 권한. `tb_menu_auth`(권한) → `tb_menu_auth_detail`(메뉴별 read/create/update/delete) 로 판정. (`database.md`)
- 관리자(`root_yn='Y'`)는 전체 접근. 그 외는 권한 매핑에 따름.
- 민감 작업(권한 부여, 출입그룹 매핑, 설정 변경)은 서버에서 권한을 **재검증**한다(화면 제어만 믿지 않음).
- **사이드바(LNB)는 read 권한 있는 메뉴만 노출**한다(`MenuService.tree(actor)` — root 는 전체, 하위가 모두 걸러진 그룹은 숨김).
- **무권한 메뉴 URL 직접 접근** 시 메인 리다이렉트가 아니라 **403 + 권한없음 페이지**(`error/forbidden`)를 보여준다. (AJAX 는 `GlobalExceptionHandler` 가 403 JSON)
- **menu_id 는 서버가 URL 로 결정**한다(클라이언트가 보내지 않음 = 권한 우회 방지). `MenuAccessInterceptor` 가 요청 URI(=`tb_menu.menu_url`)를 역조회(`MenuService.resolveMenuId`)해 요청 스코프 `CurrentMenu` 에 넣고, 컨트롤러는 그 값을 권한·감사에 쓴다. 메뉴 번호 하드코딩 없음 → tb_menu 데이터만 바꾸면 됨.
- **메뉴 접속(MENU) 감사**는 인터셉터가 자동 기록한다(정상 200 페이지 GET). 데이터 조회/입력/수정/삭제/다운로드는 서비스가 각각 기록.

## 암호화 — ARIA (표준 구현 패턴, 불변식)
개인정보/비밀번호는 **ARIA-256** 으로 암호화하여 저장한다. 참조 구현: `visitor.security.ARIAEngine`, `ARIAUtil` (ROKA 프로젝트에서 이식).

- **대상 컬럼의 단일 출처는 `database.md` 의 `Enc=Y`** 컬럼 (현재: `tb_login_user.user_name`, `tb_login_user.password`).
- 저장/조회 표준 패턴:
  ```java
  // 저장 (Service 계층)
  user.setUserName(ARIAUtil.ariaEncrypt(user.getUserName()));
  // 조회 후 복호화
  String name = ARIAUtil.ariaDecrypt(row.getUserName());
  ```
- 암호문은 **hex 대문자 문자열**로 저장되므로 컬럼 길이는 평문의 여유분을 확보한다(설계 `nvarchar(255)`).
- 규칙:
  - 암호화/복호화는 **Service 계층에서만**. Controller/Mapper 에서 직접 호출 금지.
  - 비밀번호는 복호화 후 화면에 노출하지 않는다(검증 목적 비교만).
  - 🔴 **키 하드코딩 금지**: 참조 구현은 키가 소스에 상수로 박혀 있다. 이식 시 **키를 외부(Jasypt/환경변수)로 분리**한다.
- TODO: `biostar_pw` 등 추가 암호화 대상 여부 확정(설계 `Enc` 열 갱신 → `database.md`).

## 감사 이력 (불변식)
- **사용자의 메뉴 접속, 데이터 조회, 입력, 수정, 삭제**를 `tb_system_log` 에 **간략히** 남긴다. (스키마: `database.md`)
- 기록 항목: 누가(user_id/user_name) / 무엇을(action_type, `tb_common` cmm_id='AT') / 어디서(menu_id) / 언제(reg_dt) / 상세(action_detail).
- **action_type 코드(확정)**: `MENU`(메뉴접속) / `READ`(조회) / `CREATE`(등록) / `UPDATE`(수정) / `DELETE`(삭제) / `DOWNLOAD`(엑셀 등 반출, 목적=remark) / `LOGIN` / `LOGOUT`. seed: `sql/seed/02_seed.sql`, 상수: `AirPort.service.AuditService`.
- 목록 조회(READ)는 검색조건·결과 건수를 action_detail 에 남긴다.
- 공통 처리: `AuditService.log(actor, actionType, menuId, detail)` 를 Service 계층에서 호출.
- "조회"는 **개별 SELECT 단위가 아니라 메뉴 진입·검색 실행 단위**로 남긴다(과다 적재 방지).
- 외부 시스템(BiostarX 등)으로 나가는 제어도 감사 대상. (`integration.md`)
- 구현은 공통 처리(인터셉터/AOP)로 일원화 권장 — 화면마다 제각각 남기지 않는다.

## 관련 문서
[architecture.md](architecture.md) · [database.md](database.md) · [integration.md](integration.md) · [backend.md](backend.md)
