# Suprema BiostarX 연동

> 사용자, 카드, 사용자그룹, 출입그룹, 로그인 등 연동 작업 시 읽는다. **외부 연동은 어댑터 계층으로만** (불변식).

## 원칙
- BiostarX SDK/API 호출은 전용 **adapter** 계층에 격리. Service 가 SDK 를 직접 부르지 않는다.
- BiostarX 접속정보는 `tb_system`(biostar_ip/id/pw)에서 읽는다. (`database.md`)
- 출입그룹은 `tb_ac_group.biostar_ac_id` 로 BiostarX 출입그룹과 매핑한다.
- 어댑터가 담당: 인증/세션, 요청·응답 매핑, 오류 변환, 재시도/타임아웃.

## API 레퍼런스
- 공식 문서: https://bs2api.biostar2.com (BioStar2 REST API)
- 참조 구현: ROKA `visitor.client.BioStarApiClient` (약 2000줄). CJAirPort 는 이를 `adapter` 계층으로 이식·정리한다.
- 베이스 URL: `tb_system.biostar_ip` 기반(스킴 없으면 `https://` 부여).

## 인증 / 세션
- 로그인: `POST /api/login` (body: 로그인 정보) → 성공 시 응답 헤더 **`bs-session-id`** 수신.
- 이후 모든 요청은 헤더 `bs-session-id: {세션}` 를 실어 보낸다.
- 세션은 **`adapter.BiostarSession`** 이 캐시·갱신한다(API 호출마다 로그인하지 않는다):
  - 캐시 세션이 없으면 로그인해 발급, IP/로그인ID 조합이 바뀌면 캐시 폐기 후 재로그인.
  - 인증 API 응답이 **HTTP 401 + `Response.code == "10"`("Login required.")** 면 세션 만료로 보고 재로그인 후 **1회 재시도**.
  - `BiostarAdapter` 는 `session.post(base, loginId, pw, path, body)` 로만 인증 호출한다(직접 로그인 금지).
- 세션ID/비밀번호는 로그에 남기지 않는다. (`security.md`)

## 로컬 개발(dev) 접속정보 시드
- BiostarX 연동정보는 **DB(`tb_system`)** 에서 읽는다 — properties 가 아니다. 그래서 dev 에서 tb_system 이 비면 연동 메뉴가 동작하지 않는다.
- 해결: `application-local.properties`(git-ignore, 커밋 금지)에 `app.biostar.ip/id/pw` 를 두면 **`config.BiostarLocalSeeder`(@Profile("local"))** 가 로컬 부팅 시 tb_system 에 upsert(비밀번호는 ARIA 암호화). `ip` 가 비면 시드하지 않는다.
- 운영 프로파일에는 이 시더가 로드되지 않는다 → tb_system 은 **설정관리 화면**으로만 관리. 템플릿: `application-local.properties.example`.
- **smoke-test 는 실제 자격증명을 담지 않는다**(더미로 엔드포인트 200 만 확인). 실제 기기 검증은 로컬 시드 후 화면에서 한다.

## 주요 엔드포인트 (참조 구현 기준)
| 기능 | 메서드 · 경로 |
|------|---------------|
| 로그인(세션 발급) | `POST /api/login` |
| 사용자 그룹 검색 | `POST /api/v2/user_groups/search` |
| 출입그룹 검색 | `POST /api/v2/access_groups/search` |
| 장치 검색 | `POST /api/v2/devices/search` |
| 사용자 검색(고급) | `POST /api/v2/users/advance_search` |
| 사용자 조회(존재 확인)/등록/수정/삭제 | `GET·POST·PUT·DELETE /api/users`, `GET /api/users/{id}` |
| 카드 발급 | `POST /api/cards` |
| 장치 카드 스캔 | `POST /api/devices/{deviceId}/scan_card` |
| 얼굴 크리덴셜 | `GET /api/devices/{deviceId}/credentials/face` |
| 출입 이벤트(이력) 검색 | `POST /api/events/search` |

> `v2` 와 비-`v2` 경로가 혼재한다(참조 구현 그대로). 이식 시 최신 문서 기준으로 확인·정리한다.

