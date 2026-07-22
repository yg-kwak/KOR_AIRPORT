/* 기관차량등록 화면 — 기관 소속 차량(tb_car) + 차량용 카드(tb_card) 발급.
   차량 저장 후(수정 모드) 카드 발급이 열린다. 카드구분은 서버가 '차량'으로 고정하고 패스구분은 쓰지 않는다.
   회수는 삭제가 아니라 미배정(car_id=NULL) — 다른 차량이 같은 실물 카드를 다시 쓸 수 있다. */
(function () {
  const BASE = '/company/car';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all',
    companyCode: '', carType: '', sort: 'carId', dir: 'desc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' ').slice(0, 19));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&companyCode=${encodeURIComponent(state.companyCode)}&carType=${encodeURIComponent(state.carType)}` +
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
    // 카드가 발급된 차량은 삭제 불가 — 회수가 먼저다(서버도 거부한다)
    body.innerHTML = rows.map((r) => {
      const actions = !PERM.canDelete ? '-'
        : r.cardCount > 0
          ? '<span class="form-hint">카드있음</span>'
          : `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.carId)}">삭제</button>`;
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.companyName)}</td>
        <td>${esc(r.carNo)}</td>
        <td>${esc(r.carName)}</td>
        <td>${esc(r.carTypeName)}</td>
        <td>${r.cardCount > 0 ? esc(r.cardCount) + '장' : '<span class="form-hint">없음</span>'}</td>
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
    state.companyCode = $('companyFilter').value;
    state.carType = $('typeFilter').value;
    state.page = 1;
    load();
  }

  function reset() {
    ['searchType'].forEach((id) => { $(id).value = 'all'; });
    ['keyword', 'companyFilter', 'companyFilterName', 'typeFilter', 'typeFilterName']
      .forEach((id) => { $(id).value = ''; });
    $('pageSize').value = '30';
    Object.assign(state, {
      page: 1, size: 30, keyword: '', searchType: 'all',
      companyCode: '', carType: '', sort: 'carId', dir: 'desc',
    });
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 차량 등록/수정 모달 ----
  let editMode = 'create';

  async function openModal(mode, row) {
    editMode = mode;
    $('modalTitle').textContent = mode === 'create' ? '기관차량 등록' : '기관차량 수정';
    ['carId', 'companyCode', 'companyName', 'carNo', 'carType', 'carTypeName', 'carName']
      .forEach((id) => { $(id).value = ''; });
    closeIssue();
    // 카드 발급은 차량이 저장된 뒤에만(=수정 모드) 가능하다
    $('cardSection').style.display = mode === 'edit' ? '' : 'none';
    if ($('btnDelete')) $('btnDelete').style.display = mode === 'edit' ? '' : 'none';
    $('editModal').classList.add('open');
    if (mode !== 'edit' || !row) return;

    ['carId', 'companyCode', 'companyName', 'carNo', 'carType', 'carTypeName', 'carName']
      .forEach((id) => { $(id).value = row[id] != null ? row[id] : ''; });
    loadCards(row.carId);
  }
  function closeModal() { $('editModal').classList.remove('open'); closeIssue(); }

  async function loadCards(carId) {
    const rows = (await api.get(`${BASE}/cards?carId=${encodeURIComponent(carId)}`)) || [];
    $('cardBody').innerHTML = rows.length
      ? rows.map((c) => `
        <tr>
          <td>${esc(c.biostarCardValue)}</td>
          <td>${esc(c.cardName)}</td>
          <td>${esc(c.cardStatusName)}</td>
          <td><button type="button" class="btn btn-sm btn-danger" data-act="release"
                      data-id="${esc(c.cardId)}">회수</button></td>
        </tr>`).join('')
      : '<tr><td colspan="4" class="empty">발급된 카드가 없습니다.</td></tr>';
  }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      carId: $('carId').value ? Number($('carId').value) : null,
      companyCode: $('companyCode').value || null,
      carNo: $('carNo').value.trim() || null,
      carName: $('carName').value.trim() || null,
      carType: $('carType').value || null,
    };
    const required = [
      [payload.companyCode, '기관'], [payload.carNo, '차량번호'],
      [payload.carName, '차량명칭'], [payload.carType, '차종'],
    ].find(([v]) => !v);
    if (required) { toast.warning(`${required[1]}은(는) 필수입니다.`); return; }

    if (editMode === 'create') await api.post(BASE, payload);
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
    closeModal();
    load();
  }

  // ---- 차량용 카드 발급 패널 ----
  function openIssue() {
    ['cardNo', 'cardName', 'cardStatus', 'cardStatusName',
      'cardFeePaidDt', 'cardIssueReason', 'cardRemark'].forEach((id) => { $(id).value = ''; });
    $('issueModal').classList.add('open');
  }
  function closeIssue() { $('issueModal').classList.remove('open'); }

  async function issue() {
    const payload = {
      carId: Number($('carId').value),
      cardNo: $('cardNo').value.trim() || null,
      cardName: $('cardName').value.trim() || null,
      cardStatus: $('cardStatus').value || null,
      feePaidDt: $('cardFeePaidDt').value || null,
      issueReason: $('cardIssueReason').value.trim() || null,
      remark: $('cardRemark').value.trim() || null,
    };
    const required = [
      [payload.cardNo, '카드번호'], [payload.cardName, '카드명칭'], [payload.cardStatus, '카드상태'],
    ].find(([v]) => !v);
    if (required) { toast.warning(`${required[1]}은(는) 필수입니다.`); return; }
    await api.post(BASE + '/card', payload);
    closeIssue();
    loadCards(payload.carId);
    load();
  }

  async function scan() {
    const res = await api.post(BASE + '/card/scan', {});
    if (!res || !res.success) { toast.error((res && res.message) || '카드를 읽지 못했습니다.'); return; }
    $('cardNo').value = res.cardNo;
    toast.success('카드번호를 읽었습니다.');
  }

  async function releaseCard(cardId) {
    const ok = await confirmModal.open({
      title: '회수 확인',
      message: '카드를 회수하시겠습니까? 삭제되지 않고 다른 차량이 다시 쓸 수 있습니다.',
      confirmText: '회수',
    });
    if (!ok) return;
    await api.del(`${BASE}/card?cardId=${encodeURIComponent(cardId)}`);
    loadCards(Number($('carId').value));
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));
    if ($('btnDelete')) $('btnDelete').addEventListener('click', () => remove($('carId').value));

    // 검색조건 — 기관 팝업 / 차종 코드팝업 (선택·삭제(전체) 시 즉시 재조회)
    $('companyFilterName').addEventListener('click', async () => {
      const sel = await companyPicker.open();
      if (!sel) return;
      $('companyFilter').value = sel.companyCode;
      $('companyFilterName').value = sel.companyName;
      search();
    });
    $('typeFilterName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CT', cmmName: '차종' });
      if (!sel) return;
      $('typeFilter').value = sel.codeId;
      $('typeFilterName').value = sel.codeName;
      search();
    });
    ['companyFilterName', 'typeFilterName'].forEach((id) => {
      const wrap = $(id).closest('.picker-wrap');
      const clearBtn = wrap && wrap.querySelector('.picker-clear');
      if (clearBtn) clearBtn.addEventListener('click', () => setTimeout(search, 0));
    });

    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { if (btn.dataset.act === 'del') remove(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });
    $('cardBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-act="release"]');
      if (btn) releaseCard(btn.dataset.id);
    });

    // 모달 — 기관 팝업 / 차종 코드팝업
    $('companyName').addEventListener('click', async () => {
      const sel = await companyPicker.open();
      if (sel) { $('companyCode').value = sel.companyCode; $('companyName').value = sel.companyName; }
    });
    $('carTypeName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CT', cmmName: '차종' });
      if (sel) { $('carType').value = sel.codeId; $('carTypeName').value = sel.codeName; }
    });
    $('cardStatusName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CS', cmmName: '카드상태' });
      if (sel) { $('cardStatus').value = sel.codeId; $('cardStatusName').value = sel.codeName; }
    });

    $('cardNo').addEventListener('input', (e) => { e.target.value = e.target.value.replace(/\D/g, ''); });
    $('btnIssue').addEventListener('click', openIssue);
    $('btnCardScan').addEventListener('click', scan);
    $('issueOk').addEventListener('click', issue);
    $('issueCancel').addEventListener('click', closeIssue);
    $('issueClose').addEventListener('click', closeIssue);

    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); load(); });
})();
