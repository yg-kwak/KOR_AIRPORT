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
- 로그인: `POST /api/login` (body: 로그인 정보) → 성공 시 응답 헤더 **`bs-session-id`** 수신, 응답 바디 `Response.code == "0"` 확인.
- 이후 모든 요청은 헤더 `bs-session-id: {세션}` 를 실어 보낸다.
- 세션은 어댑터에서 보관·유효성 검사 후 만료 시 재로그인(`ensureValidSession` 패턴). 세션ID/비밀번호는 로그에 남기지 않는다. (`security.md`)

## 주요 엔드포인트 (참조 구현 기준)
| 기능 | 메서드 · 경로 |
|------|---------------|
| 로그인(세션 발급) | `POST /api/login` |
| 사용자 그룹 검색 | `POST /api/v2/user_groups/search` |
| 출입그룹 검색 | `POST /api/v2/access_groups/search` |
| 장치 검색 | `POST /api/v2/devices/search` |
| 사용자 검색(고급) | `POST /api/v2/users/advance_search` |
| 사용자 조회/등록/수정/삭제 | `GET·POST·PUT·DELETE /api/users`, `/api/users/{id}` |
| 카드 발급 | `POST /api/cards` |
| 장치 카드 스캔 | `POST /api/devices/{deviceId}/scan_card` |
| 얼굴 크리덴셜 | `GET /api/devices/{deviceId}/credentials/face` |
| 출입 이벤트(이력) 검색 | `POST /api/events/search` |

> `v2` 와 비-`v2` 경로가 혼재한다(참조 구현 그대로). 이식 시 최신 문서 기준으로 확인·정리한다.

## 매핑
- 출입그룹: `tb_ac_group.biostar_ac_id`/`biostar_ac_name` ↔ BiostarX access group. (`database.md`)
- **출입권한관리 화면**(`/system/acGroup`): 최상위=tb_common(cmm_id='AR') 동기화(진입 시 insert/delete), 하위=`POST /api/v2/access_groups/search` 로 가져온 출입그룹(id/name)을 매핑 저장. 어댑터: `BiostarAdapter.searchAccessGroups`(로그인→세션→검색).
- TODO: 사용자/카드/얼굴 등 나머지 도메인 모델 ↔ BiostarX 모델 매핑 표.
- TODO: 실시간 이벤트 수신 방식(폴링 `events/search` vs 웹훅) 확정.

## 신뢰성
- 외부 장애 시 우리 시스템이 멈추지 않도록 경계 설정(타임아웃/서킷브레이커). TODO.
- 멱등성: 도어 제어/권한 부여 재시도 시 중복 방지. TODO.

## 관련 문서
[architecture.md](architecture.md) · [security.md](security.md) · [backend.md](backend.md)
