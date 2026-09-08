/* 생년월일 입력 규칙 — 저장 형태는 YYYY-MM-DD 하나다.
   받는 화면이 셋(정규인원·임시/장기 방문객·키오스크 방문객)이라 규칙을 여기 한 곳에만 둔다.
   화면마다 따로 적으면 같은 사람이 어디서 등록됐느냐에 따라 1990-01-01 과 19900101 로 갈리는데,
   암호화 컬럼이라 나중에 SQL 로 정리할 수 없다 — 들어올 때 맞춰야 한다.
   서버에도 같은 규칙이 있다(AirPort.common.BirthDates) — 최종 판정은 그쪽이다. */
window.birthDate = (function () {
  const HINT = '생년월일은 YYYY-MM-DD 형식으로 입력하세요. 예: 1990-01-01';

  /* 치는 동안 모양을 잡아 준다 — 숫자만 남기고 하이픈을 끼워 넣는다.
     19900101 도 1990.01.01 도 1990/01/01 도 같은 결과가 되므로 붙여넣기까지 함께 처리된다. */
  function normalize(value) {
    const d = String(value == null ? '' : value).replace(/\D/g, '').slice(0, 8);
    if (d.length <= 4) return d;
    if (d.length <= 6) return `${d.slice(0, 4)}-${d.slice(4)}`;
    return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6)}`;
  }

  /* 달력에 실제로 있는 날짜인지까지 본다 — 형식만 보면 2월 30일이 통과한다. */
  function isValid(value) {
    const v = normalize(value);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) return false;
    const [y, m, day] = v.split('-').map(Number);
    const dt = new Date(y, m - 1, day);
    return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === day;
  }

  /* 입력칸 하나에 붙인다(정규인원처럼 고정된 칸). */
  function bindInput(el) {
    if (el) el.addEventListener('input', (e) => { e.target.value = normalize(e.target.value); });
  }

  /* 표 안의 입력칸에 붙인다 — 행이 다시 그려지므로 컨테이너에 한 번만 위임해 둔다. */
  function bindWithin(container, selector) {
    if (!container) return;
    container.addEventListener('input', (e) => {
      const el = e.target.closest(selector);
      if (el) el.value = normalize(el.value);
    });
  }

  return { HINT, normalize, isValid, bindInput, bindWithin };
})();
