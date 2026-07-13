/* 공통 페이징 — 처음«/이전‹/번호(최대 5)/다음›/마지막» 윈도우형.
   사용: pager.render($('paging'), page, totalPages, (p) => { state.page = p; load(); });
   박스에 한 번만 위임 클릭을 걸고(중복 방지), 매 호출 시 콜백만 갱신한다. */
window.pager = (function () {
  const WIN = 5;

  function render(box, page, totalPages, onGo) {
    if (!box) return;
    box._pagerGo = onGo;
    if (!box.dataset.pagerBound) {
      box.dataset.pagerBound = '1';
      box.addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-page]');
        if (btn && !btn.disabled && box._pagerGo) box._pagerGo(Number(btn.dataset.page));
      });
    }
    if (!totalPages || totalPages <= 1) { box.innerHTML = ''; return; }

    let start = Math.max(1, page - Math.floor(WIN / 2));
    const end = Math.min(totalPages, start + WIN - 1);
    start = Math.max(1, end - WIN + 1);

    const btn = (label, target, disabled, active) =>
      `<button data-page="${target}"${disabled ? ' disabled' : ''}${active ? ' class="active"' : ''}>${label}</button>`;
    let html = '';
    html += btn('«', 1, page === 1);
    html += btn('‹', page - 1, page === 1);
    for (let i = start; i <= end; i++) html += btn(i, i, false, i === page);
    html += btn('›', page + 1, page === totalPages);
    html += btn('»', totalPages, page === totalPages);
    box.innerHTML = html;
  }

  return { render };
})();
