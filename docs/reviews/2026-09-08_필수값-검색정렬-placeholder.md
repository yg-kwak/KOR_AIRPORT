# 리뷰: 임시·장기 필수값 / 정규인원 검색·정렬 / placeholder 정리

- 일시: 2026-09-08 / 검토자: sjpark2
- 대상: `main` 2bda8e0 이후 **미커밋 작업분** (15 files, +161 −135 · 신규 1)
- 결과: 🔴 0 · 🟡 4 · 🟢 6 → **🟡-3·4 조치 완료**(재검토 참고)

## 기계 검증
`gradlew test` 전체 통과(276건) · `code-lint`(7항목) · `docs-lint` 통과.
브라우저로 오늘 바꾼 화면 전부를 직접 눌러 확인했다(등록·검증·저장·삭제까지).

---

## 🔴 차단
없음.

- **비밀값**: 변경분 스캔 0건.
- **계층**: 컨트롤러 변경은 `VisitCheckoutService` 주입·호출뿐. SQL·로직 누출 없음.
- **MyBatis**: 새 SQL 전부 mapper XML, `${}` 없음, Java 내 인라인 SQL 없음. JPA 0건.
- **외부 격리**: `VisitCheckoutService` 는 `VisitBiostarService` 를 통해서만 장비를 부른다. 직접 HTTP 없음.
- **암호화**: 새로 노출되는 암호화 컬럼 없음.
- **감사**: 퇴실 감사 5지점이 그대로 남았다(실패 감사 2곳이 `disableOrFail` 한 곳으로 합쳐졌고 호출자가 자기 문구를 넘긴다).

---

## 🟡 권고

### 🟡-1 생년월일이 빈 정규인원 4명은 이제 저장이 막힌다
[PersonService.java](../../src/main/java/AirPort/service/PersonService.java) · 앞 커밋(`2bda8e0`)의 규칙이지만 영향은 지금 드러난다

개발 DB 기준 **정규인원 11명 중 4명이 생년월일이 비어 있다.** 그 4명은 상태를 바꾸거나 카드를 발급하려고
저장하는 순간 `생년월일은(는) 필수입니다.` 로 막힌다. 정규인원 화면은 읽기전용이 되는 상태가 없어 **실제로 걸린다.**

한 번 채우면 끝나는 일이라 규칙 자체는 옳다. 다만 현장에서는 "왜 갑자기 저장이 안 되지"로 보이므로
**반영 전에 대상 인원 수를 세어 안내**해야 한다.

```sql
SELECT COUNT(*) FROM tb_person
WHERE person_type='PT01' AND del_yn='N' AND (birth_date IS NULL OR birth_date='');
```

### 🟡-2 진행 중 방문의 방문객이 같은 이유로 막힐 수 있다
[VisitRosterService.java](../../src/main/java/AirPort/service/VisitRosterService.java)

방문 저장은 방문객 전원을 `upsertVisitor` 로 다시 저장한다. 그 안에 생년월일·소속이 빈 방문객이 있으면
**그 방문 전체가 저장되지 않는다**(카드 한 장 추가하려던 것뿐이어도).

개발 DB 는 진행 중 방문이 0건이라 지금은 걸리지 않았다. 다만 **방문객 53명 중 45명이 생년월일이,
24명이 소속이 비어 있다** — 현장에 진행 중 방문이 있으면 그대로 재현된다.

```sql
SELECT COUNT(DISTINCT vp.person_id) FROM tb_visit_person vp
JOIN tb_person p ON p.person_id=vp.person_id JOIN tb_visit v ON v.visit_no=vp.visit_no
WHERE v.del_yn='N' AND v.status_code <> 'VS04' AND p.del_yn='N'
  AND (p.birth_date IS NULL OR p.birth_date='' OR p.affiliation IS NULL OR p.affiliation='');
```

> **작업목적은 문제가 없다.** 빈 방문 18건이 전부 퇴실 완료(VS04)이고, 그 상태는 화면이 저장 버튼을 숨겨
> ([visitor.js:195](../../src/main/resources/static/js/web/visitor/visitor.js)) 애초에 수정되지 않는다.
> 진행 중 방문은 모두 작업목적이 채워져 있다.

### 🟡-3 OGNL 한 글자 비교를 막을 장치가 없다
[code-lint.sh](../../scripts/code-lint.sh)

`<if test="faceYn == 'Y'">` 가 **500** 을 냈다. OGNL 이 작은따옴표 한 글자를 char 로 보고 문자열과 숫자를
비교한다. 여러 글자(`'personId'`)는 String 이라 멀쩡해서 더 헷갈리고, **그 필터를 실제로 쓸 때만** 터진다
— 화면을 눌러 보지 않으면 통과한다.

고치고 `conventions.md` §6 에 규칙으로 등록했지만, **다음 사람이 같은 것을 쓰는 것을 막지는 못한다.**
`code-lint` 에 한 줄 검사(mapper XML 에서 `test="… == '.'"` 금지)를 더하면 기계가 잡는다.

### 🟡-4 정규인원 얼굴·카드 필터가 인덱스를 타지 못한다
[TbPersonMapper.xml](../../src/main/resources/mapper/TbPersonMapper.xml)

