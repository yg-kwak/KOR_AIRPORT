/* 공통 기관 선택 팝업 (fragments/components/company-picker-modal.html 과 한 쌍).
   const sel = await companyPicker.open();  // 선택 시 {companyCode, companyName}, 닫으면 null
   tb_company 참조가 필요한 화면에서 공용으로 사용한다(조회는 로그인만 필요). */
window.companyPicker = (function () {
  let resolver = null;
  let companies = [];
  const el = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function render() {
    const kw = el('companyPickerKeyword').value.trim().toLowerCase();
    const rows = kw
      ? companies.filter((c) => (c.companyCode || '').toLowerCase().includes(kw)
          || (c.companyName || '').toLowerCase().includes(kw))
      : companies;
    el('companyPickerInfo').textContent = `총 ${rows.length}개 — 기관 1건을 선택하고 [선택]을 누르세요.`;
    el('companyPickerBody').innerHTML = rows.length
      ? rows.map((c) => `
        <tr>
          <td><input type="radio" name="companyPickerRow" data-code="${esc(c.companyCode)}" data-name="${esc(c.companyName)}"/></td>
          <td>${esc(c.companyCode)}</td>
          <td style="text-align:left">${esc(c.companyName)}</td>
        </tr>`).join('')
      : '<tr><td colspan="3" class="empty">검색 결과가 없습니다.</td></tr>';
  }

  function close(result) {
    const m = el('companyPickerModal');
    if (m) m.classList.remove('open');
    if (resolver) { resolver(result || null); resolver = null; }
  }

  async function open() {
    el('companyPickerKeyword').value = '';
    el('companyPickerInfo').textContent = '';
    el('companyPickerBody').innerHTML = '<tr><td colspan="3" class="empty">불러오는 중...</td></tr>';
    el('companyPickerModal').classList.add('open');
    companies = (await api.get('/company/company/picker')) || [];
    render();
    return new Promise((resolve) => { resolver = resolve; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const m = el('companyPickerModal');
    if (!m) return; // 화면에 fragment 미포함 시 no-op
    el('companyPickerClose').addEventListener('click', () => close(null));
    el('companyPickerCancel').addEventListener('click', () => close(null));
    el('companyPickerKeyword').addEventListener('input', render);
    m.addEventListener('click', (e) => { if (e.target === m) close(null); });
    // 행 클릭 → 라디오 체크
    el('companyPickerBody').addEventListener('click', (e) => {
      if (e.target.closest('input[type="radio"]')) return;
      const radio = e.target.closest('tr')?.querySelector('input[type="radio"]');
      if (radio) radio.checked = true;
    });
    el('companyPickerOk').addEventListener('click', () => {
      const sel = el('companyPickerBody').querySelector('input[name="companyPickerRow"]:checked');
      if (!sel) { toast.warning('기관을 선택해주세요.'); return; }
      close({ companyCode: sel.dataset.code, companyName: sel.dataset.name });
    });
  });

  return { open };
})();
