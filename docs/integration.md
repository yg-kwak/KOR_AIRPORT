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
- 세션은 **`adapter.biostar.BiostarSession`** 이 캐시·갱신한다(API 호출마다 로그인하지 않는다):
  - 캐시 세션이 없으면 로그인해 발급, IP/로그인ID 조합이 바뀌면 캐시 폐기 후 재로그인.
  - 인증 API 응답이 **HTTP 401 + `Response.code == "10"`("Login required.")** 면 세션 만료로 보고 재로그인 후 **1회 재시도**.
  - `BiostarAdapter` 는 `session.post(base, loginId, pw, path, body)` 로만 인증 호출한다(직접 로그인 금지).
- 세션ID/비밀번호는 로그에 남기지 않는다. (`security.md`)

## TLS — 인증서를 검증하지 않는다

BiostarX 는 설치할 때 만들어진 self-signed 인증서를 쓴다. 내부망 전용이므로 신뢰를 완화한다(`BiostarSession`).

풀어야 할 것이 **두 가지**다. 하나만 풀면 아래 오류로 막힌다.

| 무엇 | 안 풀면 |
|------|---------|
| 인증서 신뢰 (`X509TrustManager`) | `PKIX path building failed` |
| **호스트명 검증** | `No subject alternative names matching IP address ... found` |

호스트명 검증은 TrustManager 와 **별개**이고, `HttpClient` 는 이것을 항상 켠다.
`SSLParameters.setEndpointIdentificationAlgorithm(null)` 은 `HttpClient` 가 다시 덮어써서 듣지 않는다.
유일하게 듣는 것은 시스템 속성이다.

```java
System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
```

`HttpClient` 가 처음 만들어지기 전에 정해져야 해서 `BiostarSession` 의 static 블록에서 건다.
앱에서 `HttpClient` 를 쓰는 곳은 BiostarX 연동뿐이다.

## 정규인원 가져오기 (BiostarX → 우리 DB)

현장은 **장비에 이미 정규 사용자가 있다.** 그 상태에서 우리 화면으로 등록하면 `updateUser` 로 가서
**이미 올라간 얼굴·카드·출입그룹을 덮어쓴다.** 그래서 맞추는 방향은 하나뿐이다 — 장비를 읽어 DB 를 채운다.

설정관리 → **BiostarX 가져오기**. `PersonImportBiostarService`(대상 선별) · `PersonImportSyncService`(1명 반영) · `BiostarImportAdapter`(읽기 전용).

**대상은 전부 받아 온다.** `POST /api/v2/users/search` 를 `total` 만큼 `offset` 을 옮겨 가며 부른다(한 쪽 500명). 예전에는 `limit:1000, offset:0` 으로 **한 쪽만** 받아, 장비에 4000명이 넘는 현장에서 뒤쪽이 통째로 보이지 않았다(2026-08-19 현장 보고).

**카드·얼굴 보유는 목록 응답에 함께 온다.** `card_count` · `visual_face_count` · `face_count` 를 그대로 읽으므로 **인원마다 상세를 부르지 않는다**(4000명이면 4000번이 된다). 화면의 [보유] 필터로 "얼굴 없음"만 골라 부분적으로 가져올 수 있다.

**목록은 100명씩 쪽을 넘긴다.** 수천 행을 한 번에 그리면 브라우저가 멈춘다. 검색·선택은 거른 전체를 기준으로 하므로 쪽을 넘겨도 선택은 유지된다.

**고른 사람만 가져온다.** [대상 불러오기] → 목록에서 선택 → [미리보기] → [가져오기]. 이미 등록된 인원까지 장비 기준으로 덮어쓰므로, 무엇이 바뀌는지 모르고 전체를 돌리면 되돌릴 수 없다. 선택이 비어 있으면 서버가 거부한다.

| 읽는 곳 | 쓰는 곳 |
|---------|---------|
| `POST /api/v2/users/search` (목록) · `GET /api/users/{id}` (사진·카드·출입그룹) | 장비에 **쓰지 않는다** |

대상 선별 — 걸러진 인원은 사유와 함께 화면에 나온다.

- **범위**: 발급구분 정규등록(`tb_common` PTD/PTD01)의 `code_tag` 사용자그룹과 **그 아래 모든 하위 그룹**에
  속한 인원만. 장비에는 임시·장기 사용자도 함께 있어 전체를 끌어오면 정규가 아닌 사람이 섞인다.
  하위 그룹은 부모-자식을 따라 깊이 제한 없이 넓힌다.
  **범위 밖은 집계에도 넣지 않는다** — 세거나 사유를 남기면 "35명 중 27명 건너뜀"처럼 보여
  마치 문제가 있는 것처럼 읽힌다. 애초에 우리 대상이 아니다.
  ⚠️ 화면 트리용 `BiostarAdapter.searchUserGroups` 는 펼쳐진 일부만 주므로, 가져오기는 평면 전체 목록
  (`BiostarImportAdapter.searchUserGroups`)을 따로 읽는다.
- **기관**: 장비 사용자그룹 ID 가 `tb_company.biostar_group_id` 에 있어야 한다. 없으면 건너뛴다
  (임의로 기관을 만들면 나중에 정리가 더 어렵다).
- **출입그룹**: `tb_ac_group.biostar_ac_id` 에 매핑된 것만 가져온다.

### 이미 등록된 인원 — 장비 기준으로 맞춘다 (`PersonImportSyncService`)

`tb_person.person_id` == 장비 `user_id` 로 짝지어 비교하고, **다를 때만** 손댄다(같으면 `변경없음` 으로 집계).

| 대상 | 규칙 |
|---|---|
| 인원정보 | 성명·연락처·기관·직위·유효기간만 갱신(`updateFromBiostar`). **생년월일·신원조회·보안교육·인원상태는 건드리지 않는다** — 장비에 없는 개념이라 일반 `update` 로 쓰면 함께 비워진다 |
| 카드 | **장비 기준 덮어쓰기.** 전부 회수(`person_id=NULL`)한 뒤 장비 카드만 다시 배정 — 우리 쪽에만 있던 카드는 떨어져 나간다(삭제가 아니라 미배정이라 다른 사람이 다시 쓴다) |
| 출입권한 | **장비 기준 덮어쓰기.** 매핑된 것만 남기고 나머지는 지운다 |
| 얼굴 | 값을 **비교하지 않는다**(사진은 바이너리라 같은 사람이라도 값이 다르다). 있고 없음만 본다: 장비에 없으면 우리도 삭제 · 둘 다 있으면 그대로 · 장비에만 있으면 가져옴 |

