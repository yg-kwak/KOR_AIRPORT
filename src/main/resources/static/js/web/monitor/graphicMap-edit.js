/* 그래픽맵 편집 — 맵 추가·이름 변경·평면도 교체·삭제, 그리고 평면도 위 출입문(단말기) 배치.
   '보기'는 graphicMap.js 가 맡고 여기는 window.gmap 으로 그 상태를 이어 받는다.
   배치는 화면에서 끝까지 고친 뒤 [배치 저장] 한 번으로 서버에 보낸다 — 문을 하나 옮길 때마다 저장하면
   실수로 끈 것도 바로 반영되고, 중간 상태가 다른 상황판에 그대로 보인다. */
(function () {
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };
  let g;                 // window.gmap
  let devices = null;    // BiostarX 장치 [{id, name}] — 편집을 처음 열 때 한 번 읽는다
  let backup = null;     // 편집 전 배치 — [취소] 하면 되돌린다
  let pending = null;    // 놓으려고 고른 단말기 {id, name}
  let modalMode = 'add'; // 맵 모달: add | image

  /* ---- 맵 추가 / 평면도 교체 ---- */
  function openMapModal(mode) {
    modalMode = mode;
    g.$('mapModalTitle').textContent = mode === 'add' ? '맵 추가' : '평면도 교체';
    g.$('mapNameRow').hidden = mode !== 'add';
    g.$('mapName').value = '';
    g.$('mapFile').value = '';
    g.$('mapModal').classList.add('open');
  }
  const closeMapModal = () => g.$('mapModal').classList.remove('open');

  async function saveMapModal() {
    const file = g.$('mapFile').files[0];
    const name = g.$('mapName').value.trim();
    if (modalMode === 'add' && !name) { toast.warning('맵 이름을 입력하세요.'); return; }
    if (!file) { toast.warning('평면도 이미지를 선택하세요.'); return; }
    if (file.size > 5 * 1024 * 1024) { toast.warning('평면도 이미지는 5MB 까지 올릴 수 있습니다.'); return; }
    const fd = new FormData();
    fd.append('file', file);
    if (modalMode === 'add') fd.append('mapName', name); else fd.append('mapId', g.state.mapId);
    const url = g.BASE + (modalMode === 'add' ? '/maps' : '/maps/image');
    const tok = (document.querySelector('meta[name=_csrf]') || {}).content || '';
    const res = await window.busy.wrap(fetch(url, {
      method: 'POST', headers: { 'X-Requested-With': 'XMLHttpRequest', 'X-CSRF-TOKEN': tok }, body: fd,
    }));
    const json = await res.json().catch(() => null);
    if (!res.ok || !json || json.success === false) {
      toast.error((json && json.message) || '평면도를 올리지 못했습니다.'); return;
    }
    closeMapModal();
    toast.success(modalMode === 'add' ? '맵을 추가했습니다.' : '평면도를 바꿨습니다.');
    await g.loadMaps(modalMode === 'add' ? json.data : g.state.mapId);
  }

  async function renameMap() {
    const m = g.state.maps.find((x) => x.mapId === g.state.mapId); if (!m) return;
    const name = await promptModal.open({ title: '맵 이름 변경', label: '맵 이름', placeholder: m.mapName, confirmText: '변경' });
    if (!name || !name.trim()) return;
    await api.put(g.BASE + '/maps', { mapId: m.mapId, mapName: name.trim() });
    await g.loadMaps(m.mapId);
  }

  async function deleteMap() {
    const m = g.state.maps.find((x) => x.mapId === g.state.mapId); if (!m) return;
    const ok = await confirmModal.open({ title: '맵 삭제', confirmText: '삭제',
      message: `'${m.mapName}' 맵을 삭제하시겠습니까? 놓인 출입문 배치도 함께 지워집니다.` });
    if (!ok) return;
    await api.del(`${g.BASE}/maps?mapId=${m.mapId}`);
    try { localStorage.removeItem('graphicMapId'); } catch (e) { /* 저장이 막힌 브라우저 */ }
    await g.loadMaps(null);
  }

  /* ---- 출입문 배치 ---- */
  async function start() {
    if (g.state.mapId == null) { toast.warning('먼저 맵을 선택하세요.'); return; }
    if (!devices) devices = ((await api.get(g.BASE + '/devices')) || []).map((d) => ({ id: String(d.id), name: d.name || '' }));
    backup = g.state.doors.map((d) => ({ ...d }));
    g.state.editing = true;
    toggleBar(true);
    document.querySelector('.gmap-tab[data-tab="doors"]').click(); // 놓을 단말기가 보이게
    g.$('doorsHint').hidden = false;
    g.renderDoors(); renderDevices();
  }

  function cancel() {
    if (!g.state.editing) return;
    g.state.doors = backup || g.state.doors;
    finish();
  }

  async function save() {
    await api.put(g.BASE + '/maps', { mapId: g.state.mapId, doors: g.state.doors });
    finish();
    await g.loadMaps(g.state.mapId); // 문 수가 목록에 반영되고 새 배치로 다시 구독한다
  }

  function finish() {
    g.state.editing = false; pending = null; backup = null;
    g.$('stage').classList.remove('placing');
    toggleBar(false);
    g.$('doorsHint').hidden = true;
    g.renderDoors(); g.renderDoorList();
  }

  function toggleBar(on) {
    g.$('btnEdit').hidden = on; g.$('btnEditSave').hidden = !on; g.$('btnEditCancel').hidden = !on;
    g.$('gmap').classList.toggle('editing', on);
  }

  /* 편집 중 왼쪽 목록 — 놓을 단말기. 이미 놓인 것은 표시만 한다(한 맵에 한 번) */
  function renderDevices() {
    const placed = new Set(g.state.doors.map((d) => d.deviceId));
    g.$('doorsHead').textContent = `단말기 (${(devices || []).length})`;
    g.$('doorList').innerHTML = (devices || []).length
      ? devices.map((d) => `<li class="gmap-item${placed.has(d.id) ? ' placed' : ''}${pending && pending.id === d.id ? ' active' : ''}" data-dev="${g.esc(d.id)}">
          <span class="gmap-item-name">${g.esc(d.name || d.id)}</span>
          <span class="gmap-item-sub">${placed.has(d.id) ? '놓임' : g.esc(d.id)}</span></li>`).join('')
      : '<li class="gmap-empty">단말기가 없습니다.</li>';
  }

  function pickDevice(id) {
    const d = devices.find((x) => x.id === id);
    if (!d || g.state.doors.some((x) => x.deviceId === id)) return;
    pending = pending && pending.id === id ? null : d; // 다시 누르면 고르기 취소
    g.$('stage').classList.toggle('placing', !!pending);
    renderDevices();
  }

  /* 평면도의 한 점 → 0~1 비율 */
  function ratioAt(e) {
    const r = g.$('plane').getBoundingClientRect();
    return { x: Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)), y: Math.min(1, Math.max(0, (e.clientY - r.top) / r.height)) };
  }

  function placeAt(e) {
    if (!g.state.editing || !pending) return;
    const r = g.$('plane').getBoundingClientRect(); // 평면도 밖을 누르면 놓지 않는다
    if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) return;
    const p = ratioAt(e);
    g.state.doors.push({ deviceId: pending.id, deviceName: pending.name, posX: p.x, posY: p.y });
    pending = null;
    g.$('stage').classList.remove('placing');
    g.renderDoors(); renderDevices();
  }

  /* 놓인 문 — 끌어서 옮기고 × 로 뺀다 */
  function bindDrag() {
    const layer = g.$('doorLayer');
    let drag = null;
    layer.addEventListener('pointerdown', (e) => {
      if (!g.state.editing) return;
      const el = e.target.closest('.gmap-door'); if (!el) return;
      if (e.target.closest('.gmap-door-del')) {
        g.state.doors = g.state.doors.filter((d) => d.deviceId !== el.dataset.id);
        g.renderDoors(); renderDevices(); return;
      }
      e.preventDefault(); e.stopPropagation();
      drag = { el, door: g.state.doors.find((d) => d.deviceId === el.dataset.id) };
      el.setPointerCapture(e.pointerId);
    });
    layer.addEventListener('pointermove', (e) => {
      if (!drag) return;
      const p = ratioAt(e);
      drag.door.posX = p.x; drag.door.posY = p.y;
      drag.el.style.left = (p.x * 100) + '%'; drag.el.style.top = (p.y * 100) + '%';
    });
    const end = () => { drag = null; };
    layer.addEventListener('pointerup', end); layer.addEventListener('pointercancel', end);
  }

  function bind() {
    g = window.gmap;
    if (!g) return;
    const on = (id, ev, fn) => { const el = g.$(id); if (el) el.addEventListener(ev, fn); };
    on('btnMapAdd', 'click', () => openMapModal('add'));
    on('btnMapImage', 'click', () => openMapModal('image'));
    on('btnMapRename', 'click', renameMap);
    on('btnMapDelete', 'click', deleteMap);
    on('mapModalOk', 'click', saveMapModal);
    on('mapModalCancel', 'click', closeMapModal);
    on('mapModalClose', 'click', closeMapModal);
    if (!PERM.canCreate) return; // 배치는 등록 권한이 있을 때만
    on('btnEdit', 'click', start);
    on('btnEditSave', 'click', save);
    on('btnEditCancel', 'click', cancel);
    g.$('doorList').addEventListener('click', (e) => {
      const li = e.target.closest('[data-dev]'); if (li && g.state.editing) pickDevice(li.dataset.dev);
    });
    g.$('stage').addEventListener('click', placeAt);
    bindDrag();
  }

  window.gmapEdit = { cancel, renderDevices };
  document.addEventListener('DOMContentLoaded', bind);
})();
