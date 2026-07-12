/* 사용자관리 화면 — 골든 샘플(common) 구조를 따른다.
   목록 공통: 검색조건 + 사용여부 필터 + 페이지크기 + 컬럼 정렬. 등록/수정은 참조 select(권한/시작메뉴/근무지역) 사용.
   성명·비밀번호는 서버에서 ARIA 암호화. 비밀번호는 수정 시 빈 값이면 유지. */
(function () {
  const BASE = '/system/loginUser';
  const state = {
    page: 1,
    size: 30,
    keyword: '',
    searchType: 'all',
    useYn: '',
    sort: 'userId',
    dir: 'asc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  // 메뉴 권한(서버 렌더 시 주입). 버튼 숨김은 1차 방어 — 서버가 생성/수정/삭제를 재검증한다.
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  // 참조 옵션(권한/시작메뉴/근무지역) — 등록/수정 모달 select 에 사용.
  const refs = { auths: [], menus: [], locations: [] };

  async function loadRefs() {
    if (!PERM.canCreate) return; // 등록/수정 권한 없으면 불필요
    const data = await api.get(BASE + '/refs');
    refs.auths = data.auths || [];
    refs.menus = data.menus || [];
    refs.locations = data.locations || [];
    fillSelect('authId', refs.auths.map((a) => ({ v: a.authId, t: a.authName })));
    fillSelect('startMenuId', refs.menus.map((m) => ({ v: m.menuId, t: m.menuName })));
    fillSelect('workLocationCode', refs.locations.map((c) => ({ v: c.codeId, t: c.codeName })));
  }

  function fillSelect(id, opts) {
    const sel = $(id);
    sel.innerHTML =
      '<option value="">선택</option>' +
      opts.map((o) => `<option value="${esc(o.v)}">${esc(o.t)}</option>`).join('');
  }

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}` +
      `&searchType=${state.searchType}&useYn=${state.useYn}` +
      `&sort=${state.sort}&dir=${state.dir}`;
    const data = await api.get(BASE + '/list' + q);
    renderRows(data.content);
    renderPaging(data.page, data.totalPages);
    renderTotal(data.total);
    renderSortIndicators();
  }

  function renderTotal(total) {
    $('totalInfo').textContent = `조회결과 ${total.toLocaleString()}`;
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
      body.innerHTML = '<tr><td colspan="7" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      const actions = PERM.canDelete
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.userId)}">삭제</button>`
        : '-';
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.userId)}</td>
        <td>${esc(r.userName)}</td>
        <td>${esc(r.deptName)}</td>
        <td>${esc(r.authName)}</td>
        <td>${r.useYn === 'Y' ? '사용' : '미사용'}</td>
        <td>${r.rootYn === 'Y' ? '관리자' : '일반'}</td>
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
    state.searchType = $('searchType').value;
    state.useYn = $('useYnFilter').value;
    state.page = 1;
    load();
  }

  function reset() {
    $('searchType').value = 'all';
    $('useYnFilter').value = '';
    $('keyword').value = '';
    $('pageSize').value = '30';
    Object.assign(state, {
      page: 1, size: 30, keyword: '', searchType: 'all', useYn: '', sort: 'userId', dir: 'asc',
    });
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) {
      state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    } else {
      state.sort = col;
      state.dir = 'asc';
    }
    state.page = 1;
    load();
  }

  // ---- 엑셀 다운로드 (목적 입력 → 감사 remark 기록, 현재 검색/정렬의 전체 데이터) ----
  async function excelDownload() {
    const purpose = await promptModal.open({
      title: '엑셀 다운로드',
      label: '다운로드 목적',
      placeholder: '다운로드 목적을 입력해주세요',
      confirmText: '다운로드',
    });
    if (!purpose) return;
    const q =
      `?keyword=${encodeURIComponent(state.keyword)}` +
      `&searchType=${state.searchType}&useYn=${state.useYn}` +
      `&sort=${state.sort}&dir=${state.dir}` +
      `&purpose=${encodeURIComponent(purpose)}`;
    location.href = BASE + '/excel' + q;
  }

  // ---- 등록/수정 모달 ----
  function openModal(mode, row) {
    $('mode').value = mode;
    $('modalTitle').textContent = mode === 'create' ? '사용자 등록' : '사용자 수정';
    const isEdit = mode === 'edit';
    $('userId').value = row ? row.userId : '';
    $('userId').readOnly = isEdit; // PK 는 수정 불가
    $('userName').value = row ? row.userName || '' : '';
    $('deptName').value = row ? row.deptName || '' : '';
    $('authId').value = row && row.authId != null ? String(row.authId) : '';
    $('startMenuId').value = row && row.startMenuId != null ? String(row.startMenuId) : '';
    $('workLocationCode').value = row ? row.workLocationCode || '' : '';
    $('workType').value = row ? row.workType || '' : '';
    $('deskIp').value = row ? row.deskIp || '' : '';
    $('devId').value = row ? row.devId || '' : '';
    $('useYn').value = row ? row.useYn || 'Y' : 'Y';
    $('rootYn').value = row ? row.rootYn || 'N' : 'N';
    // 비밀번호: 등록=필수, 수정=변경 시에만 입력
    $('password').value = '';
    $('pwHint').textContent = isEdit ? '(변경 시에만 입력)' : '(필수)';
    $('password').placeholder = isEdit ? '변경하지 않으려면 비워두세요' : '';
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      userId: $('userId').value.trim(),
      userName: $('userName').value.trim(),
      password: $('password').value, // 빈 값이면 수정 시 유지
      deptName: $('deptName').value.trim(),
      authId: $('authId').value ? Number($('authId').value) : null,
      startMenuId: $('startMenuId').value ? Number($('startMenuId').value) : null,
      workLocationCode: $('workLocationCode').value || null,
      workType: $('workType').value.trim(),
      deskIp: $('deskIp').value.trim(),
      devId: $('devId').value.trim(),
      useYn: $('useYn').value,
      rootYn: $('rootYn').value,
    };
    if (!payload.userId) { toast.warning('사용자ID는 필수입니다.'); return; }
    if (!payload.userName) { toast.warning('성명은 필수입니다.'); return; }
    if ($('mode').value === 'create') {
      if (!payload.password) { toast.warning('비밀번호는 필수입니다.'); return; }
      await api.post(BASE, payload);
    } else {
      await api.put(BASE, payload);
    }
    closeModal();
    load();
  }

  async function remove(userId) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인',
      message: `선택한 사용자(${userId})를 삭제하시겠습니까?`,
      confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?userId=${encodeURIComponent(userId)}`);
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

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));

    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) {
        if (btn.dataset.act === 'del') remove(btn.dataset.id);
        return;
      }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });
    $('paging').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { state.page = Number(btn.dataset.page); load(); }
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadRefs(); load(); });
})();