⚠️ **유효기간 비교는 분까지만** 한다. 조회(`selectById`)는 화면 표시에 맞춰 `varchar(16)` 으로 읽어 초가 없고 장비는 초까지 주므로, 문자열을 그대로 맞대면 같은 시각인데도 늘 다르다고 나와 **아무리 가져와도 매번 "갱신"** 이 된다.

카드·얼굴·출입권한은 화면에서 **항목별로 골라** 가져온다(체크 해제하면 그 항목은 비교도 반영도 하지 않는다).
가져온 인원은 `biostar_user_id` 를 채워 '연동 완료'로 표시하므로, 이후 수정은 `update` 경로를 탄다(중복 생성 없음).

신규는 장비에 없는 값(생년월일·신원조회·보안교육)을 비워 두고 상태를 `신규(01)` 로 넣는다.
직위는 장비 `user_title` 과 공통코드(UT) **이름이 같을 때만** 맞춘다.

**먼저 매핑을 끝내라** — 기관과 출입그룹이 맞지 않으면 아무도 안 들어오거나 권한이 비어 들어온다.
**[미리보기]는 실행과 같은 코드를 탄다**(`dryRun`) — 사람별로 무엇이 바뀌는지 그대로 보여 주고 DB 는 건드리지 않는다. 덮어쓰기라 "미리보기엔 없던 일" 이 생기면 안 된다.

결과는 사람을 **신규 / 갱신 / 변경없음** 세 갈래로 나눠 `newUserIds`·`updatedUserIds`·`unchangedUserIds` 로 준다. 건수만으로는 "갱신 3명" 이 누구인지 알 수 없어서다 — 화면은 이 목록으로 목록의 **구분 열과 필터**를 갱신해 대상자를 바로 짚게 한다. 분류는 **사람 단위로 한 갈래에만** 넣는다. 항목별 분기에서 세면 카드만 바뀐 사람이 신규도 갱신도 변경없음도 아닌 채 집계에서 사라진다(실제로 그랬다).

### 대상 목록 (`GET /system/system/import/candidates`)
사용자ID·성명·기관·등록여부만 준다 — **상세는 읽지 않는다.** 카드·출입그룹은 1명당 1회 왕복이라, 목록을 그리자고 인원 수만큼 호출하면 화면이 열리지 않는다. 무엇이 달라지는지는 고른 뒤 미리보기가 알려 준다.

## 접속 주소 — `IP[:포트]`

설정관리에 넣는 값은 호스트 또는 `호스트:포트`다. 스킴을 붙이지 않으면 `https://` 를 앞에 붙인다.

```
<서버IP>:9443          ->  https://<서버IP>:9443
127.0.0.1:9443        ->  같은 서버에 함께 설치한 경우 권장(방화벽 무관)
```

