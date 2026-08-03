/* 처리 중 표시(전역) — 저장·삭제처럼 서버 왕복이 있는 동안 진행 중임을 알리고 중복 클릭을 막는다.
   요청이 겹칠 수 있어 카운터로 관리하고, 짧은 요청에서 화면이 깜빡이지 않게 지연 후에 띄운다.
   busy.wrap(promise) 또는 busy.start()/busy.end() — 보통은 api 래퍼가 알아서 부른다. (docs/frontend.md) */
window.busy = (function () {
  const DELAY_MS = 250; // 이보다 빨리 끝나는 요청은 표시하지 않는다(깜빡임 방지)
  let count = 0;
  let timer = null;

  function el() {
    let box = document.getElementById('busyOverlay');
    if (!box) {
      box = document.createElement('div');
      box.id = 'busyOverlay';
      box.className = 'busy-overlay';
      box.setAttribute('role', 'status');
      box.setAttribute('aria-live', 'polite');
      box.innerHTML = '<div class="busy-box"><span class="busy-spinner"></span><span>처리 중입니다…</span></div>';
      document.body.appendChild(box);
    }
    return box;
  }

  function start() {
    count += 1;
    if (count === 1 && !timer) timer = setTimeout(() => el().classList.add('open'), DELAY_MS);
  }

  function end() {
    count = Math.max(0, count - 1);
    if (count > 0) return;
    if (timer) { clearTimeout(timer); timer = null; }
    el().classList.remove('open');
  }

  async function wrap(promise) {
    start();
    try { return await promise; } finally { end(); }
  }

  return { start, end, wrap };
})();
