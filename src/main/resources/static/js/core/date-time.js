/* 일시 입력칸 — 브라우저의 datetime-local 대신 "YYYY-MM-DD HH:mm"(24시간) 을 숫자로 친다.
   datetime-local 은 OS·브라우저 언어에 따라 "오후 06:33" 처럼 12시간제로 그려져 현장에서 오전/오후를 번번이 고르게 한다.
   <input type="text" data-datetime> 에 붙이면:
     - 치는 대로 숫자만 남겨 2026-09-21 18:33 모양을 잡고(붙여넣기·T 구분자도 같은 결과),
     - .value 는 여전히 서버 형식("2026-09-21T18:33")으로 읽고 쓴다 — 화면 스크립트는 datetime-local 때와 같은 코드다.
       (빈 값이거나 아직 다 치지 않아 알아볼 수 없으면 '' — 필수 검사가 잡는다. 칸을 떠날 때 안내를 띄운다)
   받는 화면이 셋(정규인원 출입기간·임시/장기 작업기간·키오스크)이라 규칙을 여기 한 곳에만 둔다. */
window.dateTime = (function () {
  const HINT = '일시는 YYYY-MM-DD HH:mm(24시간) 으로 입력하세요. 예: 2026-09-21 18:33';
  const native = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');

  /* 숫자만 남기고 구분자를 끼워 넣는다 — 202609211833 → 2026-09-21 18:33 */
  function normalize(value) {
    const d = String(value == null ? '' : value).replace(/\D/g, '').slice(0, 12);
    let out = d.slice(0, 4);
    if (d.length > 4) out += '-' + d.slice(4, 6);
    if (d.length > 6) out += '-' + d.slice(6, 8);
    if (d.length > 8) out += ' ' + d.slice(8, 10);
    if (d.length > 10) out += ':' + d.slice(10, 12);
    return out;
  }

  /* 달력에 있는 날짜 + 24시간제 시각인지 — 형식만 보면 2월 30일 25:00 이 통과한다 */
  function isValid(value) {
    const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})$/.exec(normalize(value));
    if (!m) return false;
    const [y, mo, d, h, mi] = m.slice(1).map(Number);
    const dt = new Date(y, mo - 1, d);
    return dt.getFullYear() === y && dt.getMonth() === mo - 1 && dt.getDate() === d && h < 24 && mi < 60;
  }

  const toIso = (text) => (isValid(text) ? normalize(text).replace(' ', 'T') : '');
  const fromIso = (iso) => (iso == null || iso === '' ? '' : normalize(String(iso).slice(0, 16)));

  /* 한 칸에 붙인다 — 화면에는 24시간 문자열, .value 는 서버 형식 */
  function bind(el) {
    if (!el || el.dataset.datetimeBound) return;
    el.dataset.datetimeBound = '1';
    el.setAttribute('placeholder', el.getAttribute('placeholder') || '2026-01-01 09:00');
    el.setAttribute('inputmode', 'numeric');
    el.setAttribute('maxlength', '16');
    el.setAttribute('autocomplete', 'off');
    Object.defineProperty(el, 'value', {
      configurable: true,
      get() { return toIso(native.get.call(el)); },
      set(v) { native.set.call(el, fromIso(v)); el.classList.remove('is-invalid'); },
    });
    el.addEventListener('input', () => { native.set.call(el, normalize(native.get.call(el))); el.classList.remove('is-invalid'); });
    el.addEventListener('blur', () => {
      const text = native.get.call(el);
      if (text && !isValid(text)) { el.classList.add('is-invalid'); toast.warning(HINT); }
    });
  }

  function bindAll(root) {
    (root || document).querySelectorAll('input[data-datetime]').forEach(bind);
  }

  document.addEventListener('DOMContentLoaded', () => bindAll());
  return { HINT, normalize, isValid, toIso, fromIso, bind, bindAll };
})();
