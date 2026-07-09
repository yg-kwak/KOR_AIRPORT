# 용어집 (출입/방문자 도메인)

> 코드·문서·커밋에서 같은 용어를 쓰기 위한 사전. `architecture.md` 의 실제 도메인 기준.
> 테이블 상세는 `database.md`.

## 도메인/화면
| 용어 | 의미 | 테이블 |
|------|------|--------|
| 임시카드관리(Visitor) | 방문신청 → 임시/장기 출입등록 | (설계 예정) |
| 정규카드관리 | 정규 출입카드 등록 | (설계 예정) |
| 사용자등록(LoginUser) | 로그인 계정·소속·근무지역/유형·권한·장치 | `tb_login_user` |
| 권한메뉴관리(MenuAuth) | 권한(그룹) 및 메뉴별 CRUD 권한 | `tb_menu_auth`, `tb_menu_auth_detail` |
| 코드등록관리(Common) | 공통 코드(코드구분+코드) | `tb_common` |
| 출입권한매핑관리(AcGroup) | 출입그룹 및 BiostarX 출입그룹 매핑 | `tb_ac_group` |
| 설정관리(System) | BiostarX 연동 정보 | `tb_system` |
| 기관관리(Company) | 출입기관·기관차량 등록 | (설계 예정) |
| 감사추적(Audit) | 메뉴 접속·조회·입력·수정·삭제 이력 | `tb_system_log` |
| 메뉴(Menu) | 메뉴 트리/URL/권한 대상 | `tb_menu` |

## 공통 개념
| 용어 | 의미 |
|------|------|
| 출입그룹(Access Group) | 출입 권한 묶음. BiostarX 출입그룹과 1:1 매핑 |
| 권한(Auth) | 메뉴별 읽기/생성/수정/삭제 권한 집합 |
| 공통코드 | `cmm_id`(구분) + `code_id`(코드). 예: LO=근무지역, AT=감사유형 |
| 이력/감사(Audit) | 민감 행위 기록(누가/무엇을/어디서/언제/상세) |
| 어댑터(adapter) | BiostarX 등 외부 연동을 격리하는 계층 |
| BiostarX | Suprema 사의 출입통제 플랫폼(외부 연동 대상) |
| ARIA | 국산 표준 대칭키 암호(성명/비밀번호 등 암호화). `security.md` |

## TODO
- TODO: 미설계 도메인(임시/정규카드, 기관, 차량) 용어·테이블 확정 시 갱신.
