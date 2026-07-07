# 데이터베이스 · MyBatis

> SQL/mapper 작업의 진실 원천. DB: MSSQL, MariaDB. JPA 미사용.

## MyBatis 규약 (불변식)
- 모든 SQL 은 **mapper XML** 에. Java 문자열로 SQL 을 조립하지 않는다.
- 파라미터 바인딩은 `#{}` (PreparedStatement). `${}` 는 컬럼/테이블명 등 불가피한 경우만, 입력 검증 후.
- 동적 SQL 은 `<if>/<choose>/<foreach>` 사용.
- resultMap 명시. `select *` 지양.

## 멀티 DB (MSSQL / MariaDB)
- 두 DB 의 역할 분담을 명확히. TODO: 어느 데이터가 어느 DB 인가.
- 방언 차이(페이징, 시퀀스/IDENTITY, 날짜함수) 주의. TODO: DB별 분기 전략.
- DataSource/트랜잭션 매니저 구성. TODO.

## 네이밍
- 테이블/컬럼: TODO(대문자 스네이크 vs 소문자).
- Mapper id: `{domain}.{action}` 관례. TODO 확정.

## 마이그레이션
- TODO: 스키마 관리 방식(Flyway/수기 DDL/eGov).

## 관련 문서
[backend.md](backend.md) · [architecture.md](architecture.md) · [conventions.md](conventions.md)
