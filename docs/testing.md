# 테스트

> 테스트 작성·실행 시 읽는다. 커밋 전 최소 검증은 `/commit` 이 수행.

## 실행
- 전체: `gradlew.bat test`.
- 단위 범위: 변경한 도메인의 테스트만 선택 실행.

## 전략
- **Service** 로직: 단위 테스트 우선. (`@Service` 구체 클래스이므로 협력 객체는 목으로 주입)
- **Mapper**: MSSQL 대상 통합 테스트. 단일 벤더이므로 벤더별 이중화 검증은 불필요. TODO: 테스트 DB 방식(전용 스키마/로컬 MSSQL).
- **adapter(BiostarX)**: 외부는 목/스텁으로 격리. 실장비 테스트는 별도 표시.
- **암호화(ARIA)**: `ariaEncrypt`↔`ariaDecrypt` 왕복, 암호문이 평문 미노출인지 검증. (`security.md`)
- **감사**: 민감 경로(입력/수정/삭제/조회 진입)에서 `tb_system_log` 적재를 검증. (`security.md`)
- **순수 로직 단위 테스트(구현됨)**: `MenuServiceTest`(menu_url→menu_id 경로경계 매칭), `MenuNodeTest`(트리 조립). DB/Spring 없이 빠르게 회귀 검증 — smoke(부팅비용) 보완. 순수 함수/서비스는 여기부터 추가.
- **계층 경계·네이밍(구현됨)**: `src/test/java/AirPort/ArchitectureTest.java`(ArchUnit) — controller→service→mapper 단방향, model 순수성, `*Controller/*Service/*Mapper` 네이밍을 `gradlew test` 에서 강제. 위반 메시지의 because 절이 교정 방향을 알려준다.
- **E2E 스모크**: `scripts/smoke-test.sh` — 앱 부팅 후 로그인/CRUD/권한(403)/사이드바필터/메뉴접속감사/엑셀 등 **187개 체크**. 시드 권한(auth_id≤2)은 건드리지 않고 잔여 테스트데이터를 매 실행 정리(격리). 기능 작업 후 커밋 전 실행.


## 심화 테스트 (`scripts/deep-test.sh`)

스모크가 "엔드포인트가 도는가"를 보는 빠른 관문이라면, 심화는 **"넣은 값이 그대로 살아남는가, 권한대로 막히는가"** 를 본다.
앱 응답만 믿지 않고 **DB 원본(sqlcmd)** 과 **BiostarX(REST)** 를 직접 열어 대조한다 — 저장과 조회가 나란히 틀린 경우는 앱 응답만으로는 잡히지 않는다.

```bash
scripts/deep-test.sh                 # 전체 (앱이 떠 있어야 한다 — 부팅은 하지 않는다)
scripts/deep-test.sh 조회 권한        # 지정 절만 (조회|문자셋|삭제|권한)
SKIP_BIOSTAR=1 scripts/deep-test.sh  # 장비 쓰기 구간 건너뛰기
```

| 절 | 무엇을 보는가 |
|----|--------------|
| §1 조회 | 페이징·정렬(화이트리스트 밖 포함)·부분일치·0건·LIKE 와일드카드·SQL 주입·복합조건·기간필터·암호문 검색 |
| §2·§3 입력·수정 | 13종 문자셋(한글/영문/숫자/특수문자/따옴표/역슬래시/XSS/SQL주입/앞뒤공백/이모지 등)을 5개 화면의 이름류 필드에 등록·수정하고 **앱 응답 + DB 바이트** 양쪽에서 왕복 일치 확인 |
| §4 삭제 | 소프트/하드 삭제 구분을 DB 원본으로 확인. 정규인원은 등록→수정→삭제 전 구간을 돌리고 **BiostarX 사용자 생성·소멸까지 대조** |
| §5 권한 | 조회만/등록만/수정만/삭제만/무권한 5개 역할을 만들어 CRUD 4종 × 5역할 매트릭스 + 사이드바 노출 + URL 직접 접근 |

### 이 스크립트가 지키는 전제 (실측으로 확인한 것들)
- **한글 본문은 파일로 보낸다.** Git Bash 는 curl 인자를 CP949 로 넘겨 한글 JSON 이 HTTP 400 이 된다. `--data @파일` 이어야 통한다. GET 쿼리의 한글은 미리 `%` 인코딩해야 한다(`urlenc`).
- **DB 비교는 UTF-16LE 16진수로.** MSSQL 의 `nvarchar → varbinary` 는 UTF-16LE 다. 콘솔 코드페이지에 속지 않으려면 16진수로 받아 `iconv` 로 만든 기대값과 비교한다.
- **암호화 컬럼은 반대로 판정한다.** 앱은 평문을 돌려주되 DB 에는 평문이 있으면 안 된다(AGENTS §4 개인정보 암호화).
- **시험 데이터는 `ZZ` 접두로 격리하고 하드 삭제한다.** 앱의 DELETE 는 상당수가 소프트 삭제라 행이 남는다. 공유 개발 DB 에 매 실행 쌓이면 다음 실행의 건수 판정이 흔들린다 — 시작과 끝 양쪽에서 `purge_all`.
- **BiostarX 쓰기는 정규인원 한 구간뿐이다.** 전용 ID(`ZZSMK*`)로 격리하고, 끝나면 장비에서 사라진 것까지 확인한다(`GET /api/users/{id}` 가 200=있음 / 400=없음).
- **카드 등록은 일부러 뺐다.** 카드 등록은 BiostarX 에 카드를 만드는데([CardService.java:193](../src/main/java/AirPort/service/CardService.java)) 삭제는 소프트 삭제뿐이라 장비에 고아 카드가 쌓인다. 그래서 카드는 행을 DB 에 심고 **수정·조회 경로만** 검증한다.

## 관례
- given-when-then 구조. 테스트명은 한글 또는 서술형 허용.
- TODO: 커버리지 목표/도구.

## 관련 문서
[backend.md](backend.md) · [database.md](database.md) · [integration.md](integration.md) · [security.md](security.md)
