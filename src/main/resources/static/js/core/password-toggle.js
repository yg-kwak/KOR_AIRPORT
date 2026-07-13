/* 모든 비밀번호 입력칸에 표시/숨김(눈 아이콘) 토글을 자동 부착. (전 페이지 공통)
   input[type=password] 를 .pw-wrap 으로 감싸고 우측에 토글 버튼을 넣는다. 동적 추가분도 커버. */
(function () {
  const EYE =
    '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/><circle cx="12" cy="12" r="3"/></svg>';
  const EYE_OFF =
    '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>';

  function attach(inp) {
    if (!inp || inp.dataset.pwToggle) return;
    if ((inp.getAttribute('type') || '').toLowerCase() !== 'password') return;
    inp.dataset.pwToggle = '1';

    const wrap = document.createElement('span');
    wrap.className = 'pw-wrap';
    inp.parentNode.insertBefore(wrap, inp);
    wrap.appendChild(inp);

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'pw-toggle';
    btn.setAttribute('aria-label', '비밀번호 표시');
    btn.innerHTML = EYE;
    btn.addEventListener('click', () => {
      const show = inp.getAttribute('type') === 'password';
      inp.setAttribute('type', show ? 'text' : 'password');
      btn.innerHTML = show ? EYE_OFF : EYE;
      btn.setAttribute('aria-label', show ? '비밀번호 숨김' : '비밀번호 표시');
    });
    wrap.appendChild(btn);
  }

  function scan(root) {
    (root || document).querySelectorAll('input[type="password"]').forEach(attach);
  }

  document.addEventListener('DOMContentLoaded', () => {
    scan(document);
    new MutationObserver((muts) => {
      muts.forEach((m) =>
        m.addedNodes.forEach((n) => {
          if (n.nodeType !== 1) return;
          if (n.matches && n.matches('input[type="password"]')) attach(n);
          if (n.querySelectorAll) scan(n);
        }));
    }).observe(document.body, { childList: true, subtree: true });
  });
})();
