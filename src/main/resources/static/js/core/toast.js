/* 공통 토스트 알림 — 모든 화면 공용. 서버 응답(ApiResponse.message)은 app.js 가 자동 토스트,
   화면에서 직접 쓸 때는 toast.success/error/warning(message[, {duration}]).
   특징: 유형별 아이콘·색, 마우스 오버 시 자동 사라짐 정지, X 버튼 즉시 닫기. */
window.toast = (function () {
  const ICON = {
    success:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
    error:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
    warning:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
  };

  function container() {
    let c = document.getElementById('toastContainer');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toastContainer';
      c.className = 'toast-container';
      document.body.appendChild(c);
    }
    return c;
  }

  function show({ type = 'success', message = '', duration = 3000 } = {}) {
    const el = document.createElement('div');
    el.className = 'toast toast-' + (ICON[type] ? type : 'success');
    el.innerHTML =
      (ICON[type] || ICON.success) +
      '<span class="toast-msg"></span>' +
      '<button class="toast-close" type="button" aria-label="닫기">&times;</button>';
    el.querySelector('.toast-msg').textContent = message; // XSS 안전(textContent)
    container().appendChild(el);

    let timer = null;
    const dismiss = () => {
      if (timer) clearTimeout(timer);
      el.classList.add('hiding');
      setTimeout(() => el.remove(), 200);
    };
    const start = () => { if (duration > 0) timer = setTimeout(dismiss, duration); };

    el.querySelector('.toast-close').addEventListener('click', dismiss);
    // 마우스 오버 시 자동 사라짐 정지, 벗어나면 재개
    el.addEventListener('mouseenter', () => { if (timer) clearTimeout(timer); });
    el.addEventListener('mouseleave', start);
    start();
    return el;
  }

  return {
    show,
    success: (m, o) => show({ ...(o || {}), type: 'success', message: m }),
    error: (m, o) => show({ ...(o || {}), type: 'error', message: m }),
    warning: (m, o) => show({ ...(o || {}), type: 'warning', message: m }),
  };
})();