연결 테스트가 실패하면 화면 문구가 무엇을 고쳐야 하는지 알려준다. 자세한 내용(요청 URL·HTTP 상태·응답 앞부분)은 로그에 남는다.

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
  - **얼굴(둘 중 하나)**: ①파일 업로드·웹캠 `PUT /api/users/check/upload_picture` → 응답 `image`=정규화 사진, **`image_template`=bin_type 5, `image_template_2`=bin_type 9**(장치 촬영과 순서 반대 — 뒤바꿔 보내면 인증이 안 된다). 응답 템플릿은 **고정 버퍼라 뒤가 널(0x00)로 채워져** 오므로 그 꼬리를 잘라 표준 base64(`==` 패딩)로 만들어 `template_ex` 에 넣는다(`BiostarUserAdapter.normalizeTemplate`). JSON 의 `\/` 이스케이프는 파싱 단계에서 `/` 로 풀린다. ②장치 촬영 `GET /api/devices/{tb_login_user.dev_id}/credentials/face` → `template_ex_normalized_image` + `templates[]`(여기는 `credential_bin_type` 으로 직접 매칭). **브라우저가 BiostarX 를 직접 부를 수 없어 서버가 중계**한다.
  - **존재 확인 후 upsert(등록·수정 공통)**: 저장 전 `GET /api/users/{인원ID}` 로 확인해 **있으면 수정(PUT), 없으면 등록(POST)** 한다. 등록에서 들어와도 이미 있으면 덮어쓰고(비교 기준을 비워 전 항목 전송), 수정에서 들어왔는데 없으면 새로 만든다 — 우리 DB 와 BiostarX 가 어긋나 있어도 저장 한 번으로 맞춰진다. 확인 호출이 통신 오류로 실패하면 '없음'으로 보지만, 이어지는 등록도 같은 이유로 실패해 경고가 남는다.
  - **얼굴 템플릿은 정확히 544바이트**(헤더 32 + 데이터 512, bin_type 5·9 동일). `upload_picture` 응답은 그보다 긴 고정 버퍼라 뒤에 잔여 데이터가 딸려 온다 — **널 꼬리 제거만으로는 부족하다**(버퍼 끝이 널이 아닌 값 `08 4f 3f 5f f6 7f` 로 끝나는 경우가 있어 550바이트가 그대로 전송됐고, 그 사용자는 장치 인증에 실패했다). `BiostarUserAdapter.normalizeTemplate` 이 **길이로 자른다**.
    검증 방법: BiostarX 화면으로 등록한 사용자와 우리가 등록한 사용자를 `GET /api/users/{id}` 로 비교하면 `template_ex` 길이가 같아야 한다.
  - **사진(photo) ↔ 인증용 얼굴(visualFaces) 은 다른 이미지다**: `photo` 에는 **원본 사진**(사용자 사진·카드 출력용), `credentials.visualFaces[0].template_ex_normalized_image` 에는 **upload_picture 가 돌려준 `image`(정규화 얼굴)** 를 넣는다. 원본을 인증용 자리에 넣으면 함께 보내는 `template_ex`(9/5)와 짝이 맞지 않아 **장치 인증이 실패**한다. 장치 촬영(`credentials/face`)은 정규화 이미지만 주므로 그것을 사진으로도 쓴다.
  - **장치ID(dev_id)는 세션이 아니라 DB 에서 읽는다** — 세션의 로그인 사용자는 로그인 시점 스냅샷이라, 사용자관리에서 장치를 바꿔도 재로그인 전에는 옛 장치로 스캔·촬영이 나갔다(`CardService.currentDevId`).
  - **방문객 제거 = 장비 사용자 삭제**: 방문 저장 시 폼에서 빠진 방문객은 `VisitRosterService` 가 `deleteVisitors` 로 BiostarX 사용자도 지운다. 빠뜨리면 **DB 에는 없고 장비에만 남아 계속 출입이 가능**하다. 실패하면 방문 삭제와 같은 정책으로 저장 전체를 롤백하고 사유를 알린다(감사에 기록).
  - **한 실물 카드는 한 사람에게만**: 같은 저장 요청 안에서 같은 `cardId` 를 두 방문객에게 주면 거부한다(tb_card.person_id 는 단일 컬럼이라 뒤에 배정한 사람만 남고 앞사람은 조용히 카드를 잃는다). 화면도 카드 선택 팝업에서 이미 고른 카드를 감춘다.
  - **오류 사유 노출 규칙**: BiostarX 는 **4xx/5xx 응답 본문에도 사유(`Response.message`/`code`)를 담아 보낸다**. `BiostarAdapter.responseError` 는 상태코드보다 본문 사유를 먼저 쓰고, 사유가 없을 때만 상태코드별 안내로 대체하며 원문 앞부분을 warn 로깅한다(상태코드만 노출하면 화면에 "HTTP 400" 만 남아 원인을 알 수 없다).
  - **얼굴 변환 실패 처리**: 사진은 서버(`PersonFaceService`)가 먼저 검증한다 — base64 유효성, 4MB 이하, **선두 바이트로 JPG/PNG 판정**(이름만 .jpg 인 HEIC 차단). 장비 응답이 200 이어도 **템플릿 2종이 모두 비면 실패**로 처리한다(통과시키면 얼굴 없는 사용자로 저장된다). 실패 메시지에는 사진 요건 안내를 함께 붙인다.
  - **사용자 생성**: `POST /api/users` — `user_group_id`=`tb_company.biostar_group_id`, `disabled`=`tb_common`(PS).code_tag, `user_title`=`tb_common`(UT).code_name, `access_groups`=선택한 `tb_ac_group.biostar_ac_id` 목록, `credentials.visualFaces`=얼굴 3종. 사진/얼굴은 `tb_person_photo` 에도 저장.
  - **실패 정책(등록·수정 공통)**: **BiostarX 동기화가 성공해야 저장**한다 — 실패(설정 없음 / 소속 기관에 `biostar_group_id` 없음 / 장비 오류)면 트랜잭션을 롤백하고 사유를 예외로 알린다(장비엔 없고 DB엔 있는 유령 인원 방지 — **장비-DB 정합성 최우선**). 소속 기관에 그룹이 없으면 BiostarX 호출 전에 막고 "기관을 먼저 동기화하라"고 안내한다. 동기화는 전담 서비스 `PersonBiostarService.syncPersonToBiostar(form, before)` — 등록은 `before=empty`, 수정은 변경 전 스냅샷(VisitBiostarService 와 같은 역할 분리 패턴). 통신 오류는 '사용자 없음'과 구분해 실패로 처리(userExists 3상). 성공 시 `tb_person.biostar_user_id`=인원ID.
  - **비활성 상태면 얼굴도 함께 지운다**: 상태(`tb_common` PS)의 `code_tag='true'`(정지·퇴사·회수·분실)로 저장하면 `disabled=true` 와 함께 **사진·인증얼굴·템플릿을 비워** 보낸다(`credentials.visualFaces=[]`). 출입만 막고 생체정보를 장비에 남겨 두면 상태를 되돌리는 순간 예전 얼굴로 문이 열린다.
    **우리 DB(`tb_person_photo`)도 같은 판정으로 지운다** — 한쪽만 지우면 다음 저장에서 되살아난다. 판정은 `PersonBiostarService.isDisabled` 한 곳에 있고 장비·DB 양쪽이 그것을 쓴다.
    되돌릴 수 없으므로 **화면이 저장 전에 확인을 받는다**(어떤 상태가 비활성인지는 서버가 `PAGE_DISABLED_STATUS` 로 내려준다 — 화면에 코드를 박지 않는다). 상태를 되돌리면 얼굴은 **다시 등록해야 한다**.
  - **수정**: `PUT /api/users/{인원ID}` — **변경된 항목만** 전송(델타). 있다가 없어진 값은 공란(문자열 `""`, 목록 `[]`), 얼굴 삭제는 `credentials.visualFaces=[]`. 변경이 없으면 호출하지 않는다.
  - **삭제**: `DELETE /api/users?id={인원ID}&group_id={기관 그룹ID}`. 우리 DB 는 소프트 삭제(`del_yn='Y'`). **삭제도 등록·수정과 동일하게 BiostarX 성공해야 커밋** — 실패면 롤백+사유 예외(장비 유령 사용자 방지), 실패 사실은 `AuditService.logAlways`(REQUIRES_NEW)로 롤백돼도 감사에 남긴다. 일괄삭제는 한 건 실패 시 전체 롤백.
  - **카드**(카드정보 탭): **카드 추가 확인 시 즉시** `POST /api/cards` 로 카드를 만들고(`CardCollection.rows[0]` 에 `card_type`={id:0,name:CSN,type:1,mode:C} + `card_id`/`display_card_id`=카드번호), 응답의 `id`→`tb_card.biostar_card_id`, `card_id`→`tb_card.biostar_card_value` 로 화면이 들고 있다가 **인원 저장 시** tb_card 저장 + 사용자 payload 의 `cards[]` 로 부여한다(`is_assigned=true`). 카드번호는 직접 입력하거나 `POST /api/devices/{dev_id}/scan_card`(본문 `{"noblockui":true}` → `Card.card_id`)로 장치에서 읽는다. 어댑터: `BiostarCardAdapter`.
    - **카드 종류는 CSN 고정** — 우리 `tb_common`(CDT) 카드종류는 업무 분류용이라 BiostarX 로 넘기지 않는다. 인원 화면이 발급하는 카드는 `CDT01`(인원) **서버 고정**(`CardService.CARD_TYPE_PERSON`), 패스구분(`tb_card.pass_type` → `tb_common` PT)은 화면에서 선택한다.
    - **주의(정책상 감수)**: 카드는 즉시 등록되므로 인원 저장을 취소하면 BiostarX 에만 남는다(우리 DB 미기록).
    - **카드 존재 확인 후 upsert(자가치유)**: 부여 시점에 `CardService.ensureBiostarCard` 가 카드가 장비에 **실제로 있는지 확인**하고(`GET /api/cards` 목록 — 이 장비는 카드 **단건 조회·검색을 지원하지 않는다**: `GET /api/cards/{id}`·`POST /api/v2/cards/search` 모두 code 103), 없으면 `POST /api/cards` 로 (재)등록해 `biostar_card_id` 를 채운다. 두 경우를 모두 복구한다 — ①DB 에 id 가 없는 카드(시드·과거 실패), ②**관리자가 BiostarX 에서 카드를 지운 경우**(stale id → 새 id 로 재등록). 조회 실패(통신·세션)는 '있는 것처럼' 진행하지 않고 예외로 올린다. 실패는 모두 예외 → 저장 롤백 + 사유 안내. 차량 카드(CDT02)는 대상 아님. 적용: 정규인원 저장(`saveCards`)·방문 저장(`VisitRosterService.saveChildren`). 사용자(User)는 `userExists` 로 같은 자가치유가 이미 동작한다.
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
  - **동기화 실패면 상태를 올리지 않는다**: 전원 카드를 발급해도 BiostarX 등록이 실패하면 `신청(VS01)` 으로 되돌린다.
    상태만 '입실 중'이 되면 **카드가 문을 열지 못하는데도 처리가 끝난 것처럼 보인다.** 저장 자체는 유지하고(장비에 부분 생성된
    사용자를 유령으로 남기지 않기 위해) 사유를 화면과 감사에 남긴다 — 원인을 고친 뒤 다시 저장하면 재시도된다.
  - **실패한 요청은 본문이 함께 남는다**: 응답만으로는 무엇이 잘못됐는지 알 수 없어, HTTP 400 이상이면
    보낸 본문을 로그에 적는다(`BiostarSession`). 성명·사진·연락처는 가리고 진단에 필요한 구조만 남긴다.
    성공한 요청은 남기지 않는다(양이 많고 개인정보다). 401 도 제외 — 세션 만료는 재로그인 후 다시 보낸다.

    ```
    WARN BiostarSession - BiostarX 요청 실패 — POST /api/users 본문: {"User":{"name":"***",
      "photo":"***","phone":"***","user_id":"IS000046","user_group_id":{"id":"1004"},
      "access_groups":[{"id":1},{"id":3}],"cards":[{"card_id":"1111114"}]}}
    WARN BiostarAdapter - BiostarX 응답 HTTP 400 — 본문: {"Response":{"code":"65717", ...
    ```

    위 예에서 범인은 `user_group_id: 1004` 다 — 그 그룹이 장비에 없다.

  - **자주 겪는 실패**: `code 65717 "not defined"` 는 payload 가 가리키는 대상이 장비에 없다는 뜻이다. 대부분
    **발급구분(PTD) `code_tag` 의 사용자그룹 ID** 가 그 BiostarX 에 없어서다(시드값 1003·1004·1005 는 예시다).
    설정관리 화면에서 실제 그룹 ID 로 바꾼다. 바로 앞줄의 `code 201 "User can not be found with id"` 는
    신규 방문객 존재 확인이라 정상이다.
  - **카드 보유 상태(입실중 VS03·미반납 VS05) 카드 규칙**: 카드 **교환만 허용** — 카드 회수(빈 카드)나 방문객 제외는 수정으로 불가, 퇴실 처리로만 가능(카드 없는 입실중 상태 방지).
  - **인증 모드는 카드 전용**: 방문객(임시·장기·상주·순찰·대여)은 **얼굴을 등록하지 않는다.** 장비/사용자그룹 기본 모드가 얼굴을 요구하면 카드를 대도 문이 열리지 않으므로, 사용자 payload 에 개인 인증 모드를 함께 보낸다 — `private_operation_modes: [{index:0, user_id:{인원ID}, operation_method:1, operation_mode:21}]`(21 = 카드만, `BiostarUserAdapter.OPERATION_MODE_CARD_ONLY`). 등록(POST)·수정(PUT) 모두 실린다.
    **정규인원(PT01)에는 보내지 않는다** — 얼굴+카드로 인증하므로 장비/사용자그룹 설정을 그대로 따라야 한다. 붙이면 그 설정을 덮어써 얼굴 인증이 막힌다. 경계는 서비스로 갈린다: 방문객은 전부 `VisitBiostarService` 를 지나고 정규는 `PersonBiostarService` 만 지난다. 퇴실(disable)은 이 값을 건드리지 않는다(before/after 가 같아 델타에 안 실린다).
