/* 공통 기간 프리셋 — 1개월/3개월/6개월/1년/직접입력.
   사용:
     const ctl = period.attach(selectEl, dateRangeEl, startEl, endEl); // 직접입력 시에만 date input 노출
     const { start, end } = ctl.value();  // 현재 선택의 시작~종료(yyyy-MM-dd)
     ctl.reset('1m');                      // 기본(1개월)로 되돌리고 date input 숨김
   select 의 value 는 1m|3m|6m|1y|custom 를 쓴다. */
window.period = (function () {
  const p2 = (n) => String(n).padStart(2, '0');
  const ymd = (d) => `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`;
  const today = () => ymd(new Date());

  /** 프리셋 → {start,end}. custom 은 호출 측에서 date input 값을 쓴다. */
  function range(type) {
    const end = new Date();
    const start = new Date();
    if (type === '1m') start.setMonth(start.getMonth() - 1);
    else if (type === '3m') start.setMonth(start.getMonth() - 3);
    else if (type === '6m') start.setMonth(start.getMonth() - 6);
    else if (type === '1y') start.setFullYear(start.getFullYear() - 1);
    return { start: ymd(start), end: ymd(end) };
  }

  function attach(sel, rangeBox, startEl, endEl) {
    function onChange() {
      const custom = sel.value === 'custom';
      if (rangeBox) rangeBox.style.display = custom ? 'inline-flex' : 'none';
      if (custom) {
        if (!startEl.value) startEl.value = today();
        if (!endEl.value) endEl.value = today();
      }
    }
    sel.addEventListener('change', onChange);
    onChange();
    return {
      value() {
        return sel.value === 'custom'
          ? { start: startEl.value, end: endEl.value }
          : range(sel.value);
      },
      reset(defType) {
        sel.value = defType || '1m';
        startEl.value = '';
        endEl.value = '';
        onChange();
      },
    };
  }

  return { range, today, ymd, attach };
})();
