/* 기관차량등록 화면 — 목록은 '기관'(삭제되지 않은 tb_company)이고, 차량·카드는 기관을 눌러 모달에서 다룬다.
   차량 자체의 마스터 관리는 차량등록관리(/carInfo/car) 담당. 여기는 기관 중심이다.
   카드구분은 서버가 '차량'으로 고정하고 패스구분은 쓰지 않는다. 회수는 삭제가 아니라 미배정(car_id=NULL). */
(function () {
  const BASE = '/company/car';
  const state = { page: 1, size: 30, keyword: '', searchType: 'all', useYn: '', sort: 'companyCode', dir: 'asc' };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' ').slice(0, 19));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  // ---- 기관 목록 ----
  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&useYn=${state.useYn}&sort=${state.sort}&dir=${state.dir}`;
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
      body.innerHTML = '<tr><td colspan="6" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => `
      <tr class="row-click" data-code="${esc(r.companyCode)}" data-name="${esc(r.companyName)}">
        <td>${esc(r.companyCode)}</td>
        <td>${esc(r.companyName)}</td>
        <td>${esc(r.companyTypeName)}</td>
        <td>${r.carCount > 0 ? esc(r.carCount) + '대' : '<span class="form-hint">없음</span>'}</td>
        <td>${r.useYn === 'Y' ? '사용' : '미사용'}</td>
        <td>${esc(fmtDt(r.regDt))}</td>
      </tr>`).join('');
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
    $('keyword').value = '';
    $('useYnFilter').value = '';
    $('pageSize').value = '30';
    Object.assign(state, { page: 1, size: 30, keyword: '', searchType: 'all', useYn: '', sort: 'companyCode', dir: 'asc' });
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 기관 모달(차량 목록) ----
  function openCompany(code, name) {
    $('companyCode').value = code;
    $('modalTitle').textContent = `기관 차량 — ${name} (${code})`;
    closeCarPanel();
    $('editModal').classList.add('open');
    loadCars();
  }
  function closeCompany() { $('editModal').classList.remove('open'); closeCarPanel(); }

  async function loadCars() {
    const code = $('companyCode').value;
    const rows = (await api.get(`${BASE}/cars?companyCode=${encodeURIComponent(code)}`)) || [];
    $('carBody').innerHTML = rows.length
      ? rows.map((c) => `
        <tr class="row-click" data-json='${esc(JSON.stringify(c))}'>
          <td>${esc(c.carNo)}</td>
          <td>${esc(c.carName)}</td>
          <td>${esc(c.carTypeName)}</td>
          <td>${c.cardCount > 0 ? esc(c.cardCount) + '장' : '<span class="form-hint">없음</span>'}</td>
        </tr>`).join('')
      : '<tr><td colspan="4" class="empty">등록된 차량이 없습니다.</td></tr>';
    load(); // 기관 목록의 등록차량 수 갱신
  }

  // ---- 차량 패널(우측) ----
  function openCarPanel(car) {
    const isNew = !car;
    $('carPanelTitle').textContent = isNew ? '차량 추가' : '차량 정보';
    ['carId', 'carNo', 'carName', 'carType', 'carTypeName'].forEach((id) => { $(id).value = ''; });
    if (!isNew) {
      ['carId', 'carNo', 'carName', 'carType', 'carTypeName']
        .forEach((id) => { $(id).value = car[id] != null ? car[id] : ''; });
    }
    // 카드는 저장된 차량에만 발급할 수 있다
    $('cardSection').style.display = isNew ? 'none' : '';
    if ($('btnCarDelete')) $('btnCarDelete').style.display = isNew ? 'none' : '';
    $('carPanel').classList.add('open');
    if (!isNew) loadCards(car.carId);
  }
  function closeCarPanel() { $('carPanel').classList.remove('open'); closeIssue(); }

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

  async function saveCar() {
    if (!PERM.canCreate) return;
    const payload = {
      carId: $('carId').value ? Number($('carId').value) : null,
      companyCode: $('companyCode').value || null,
      carNo: $('carNo').value.trim() || null,
      carName: $('carName').value.trim() || null,
      carType: $('carType').value || null,
    };
    const required = [
      [payload.carNo, '차량번호'], [payload.carName, '차량명칭'], [payload.carType, '차종'],
    ].find(([v]) => !v);
    if (required) { toast.warning(`${required[1]}은(는) 필수입니다.`); return; }

    if (payload.carId == null) await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeCarPanel();
    loadCars();
  }

  async function removeCar() {
    if (!PERM.canDelete) return;
    const carId = $('carId').value;
    if (!carId) return;
    const ok = await confirmModal.open({
      title: '삭제 확인', message: '선택한 차량을 삭제하시겠습니까?', confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?carId=${encodeURIComponent(carId)}`);
    closeCarPanel();
    loadCars();
  }

  // ---- 차량용 카드 발급 ----
  function openIssue() {
    ['cardNo', 'cardName', 'cardStatus', 'cardStatusName',
      'cardFeePaidDt', 'cardIssueReason', 'cardRemark'].forEach((id) => { $(id).value = ''; });
    $('issueModal').classList.add('open');
  }
  function closeIssue() { $('issueModal').classList.remove('open'); }

  async function issue() {
    const carId = Number($('carId').value);
    const payload = {
      carId,
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
    loadCards(carId);
    loadCars();
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
    loadCars();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('useYnFilter').addEventListener('change', search);
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });

    // 기관 행 클릭 → 모달
    $('gridBody').addEventListener('click', (e) => {
      const tr = e.target.closest('tr[data-code]');
      if (tr) openCompany(tr.dataset.code, tr.dataset.name);
    });
    // 차량 행 클릭 → 우측 패널
    $('carBody').addEventListener('click', (e) => {
      const tr = e.target.closest('tr[data-json]');
      if (tr) openCarPanel(JSON.parse(tr.dataset.json));
    });
    $('cardBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-act="release"]');
      if (btn) releaseCard(btn.dataset.id);
    });

    if ($('btnNewCar')) $('btnNewCar').addEventListener('click', () => openCarPanel(null));
    if ($('btnCarSave')) $('btnCarSave').addEventListener('click', saveCar);
    if ($('btnCarDelete')) $('btnCarDelete').addEventListener('click', removeCar);
    $('carPanelCancel').addEventListener('click', closeCarPanel);
    $('carPanelClose').addEventListener('click', closeCarPanel);
    $('btnCancel').addEventListener('click', closeCompany);
    $('modalClose').addEventListener('click', closeCompany);

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

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); load(); });
})();
