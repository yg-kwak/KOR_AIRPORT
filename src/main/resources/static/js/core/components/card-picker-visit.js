/* 방문 카드 선택 팝업 (조각 web/visitor/visitor.html 의 #vcpModal 과 한 쌍).
   화면 상태(방문객·차량 배열)를 모르고, 필요한 값만 받아 고른 카드를 콜백으로 돌려준다.

   visitCardPicker.open({
     kind: 'vis'|'car',      // 방문객=검색+스캔 / 차량=검색만
     listUrl, scanUrl,       // 미할당 카드 조회 / 카드 스캔 endpoint
     exclude: [cardId, ...], // 이미 다른 행이 고른 카드(중복 발급 방지)
     onPick: (card) => {}    // 고른 카드(취소하면 호출되지 않는다)
   });  (docs/frontend.md) */
window.visitCardPicker = (function () {
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  let cfg = null;
  let chosen = null;

  function open(opts) {
    cfg = opts || {};
    chosen = null;
    $('vcpTitle').textContent = cfg.kind === 'vis' ? '방문객 카드 선택' : '차량 카드 선택';
    $('vcpScan').style.display = cfg.kind === 'vis' ? '' : 'none'; // 차량카드는 스캔 없음
    $('vcpKeyword').value = '';
    $('vcpModal').classList.add('open');
    load();
  }
  function close() { $('vcpModal').classList.remove('open'); }

  async function load() {
    const all = (await api.get(cfg.listUrl + '?keyword=' + encodeURIComponent($('vcpKeyword').value.trim()))) || [];
    const skip = new Set(cfg.exclude || []); // 이미 다른 행이 고른 카드는 숨긴다(서버도 중복을 거부)
    const rows = all.filter((c) => !skip.has(c.cardId));
    $('vcpBody').innerHTML = rows.length
      ? rows.map((c, i) => `<tr class="row-click vcp-row" data-idx="${i}">
          <td><input type="radio" name="vcp" value="${i}"/></td>
          <td>${esc(c.biostarCardValue)}</td>
          <td style="text-align:left">${esc(c.cardName)}</td>
          <td>${badge.cardStatus(c.cardStatus, c.cardStatusName)}</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">미할당 카드가 없습니다.</td></tr>';
    $('vcpBody').dataset.rows = JSON.stringify(rows);
  }

  async function scan() {
    const res = await api.post(cfg.scanUrl, {});
    if (!res || !res.success) { toast.warning((res && res.message) || '카드 스캔에 실패했습니다.'); return; }
    $('vcpKeyword').value = res.cardNo || '';
    await load();
    const rows = JSON.parse($('vcpBody').dataset.rows || '[]');
    const idx = rows.findIndex((c) => String(c.biostarCardValue) === String(res.cardNo));
    const tr = idx >= 0 && $('vcpBody').querySelector(`.vcp-row[data-idx="${idx}"]`);
    if (tr) { tr.querySelector('input[type=radio]').checked = true; chosen = rows[idx]; }
    else toast.warning('스캔한 카드가 미할당 목록에 없습니다. (카드관리에서 등록·회수 여부 확인)');
  }

  function apply(card) {
    if (cfg && cfg.onPick) cfg.onPick(card);
    close();
  }

  document.addEventListener('DOMContentLoaded', () => {
    if (!$('vcpModal')) return; // 이 팝업이 없는 화면
    $('vcpSearch').addEventListener('click', load);
    $('vcpKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') load(); });
    $('vcpScan').addEventListener('click', scan);
    $('vcpBody').addEventListener('click', (e) => {
      const row = e.target.closest('.vcp-row'); if (!row) return;
      row.querySelector('input[type=radio]').checked = true;
      chosen = JSON.parse($('vcpBody').dataset.rows)[Number(row.dataset.idx)];
    });
    $('vcpOk').addEventListener('click', () => (chosen ? apply(chosen) : toast.warning('카드를 선택하세요.')));
    $('vcpCancel').addEventListener('click', close);
    $('vcpClose').addEventListener('click', close);
  });

  return { open };
})();