`CASE WHEN EXISTS(...) THEN 'Y' ELSE 'N' END = #{faceYn}` 는 행마다 계산하므로 전건 스캔이다.
목록에 보이는 값과 같은 식을 쓴다는 점은 옳지만(보이는 값과 필터가 갈리지 않는다), 인원이 수천 명이 되면
느려질 수 있다. 지금 규모(11명)에서는 문제가 아니라 **그대로 두었다.**

필요해지면 `EXISTS` / `NOT EXISTS` 로 갈라 쓴다. 그때 🟡-3 의 함정을 다시 밟지 않도록 주의한다.

---

## 🟢 양호

- **상한에 닿은 파일을 미루지 않고 쪼갰다** — `VisitService` 가 505줄이 되자 퇴실 3종을
  [VisitCheckoutService](../../src/main/java/AirPort/service/VisitCheckoutService.java) 로 떼어냈다(413 + 131줄).
  호출부는 컨트롤러 둘뿐이라 경계가 깨끗했고, 옮기면서 중복된 검증·차량회수·실패감사를 helper 로 모았다.
- **옮긴 코드가 감사를 잃지 않았다** — 5지점을 세어 확인했다. 로깅은 한 번 빠지면 조용히 사라진다.
- **새 필수값을 테스트로 못 박았다** — `작업기간과_작업목적은_필수다` 가 세 값을 하나씩 비워 본다.
  픽스처만 고치고 넘어갔다면 규칙이 사라져도 초록이었을 것이다.
- **화면을 눌러서 확인했다** — 필터·정렬·모달·팝업을 실제로 조작했고, 그 과정에서 🟡-3 의 500 을 잡았다.
  API 만 두드렸다면 통과했을 결함이다.
- **placeholder 잘림을 눈대중이 아니라 재서 찾았다** — 캔버스로 글자 폭을 계산해 목록 16개와 모달·팝업을
  훑어 4건을 찾고, 고친 뒤 다시 재어 0건을 확인했다.
- **넓히는 대신 문구를 고친 판단** — 카드등록관리 검색어는 옆 select 가, 기관 모달은 라벨이 이미 같은 말을
  하고 있었다. 칸을 늘리는 대신 중복을 걷어냈다(`conventions.md` §2 의 "무엇을 찾는지는 옆 select 로").

## 조치
- 🟡-1·2: **현장 반영 전 확인 필요** — 위 SQL 로 대상 건수를 세고, 있으면 안내한다. 코드 변경은 없다.
- 🟡-3·4: **수정 완료** (아래 재검토 참고).

---

# 재검토 (2026-09-08, 🟡-3·4 조치 후)

`gradlew test` 전체 통과 · `code-lint`(8항목) · `docs-lint` 통과. 화면에서 필터·정렬을 직접 눌러 확인했다.

## 🟡-3 — `code-lint` 에 검사를 더했다 (검사 [8])

mapper XML 의 `<if test>` 에서 **작은따옴표 한 글자 비교**를 잡는다. 안전한 형태(`"Y".equals(field)`)와
문서 위치를 함께 안내한다.

> **검사가 실제로 잡는지 확인했다.** 고쳤던 함정(`test="faceYn == 'Y'"`)을 일부러 되살려 돌려 보고
> `❌ … 한 글자 비교` 와 **종료코드 1** 을 확인한 뒤 원복했다. CI 가 막는다.

## 🟡-4 — 필터를 `EXISTS` / `NOT EXISTS` 로 갈랐다

`CASE WHEN EXISTS(...) END = #{param}` 은 행마다 계산한다. 갈라 두면 준(semi)조인이라 대상 표의 인덱스를
탈 수 있다(`tb_person_photo` 는 PK, `tb_card` 는 `IX_tb_card_person`).

갈래를 나누려면 OGNL 비교가 필요한데, 그것이 바로 🟡-3 의 함정이었다. **큰따옴표 String 비교**
(`<if test='"Y".equals(faceYn)'>`)를 쓴다 — `"Y"` 는 OGNL 에서 String 이라 char 로 해석되지 않고,
값이 null 이어도 `false` 라 안전하다.

> ⚠️ **실행계획은 지금 데이터에서 두 형태가 같다.** `SET SHOWPLAN_TEXT` 로 재 보니 둘 다
> `Clustered Index Scan ×2 + Nested Loops` 였다 — 인원 11명·카드 수십 건이라 옵티마이저가 어느 쪽이든
> 스캔을 고른다. **이번 변경은 측정된 성능 개선이 아니라, 자료가 늘었을 때 옵티마이저가 인덱스를 고를 수
> 있게 하는 모양 교정이다.** 현장 규모에서 실제로 갈리는지는 그때 다시 재야 한다.

## 곁들여 정리한 것 — 검색어 문구 통일

규칙 대장은 `검색어를 입력하세요` 인데 7개 화면이 `검색어를 입력해주세요` 로, 1개가
`기관코드·기관명·차량번호` 로 갈려 있었다. **11개 화면 전부**를 규칙대로 맞췄다.
기관차량의 서술형 문구는 옆 select 가 그 셋을 그대로 나열하고 있어 중복이었다(`conventions.md` §2).
