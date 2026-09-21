/* 생년월일 입력 규칙 — 화면은 여섯 자리(YYMMDD, 예: 900101)로 받고 보여 준다.
   받는 화면이 넷(정규인원·임시/장기 방문객·키오스크 방문객·제재인원)이라 규칙을 여기 한 곳에만 둔다.
   저장은 서버가 YYYY-MM-DD 로 한다(세기는 서버가 붙인다 — 올해 두 자리 이하면 2000년대).
   서버에도 같은 규칙이 있다(AirPort.common.BirthDates) — 최종 판정은 그쪽이다. */
window.birthDate = (function () {
  const HINT = '생년월일 6자리(YYMMDD)로 입력하세요. 예: 900101';

  /* 치는 동안 모양을 잡아 준다 — 숫자만 남기고 여섯 자리로 자른다.
     19900101 · 1990-01-01 · 1990.01.01 처럼 네 자리 연도로 붙여넣어도 900101 이 된다
     (그래서 입력칸 maxlength 는 6 이 아니라 10 이다 — 6 이면 붙여넣은 1990-01-01 이 199001 로 잘린 뒤에 들어온다). */
  function normalize(value) {
    const d = String(value == null ? '' : value).replace(/\D/g, '');
    // 여덟 자리(네 자리 연도)가 채워지는 순간 뒤 여섯 자리로 — 그 전에는 치는 중이므로 자르지 않는다
    // (여섯 자리에서 잘라 버리면 1990-01-01 을 한 글자씩 칠 때 199001 에서 멈춘다)
    return d.length >= 8 ? d.slice(2, 8) : d;
  }

  /* 서버와 같은 세기 규칙 — 올해 두 자리 이하면 2000년대, 크면 1900년대(2026: 26 → 2026, 27 → 1927) */
  function fullYear(yy) {
    return yy <= new Date().getFullYear() % 100 ? 2000 + yy : 1900 + yy;
  }

  /* 달력에 실제로 있는 날짜인지까지 본다 — 형식만 보면 2월 30일이 통과한다. */
  function isValid(value) {
    const v = normalize(value);
    if (!/^\d{6}$/.test(v)) return false;
    const y = fullYear(Number(v.slice(0, 2))), m = Number(v.slice(2, 4)), day = Number(v.slice(4));
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
