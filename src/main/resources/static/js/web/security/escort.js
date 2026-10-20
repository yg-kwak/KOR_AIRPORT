/* 에스코트 인원 관리 — BiostarX 출입문의 에스코트(auth_door_control) 사용자 조회·추가·제외.
   이 시스템에는 저장하지 않는다 — 출입문·사용자 모두 그때그때 BiostarX 에서 읽는다.
   왼쪽 출입문을 고르면 오른쪽에 등록된 사용자, [사용자 추가·제외] → 그 문에 넣을 수 있는 사용자 목록에서
   새로 넣을 사람은 체크하고, 등록된 사람(체크된 채로 나온다)은 체크를 풀어 [완료]. */
(function () {
  const BASE = '/security/escort';
  const PAGE_SIZE = 200; // 추가할 수 있는 사용자 한 쪽 — BiostarX 화면과 같다
  const MAX_USERS = 300; // 출입문 하나에 넣을 수 있는 에스코트 사용자(서버도 같은 값으로 막는다)
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };
  const state = {
    doors: [],          // [{id, name, entryDeviceId, entryDeviceName}]
    door: null,         // 고른 문의 에스코트 설정 {doorId, doorName, enabled, scheduleName, users}
    page: 1, keyword: '', userGroupId: '', total: 0, shown: 0,
    picked: new Map(),  // 새로 넣을 사람 userId → name (쪽·검색을 바꿔도 유지)
    removed: new Set(), // 체크를 푼 등록자 userId — [완료] 때 빠진다
    groupsLoaded: false,
  };
  const registeredIds = () => new Set((state.door.users || []).map((u) => String(u.userId)));

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

  /* 등록된 사용자 — 에스코트가 꺼진 문은 버튼을 숨기고 사유를 보인다 */
  function renderUsers() {
    const d = state.door;
    const users = d.users || [];
    $('userTotal').textContent = `${d.doorName || '문 ' + d.doorId} — 등록된 사용자 ${users.length}명`;
    $('doorInfo').innerHTML = d.enabled
      ? `에스코트 사용 중${d.scheduleName ? ' · 일정 ' + esc(d.scheduleName) : ''}`
      : '<span class="text-danger">이 출입문은 에스코트(auth_door_control)가 꺼져 있어 사용자를 바꿀 수 없습니다. BiostarX 에서 먼저 켜세요.</span>';
    if ($('btnAddUser')) $('btnAddUser').hidden = !(d.enabled && PERM.canCreate);
    $('userBody').innerHTML = users.length
      ? users.map((u) => `<tr><td>${esc(u.userId)}</td><td style="text-align:left">${esc(u.name || '-')}</td></tr>`).join('')
      : '<tr><td colspan="2" class="empty">등록된 사용자가 없습니다.</td></tr>';
  }

  /* ---- 사용자 추가·제외 ---- */
  async function openModal() {
    if (!state.door || !state.door.enabled) return;
    Object.assign(state, { page: 1, keyword: '', userGroupId: '' });
    state.picked.clear(); state.removed.clear();
    $('userKeyword').value = ''; $('userGroupFilter').value = '';
    $('addModalTitle').textContent = `사용자 추가·제외 — ${state.door.doorName || '문 ' + state.door.doorId}`;
    $('addModal').classList.add('open');
    loadGroups();
    loadCandidates();
  }
  function closeModal() { $('addModal').classList.remove('open'); }

  /* 사용자 그룹 — 검색 조건용. 못 읽어도 '전체' 로 찾을 수 있으니 조용히 넘어간다 */
  async function loadGroups() {
    if (state.groupsLoaded) return;
    const groups = await api.get(BASE + '/userGroups', { quiet: true });
    if (!groups) return;
    state.groupsLoaded = true;
    $('userGroupFilter').innerHTML = '<option value="">전체</option>' + groups
      .slice().sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'ko'))
      .map((g) => `<option value="${esc(g.id)}">${esc(g.name || g.id)}</option>`).join('');
  }

  async function loadCandidates() {
    $('addBody').innerHTML = '<tr><td colspan="4" class="empty">불러오는 중...</td></tr>';
    const q = new URLSearchParams({ doorId: state.door.doorId, page: state.page, size: PAGE_SIZE, keyword: state.keyword });
    if (state.userGroupId) q.append('userGroupId', state.userGroupId);
    const res = await api.get(`${BASE}/users?${q}`);
    if (!res) { $('addBody').innerHTML = '<tr><td colspan="4" class="empty">조회 실패</td></tr>'; return; }
    renderCandidates(res);
  }

  /* 등록된 사람은 체크된 채로 나온다 — 풀면 제외(삭제 권한이 없으면 풀 수 없다) */
  function renderCandidates(res) {
    const registered = registeredIds();
    const rows = res.rows || [];
    $('addBody').innerHTML = rows.length
      ? rows.map((u) => {
        const id = String(u.userId);
        const isReg = registered.has(id);
        const out = isReg && state.removed.has(id);
        const checked = isReg ? !out : state.picked.has(id);
        const locked = isReg && !PERM.canDelete;
        const mark = isReg ? ` <span class="form-hint">(${out ? '제외 예정' : '등록됨'})</span>` : '';
        return `<tr class="row-click${out ? ' escort-out' : ''}">
          <td><input type="checkbox" data-id="${esc(id)}" data-name="${esc(u.name || '')}" data-reg="${isReg ? 'Y' : 'N'}"${checked ? ' checked' : ''}${locked ? ' disabled' : ''}/></td>
          <td>${esc(id)}</td>
          <td style="text-align:left">${esc(u.name || '-')}${mark}</td>
          <td style="text-align:left">${esc(u.groupName || '-')}</td></tr>`;
      }).join('')
      : '<tr><td colspan="4" class="empty">추가할 수 있는 사용자가 없습니다.</td></tr>';
    // 사용자 그룹으로 거르면 장비가 건수를 주지 않는다(total -1) — 다음 쪽이 있는지만 안다
    const known = res.total >= 0;
    const totalPages = known ? Math.max(1, Math.ceil(res.total / PAGE_SIZE)) : (res.more ? state.page + 1 : state.page);
    pager.render($('addPaging'), state.page, totalPages, (p) => { state.page = p; loadCandidates(); });
    state.total = known ? res.total : -1;
    state.shown = (state.page - 1) * PAGE_SIZE + rows.length;
    renderPicked();
  }

  function renderPicked() {
    const count = state.total >= 0 ? `${state.total}명` : `${state.shown}명 이상`;
    const after = afterCount();
    $('addInfo').textContent = `넣을 수 있는 사용자 ${count} · 추가 ${state.picked.size}명 · 제외 ${state.removed.size}명`
      + ` · 반영 후 ${after}/${MAX_USERS}명`;
    $('addInfo').classList.toggle('text-danger', overLimit(after));
    // 머리 체크 — 이 쪽에서 바꿀 수 있는 칸이 모두 체크면 체크, 일부면 반쯤
    const boxes = [...$('addBody').querySelectorAll('input[type="checkbox"]:not(:disabled)')];
    const on = boxes.filter((b) => b.checked).length;
    $('checkAll').checked = boxes.length > 0 && on === boxes.length;
    $('checkAll').indeterminate = on > 0 && on < boxes.length;
    $('checkAll').disabled = boxes.length === 0;
  }

  /* 한 칸의 체크를 상태에 옮긴다 — 등록자는 풀면 제외, 새 사람은 체크하면 추가 */
  function applyBox(cb) {
    const id = cb.dataset.id;
    if (cb.dataset.reg === 'Y') {
      if (cb.checked) state.removed.delete(id); else state.removed.add(id);
      const tr = cb.closest('tr');
      tr.classList.toggle('escort-out', !cb.checked);
      const hint = tr.querySelector('.form-hint'); if (hint) hint.textContent = cb.checked ? '(등록됨)' : '(제외 예정)';
    } else if (cb.checked) state.picked.set(id, cb.dataset.name); else state.picked.delete(id);
  }

  const nowCount = () => (state.door.users || []).length;
  const afterCount = () => nowCount() - state.removed.size + state.picked.size;
  /* 300명을 넘고 지금보다 늘어날 때만 막는다 — 이미 넘긴 문(BiostarX 에서 직접 넣은 경우)도 줄이는 것은 된다(서버 overLimit 과 같은 식) */
  const overLimit = (after) => after > MAX_USERS && after > nowCount();

  async function confirmAdd() {
    if (!state.picked.size && !state.removed.size) { toast.warning('추가하거나 뺄 사용자를 선택하세요.'); return; }
    const after = afterCount();
    if (overLimit(after)) {
      const excess = after - Math.max(MAX_USERS, nowCount());
      toast.warning(`에스코트 사용자는 출입문 하나에 ${MAX_USERS}명까지입니다. (반영 후 ${after}명 — ${excess}명을 빼 주세요)`);
      return;
    }
    const name = state.door.doorName || '문 ' + state.door.doorId;
    const parts = [];
    if (state.picked.size) parts.push(`${state.picked.size}명 추가`);
    if (state.removed.size) parts.push(`${state.removed.size}명 제외`);
    const ok = await confirmModal.open({
      title: '에스코트 사용자 반영', confirmText: '반영',
      message: `'${name}' 에 ${parts.join(' · ')} 하시겠습니까? BiostarX 에 바로 반영됩니다.`
        + (after <= 0 ? '\n\n반영하면 이 출입문에 에스코트 사용자가 한 명도 남지 않습니다.' : `\n\n반영 후 ${after}명`),
    });
    if (!ok) return;
    await api.put(BASE, { doorId: state.door.doorId, userIds: [...state.picked.keys()], removeIds: [...state.removed] }); // 결과 문구는 서버가 토스트로
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
    const search = () => {
      state.keyword = $('userKeyword').value.trim(); state.userGroupId = $('userGroupFilter').value; state.page = 1;
      loadCandidates();
    };
    $('btnUserSearch').addEventListener('click', search);
    $('userGroupFilter').addEventListener('change', search);
    $('userKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    // 이 쪽 전체 선택/해제 — 바꿀 수 있는 칸만(등록자 해제는 제외가 된다)
    $('checkAll').addEventListener('change', () => {
      $('addBody').querySelectorAll('input[type="checkbox"]:not(:disabled)').forEach((cb) => {
        if (cb.checked !== $('checkAll').checked) { cb.checked = $('checkAll').checked; applyBox(cb); }
      });
      renderPicked();
    });
    // 행을 눌러도 체크가 바뀐다(체크박스 직접 클릭은 기본 동작)
    $('addBody').addEventListener('click', (e) => {
      if (e.target.closest('input[type="checkbox"]')) return;
      const box = e.target.closest('tr')?.querySelector('input[type="checkbox"]');
      if (box && !box.disabled) { box.checked = !box.checked; box.dispatchEvent(new Event('change', { bubbles: true })); }
    });
    $('addBody').addEventListener('change', (e) => {
      const cb = e.target.closest('input[type="checkbox"]'); if (!cb || cb.disabled) return;
      applyBox(cb);
      renderPicked();
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadDoors(); });
})();