## 매핑
- 출입그룹: `tb_ac_group.biostar_ac_id`/`biostar_ac_name` ↔ BiostarX access group. (`database.md`)
- **정규인원 ↔ BiostarX 사용자**(`/person/person`): 인원(`tb_person`, `person_type='PT01'`) 등록 시 BiostarX 사용자를 생성한다. 어댑터: `BiostarUserAdapter`.
  - **얼굴(둘 중 하나)**: ①파일 업로드 `PUT /api/users/check/upload_picture` → 응답 `image`=사진, `image_template`/`image_template_2`=템플릿(9/5). ②장치 촬영 `GET /api/devices/{tb_login_user.dev_id}/credentials/face` → `template_ex_normalized_image` + `templates[]`. **브라우저가 BiostarX 를 직접 부를 수 없어 서버가 중계**한다.
  - **존재 확인 후 upsert(등록·수정 공통)**: 저장 전 `GET /api/users/{인원ID}` 로 확인해 **있으면 수정(PUT), 없으면 등록(POST)** 한다. 등록에서 들어와도 이미 있으면 덮어쓰고(비교 기준을 비워 전 항목 전송), 수정에서 들어왔는데 없으면 새로 만든다 — 우리 DB 와 BiostarX 가 어긋나 있어도 저장 한 번으로 맞춰진다. 확인 호출이 통신 오류로 실패하면 '없음'으로 보지만, 이어지는 등록도 같은 이유로 실패해 경고가 남는다.
  - **사용자 생성**: `POST /api/users` — `user_group_id`=`tb_company.biostar_group_id`, `disabled`=`tb_common`(PS).code_tag, `user_title`=`tb_common`(UT).code_name, `access_groups`=선택한 `tb_ac_group.biostar_ac_id` 목록, `credentials.visualFaces`=얼굴 3종. 사진/얼굴은 `tb_person_photo` 에도 저장.
  - **실패 정책(등록·수정 공통)**: **BiostarX 동기화가 성공해야 저장**한다 — 실패(설정 없음 / 소속 기관에 `biostar_group_id` 없음 / 장비 오류)면 트랜잭션을 롤백하고 사유를 예외로 알린다(장비엔 없고 DB엔 있는 유령 인원 방지 — **장비-DB 정합성 최우선**). 소속 기관에 그룹이 없으면 BiostarX 호출 전에 막고 "기관을 먼저 동기화하라"고 안내한다. 동기화는 전담 서비스 `PersonBiostarService.syncPersonToBiostar(form, before)` — 등록은 `before=empty`, 수정은 변경 전 스냅샷(VisitBiostarService 와 같은 역할 분리 패턴). 통신 오류는 '사용자 없음'과 구분해 실패로 처리(userExists 3상). 성공 시 `tb_person.biostar_user_id`=인원ID.
  - **수정**: `PUT /api/users/{인원ID}` — **변경된 항목만** 전송(델타). 있다가 없어진 값은 공란(문자열 `""`, 목록 `[]`), 얼굴 삭제는 `credentials.visualFaces=[]`. 변경이 없으면 호출하지 않는다.
  - **삭제**: `DELETE /api/users?id={인원ID}&group_id={기관 그룹ID}`. 우리 DB 는 소프트 삭제(`del_yn='Y'`). **삭제도 등록·수정과 동일하게 BiostarX 성공해야 커밋** — 실패면 롤백+사유 예외(장비 유령 사용자 방지), 실패 사실은 `AuditService.logAlways`(REQUIRES_NEW)로 롤백돼도 감사에 남긴다. 일괄삭제는 한 건 실패 시 전체 롤백.
  - **카드**(카드정보 탭): **카드 추가 확인 시 즉시** `POST /api/cards` 로 카드를 만들고(`CardCollection.rows[0]` 에 `card_type`={id:0,name:CSN,type:1,mode:C} + `card_id`/`display_card_id`=카드번호), 응답의 `id`→`tb_card.biostar_card_id`, `card_id`→`tb_card.biostar_card_value` 로 화면이 들고 있다가 **인원 저장 시** tb_card 저장 + 사용자 payload 의 `cards[]` 로 부여한다(`is_assigned=true`). 카드번호는 직접 입력하거나 `POST /api/devices/{dev_id}/scan_card`(본문 `{"noblockui":true}` → `Card.card_id`)로 장치에서 읽는다. 어댑터: `BiostarCardAdapter`.
    - **카드 종류는 CSN 고정** — 우리 `tb_common`(CDT) 카드종류는 업무 분류용이라 BiostarX 로 넘기지 않는다. 인원 화면이 발급하는 카드는 `CDT01`(인원) **서버 고정**(`CardService.CARD_TYPE_PERSON`), 패스구분(`tb_card.pass_type` → `tb_common` PT)은 화면에서 선택한다.
    - **주의(정책상 감수)**: 카드는 즉시 등록되므로 인원 저장을 취소하면 BiostarX 에만 남는다(우리 DB 미기록).
    - **회수·재사용**: 목록에서 제외한 카드는 삭제하지 않고 `person_id=NULL, use_yn='Y', del_yn='N'` 로 되돌린다(실물 카드라서). 같은 카드번호를 다시 추가하면(직접 입력·SCAN·**할당하기 팝업**) `POST /api/cards` 를 부르지 않고 그 행을 재사용하며, **이미 다른 인원에게 발급된 카드번호는 거부**한다(한 카드가 두 사람에게 붙는 것을 막는다). 사용자 쪽 회수는 수정 델타의 `cards[]` 가 처리한다.
