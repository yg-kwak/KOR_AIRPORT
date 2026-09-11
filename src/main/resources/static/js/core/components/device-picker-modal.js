/* 공통 BiostarX 장치 선택 팝업 (fragments/components/device-picker-modal.html 과 한 쌍).
   const sel  = await devicePicker.open('/system/loginUser/biostarDevices');            // {id, name} | 닫으면 null
   const sels = await devicePicker.open(url, { multiple: true, selected: ['1','2'] });  // [{id, name}, ...] | null

   목록 URL 을 인자로 받는다 — 화면마다 자기 menu_id 로 권한을 확인해야 해서 엔드포인트가 다르다.
   응답은 두 모양을 모두 받는다: 배열 그대로, 또는 {success, message, devices}.
   여러 대 모드는 이벤트 로그 화면이 쓴다(한 화면에서 N대를 본다). `selected` 를 주면 그 장치가 미리 체크된다 —
   다시 열었을 때 지금 보고 있는 것이 무엇인지 보이지 않으면, 고르던 것을 통째로 다시 골라야 한다. */
window.devicePicker = (function () {
  let resolver = null;
  let devices = [];
  let multiple = false;
  let preset = new Set(); // 다시 열었을 때 미리 체크해 둘 장치
  const el = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function render() {
    const kw = el('devicePickerKeyword').value.trim().toLowerCase();
    const rows = kw
      ? devices.filter((d) => String(d.id).includes(kw) || (d.name || '').toLowerCase().includes(kw))
      : devices;
    el('devicePickerInfo').textContent = multiple
      ? `총 ${rows.length}개 — 볼 장치를 모두 고르고 [선택]을 누르세요.`
      : `총 ${rows.length}개 — 장치 1건을 선택하고 [선택]을 누르세요.`;
    /* 검색으로 목록이 걸러져도 이미 고른 것은 유지된다 — 체크 상태를 preset 에 담아 두고 다시 그린다.
       그러지 않으면 검색어를 바꾸는 순간 앞서 고른 장치가 조용히 풀린다. */
    const box = (d) => (multiple
      ? `<input type="checkbox" name="devicePickerRow" data-id="${esc(d.id)}" data-name="${esc(d.name)}"${preset.has(String(d.id)) ? ' checked' : ''}/>`
      : `<input type="radio" name="devicePickerRow" data-id="${esc(d.id)}" data-name="${esc(d.name)}"/>`);
    el('devicePickerBody').innerHTML = rows.length
      ? rows.map((d) => `
        <tr>
          <td>${box(d)}</td>
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

  async function open(url, opts) {
    multiple = !!(opts && opts.multiple);
    preset = new Set(((opts && opts.selected) || []).map(String));
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
    // 행 클릭 → 그 줄의 라디오/체크박스를 토글
    el('devicePickerBody').addEventListener('click', (e) => {
      const box = e.target.closest('tr')?.querySelector('input[name="devicePickerRow"]');
      if (!box) return;
      if (e.target !== box) box.checked = multiple ? !box.checked : true;
      if (multiple) { if (box.checked) preset.add(String(box.dataset.id)); else preset.delete(String(box.dataset.id)); }
    });
    el('devicePickerOk').addEventListener('click', () => {
      const picked = [...el('devicePickerBody').querySelectorAll('input[name="devicePickerRow"]:checked')]
        .map((i) => ({ id: i.dataset.id, name: i.dataset.name }));
      if (!picked.length) { toast.warning('장치를 선택해주세요.'); return; }
      // 여러 대 모드는 검색으로 가려진 장치도 고른 것이다 — preset 이 화면 밖의 선택까지 들고 있다
      if (!multiple) { close(picked[0]); return; }
      const shown = new Set(picked.map((d) => String(d.id)));
      const hidden = devices.filter((d) => preset.has(String(d.id)) && !shown.has(String(d.id)))
        .map((d) => ({ id: String(d.id), name: d.name }));
      close(picked.concat(hidden));
    });
  });

  return { open };
})();
