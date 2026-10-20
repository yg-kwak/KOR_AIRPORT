/* 에스코트 인원 관리 — BiostarX 출입문의 에스코트(auth_door_control) 사용자 조회·추가.
   이 시스템에는 저장하지 않는다 — 출입문·사용자 모두 그때그때 BiostarX 에서 읽는다.
   왼쪽 출입문을 고르면 오른쪽에 등록된 사용자, [사용자 추가] → 그 문에 넣을 수 있는 사용자를 골라 [완료]. */
(function () {
  const BASE = '/security/escort';
  const PAGE_SIZE = 200; // 추가할 수 있는 사용자 한 쪽 — BiostarX 화면과 같다
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false };
  const state = {
    doors: [],          // [{id, name, entryDeviceId, entryDeviceName}]
    door: null,         // 고른 문의 에스코트 설정 {doorId, doorName, enabled, scheduleName, users}
    page: 1, keyword: '',
    picked: new Map(),  // 추가할 사용자 userId → name (쪽을 넘겨도 유지)
  };

  /* ---- 출입문 ---- */
  async function loadDoors() {
    state.doors = (await api.get(BASE + '/doors')) || [];
    renderDoors();
  }

  function renderDoors() {
    const kw = $('doorKeyword').value.trim().toLowerCase();
    const rows = kw
      ? state.doors.filter((d) => [d.id, d.name, d.entryDeviceName].some((v) => String(v || '').toLowerCase().includes(kw)))
      : state.doors;
    $('doorTotal').textContent = `출입문 ${rows.length}개` + (kw ? ` (전체 ${state.doors.length})` : '');
    $('doorBody').innerHTML = rows.length
      ? rows.map((d) => `<tr class="row-click${state.door && state.door.doorId === d.id ? ' selected' : ''}" data-id="${esc(d.id)}">
          <td>${esc(d.id)}</td><td style="text-align:left">${esc(d.name)}</td><td style="text-align:left">${esc(d.entryDeviceName || '-')}</td></tr>`).join('')
      : `<tr><td colspan="3" class="empty">${state.doors.length ? '검색 결과가 없습니다.' : '출입문이 없습니다.'}</td></tr>`;
  }

  async function selectDoor(doorId) {
    const d = await api.get(`${BASE}/door?doorId=${encodeURIComponent(doorId)}`);
    if (!d) return;
    state.door = d;
    renderDoors();
    renderUsers();
  }

  /* 등록된 사용자 — 에스코트가 꺼진 문은 추가 버튼을 숨기고 사유를 보인다 */
  function renderUsers() {
    const d = state.door;
    const users = d.users || [];
    $('userTotal').textContent = `${d.doorName || '문 ' + d.doorId} — 등록된 사용자 ${users.length}명`;
    $('doorInfo').innerHTML = d.enabled
      ? `에스코트 사용 중${d.scheduleName ? ' · 일정 ' + esc(d.scheduleName) : ''}`
      : '<span class="text-danger">이 출입문은 에스코트(auth_door_control)가 꺼져 있어 사용자를 추가할 수 없습니다. BiostarX 에서 먼저 켜세요.</span>';
    if ($('btnAddUser')) $('btnAddUser').hidden = !(d.enabled && PERM.canCreate);
    $('userBody').innerHTML = users.length
      ? users.map((u) => `<tr><td>${esc(u.userId)}</td><td style="text-align:left">${esc(u.name || '-')}</td></tr>`).join('')
      : '<tr><td colspan="2" class="empty">등록된 사용자가 없습니다.</td></tr>';
  }

  /* ---- 사용자 추가 ---- */
  function openModal() {
    if (!state.door || !state.door.enabled) return;
    state.page = 1; state.keyword = ''; state.picked.clear();
    $('userKeyword').value = '';
    $('addModalTitle').textContent = `사용자 추가 — ${state.door.doorName || '문 ' + state.door.doorId}`;
    $('addModal').classList.add('open');
    loadCandidates();
  }
  function closeModal() { $('addModal').classList.remove('open'); }

  async function loadCandidates() {
    $('addBody').innerHTML = '<tr><td colspan="4" class="empty">불러오는 중...</td></tr>';
    const q = new URLSearchParams({ doorId: state.door.doorId, page: state.page, size: PAGE_SIZE, keyword: state.keyword });
    const res = await api.get(`${BASE}/users?${q}`);
    if (!res) { $('addBody').innerHTML = '<tr><td colspan="4" class="empty">조회 실패</td></tr>'; return; }
    renderCandidates(res);
  }

  /* 이미 등록된 사람은 고를 수 없게 둔다 — 같은 사람을 두 번 보내지 않는다 */
  function renderCandidates(res) {
    const registered = new Set((state.door.users || []).map((u) => String(u.userId)));
    const rows = res.rows || [];
    $('addBody').innerHTML = rows.length
      ? rows.map((u) => {
        const id = String(u.userId);
        const done = registered.has(id);
        return `<tr class="row-click${done ? ' ac-used' : ''}">
          <td><input type="checkbox" data-id="${esc(id)}" data-name="${esc(u.name || '')}"${done ? ' disabled checked' : (state.picked.has(id) ? ' checked' : '')}/></td>
          <td>${esc(id)}</td>
          <td style="text-align:left">${esc(u.name || '-')}${done ? ' <span class="form-hint">(등록됨)</span>' : ''}</td>
          <td style="text-align:left">${esc(u.groupName || '-')}</td></tr>`;
      }).join('')
      : '<tr><td colspan="4" class="empty">추가할 수 있는 사용자가 없습니다.</td></tr>';
    const totalPages = Math.max(1, Math.ceil((res.total || 0) / PAGE_SIZE));
    pager.render($('addPaging'), state.page, totalPages, (p) => { state.page = p; loadCandidates(); });
    renderPicked(res.total || 0);
  }

  function renderPicked(total) {
    if (total != null) state.total = total;
    $('addInfo').textContent = `추가할 수 있는 사용자 ${state.total || 0}명 · 고른 사용자 ${state.picked.size}명`;
  }

  async function confirmAdd() {
    if (!state.picked.size) { toast.warning('추가할 사용자를 선택하세요.'); return; }
    const name = state.door.doorName || '문 ' + state.door.doorId;
    const ok = await confirmModal.open({
      title: '사용자 추가', confirmText: '추가',
      message: `${state.picked.size}명을 '${name}' 의 에스코트 사용자로 추가하시겠습니까? BiostarX 에 바로 반영됩니다.`,
    });
    if (!ok) return;
    await api.put(BASE, { doorId: state.door.doorId, userIds: [...state.picked.keys()] }); // 결과 문구는 서버가 토스트로
    closeModal();
    selectDoor(state.door.doorId); // 장비에서 다시 읽어 실제 반영 결과를 보인다
  }

  function bind() {
    $('doorKeyword').addEventListener('input', renderDoors);
    $('doorBody').addEventListener('click', (e) => {
      const tr = e.target.closest('tr[data-id]'); if (tr) selectDoor(tr.dataset.id);
    });
    if ($('btnAddUser')) $('btnAddUser').addEventListener('click', openModal);
    $('addModalClose').addEventListener('click', closeModal);
    $('btnAddCancel').addEventListener('click', closeModal);
    $('btnAddConfirm').addEventListener('click', confirmAdd);
    const search = () => { state.keyword = $('userKeyword').value.trim(); state.page = 1; loadCandidates(); };
    $('btnUserSearch').addEventListener('click', search);
    $('userKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    // 행을 눌러도 체크가 바뀐다(체크박스 직접 클릭은 기본 동작)
    $('addBody').addEventListener('click', (e) => {
      if (e.target.closest('input[type="checkbox"]')) return;
      const box = e.target.closest('tr')?.querySelector('input[type="checkbox"]');
      if (box && !box.disabled) { box.checked = !box.checked; box.dispatchEvent(new Event('change', { bubbles: true })); }
    });
    $('addBody').addEventListener('change', (e) => {
      const cb = e.target.closest('input[type="checkbox"]'); if (!cb || cb.disabled) return;
      if (cb.checked) state.picked.set(cb.dataset.id, cb.dataset.name); else state.picked.delete(cb.dataset.id);
      renderPicked();
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadDoors(); });
})();