- **기관차량 카드**(`/company/companyCar`): 차량 카드(카드구분 **차량 고정**·패스구분 미사용)는 **BiostarX 에 등록하지 않는다** — 차량은 BiostarX 사용자/카드 대상이 아니라 `tb_card` 에만 저장한다(`biostar_card_id`=NULL). 카드등록관리(`/card/card`)에서 카드구분을 차량(CDT02)으로 만들어도 동일하게 BiostarX 를 건너뛴다. 인원 카드(CDT01)만 `POST /api/cards` 로 장비에 올린다. 회수는 `tb_card.car_id=NULL`(삭제 아님).
- **기관 ↔ BiostarX 사용자그룹**(`/company/company`): BiostarX 의 **사용자 그룹**을 본 시스템에서는 **기관**으로 표현한다. 연결 ID 는 `tb_company.biostar_group_id`.
  - **부모 그룹 결정(코드 체인)**: `tb_common`(cmm_id='PT').code_tag → 발급구분 코드(예: `PTD01`) → `tb_common`(cmm_id='PTD', code_id='PTD01').**code_tag = BiostarX 부모 사용자그룹 ID**(예: 14227). 이 그룹 아래에 기관 그룹을 만들고 사용자를 넣는다.
  - **등록**: 모달에서 기존 그룹을 고르면 그 ID 를 저장(생성 API 미호출). 비우면 `POST /api/user_groups`(parent_id=PTD01 code_tag, depth 2, name=기관명)로 생성 후 반환 ID 저장. 응답에 ID 가 없으면 검색으로 보완 조회.
  - **이름 수정**: 기관명이 바뀌면 `PUT /api/user_groups/{groupId}`(본문 id 는 해당 그룹 자기 id).
  - **선택 팝업**: `POST /api/v2/user_groups/search` 결과에서 `parent_id.id == PTD01 code_tag` 인 그룹만 노출.
  - **실패 정책**: 연동 실패(예: 이름 중복 `code 65646` "User group name is duplicated.")여도 **기관 저장은 유지**하고 경고 메시지로 알린다. 그룹 미연동(`biostar_group_id`=NULL) 상태로 **수정 저장하면 "그룹을 선택해 다시 저장하라"는 안내 경고**를 돌려준다(자동 생성은 하지 않음 — 인원 등록이 차단되는 막다른 길 안내). 어댑터: `BiostarAdapter.searchUserGroups/createUserGroup/updateUserGroupName`.
