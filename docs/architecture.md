# 아키텍처

> 전체 구조와 계층 경계의 진실 원천. 불변식 요약은 `AGENTS.md §4`.

## 시스템 개요
공항 출입관리·방문자 통제 시스템. 웹 백엔드(Spring Boot + eGovFrame)가 도메인/데이터를
담당하고, shadcn 프론트가 화면을 담당하며, Suprema BiostarX 와 연동해 실제 도어를 제어한다.

```
[shadcn 프론트(web/)] → REST → [Spring Boot + eGovFrame(project/)]
                                     │
                          Controller → Service → Mapper(MyBatis)
                                     │                    │
                          [BiostarX 어댑터]          [MSSQL / MariaDB]
                                     │
                             [Suprema BiostarX]
```

## 계층 경계 (불변식)
- **Controller**: 요청/응답 매핑, 검증, 인증 컨텍스트. 비즈니스 로직·SQL 금지.
- **Service**: 도메인 로직, 트랜잭션 경계. 외부 SDK 직접 호출 금지(어댑터 경유).
- **Mapper(MyBatis)**: SQL 전담. XML 에만 SQL.
- **Adapter**: BiostarX 등 외부 연동 격리. 실패/재시도/매핑 담당.
- 의존 방향은 **단방향**: Controller → Service → Mapper/Adapter.

## 주요 도메인
- 출입 권한(Access): 게이트/도어 권한 부여·회수.
- 방문자(Visitor): 사전등록 → 승인 → 발급 → 회수.
- 이력(Audit): 권한/도어 제어 전 과정 기록. (`security.md`)

## TODO (채워야 할 결정)
- TODO: 인증 방식(세션/JWT/SSO)과 eGov 보안 모듈 사용 범위.
- TODO: 프론트↔백 API 규격(REST 경로/버전) 정의 위치.
- TODO: 트랜잭션 전파 정책, 멀티 DB(MSSQL/MariaDB) 역할 분담.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [biostar-integration.md](biostar-integration.md) · [security.md](security.md)
