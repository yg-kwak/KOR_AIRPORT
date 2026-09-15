/* 방문구분 — 인원 / 차량 / 인원+차량. 고른 쪽만 입력받는다.
   받는 화면이 셋(임시인원등록·장기출입등록·키오스크)이라 '무엇을 감추는가'를 여기 한 곳에 둔다.
   화면은 감출 요소에 data-kind="person" 또는 data-kind="car" 만 달면 된다.
   서버에도 같은 규칙이 있다(AirPort.common.VisitKinds) — 고른 쪽은 있어야 하고, 고르지 않은 쪽은 비어야 한다. */
window.visitKind = (function () {
  const PERSON = 'PERSON', CAR = 'CAR', BOTH = 'BOTH';
  const HIDE = 'kind-hidden';
  const person = (k) => k === PERSON || k === BOTH;
  const car = (k) => k === CAR || k === BOTH;
  let root = null;
  let onChange = null;

  const radios = () => (root ? [...root.querySelectorAll('input[name="visitKind"]')] : []);

  function get() {
    const r = radios().find((x) => x.checked);
    return r ? r.value : null;
  }

  /* 고르지 않은 쪽을 감춘다. 감춘 칸의 값은 저장 때 prune 이 비운다 — 보이지 않는 값이 서버로 가면
     "인원" 인데 차량이 딸려 가서 거절된다. */
  function apply() {
    const k = get();
    document.querySelectorAll('[data-kind]').forEach((el) => {
      const want = el.dataset.kind === 'person' ? person(k) : car(k);
      el.classList.toggle(HIDE, !want);
    });
    if (onChange) onChange(k);
  }

  function set(k) {
    radios().forEach((r) => { r.checked = r.value === k; });
    apply();
  }

  /* 저장값이 없는 옛 방문 — 명단으로 되짚는다. 둘 다 없으면 모든 칸을 여는 인원+차량. */
  function infer(visitors, cars) {
    const v = (visitors || []).length > 0, c = (cars || []).length > 0;
    if (v && !c) return PERSON;
    if (c && !v) return CAR;
    return BOTH;
  }

  /* 저장 직전 — 고르지 않은 쪽을 비운다(감춰진 칸에 남아 있던 값). */
  function prune(payload) {
    const k = get();
    if (!person(k)) { payload.visitors = []; payload.acGroupIds = []; }
    if (!car(k)) { payload.cars = []; payload.carAcCodes = []; }
    payload.visitKind = k;
    return payload;
  }

  /* 화면 쪽 사전 검사 — 서버가 최종이지만, 탭에 감춰진 표가 비어 있는 것을 저장 뒤에 알면 늦다. */
  function problem(payload) {
    const k = payload.visitKind;
    if (!k) return '방문구분(인원/차량/인원+차량)을 선택하세요.';
    if (person(k) && !(payload.visitors || []).length) return '방문구분에 인원이 있으면 방문객을 1명 이상 입력하세요.';
    if (car(k) && !(payload.cars || []).length) return '방문구분에 차량이 있으면 차량을 1대 이상 입력하세요.';
    return null;
  }

  function init(id, cb) {
    root = document.getElementById(id);
    onChange = cb || null;
    if (root) root.addEventListener('change', (e) => { if (e.target.name === 'visitKind') apply(); });
  }

  return { PERSON, CAR, BOTH, init, get, set, apply, infer, prune, problem, person, car };
})();