- **사용자관리 장치ID**(`/system/loginUser`): 장치ID(`dev_id`) 선택 팝업 — `POST /api/v2/devices/search`(`feature_types=[card]`)로 장치(`DeviceCollection.rows[].{id,name}`) 조회 후 선택한 `id` 를 `tb_login_user.dev_id` 에 저장. 어댑터: `BiostarAdapter.searchDevices`. 클라이언트에서 장치ID/장치명으로 필터.
- **출입권한관리 화면**(`/security/acGroup`): 최상위=tb_common(cmm_id='AR') 동기화(진입 시 insert/delete), 하위=`POST /api/v2/access_groups/search` 로 가져온 출입그룹(id/name)을 매핑 저장. 어댑터: `BiostarAdapter.searchAccessGroups`(로그인→세션→검색).
- **방문(임시·장기) ↔ BiostarX**(`tb_visit`): 정규(`tb_company` 기반)와 달리 **기관 사용자그룹을 만들지 않는다**. 방문 인원(`tb_person`, `person_type`=임시/장기)의 `user_group_id` 는 `PT`→`PTD` 체인의 **임시/장기 부모 그룹(code_tag) 아래로 직접** 편입한다(중간 기관 그룹 없음). 방문 카드는 별도 테이블 없이 정규와 **동일한 `tb_card`**(`person_id`/`car_id`, `pass_type`=방문유형)로 발급하며, 인원 카드(CDT01)만 `POST /api/cards`, 차량 카드는 BiostarX 미등록(정규 규칙과 동일).
  - **공통구역 materialize(승인 시)**: `tb_visit_ac_group`(최상위 출입그룹)을 **하위 BiostarX 매핑그룹(`biostar_ac_id`)으로 확장**해 각 방문 인원의 `tb_person_ac_group` 에 기록 → `POST/PUT /api/users` 의 `access_groups` 로 정규와 동일하게 전송. 차량은 `tb_visit_car_ac_group`(CAR)을 각 차량 `tb_car_ac_group` 으로 복제(BiostarX 미전송). `work_start_dt/work_end_dt` 는 인원 `access_start_dt/access_end_dt` 로 전파.
  - **구역 선택 범위**: 방문유형(`tb_common` cmm_id='PT')의 **`code_remark='Y'` 면 하위 세부 트리까지 선택 가능**, 아니면 **최상위 그룹만**(선택 팝업이 `parent_ac_group_id IS NULL` 만 노출 + 저장 시 재검증). 예: 임시=최상위만, 장기·상주=세부까지.
  - **퇴실·방문삭제 실패 정책**: 퇴실(사용자 disable+카드 제거)·방문삭제(사용자 삭제)는 **BiostarX 성공해야 커밋** — 실패면 롤백+사유 예외 후 재시도 유도(실패해도 DB만 회수하면 장비에서 계속 출입 + 카드 재대여로 **이중 사용**이 되기 때문). 실패는 `logAlways` 로 감사에 남는다. 방문 **저장**(syncVisitors)만 경고 유지(장비에 없으면 출입 불가라 안전한 방향 + 재저장 upsert 로 자가치유).
  - **입실중(VS03) 카드 규칙**: 카드 **교환만 허용** — 카드 회수(빈 카드)나 방문객 제외는 수정으로 불가, 퇴실 처리로만 가능(카드 없는 입실중 상태 방지).
- TODO: 사용자/카드/얼굴 등 나머지 도메인 모델 ↔ BiostarX 모델 매핑 표.
- TODO: 실시간 이벤트 수신 방식(폴링 `events/search` vs 웹훅) 확정.

## 신뢰성
- 외부 장애 시 우리 시스템이 멈추지 않도록 경계 설정(타임아웃/서킷브레이커). TODO.
- 멱등성: 도어 제어/권한 부여 재시도 시 중복 방지. TODO.

## 카드 차단/해제 (블랙리스트)

- 카드 상태(tb_common CS)의 `code_tag`로 BiostarX 블랙리스트를 동기화한다. **`code_tag='Y'`면 차단, 아니면 해제.** (CS01 정상=N, CS02 분실·CS03 반납·CS04 정지·CS05 회수=Y)
- 차단: `POST /api/cards/blacklist` `{"Blacklist":{"card_id":{"id":"<biostar_card_id>"}}}` / 해제: `DELETE /api/cards/blacklist?id=<biostar_card_id>`. (`BiostarCardAdapter.blacklistCard`/`removeBlacklist`)
- 호출 시점: 카드 상태가 저장되는 곳 — 카드관리 수정(`CardService.updateCard`)과 인원 저장의 카드 반영(`saveCards`). id 는 `tb_card.biostar_card_id`(장비 미등록 카드는 동기화 생략).
- **차단 여부가 바뀔 때만 호출**한다(변경 전 상태와 비교. 신규 카드는 장비 기본이 비차단이라 '비차단'으로 간주). BiostarX 블랙리스트 API 는 **멱등하지 않아** 이미 해제된 카드를 다시 해제하면 `HTTP 500` 을 돌려주므로, 상태 변화가 없는 저장에서는 아예 호출하지 않는다.
- **실패 정책은 비대칭**: **차단(block) 실패 = 예외로 저장 롤백**(분실 카드가 장비에서 계속 유효하면 보안 위험), **해제(unblock) 실패 = 경고만, 저장 유지**(실패해도 카드가 계속 차단될 뿐이라 보안 위험이 아니고, '이미 해제됨'이 오류로 오는 경우가 많다). 어느 쪽이든 실패는 `logAlways` 로 `tb_system_log` 에 남는다.