- TODO: 사용자/카드/얼굴 등 나머지 도메인 모델 ↔ BiostarX 모델 매핑 표.
- TODO: 실시간 이벤트 수신 방식(폴링 `events/search` vs 웹훅) 확정.

## 실시간 이벤트 (모니터링 → 실시간 이벤트, `/monitor/event`)
장비에서 인증이 일어나는 순간을 화면에 띄운다. 흐름은 **소켓 연결 → 이벤트 시작 → MESSAGE 수신** 이다.

- **소켓**: `wss://{IP}/wsapi`. 어댑터: `BiostarEventSocket`. 재연결마다 **세션을 새로 받는다** — 만료된 세션으로 다시 여는 것이 가장 흔한 실패다.
- **세션은 헤더가 아니라 소켓 본문으로 알린다** — 연결 직후 첫 메시지로 `bs-session-id={세션}` 을 **평문으로** 보내고, 장비가 `{"Response":{"code":"0"}}` 로 답하면 그 소켓이 세션에 묶인다. BiostarX 자체 화면이 그렇게 한다(브라우저는 WebSocket 핸드셰이크에 임의 헤더를 못 붙이므로 다른 방법이 없다).
  **이 한 줄을 빼면 아무 증상이 없다** — 핸드셰이크도 통과하고 `events/start` 도 `code 0` 을 주는데 이벤트만 한 건도 오지 않는다. 화면에는 "연결됨"으로 보인다.
