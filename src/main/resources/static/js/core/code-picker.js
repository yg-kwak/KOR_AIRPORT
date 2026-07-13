/* 공통 코드 선택 팝업 (fragments/components/code-picker.html 과 한 쌍).
   const sel = await codePicker.open({ cmmId, cmmName });  // 선택 시 {codeId, codeName}, 닫으면 null
   tb_common 참조를 select 대신 팝업으로 조회하는 화면에서 공용으로 사용한다. */
window.codePicker = (function () {
  let cfg = null;
  let resolver = null;
  const el = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function emptyRow() {
    return '<tr><td colspan="3" class="empty">검색 결과가 없습니다.</td></tr>';
  }

  async function search() {
    const kw = el('codePickerKeyword').value.trim();
    const list = await api.get(
      `/system/common/picker?cmmId=${encodeURIComponent(cfg.cmmId)}&keyword=${encodeURIComponent(kw)}`);
    render(list || []);
  }

  function render(rows) {
    const body = el('codePickerBody');
    if (!rows.length) { body.innerHTML = emptyRow(); return; }
    body.innerHTML = rows.map((r) => `
      <tr>
        <td>${esc(r.codeId)}</td>
        <td>${esc(r.codeName)}</td>
        <td><button class="btn btn-sm btn-primary" data-code="${esc(r.codeId)}" data-name="${esc(r.codeName)}">선택</button></td>
      </tr>`).join('');
  }

  function choose(codeId, codeName) {
    close({ codeId, codeName });
  }

  function close(result) {
    const m = el('codePickerModal');
    if (m) m.classList.remove('open');
    if (resolver) { resolver(result || null); resolver = null; }
  }

  function open({ cmmId, cmmName } = {}) {
    cfg = { cmmId, cmmName };
    el('codePickerGroup').value = cmmId + (cmmName ? ' : ' + cmmName : '');
    el('codePickerKeyword').value = '';
    el('codePickerBody').innerHTML = emptyRow();
    el('codePickerModal').classList.add('open');
    search();
    return new Promise((resolve) => { resolver = resolve; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const m = el('codePickerModal');
    if (!m) return; // fragment 미포함 화면에서는 no-op
    el('codePickerSearch').addEventListener('click', search);
    el('codePickerKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); search(); } });
    el('codePickerClose').addEventListener('click', () => close(null));
    m.addEventListener('click', (e) => { if (e.target === m) close(null); });
    el('codePickerBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-code]');
      if (btn) choose(btn.dataset.code, btn.dataset.name);
    });
  });

  return { open };
})();
