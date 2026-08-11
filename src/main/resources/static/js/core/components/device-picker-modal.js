/* 공통 BiostarX 장치 선택 팝업 (fragments/components/device-picker-modal.html 과 한 쌍).
   const sel = await devicePicker.open('/system/loginUser/biostarDevices'); // {id, name} | 닫으면 null

   목록 URL 을 인자로 받는다 — 화면마다 자기 menu_id 로 권한을 확인해야 해서 엔드포인트가 다르다.
   응답은 두 모양을 모두 받는다: 배열 그대로, 또는 {success, message, devices}. */
window.devicePicker = (function () {
  let resolver = null;
  let devices = [];
  const el = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function render() {
    const kw = el('devicePickerKeyword').value.trim().toLowerCase();
    const rows = kw
      ? devices.filter((d) => String(d.id).includes(kw) || (d.name || '').toLowerCase().includes(kw))
      : devices;
    el('devicePickerInfo').textContent = `총 ${rows.length}개 — 장치 1건을 선택하고 [선택]을 누르세요.`;
    el('devicePickerBody').innerHTML = rows.length
      ? rows.map((d) => `
        <tr>
          <td><input type="radio" name="devicePickerRow" data-id="${esc(d.id)}" data-name="${esc(d.name)}"/></td>
          <td>${esc(d.id)}</td>
          <td style="text-align:left">${esc(d.name)}</td>
        </tr>`).join('')
      : '<tr><td colspan="3" class="empty">검색 결과가 없습니다.</td></tr>';
  }

  function close(result) {
    const m = el('devicePickerModal');
    if (m) m.classList.remove('open');
    if (resolver) { resolver(result || null); resolver = null; }
  }

  /* 조회 실패도 팝업 안에서 사유를 보여 준다 — 장비가 안 잡히는 상황이 가장 흔한 실패라
     빈 목록만 띄우면 "장치가 없다"와 "장비에 못 붙었다"가 구분되지 않는다. */
  function fail(message) {
    devices = [];
    el('devicePickerInfo').textContent = message || 'BiostarX 장치를 불러오지 못했습니다.';
    el('devicePickerBody').innerHTML = '<tr><td colspan="3" class="empty">조회 실패</td></tr>';
  }

  async function open(url) {
    el('devicePickerKeyword').value = '';
    el('devicePickerInfo').textContent = 'BiostarX 장치를 불러오는 중...';
    el('devicePickerBody').innerHTML = '<tr><td colspan="3" class="empty">불러오는 중...</td></tr>';
    el('devicePickerModal').classList.add('open');
    try {
      const res = await api.get(url);
      if (Array.isArray(res)) devices = res;
      else if (res && res.success) devices = res.devices || [];
      else { fail(res && res.message); return new Promise((r) => { resolver = r; }); }
      render();
    } catch (e) {
      fail(); // 실패 토스트는 api 래퍼가 이미 표시
    }
    return new Promise((resolve) => { resolver = resolve; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const m = el('devicePickerModal');
    if (!m) return; // 화면에 fragment 미포함 시 no-op
    el('devicePickerClose').addEventListener('click', () => close(null));
    el('devicePickerCancel').addEventListener('click', () => close(null));
    el('devicePickerKeyword').addEventListener('input', render);
    m.addEventListener('click', (e) => { if (e.target === m) close(null); });
    // 행 클릭 → 라디오 체크
    el('devicePickerBody').addEventListener('click', (e) => {
      if (e.target.closest('input[type="radio"]')) return;
      const radio = e.target.closest('tr')?.querySelector('input[type="radio"]');
      if (radio) radio.checked = true;
    });
    el('devicePickerOk').addEventListener('click', () => {
      const sel = el('devicePickerBody').querySelector('input[name="devicePickerRow"]:checked');
      if (!sel) { toast.warning('장치를 선택해주세요.'); return; }
      close({ id: sel.dataset.id, name: sel.dataset.name });
    });
  });

  return { open };
})();