- **순서가 중요하다 — 응답을 받고 나서 `events/start` 를 부른다.** 보내자마자 이어서 부르면 장비가 아직 소켓을 세션에 붙이지 않은 상태에서 시작 요청을 받는다. 요청은 `code 0` 을 주고 이벤트는 오지 않는다. 증상이 없어 가장 찾기 어려운 실패라, 응답(최대 10초)을 기다린 뒤에 시작한다.
- **시작**: `POST /api/events/start` — **소켓을 연 뒤** 같은 세션으로 불러야 MESSAGE 가 흐른다. 어댑터: `BiostarEventAdapter`.
- **인증 사진**: `GET /api/events/images/{image_id.image_data}` → `ImageLog.data`(base64). 사진이 없어도 실패로 다루지 않는다(등록 사진만 보여 준다).
- **브라우저는 이 소켓을 직접 열 수 없다** — BiostarX 인증서는 self-signed 이고 세션은 서버만 갖고 있다. 서버가 소켓 **하나**를 열어 받고 화면에는 **SSE**(`/monitor/event/stream`)로 다시 민다. 화면이 여럿 열려도 장비 연결은 하나이며, 마지막 화면이 닫히면 소켓도 닫는다.
- **표기 대상과 문구는 서버가 정한다**(`BiostarAuthEvent.resultLabel`). 장비는 문 열림·장치 연결까지 같은 소켓으로 흘려보내므로, **표에 있는 코드만** 화면에 올린다. 표에 없어도 이름이 `VERIFY_SUCCESS_*`/`IDENTIFY_SUCCESS_*` 로 시작하면 통과로 잡는다 — 표에 없는 인증 수단 조합이 현장에서 쓰이면 그 사람만 화면에서 사라지기 때문이다.

  | 코드 | BiostarX 이벤트 | 화면 표기 | 색 |
  |------|----------------|-----------|-----|
  | 4102 | 카드 | O 인증 성공 | 초록 `#3cb371` |
  | 4106 | 카드 + 얼굴 | O 인증 성공 | 초록 |
  | 4867 | 얼굴 | O 인증 성공 | 초록 |
  | 6401 | 출입거부 — 잘못된 출입그룹 | X 출입제한구역 | 빨강 `#dc143c` |
  | 6405 | 출입거부 — 하드 안티패스백 | X 안티 패스 | 빨강 |

  **출입거부도 화면에 올린다** — 못 들어간 사람이야말로 봐야 한다. 거부면 초록이던 곳(패널 머리·결과 상자·지난 인증 테두리)이 통째로 붉어진다. 멀리 있는 모니터에서 색만 보고 판단하기 때문이다.
- **사진 없는 칸에 무엇을 세울지는 인원 구분이 정한다** — 서버가 `faceUser`(정규인원인가)를 내려주고 화면이 그림을 고른다.

  | 인원 | 사진 없을 때 | 뜻 |
  |---|---|---|
  | 정규(PT01) | **사람 모양**(회색) | 얼굴이 있어야 정상인데 **빠졌다** — 등록이 필요하다 |
  | 임시·장기·상주·순찰·대여 | **카드 모양**(보라) | 카드로만 인증해 장비가 얼굴을 안 찍는다 — **원래 없는 것이 정상** |

  둘을 같은 그림으로 두면 "등록이 빠진 사람" 과 "원래 없는 사람" 이 구분되지 않는다. 우리 DB 에 없는 사용자(미등록 인증)는 구분을 알 수 없어 카드 모양이다. 빈칸으로 두지 않는 이유는 그대로다 — 칸이 무너지면 옆 사진이 밀려 누구 것인지 헷갈린다.
- **MAIN 의 등록 사진·인증 사진은 같은 높이(260px)로 세운다** — 두 장이 나란히 있어 크기가 다르면 눈에 거슬린다. 다만 **칸을 꽉 채우지는 않는다**:
  - 실제 사진이 작다. 등록 사진은 `224x224`(BiostarX 정규화 얼굴) 또는 `141x187` 정도인데, 칸 가로(약 390px)에 맞춰 늘리면 **1.7~1.9배 확대돼 뭉개진다**.
  - `object-fit: cover` 로 채우면 세로 사진의 **위아래(머리·턱)가 잘린다**.
  - 그래서 `contain` + 높이 260px 이다. 확대는 1.2배 안쪽(224 기준)이고 잘리지도 비율이 일그러지지도 않는다. 남는 자리는 회색 바탕이 채워 액자처럼 보인다.
  - 지난 인증 띠(`.monitor-card-photos`)는 별도 규칙(120px)이라 이 값과 무관하다.
- **SSE 전송은 emitter 마다 잠근다** — `SseEmitter` 는 동시 전송을 막아 주지 않는데 이벤트 스레드와 연결유지(ping) 스레드가 같은 화면에 쓴다. 겹치면 프레임이 끼어들어 JSON 이 깨지고 **그 이벤트가 조용히 사라진다**(사진이 실린 큰 프레임일수록 잘 겹친다).
- **사진이 안 보일 때**: `MonitorService` 가 인증 한 건마다 남기는 로그로 어디서 끊겼는지 바로 안다.
  `인증 VERIFY_SUCCESS_CARD_FACE(4106) 인원=400001 사진ID=... 인증사진=11628자 등록사진=없음`
  `사진ID=없음` 이면 장비가 이벤트에 안 실어 보낸 것, `인증사진=없음` 이면 장비가 사진을 안 준 것, `등록사진=없음` 이면 `tb_person_photo` 에 없는 것이다(정규인원 가져오기에서 **얼굴**을 함께 가져와야 채워진다).
- **거르기**: 화면에서 고른 장치(`device_id.id`)의 이벤트만 올린다. 장치를 고르기 전에는 아무 것도 흐르지 않는다.
- **붙이는 값**: `user_id` 로 우리 DB 를 찾아 성명(`tb_person`, 복호화)·소속·허가구역·등록사진(`tb_person_photo`). 장비에 있고 우리 DB 에 없는 사람도 화면에는 띄운다 — 누가 지나갔는지가 정보다.
  - **소속**: `tb_person.affiliation`(방문객 자유입력)이 있으면 그 값, 없으면 `tb_company.company_name`. 방문객은 기관에 매이지 않고 소속을 직접 적으므로 그 값이 정확하다.
  - **허가구역**: 인원 구분에 따라 출처가 다르다 — 정규(PT01)는 사람에게 붙은 `tb_person_ac_group`, **그 밖(임시·장기·상주·순찰·대여)은 방문 단위인 `tb_visit_ac_group`**(가장 최근 방문). 방문객은 `tb_person_ac_group` 이 비어 있어(materialize 미구현) 거기만 보면 허가구역이 늘 공란이다.
    매핑된 그룹은 **하위 노드일 수 있으므로 반드시 최상위 구역으로 올려 중복을 없앤다.** 그대로 세면 "인원구역2 / 인원구역2 안쪽 / 2안쪽 A" 가 2 를 세 번 찍어 `12345` 가 `2122345` 가 된다. 하위는 부모의 `ar_code` 를 물려받으므로 그것으로 최상위(`parent_ac_group_id IS NULL`)를 찾는다(재귀 불필요). 그 뒤 이름에서 번호만 이어 붙인다(예 "12345").
