# Suprema BiostarX 연동

> 사용자, 카드, 사용자그룹, 출입그룹, 로그인 등 연동 작업 시 읽는다. **외부 연동은 어댑터 계층으로만** (불변식).

## 원칙
- BiostarX SDK/API 호출은 전용 **adapter** 계층에 격리. Service 가 SDK 를 직접 부르지 않는다.
- BiostarX 접속정보는 `tb_system`(biostar_ip/id/pw)에서 읽는다. (`database.md`)
- 출입그룹은 `tb_ac_group.biostar_ac_id` 로 BiostarX 출입그룹과 매핑한다.
- 어댑터가 담당: 인증/세션, 요청·응답 매핑, 오류 변환, 재시도/타임아웃.

## 연동 범위 (TODO 채우기)
- TODO: BiostarX 버전/엔드포인트, 인증 방식(API key/세션).
- TODO: 동기화 대상 — 사용자/카드/권한그룹/도어/이벤트.
- TODO: 실시간 이벤트 수신 방식(웹훅/폴링).
- TODO: 우리 도메인 모델 ↔ BiostarX 모델 매핑 표.

## 신뢰성
- 외부 장애 시 우리 시스템이 멈추지 않도록 경계 설정(타임아웃/서킷브레이커). TODO.
- 멱등성: 도어 제어/권한 부여 재시도 시 중복 방지. TODO.

## 관련 문서
[architecture.md](architecture.md) · [security.md](security.md) · [backend.md](backend.md)
