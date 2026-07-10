# TASKS — 작업 보드

> **사용법 (팀 공통)**
> 1. 할일이 생기면 누구든 **📋 백로그**에 한 줄 추가한다 (`- [ ] 제목 — 요청: 이름, 날짜, 메모`).
> 2. 작업을 시작할 때 그 줄을 **🔧 진행중**으로 옮기고 `담당`·`시작일`을 적는다. ← *이 순간부터 "누가 뭘 하는지"가 보인다*
> 3. 끝나면 **✅ 완료**로 옮기고 `완료일`·`커밋`을 적는다. 완료가 20건을 넘으면 오래된 것을 `docs/tasks-archive.md` 로 내린다.
> - 에이전트(Claude)도 이 보드를 따른다: 작업 시작/완료 시 보드를 갱신하고 커밋에 포함한다.
> - 상세 스펙이 필요한 항목은 백로그 줄에서 `docs/` 문서나 이슈 링크로 연결한다.

## 📋 백로그 (할일)
- [ ] 사용자등록(tb_login_user) CRUD 화면 — ARIA 암호화 실사용(성명/비밀번호), 골든 패턴 복제
- [ ] 권한메뉴관리(tb_menu_auth/detail) 화면 — 권한×메뉴 CRUD 매트릭스 UI
- [ ] 메뉴관리(tb_menu) 화면 — 트리(부모-자식) 관리
- [ ] 설정관리(tb_system) 화면 — 단일 행 수정형, biostar_pw 암호화 여부 결정(docs/database.md TODO)
- [ ] 감사추적(tb_system_log) 조회 화면 — 조회 전용, 기간/유형/사용자 검색 + 엑셀
- [ ] 출입권한매핑(tb_ac_group) 화면 + BiostarX adapter 착수 (docs/integration.md)
- [ ] ArchUnit 구조 테스트 도입 — 계층 단방향 강제 (docs/conventions.md §9)
- [ ] 미설계 도메인 테이블 설계 반영 — 임시/정규카드·기관·차량 (table.xlsx 갱신 시)
- [ ] 로그인 정책 구현 — 실패횟수 잠금(login_fail_cnt), 비밀번호 변경주기(password_change_dt)
- [ ] 운영 배포 준비 — 기동 스크립트/서비스 등록, 운영 ARIA 키 seed 재암호화 (docs/deployment.md TODO)

## 🔧 진행중
*(없음)*

## ✅ 완료
- [x] 하네스 구축(AGENTS/docs/skills/hooks/CI) 및 정합화 — 담당: yg-kwak, ~2026-07-08, `1592fe3` 외
- [x] 앱 스캐폴딩 + DB(DDL/seed) + 공통기반(인증/ARIA/감사/예외) — 담당: yg-kwak, 2026-07-09, `5747902`
- [x] 골든 샘플: 공통코드관리 CRUD — 담당: yg-kwak, 2026-07-09, `5747902`
- [x] Gradle 저장소 루트 이동(project/ 제거) + IntelliJ 실행 정리 — 담당: yg-kwak, 2026-07-10, `3207b97`
- [x] 목록 강화(정렬/페이지크기/검색조건/사용여부) + 레이아웃 — 담당: yg-kwak, 2026-07-10, `bc73d08` `419f2d9`
- [x] 메뉴 권한 기반 CRUD 통제(화면+서버 이중 방어, viewer 계정) — 담당: yg-kwak, 2026-07-10, `2698234`
- [x] 행 클릭 수정 + 공통 확인/입력 모달(components) — 담당: yg-kwak, 2026-07-10, `0d10e50` `a02b72d`
- [x] 엑셀 다운로드(목적→감사 remark, 전체 데이터) — 담당: yg-kwak, 2026-07-10, `a02b72d`
- [x] 조회 감사(READ 조건+건수) + LOGIN/LOGOUT + user_input 정정 — 담당: yg-kwak, 2026-07-10, `11b9ea3`
- [x] 규칙 대장(conventions.md) 전면 확장 + 자동 등록 장치 — 담당: yg-kwak, 2026-07-10, `c7d7e23`
