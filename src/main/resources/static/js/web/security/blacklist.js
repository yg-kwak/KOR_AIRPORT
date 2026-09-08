/* 제재인원관리 — 골든 샘플(company) 구조를 따른다.
   성명·생년월일은 서버에서 ARIA 암호화하며, 그 쌍으로 임시·장기·정규 등록을 막는다.
   암호문이라 성명은 부분검색이 되지 않는다 — 검색은 소속·비고(평문) 또는 성명 완전일치. */
(function () {
  const BASE = '/security/blacklist';
  const INIT = { page: 1, size: 30, keyword: '', searchType: 'all', banState: '', sort: 'regDt', dir: 'desc' };
  const state = { ...INIT };

  const $ = (id) => document.getElementById(id);
  // 오늘 날짜(YYYY-MM-DD, 로컬 기준). toISOString 은 UTC 라 이른 아침에 하루 전으로 밀린다
  const today = () => {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  };
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  // 메뉴 권한(서버 렌더 시 주입). 버튼 숨김은 1차 방어 — 서버가 재검증한다.
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&banState=${state.banState}&sort=${state.sort}&dir=${state.dir}`;
    const data = await api.get(BASE + '/list' + q);
    renderRows(data.content);
    pager.render($('paging'), data.page, data.totalPages, (p) => { state.page = p; load(); });
    $('totalInfo').textContent = `조회결과 ${data.total.toLocaleString()}`;
    renderSortIndicators();
  }

  function renderSortIndicators() {
    document.querySelectorAll('th.sortable').forEach((th) => {
      const ind = th.querySelector('.sort-ind');
      const on = th.dataset.sort === state.sort;
      ind.textContent = on ? (state.dir === 'asc' ? ' ▲' : ' ▼') : '';
      th.classList.toggle('sorted', on);
    });
  }

  /* 상태는 색으로도 갈라 준다 — 목록에서 '지금 막히는 사람'이 한눈에 보여야 한다. */
  function statusBadge(s) {
    if (s === '정지 중') return badge.of(s, 'error');
    if (s === '예정') return badge.of(s, 'warning');
    if (s === '해제') return badge.of(s, 'done');
    return badge.of(s, 'none');
  }

  function renderRows(rows) {
    const body = $('gridBody');
    if (!rows || rows.length === 0) {
      body.innerHTML = '<tr><td colspan="9" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      // 이미 풀린 제재는 다시 풀 것도 고칠 것도 없다 — 이력으로만 남는다
      const released = r.delYn === 'Y';
      const actions = PERM.canDelete && !released
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${r.blacklistId}">해제</button>`
        : '-';
      const row = PERM.canCreate && !released ? ` class="row-click" data-json='${esc(JSON.stringify(r))}'` : '';
      return `
      <tr${row}>
        <td>${esc(r.personName)}</td>
        <td>${esc(r.birthDate)}</td>
        <td>${esc(r.affiliation)}</td>
        <td>${esc(r.banStartDt) || '-'}</td>
        <td>${esc(r.banEndDt) || '무기한'}</td>
        <td>${statusBadge(r.banStatus)}</td>
        <td>${esc(r.remark)}</td>
        <td>${esc((r.regDt || '').toString().slice(0, 10))}</td>
        <td>${actions}</td>
      </tr>`;
    }).join('');
  }

  function search() {
    state.keyword = $('keyword').value.trim();
    state.searchType = $('searchType').value;
    state.banState = $('banStateFilter').value;
    state.page = 1;
    load();
  }

  function reset() {
    $('searchType').value = 'all';
    $('banStateFilter').value = '';
    $('keyword').value = '';
    $('pageSize').value = '30';
    Object.assign(state, INIT);
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 모달 ----
  const FIELDS = ['blacklistId', 'personName', 'birthDate', 'affiliation', 'banStartDt', 'banEndDt', 'remark'];

  function openModal(mode, row) {
    $('modalTitle').textContent = mode === 'create' ? '제재인원 등록' : '제재인원 수정';
    FIELDS.forEach((id) => { $(id).value = ''; });
    // 등록은 대개 '오늘부터' 막는다 — 비워 두면 언제부터였는지 이력에 남지 않는다
    if (mode === 'create') $('banStartDt').value = today();
    if (row) FIELDS.forEach((id) => { $(id).value = row[id] != null ? row[id] : ''; });
    $('editModal').classList.add('open');
  }

  function closeModal() { $('editModal').classList.remove('open'); }

  async function save() {
    const payload = {
      blacklistId: $('blacklistId').value ? Number($('blacklistId').value) : null,
      personName: $('personName').value.trim() || null,
      birthDate: birthDate.normalize($('birthDate').value) || null,
      affiliation: $('affiliation').value.trim() || null,
      banStartDt: $('banStartDt').value || null,
      banEndDt: $('banEndDt').value || null,
      remark: $('remark').value.trim() || null,
    };
    if (!payload.personName) { toast.warning('성명은 필수입니다.'); return; }
    if (!payload.birthDate) { toast.warning('생년월일은 필수입니다.'); return; }
    if (!birthDate.isValid(payload.birthDate)) { toast.warning(birthDate.HINT); return; }
    if (payload.banStartDt && payload.banEndDt && payload.banStartDt > payload.banEndDt) {
      toast.warning('정지기간 시작은 종료보다 늦을 수 없습니다.'); return;
    }
    if (payload.blacklistId == null) await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  /* 해제는 되돌리기 어려운 축이라 확인을 받는다 — 푸는 순간 그 사람의 등록이 다시 열린다. */
  async function remove(id) {
    const ok = await confirmModal.open({
      title: '제재 해제',
      message: '해제하면 이 사람의 임시·장기·정규 등록이 다시 가능해집니다. 해제하시겠습니까?',
      confirmText: '해제',
    });
    if (!ok) return;
    await api.del(`${BASE}?blacklistId=${id}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', () => { state.size = Number($('pageSize').value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));
    $('btnSave').addEventListener('click', save);
    $('modalCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);
    $('editModal').addEventListener('click', (e) => { if (e.target === $('editModal')) closeModal(); });
    birthDate.bindInput($('birthDate')); // 입력 보정은 공용 규칙(core/birth-date.js)

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));

    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { if (btn.dataset.act === 'del') remove(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); load(); });
})();
