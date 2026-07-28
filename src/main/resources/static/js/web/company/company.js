/* 기관등록관리 화면 — 골든 샘플(loginUser) 구조를 따른다.
   목록 공통: 검색조건 + 사용유무 필터 + 페이지크기 + 컬럼 정렬. 기관구분은 공통 코드팝업(CO).
   대표자(ceo_name)는 서버에서 ARIA 암호화. 삭제는 소프트 삭제. */
(function () {
  const BASE = '/company/company';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all', useYn: '', sort: 'companyCode', dir: 'asc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  // 메뉴 권한(서버 렌더 시 주입). 버튼 숨김은 1차 방어 — 서버가 생성/수정/삭제를 재검증한다.
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}&useYn=${state.useYn}` +
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
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.companyCode)}">삭제</button>`
        : '-';
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.companyCode)}</td>
        <td>${esc(r.companyTypeName)}</td>
        <td>${esc(r.companyName)}</td>
        <td>${esc(r.ceoName)}</td>
        <td>${esc(r.tel)}</td>
        <td>${r.useYn === 'Y' ? '사용' : '미사용'}</td>
        <td>${actions}</td>
      </tr>`;
    }).join('');
  }

  function renderPaging(page, totalPages) {
    pager.render($('paging'), page, totalPages, (p) => { state.page = p; load(); });
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
      page: 1, size: 30, keyword: '', searchType: 'all', useYn: '', sort: 'companyCode', dir: 'asc',
    });
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 엑셀 다운로드 (목적 입력 → 감사 remark, 현재 검색/정렬의 전체 데이터) ----
  async function excelDownload() {
    const purpose = await promptModal.open({
      title: '엑셀 다운로드', label: '다운로드 목적',
      placeholder: '다운로드 목적을 입력해주세요', confirmText: '다운로드',
    });
    if (!purpose) return;
    const q =
      `?keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}&useYn=${state.useYn}` +
      `&sort=${state.sort}&dir=${state.dir}&purpose=${encodeURIComponent(purpose)}`;
    location.href = BASE + '/excel' + q;
  }

  // ---- BiostarX 사용자그룹(=기관) 선택 팝업 (PTD01 하위만) ----
  let allGroups = []; // 조회된 전체 그룹(클라이언트 필터용)

  async function openGroupModal() {
    $('groupFilter').value = '';
    $('groupInfo').textContent = 'BiostarX 사용자그룹을 불러오는 중...';
    $('groupList').innerHTML = '<tr><td colspan="3" class="empty">불러오는 중...</td></tr>';
    $('groupModal').classList.add('open');
    const res = await api.get(BASE + '/biostarGroups'); // {success,message,groups}
    if (!res || !res.success) {
      $('groupInfo').textContent = (res && res.message) || 'BiostarX 사용자그룹 조회 실패';
      $('groupList').innerHTML = '<tr><td colspan="3" class="empty">조회 실패</td></tr>';
      allGroups = [];
      return;
    }
    allGroups = res.groups || [];
    $('groupInfo').textContent = `총 ${allGroups.length}개 — 그룹 1건을 선택하고 [선택]을 누르세요.`;
    renderGroupList();
  }

  // 그룹ID/그룹명으로 클라이언트 필터
  function renderGroupList() {
    const kw = $('groupFilter').value.trim().toLowerCase();
    const rows = kw
      ? allGroups.filter((g) => String(g.id).includes(kw) || (g.name || '').toLowerCase().includes(kw))
      : allGroups;
    $('groupList').innerHTML = rows.length
      ? rows.map((g) => `
        <tr>
          <td><input type="radio" name="groupPick" data-id="${esc(g.id)}"/></td>
          <td>${esc(g.id)}</td>
          <td style="text-align:left">${esc(g.name)}</td>
        </tr>`).join('')
      : '<tr><td colspan="3" class="empty">검색 결과가 없습니다.</td></tr>';
  }

  function closeGroupModal() { $('groupModal').classList.remove('open'); }

  function confirmGroup() {
    const sel = $('groupList').querySelector('input[name="groupPick"]:checked');
    if (!sel) { toast.warning('사용자그룹을 선택해주세요.'); return; }
    $('biostarGroupId').value = sel.dataset.id; // biostar_group_id = 그룹 id
    closeGroupModal();
  }

  // ---- 등록/수정 모달 ----
  function openModal(mode, row) {
    $('mode').value = mode;
    $('modalTitle').textContent = mode === 'create' ? '기관 등록' : '기관 수정';
    const isEdit = mode === 'edit';
    $('companyCode').value = row ? row.companyCode : '';
    $('companyCode').readOnly = isEdit; // PK 는 수정 불가
    $('companyType').value = row ? row.companyType || '' : '';
    $('companyTypeName').value = row ? row.companyTypeName || '' : ''; // 코드명 표시(조인)
    $('companyName').value = row ? row.companyName || '' : '';
    $('ceoName').value = row ? row.ceoName || '' : '';
    $('tel').value = row ? row.tel || '' : '';
    $('fax').value = row ? row.fax || '' : '';
    $('addr').value = row ? row.addr || '' : '';
    $('serviceStartDt').value = row ? row.serviceStartDt || '' : '';
    $('serviceEndDt').value = row ? row.serviceEndDt || '' : '';
    $('useYn').value = row ? row.useYn || 'Y' : 'Y';
    $('biostarGroupId').value = row && row.biostarGroupId != null ? row.biostarGroupId : '';
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      companyCode: $('companyCode').value.trim(),
      companyType: $('companyType').value || null,
      companyName: $('companyName').value.trim() || null,
      ceoName: $('ceoName').value.trim() || null,
      tel: $('tel').value.trim() || null,
      fax: $('fax').value.trim() || null,
      addr: $('addr').value.trim() || null,
      serviceStartDt: $('serviceStartDt').value || null,
      serviceEndDt: $('serviceEndDt').value || null,
      useYn: $('useYn').value,
      // 선택하면 그 그룹에 연결, 비우면 서버가 기관명으로 새 그룹 생성
      biostarGroupId: $('biostarGroupId').value ? Number($('biostarGroupId').value) : null,
    };
    if (!payload.companyCode) { toast.warning('기관코드는 필수입니다.'); return; }
    if (!payload.companyName) { toast.warning('기관명은 필수입니다.'); return; }
    if ($('mode').value === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(companyCode) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인', message: `선택한 기관(${companyCode})을 삭제하시겠습니까?`, confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?companyCode=${encodeURIComponent(companyCode)}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));

    // 기관구분: 공통 코드팝업(tb_common 'CO') 선택 → 코드/코드명 채움
    $('companyTypeName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CO', cmmName: '기관구분' });
      if (sel) {
        $('companyType').value = sel.codeId;
        $('companyTypeName').value = sel.codeName;
      }
    });
    // BiostarX 사용자그룹: 선택 팝업(PTD01 하위) → biostar_group_id 채움
    $('biostarGroupId').addEventListener('click', openGroupModal);
    $('groupClose').addEventListener('click', closeGroupModal);
    $('groupCancel').addEventListener('click', closeGroupModal);
    $('groupConfirm').addEventListener('click', confirmGroup);
    $('groupFilter').addEventListener('input', renderGroupList);
    $('groupModal').addEventListener('click', (e) => { if (e.target === $('groupModal')) closeGroupModal(); });
    // 행 클릭 → 라디오 체크
    $('groupList').addEventListener('click', (e) => {
      if (e.target.closest('input[type="radio"]')) return;
      const radio = e.target.closest('tr')?.querySelector('input[type="radio"]');
      if (radio) radio.checked = true;
    });

    $('btnExcel').addEventListener('click', excelDownload);
    if ($('btnExcelImport')) $('btnExcelImport').addEventListener('click', () => excelImport.open({
      baseUrl: BASE,
      hint: ['양식을 내려받아 기관 정보를 채운 뒤 파일을 선택해 업로드하세요.', '<b>기관코드·기관명</b>은 필수입니다.',
        '2행은 예시이니 지우거나 덮어써서 입력하세요(그대로 두면 등록 시 건너뜁니다).'],
      onDone: load,
    }));
    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);

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