## 카드 프린트 (adapter/cardprint)

- 디자인 export(card_project) JSON을 템플릿으로 **앞/뒤 카드 이미지를 서버에서 렌더(PNG)** 하고, **실제 인쇄는 클라이언트 브라우저**가 한다(카드 프린터가 관리자 PC 에 USB/LAN 연결). 서버 인쇄(Java `PrinterJob`)는 드라이버 여백을 못 없애고 래스터화 아티팩트(붉은 점)가 생겨, 참조 카드SW 와 동일하게 **브라우저 인쇄**로 전환했다.
- 계층: `CardPrintAdapter`(템플릿 로드만) ← `CardPrintRenderer`(한 면 렌더+PNG data URL) ← `CardPrintService`(인원·카드·얼굴 매핑, 미리보기/일괄 이미지 반환+감사).
- 좌표계가 둘: **위치**는 디자인 캔버스 px(폭 `card-print.canvas-width`, 기본 540 → 배경 해상도로 스케일), **폰트 크기는 pt**라 배경 실효 DPI(=배경폭 px / 54mm)로 pt→px 변환한다. 텍스트 y 는 **baseline** 기준. 템플릿 폰트(Arial 등)가 한글을 못 그리면 `Malgun Gothic` 폴백.
- 텍스트 `{컬럼}` 바인딩 매핑: `{이름}`=성명, `{회사명}`=기관명, `{구역}`=권한의 최상위 구역번호(ar_code 숫자, 오름차순 연결 예 `12`), `{발급번호}`=`발급번호 : `+카드명칭, `{발급일}`=오늘. 텍스트는 y=상단 기준 상단정렬.
- **브라우저 인쇄 방식**: 프론트(`card-print.js`)가 미리보기 PNG 들을 body 직속 `#cardPrintArea` 에 `.print-card-page > .print-card-img` 로 넣고 `window.print()`. CSS `@media print { @page{margin:0} .print-card-img{width:100%;height:100%;object-fit:fill} body>*:not(#cardPrintArea){display:none} }` 로 **여백 없이 카드에 꽉 차게**, 이미지가 이미 PNG라 붉은 점 없음. 앞/뒤는 `page-break-after:always` 로 2페이지(프린터 드라이버가 양면 처리). 풀블리드는 **드라이버의 over-the-edge 설정**으로 맞춘다.
- 엔드포인트: `POST /person/person/card/print/preview`(단건 이미지), `/card/print`(단건 감사만), `/card/print/bulk/check`(대상 검증), `/card/print/bulk`(대상 전원 이미지 반환+감사). 인쇄 자체는 서버가 하지 않는다.
- **얼굴(tb_person_photo)·카드가 모두 등록된 인원만** 출력(서버 검증 + 화면 게이트).
- 설정: `card-print.project-file`(템플릿 경로, `card-templates/`는 대용량이라 저장소 제외), `card-print.canvas-width`(요소 좌표계 기준폭). 프린터명·오프셋·배율 등 서버 인쇄 설정은 브라우저 인쇄 전환으로 제거됨.
- 화면: 정규인원 수정 모달 카드정보 탭 관리 열의 **출력** 버튼 → 미리보기(앞/뒤) 후 인쇄. (`/person/person/card/print/preview`, `/card/print`)
- **일괄 출력**: 목록에서 인원 선택 → **카드 출력**(선택 삭제 왼쪽). 전량 검증(카드 1장·얼굴 보유) 후 각자 출력. 카드 2장 이상 보유자가 있으면 인원ID를 알리고 아무것도 출력하지 않는다. (`/card/print/bulk`)

## 관련 문서
[architecture.md](architecture.md) · [security.md](security.md) · [backend.md](backend.md)
