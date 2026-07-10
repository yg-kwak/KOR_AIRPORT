/* 공통코드관리 화면 — 골든 샘플. 신규 CRUD 화면 스크립트는 이 구조를 따른다.
   목록 공통 기능: 검색조건 + 사용여부 필터 + 페이지크기 + 컬럼 정렬(오름/내림). */
(function () {
  const BASE = '/system/commonCode';
  const state = {
    page: 1,
    size: 30,
    keyword: '',
    searchType: 'all',
    useYn: '',
    sort: 'cmmId',
    dir: 'asc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  // 메뉴 권한(서버 렌더 시 주입). 버튼 숨김은 1차 방어 — 서버가 생성/수정/삭제를 재검증한다.
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  // 사용자 추가 허용 코드구분 { cmmId: cmmName }. 등록 모달 select 에 사용(서버가 재검증).
  const groupNames = {};

  async function loadGroups() {
    if (!PERM.canCreate) return; // 등록 권한 없으면 불필요
    const list = await api.get(BASE + '/groups');
    (list || []).forEach((g) => { groupNames[g.cmmId] = g.cmmName || ''; });
  }

  // 등록 모달 select 옵션 구성. lockedCmmId 가 있으면(수정) 그 값만 표시하고 잠근다.
  function fillGroupSelect(lockedCmmId) {
    const sel = $('cmmId');
    if (lockedCmmId != null) {
      sel.innerHTML = `<option value="${esc(lockedCmmId)}">${esc(lockedCmmId)}</option>`;
      sel.value = lockedCmmId;
      sel.disabled = true;
      return;
    }
    sel.disabled = false;
    sel.innerHTML =
      '<option value="">선택</option>' +
      Object.keys(groupNames)
        .map((id) => `<option value="${esc(id)}">${esc(id)}</option>`)
        .join('');
    sel.value = '';
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
      // 시스템 코드(user_input='N')는 화면에서 수정/삭제 불가 — DB 관리 대상(서버가 2차 차단)
      const editable = r.userInput === 'Y';
      const actions = (PERM.canDelete && editable)
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-cmm="${esc(r.cmmId)}" data-code="${esc(r.codeId)}">삭제</button>`
        : '-';
      return `
      <tr${PERM.canCreate && editable ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.cmmId)}</td>
        <td>${esc(r.cmmName)}</td>
        <td>${esc(r.codeId)}</td>
        <td>${esc(r.codeName)}</td>
        <td>${r.useYn === 'Y' ? '사용' : '미사용'}</td>
        <td>${editable ? '사용자' : '<span class="tag-system">시스템</span>'}</td>
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
      page: 1, size: 30, keyword: '', searchType: 'all', useYn: '', sort: 'cmmId', dir: 'asc',
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
    location.href = BASE + '/excel' + q; // 브라우저 다운로드
  }

  // ---- 등록/수정 모달 ----
  function openModal(mode, row) {
    $('mode').value = mode;
    $('modalTitle').textContent = mode === 'create' ? '공통코드 등록' : '공통코드 수정';
    if (mode === 'create') {
      fillGroupSelect(null); // 허용 구분 목록 select
      $('cmmName').value = '';
    } else {
      fillGroupSelect(row.cmmId); // 수정: 구분 잠금
      $('cmmName').value = row.cmmName || '';
    }
    $('codeId').value = row ? row.codeId : '';
    $('codeName').value = row ? row.codeName || '' : '';
    $('useYn').value = row ? row.useYn || 'Y' : 'Y';
    $('codeId').readOnly = mode === 'edit';
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      cmmId: $('cmmId').value.trim(),
      cmmName: $('cmmName').value.trim(),
      codeId: $('codeId').value.trim(),
      codeName: $('codeName').value.trim(),
      useYn: $('useYn').value,
    };
    if (!payload.cmmId || !payload.codeId) { alert('코드구분ID와 코드ID는 필수입니다.'); return; }
    if (!payload.codeName) { alert('코드명을 입력해주세요.'); return; }
    if ($('mode').value === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(cmmId, codeId) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인',
      message: `선택한 공통코드(${cmmId}/${codeId})를 삭제하시겠습니까?`,
      confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?cmmId=${encodeURIComponent(cmmId)}&codeId=${encodeURIComponent(codeId)}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    // 등록 버튼은 create 권한이 없으면 서버 렌더에서 제외됨(th:if)
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));
    $('btnExcel').addEventListener('click', excelDownload);
    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);
    // 코드구분ID 선택 → 코드구분명 자동(읽기전용)
    $('cmmId').addEventListener('change', (e) => { $('cmmName').value = groupNames[e.target.value] || ''; });

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));

    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) {
        // 버튼 클릭은 행 클릭(수정 진입)과 분리
        if (btn.dataset.act === 'del') remove(btn.dataset.cmm, btn.dataset.code);
        return;
      }
      // 행 클릭 → 수정 모달 (create 권한 필요)
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });
    $('paging').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { state.page = Number(btn.dataset.page); load(); }
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadGroups(); load(); });
})();