- **감사**: 구독 시작에 한 번만 READ 로 남긴다. 이벤트마다 남기면 인증 한 번에 한 줄씩 쌓여 감사추적이 장비 로그가 된다.
- **브라우저가 스트림을 끊는 것은 정상이다** — 화면을 하루 종일 켜 두면 한산한 시간대에 몇 분마다 끊겼다 붙는다(EventSource 가 스스로 재연결한다). 우리 서버가 BiostarX 로 연 소켓은 그대로라 장비 쪽은 아무 영향이 없다.
  그때 서버가 그 스트림에 연결유지 신호를 쓰면 `CloseNowException` 이 난다. **이것을 500 오류로 다루면 안 된다** — 서버 잘못이 아니고, 게다가 응답이 이미 나간 뒤라 오류 본문(JSON)을 실을 수도 없다(`Content-Type` 이 `text/event-stream`). `GlobalExceptionHandler` 가 끊김을 알아보고 INFO 한 줄만 남긴 뒤 본문 없이 끝낸다.

## 신뢰성
- 외부 장애 시 우리 시스템이 멈추지 않도록 경계 설정(타임아웃/서킷브레이커). TODO.
- 멱등성: 도어 제어/권한 부여 재시도 시 중복 방지. TODO.

## 카드 차단/해제 (블랙리스트)

- 카드 상태(tb_common CS)의 `code_tag`로 BiostarX 블랙리스트를 동기화한다. **`code_tag='Y'`면 차단, 아니면 해제.** (CS01 정상=N, CS02 분실·CS03 반납·CS04 정지·CS05 폐기=Y)
- 🔴 **`code_tag` 가 비어 있으면 차단도 발급 제한도 조용히 무력화된다** — `isBlocked()` 가 항상 false 가 되어 분실 카드를 차단 없이 통과시킨다. 과거 시드가 이 컬럼을 채우지 않아 실제로 발생했다. seed·설치 스크립트가 값을 넣고, 설치 스크립트 보정 절이 기존 DB 도 복구한다.
- **장비 미등록 인원카드를 차단하려 하면 거부**한다(예외). 예전에는 `biostar_card_id` 가 없으면 조용히 넘어가 "수정되었습니다"만 뜨고 실제로는 아무 일도 일어나지 않았다 — 사용자는 막았다고 믿는데 그 카드가 나중에 장비에 등록되면 차단 없이 유효해진다. 차량 카드는 애초에 장비 대상이 아니므로 그대로 건너뛴다.
- **차단 상태(= 정상 아님) 카드는 새로 발급할 수 없다** — 검사가 **두 겹**이다. ①`requireIssuable`: 이미 저장된 카드의 상태를 본다. ②`requireIssuableStatus`: **지금 저장하려는 상태**를 본다 — 발급 화면이 카드상태를 직접 고를 수 있어, 새 카드번호를 처음부터 '분실'로 만들면 ①은 검사할 행이 없어 통과한다. 정규인원 카드탭·방문객·방문차량·기관차량 **모든 발급 경로**에 걸리고, 카드 선택 팝업(`selectUnassigned`)에서도 목록에서 빠진다.
  - 카드등록관리(마스터)에는 ②를 걸지 않는다 — 거기서는 분실·폐기 카드를 **기록**해야 한다.
- **카드구분은 교차 배정하지 않는다** — 차량 카드(CDT02)를 사람에게, 인원 카드(CDT01)를 차량에 줄 수 없다(`requireIssuableToPerson`/`ToCar`). 저장이 카드구분을 덮어쓰기 때문에, 막지 않으면 차량 카드가 조용히 인원 카드로 바뀌고 그 카드가 차량에 물려 있었다면 **차량에서 카드를 빼앗는다**. 목록(선택 팝업)도 화면별 cardType 으로 걸러 준다. 판정은 블랙리스트와 **같은 `code_tag` 기준**이라 상태를 추가해도 코드만 맞추면 함께 적용된다.
  - 이미 그 대상이 들고 있던 카드는 검사하지 않는다 — 분실 신고된 카드를 둔 채 다른 항목만 고치는 저장까지 막으면 정정이 불가능해진다.
  - 회수(`person_id=NULL`)는 상태를 바꾸지 않으므로, **정상 카드의 회수→재사용은 종전대로** 된다. 반납·폐기로 표시한 카드는 카드등록관리에서 '정상'으로 되돌려야 다시 나간다.
- 차단: `POST /api/cards/blacklist` `{"Blacklist":{"card_id":{"id":"<biostar_card_id>"}}}` / 해제: `DELETE /api/cards/blacklist?id=<biostar_card_id>`. (`BiostarCardAdapter.blacklistCard`/`removeBlacklist`)
- 호출 시점: 카드 상태가 저장되는 곳 — 카드관리 수정(`CardService.updateCard`)과 인원 저장의 카드 반영(`saveCards`). id 는 `tb_card.biostar_card_id`(장비 미등록 카드는 동기화 생략).
- **차단 여부가 바뀔 때만 호출**한다(변경 전 상태와 비교. 신규 카드는 장비 기본이 비차단이라 '비차단'으로 간주). BiostarX 블랙리스트 API 는 **멱등하지 않아** 이미 해제된 카드를 다시 해제하면 `HTTP 500` 을 돌려주므로, 상태 변화가 없는 저장에서는 아예 호출하지 않는다.
- **실패 정책은 비대칭**: **차단(block) 실패 = 예외로 저장 롤백**(분실 카드가 장비에서 계속 유효하면 보안 위험), **해제(unblock) 실패 = 경고만, 저장 유지**(실패해도 카드가 계속 차단될 뿐이라 보안 위험이 아니고, '이미 해제됨'이 오류로 오는 경우가 많다). 어느 쪽이든 실패는 `logAlways` 로 `tb_system_log` 에 남는다.

## 연동 로그 — 한 줄로 전부 켠다

외부와 주고받은 **요청·응답 본문**은 `DEBUG` 로 남긴다. 연동마다 켜는 방법이 다르면 현장에서 "주차는 보이는데 장비는 안 보인다"가 되므로 **스위치는 하나**다.

