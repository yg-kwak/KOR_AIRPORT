/* 차량등록관리 화면 — 골든 샘플(loginUser) 구조를 따른다.
   목록 공통: 검색조건 + 페이지크기 + 컬럼 정렬. 차종은 공통 코드팝업(CT). 삭제는 소프트 삭제. */
(function () {
  const BASE = '/carInfo/car';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all', sort: 'carId', dir: 'desc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' ').slice(0, 19));

  // 메뉴 권한(서버 렌더 시 주입). 버튼 숨김은 1차 방어 — 서버가 생성/수정/삭제를 재검증한다.
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
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
      body.innerHTML = '<tr><td colspan="5" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      const actions = PERM.canDelete
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.carId)}">삭제</button>`
        : '-';
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.carNo)}</td>
        <td>${esc(r.carName)}</td>
        <td>${esc(r.carTypeName)}</td>
        <td>${esc(fmtDt(r.regDt))}</td>
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
    state.page = 1;
    load();
  }

  function reset() {
    $('searchType').value = 'all';
    $('keyword').value = '';
    $('pageSize').value = '30';
    Object.assign(state, { page: 1, size: 30, keyword: '', searchType: 'all', sort: 'carId', dir: 'desc' });
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
      `?keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&sort=${state.sort}&dir=${state.dir}&purpose=${encodeURIComponent(purpose)}`;
    location.href = BASE + '/excel' + q;
  }

  // ---- 등록/수정 모달 ----
  function openModal(mode, row) {
    $('mode').value = mode;
    $('modalTitle').textContent = mode === 'create' ? '차량 등록' : '차량 수정';
    $('carId').value = row ? row.carId : '';
    $('carNo').value = row ? row.carNo || '' : '';
    $('carName').value = row ? row.carName || '' : '';
    $('carType').value = row ? row.carType || '' : '';
    $('carTypeName').value = row ? row.carTypeName || '' : ''; // 코드명 표시(조인)
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      carId: $('carId').value ? Number($('carId').value) : null,
      carNo: $('carNo').value.trim(),
      carName: $('carName').value.trim() || null,
      carType: $('carType').value || null,
    };
    if (!payload.carNo) { toast.warning('차량번호는 필수입니다.'); return; }
    if ($('mode').value === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(carId) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인', message: '선택한 차량을 삭제하시겠습니까?', confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?carId=${encodeURIComponent(carId)}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));

    // 차종: 공통 코드팝업(tb_common 'CT') 선택 → 코드/코드명 채움
    $('carTypeName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CT', cmmName: '차종' });
      if (sel) {
        $('carType').value = sel.codeId;
        $('carTypeName').value = sel.codeName;
      }
    });
    $('btnExcel').addEventListener('click', excelDownload);
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
