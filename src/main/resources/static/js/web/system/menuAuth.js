/* 권한메뉴관리 화면 — 골든 샘플 구조 + 메뉴 권한 매트릭스(조회/등록·수정/삭제).
   권한(tb_menu_auth) 목록 CRUD, 편집 모달에서 메뉴별 CRUD 권한을 체크박스로 관리한다. */
(function () {
  const BASE = '/system/menuAuth';
  const state = { page: 1, size: 30, keyword: '', sort: 'authId', dir: 'asc' };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  let leafMenus = []; // [{menuId, menuName}] — 매트릭스 행

  async function loadMenus() {
    if (!PERM.canCreate) return;
    leafMenus = (await api.get(BASE + '/menus')) || [];
  }

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&sort=${state.sort}&dir=${state.dir}`;
    const data = await api.get(BASE + '/list' + q);
    renderRows(data.content);
    renderPaging(data.page, data.totalPages);
    $('totalInfo').textContent = `조회결과 ${data.total.toLocaleString()}`;
    renderSortIndicators();
  }

  function renderSortIndicators() {
    document.querySelectorAll('th.sortable').forEach((th) => {
      const ind = th.querySelector('.sort-ind');
      if (th.dataset.sort === state.sort) {
        ind.textContent = state.dir === 'asc' ? ' ▲' : ' ▼';
        th.classList.add('sorted');
      } else {
        ind.textContent = '';
        th.classList.remove('sorted');
      }
    });
  }

  function renderRows(rows) {
    const body = $('gridBody');
    if (!rows || rows.length === 0) {
      body.innerHTML = '<tr><td colspan="4" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      const actions = PERM.canDelete
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.authId)}">삭제</button>`
        : '-';
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.authId)}</td>
        <td>${esc(r.authName)}</td>
        <td>${esc(r.menuCount != null ? r.menuCount : 0)}</td>
        <td>${actions}</td>
      </tr>`;
    }).join('');
  }

  function renderPaging(page, totalPages) {
    const box = $('paging');
    if (!totalPages || totalPages <= 1) { box.innerHTML = ''; return; }
    let html = '';
    for (let i = 1; i <= totalPages; i++) {
      html += `<button data-page="${i}" class="${i === page ? 'active' : ''}">${i}</button>`;
    }
    box.innerHTML = html;
  }

  function search() {
    state.keyword = $('keyword').value.trim();
    state.page = 1;
    load();
  }
  function reset() {
    $('keyword').value = '';
    $('pageSize').value = '30';
    Object.assign(state, { page: 1, size: 30, keyword: '', sort: 'authId', dir: 'asc' });
    load();
  }
  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  async function excelDownload() {
    const purpose = await promptModal.open({
      title: '엑셀 다운로드', label: '다운로드 목적',
      placeholder: '다운로드 목적을 입력해주세요', confirmText: '다운로드',
    });
    if (!purpose) return;
    location.href = BASE + '/excel'
      + `?keyword=${encodeURIComponent(state.keyword)}&sort=${state.sort}&dir=${state.dir}`
      + `&purpose=${encodeURIComponent(purpose)}`;
  }

  // ---- 매트릭스 ----
  function buildMatrix(detailMap) {
    // detailMap: { menuId: {read, create, del} } — 편집 시 기존값, 등록 시 빈 객체
    $('matrixBody').innerHTML = leafMenus.map((m) => {
      const d = detailMap[m.menuId] || {};
      const chk = (col, on) =>
        `<input type="checkbox" data-menu="${esc(m.menuId)}" data-col="${col}"${on ? ' checked' : ''}/>`;
      return `
      <tr>
        <td style="text-align:left">${esc(m.menuName)}</td>
        <td>${chk('read', d.read)}</td>
        <td>${chk('create', d.create)}</td>
        <td>${chk('del', d.del)}</td>
      </tr>`;
    }).join('');
    document.querySelectorAll('.matrix-all input').forEach((c) => (c.checked = false));
  }

  function openModal(mode, row) {
    $('mode').value = mode;
    $('modalTitle').textContent = mode === 'create' ? '권한 등록' : '권한 수정';
    $('authId').value = row ? row.authId : '';
    $('authName').value = row ? row.authName || '' : '';
    if (mode === 'edit' && row) {
      api.get(BASE + '/detail?authId=' + encodeURIComponent(row.authId)).then((list) => {
        const map = {};
        (list || []).forEach((d) => {
          map[d.menuId] = { read: d.readAuth === 'Y', create: d.createAuth === 'Y', del: d.deleteAuth === 'Y' };
        });
        buildMatrix(map);
      });
    } else {
      buildMatrix({});
    }
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  function collectDetails() {
    return leafMenus.map((m) => {
      const q = (col) => document.querySelector(`#matrixBody input[data-menu="${m.menuId}"][data-col="${col}"]`);
      return {
        menuId: m.menuId,
        readAuth: q('read').checked ? 'Y' : 'N',
        createAuth: q('create').checked ? 'Y' : 'N',
        deleteAuth: q('del').checked ? 'Y' : 'N',
      };
    });
  }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      authId: $('authId').value ? Number($('authId').value) : null,
      authName: $('authName').value.trim(),
      details: collectDetails(),
    };
    if (!payload.authName) { toast.warning('권한명은 필수입니다.'); return; }
    if ($('mode').value === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(authId) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인', message: `선택한 권한(ID ${authId})을 삭제하시겠습니까?`, confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?authId=${encodeURIComponent(authId)}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));
    $('btnExcel').addEventListener('click', excelDownload);
    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);

    // 컬럼 전체 토글
    document.querySelectorAll('.matrix-all input').forEach((all) => {
      all.addEventListener('change', (e) => {
        const col = e.target.dataset.all;
        document.querySelectorAll(`#matrixBody input[data-col="${col}"]`).forEach((c) => (c.checked = e.target.checked));
      });
    });

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));

    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { if (btn.dataset.act === 'del') remove(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });
    $('paging').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { state.page = Number(btn.dataset.page); load(); }
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadMenus(); load(); });
})();