```properties
logging.level.AirPort=DEBUG
```

| 어디서 | 무엇이 남나 |
|---|---|
| `BiostarSession` | 모든 BiostarX 호출(등록·수정·삭제·조회)의 메서드·경로·본문·HTTP 상태·소요시간 |
| `AmanoParkingAdapter` | 아마노에 보낸 정기권 등록·삭제 본문과 받은 응답·소요시간 |
| `ParkingEventApiController` | 주차서버가 보낸 입·출차 이벤트와 우리가 돌려준 응답 |

- 각 줄에 `[요청ID]` 가 붙어 **한 건을 처음부터 끝까지 이어 볼 수 있다**.
- **성명·비밀번호는 `***` 로 가린다.** 성명은 DB 에서 ARIA 로 암호화하는 항목이라(AGENTS §4), 로그에 평문으로 쌓이면 암호화가 무의미해진다. `Authorization` 헤더와 `bs-session-id` 는 아예 찍지 않는다(그대로 쓰면 남의 세션으로 장비를 조작할 수 있다).
- **긴 값은 잘라 낸다** — 얼굴 사진(BASE64)이나 수천 건짜리 목록이 로그 파일을 삼키지 않도록 1500자에서 끊고 총 길이를 덧붙인다.
- 요청은 **보내기 전에** 남긴다. 응답이 오지 않아도(타임아웃) 무엇을 보냈는지는 남아야 한다.
- 평소에는 꺼 둔다 — 실시간 이벤트 화면을 켜 두면 특히 빠르게 쌓인다.

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

## 주차 차단기 (아마노 주차관제, adapter/parking)

차량에 **차량구역**(`tb_common` cmm_id=`CAR`)을 부여하면, 그 구역 차단기가 자동으로 열리도록 아마노 주차관제에 **정기권**을 등록한다. 차량이 붙는 화면이 둘이라 `ParkingPassService` 한 곳에 모았다 — 구역→종별 매핑과 회수 규칙이 갈리면 두 화면의 차단기 동작이 달라진다.

| 화면 | 구역 출처 | 정기권 종료일 |
|---|---|---|
| 방문(임시·장기·상주) | `tb_visit_car_ac_group`(방문 단위) | `tb_visit.work_end_dt` |
| 기관차량등록 | `tb_car_ac_group`(차량 단위) | **`20371231`** — 상주라 끝나는 날이 없다 |

### 규격
- `POST http://{host}:9948/interop/{resource}.do` (https 는 9938). **POST 전용**, `application/json` **UTF-8**.
- 인증: **HTTP Basic** — `Authorization: Basic base64(userId:userPw)`.
- 쓰는 리소스: `setCustdefInfo.do`(정기권 등록) · `deleteCustdefInfo.do`(정기권 삭제) · `getCustdefList.do`(조회, 점검용).
- **조회는 차량번호로 걸러지지 않는다.** `getCustdefList.do` 에 `carNo`·`searchWord`·`custCarNo`·`keyword` 를 넣어도 무시되고 전체(수천 건)가 온다(2026-08-19 실증). 그래서 "이미 등록됐는지" 를 조회로 미리 확인하지 않는다.
- **현장에서 요청·응답 보기**: 아래 §연동 로그 참고 — 연동별로 따로 켜지 않는다.
- **성공 판정은 HTTP 상태가 아니라 본문 `data.success`** 다. 거부도 `HTTP 200` 으로 돌아온다. 사유는 `data.errorMessage`(한글 UTF-8 — 응답에 charset 이 없어 명시적으로 UTF-8 로 읽는다).

### 실증으로 확인한 제약 (2026-08-13, 시험서버)
- **차량 1대 = 정기권 1건 = 종별 1개.** 같은 `(lotAreaNo, carNo)` 로 종별만 바꿔 다시 등록하면 `"[정기차량 등록] 이미 등록된 차량 (…)"` 으로 **거부**된다.
- 그래서 **등록을 먼저 던지고, `"이미 등록된"` 으로 거부될 때만 삭제 후 재등록**한다(`AmanoParkingAdapter.register`). 신규 차량은 호출 한 번으로 끝난다 — 예전처럼 항상 지우고 등록하면 없는 차를 지우는 호출이 아마노에 계속 쌓인다. 다른 사유의 거부에는 삭제를 보내지 않는다(멀쩡한 정기권이 사라진다).
- **회수 전에 남은 주체를 본다.** 정기권은 차량번호 하나에 한 장뿐인데 같은 차가 여러 방문에 걸릴 수 있다. 지우기 전에 그 차를 아직 쓰는 기관차량·방문이 있는지 보고, 있으면 지우지 않고 **그쪽 기준으로 되돌린다**(`TbCarMapper.selectParkingCarByNo` · `TbVisitMapper.selectParkingVisitsByCarNo`). 기관차량을 먼저 보는 이유는 종료일이 2037-12-31 로 어떤 방문보다 길기 때문이다.
### 구역 → 종별 (현재 설정)
- **차단기가 달린 구역만 `code_tag` 를 갖는다.** 단말기가 한 대라 지금은 **차량구역2(`CAR02` → `passType2`)뿐**이고, 나머지 구역은 주차와 무관해 비어 있다.
- 그래서 고른 구역 중 **종별이 있는 것**을 찾는다. 정렬해 맨 앞을 집으면 `차량구역1+2` 를 고른 순간 태그 없는 `CAR01` 이 잡혀 **등록이 통째로 빠진다**(회귀 테스트로 고정: `종별이_없는_구역을_함께_골라도_차단기가_붙은_구역으로_등록한다`).
- 종별이 비어 있는 것은 정상이라 DEBUG 로만 남긴다. 값이 있는데 1~8 밖이면 설정 실수라 WARN.
- **미결**: 단말기가 늘어 **종별이 붙은 구역을 2군데 이상** 고르게 되면 어떻게 보낼지. API 로는 표현할 수 없어 아마노에 **조합 종별**(`passType3`~`passType8`)을 신설하는 협의가 필요하다(구역 3개의 조합은 7가지라 종별 8개 안에 들어간다). **잠정 동작**: 종별이 있는 구역 중 가장 앞선 하나로만 등록하고 나머지는 WARN 로그로 남긴다 — `ParkingPassService.passType`. 종별이 하나뿐인 지금은 이 분기를 타지 않는다.

### 필드 매핑 (등록)
| 아마노 | 값 |
|---|---|
| `lotAreaNo` | 설정 `app.parking.lot-area-no` (청주공항 20) |
| `carNo` | `tb_car.car_no` — **공백 제거**(규격이 "공백없이 전체번호") |
| `userName` | `tb_car.car_name` |
| `passType` | 차량구역 `tb_common(CAR).code_tag` 의 숫자 → `passType{n}` (1~8 밖이면 등록하지 않는다). **종별이 붙은 구역만 차단기가 있다** — 현재 단말기가 한 대라 `CAR02` 뿐 |
| `startDate` | 등록 당일 `yyyyMMdd` |
| `endDate` | 방문은 `tb_visit.work_end_dt` 의 `yyyyMMdd`, 기관차량은 `20371231` |
| `dongCode`·`hoCode`·`remark`·`tel`·`mobile`·`carModel` | 공란 (공동주택용) |
| `groupNo` | 0 · `siteID` 0 · `noAlarm` false · `isVIP` false · `iTendatedOverlapped` 0 |

삭제 본문은 `{lotAreaNo, carNo}` **뿐**이다(차량번호 단위 — 구역별 삭제가 없다).

### 반영 시점 (ParkingPassService)
- **방문 저장**(`VisitRosterService.saveChildren` → `syncVisit`): 차량을 재구성하기 **전에** 기존 차량번호를 읽어 두고, 저장 후 ① 빠진 차량은 정기권 삭제 ② 남은 차량은 삭제 후 재등록.
- **방문 삭제·정리**(`clearRoster` → `removeAll`): 그 방문 차량의 정기권을 전부 회수한다. 안 지우면 방문이 없어져도 그 차는 계속 들어온다.
- **기관차량 등록·수정**(`CompanyCarService.create/update` → `syncCar`): 저장 후 재등록. **차량번호를 고치면 옛 번호의 정기권을 회수**한다 — 안 지우면 예전 번호가 계속 차단기를 연다.
- **기관차량 삭제**(`CompanyCarService.delete` → `removeAll`): 정기권 회수.
- **차량구역을 모두 해제하면 정기권을 지운다**(구역 없음 = 주차 권한 없음). 안 지우면 화면에서는 권한을 뺐는데 차단기만 계속 열린다.
- **실패해도 방문 저장을 롤백하지 않는다** — 차단기는 부가 기능이라, 주차관제가 죽었을 때 방문 등록 자체가 멈추면 안 된다. 대신 사유를 화면 경고 + `logAlways`(tb_system_log)로 남긴다. (BiostarX 사용자 동기화는 반대로 롤백 — 그쪽은 출입 자체가 걸린다.)
- 방문 종료일이 없으면 **등록하지 않는다**(무기한 개방 방지).

### 입·출차 이벤트 수신 (주차서버 → 우리, `POST /api/InOutCar`)

**방향이 반대인 유일한 연동이다.** 정기권은 우리가 아마노를 호출하지만, 입·출차는 **주차서버가 우리를 호출한다**. 아마노는 조회 API 를 주지 않고(문서상 "지원되지 않음"), 대신 파트너사가 `http://{도메인명}/api/InOutCar` 를 제공하면 밀어 주는 방식이다.

- **경로는 저쪽 규격**이라 우리 화면 규약(`/{영역}/{stem}` ↔ `tb_menu.menu_url`)의 예외다. `code-lint [4]` 와 `WebConfig` 가 `/api/**` 를 제외한다.
- **인증이 없다** — 세션도 토큰도 없는 요청이다. 보내는 쪽 IP 로 막는다: `app.parking.event.allow-ips`(콤마 구분). **비우면 모두 허용**이니 운영에서는 반드시 채운다.
- **응답은 항상 200.** 못 알아들은 요청(eventType 없음·시각 형식 오류)도, 저장에 실패해도 200 이다. 500 을 주면 주차서버가 같은 건을 계속 재전송해 저쪽 큐가 막힌다. 사유는 우리 로그에 남긴다.
- **중복 수신은 정상이다.** 응답을 못 받으면 주차서버가 다시 보낸다. `(event_type, car_no, event_dt)` 유일키 + `INSERT … WHERE NOT EXISTS` 로 한 줄만 남긴다.

| eventType | 뜻 |
|---|---|
| `EnteredCar` / `ExitedCar` | 입·출차 (차단기 자동 열림) |
| `EnteredCarNotOpen` / `ExitedCarNotOpen` | 인식했으나 **차단기 안 열림**(수동입차 포함) — "왜 못 들어갔나" 를 찾는 단서 |
| `EnteredRearCar` / `ExitedRearCar` | 후면 인식 (기본 제공 아님, 기본 정보만 온다) |

주요 필드: `carNumber`(미인식은 **`No_Detection`**, 부분인식은 `X` 포함) · `eventTime`/`inDtm`(`yyyyMMddHHmmss`) · `passType`(`passType1`~`8`/`normal`/`visitor`) · `iID`(**-1 이면 출입권한 없는 차량**) · `isCustDef`(정기차량 여부). 문서가 "필드는 추가될 수 있다"고 명시해 **모르는 키는 무시**하고 원문을 `raw_json` 에 보관한다.

화면은 **차량관리 → 주차 조회**(menu 602, `/carInfo/parkingEvent`) — 기간·입출구분·미개방·차량번호로 조회하고, 출차 건은 함께 온 입차 시각을 같이 보여 준다. 우리 DB 에 등록된 차량이면 차량명·기관을 붙인다.

### 설정 (`application.properties`, 비밀값은 환경변수로만)
`app.parking.enabled`(기본 **false** — 개발·시험이 현장 주차장을 건드리지 않게) · `app.parking.base-url` · `app.parking.user` · `app.parking.password` · `app.parking.lot-area-no` · `app.parking.event.allow-ips`(수신 허용 IP). 각각 `PARKING_ENABLED`/`PARKING_URL`/`PARKING_USER`/`PARKING_PASSWORD`/`PARKING_LOT_AREA_NO`/`PARKING_EVENT_ALLOW_IPS` 로 주입한다. **수신(`/api/InOutCar`)은 `enabled` 와 무관하게 항상 열려 있다** — 저쪽이 보내는 것을 우리가 켜고 끄는 값이 아니다. (`security.md`, `deployment.md`)

## 관련 문서
[architecture.md](architecture.md) · [security.md](security.md) · [backend.md](backend.md)
