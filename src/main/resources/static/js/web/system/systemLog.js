/* 감사추적(tb_system_log) 화면 — 조회 전용(입력/수정/삭제 없음).
   기간·유형·검색어 필터 + 정렬(기본 최신순) + 엑셀 다운로드. */
(function () {
  const BASE = '/system/systemLog';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all',
    actionType: '', startDate: '', endDate: '', sort: 'regDt', dir: 'desc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' ').slice(0, 19));

  // ---- 기간 프리셋 ----
  const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  function periodRange(type) {
    const end = new Date();
    const start = new Date();
    if (type === '1m') start.setMonth(start.getMonth() - 1);
    else if (type === '3m') start.setMonth(start.getMonth() - 3);
    else if (type === '6m') start.setMonth(start.getMonth() - 6);
    else if (type === '1y') start.setFullYear(start.getFullYear() - 1);
    return { start: ymd(start), end: ymd(end) };
  }
  /** 기간 select 상태를 state 에 반영. custom 이면 date input 값 사용, 아니면 프리셋 계산. */
  function applyPeriod() {
    const t = $('periodType').value;
    if (t === 'custom') {
      state.startDate = $('startDate').value;
      state.endDate = $('endDate').value;
    } else {
      const r = periodRange(t);
      state.startDate = r.start;
      state.endDate = r.end;
    }
  }
  /** 기간 select 변경 시: 직접입력이면 date input 노출(기본 오늘), 아니면 숨김. */
  function onPeriodChange() {
    const custom = $('periodType').value === 'custom';
    $('dateRange').style.display = custom ? 'inline-flex' : 'none';
    if (custom) {
      const t = ymd(new Date());
      if (!$('startDate').value) $('startDate').value = t;
      if (!$('endDate').value) $('endDate').value = t;
    }
  }

  async function loadTypes() {
    const list = await api.get(BASE + '/types');
    $('actionTypeFilter').insertAdjacentHTML('beforeend',
      (list || []).map((c) => `<option value="${esc(c.codeId)}">${esc(c.codeName)}</option>`).join(''));
  }

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&actionType=${encodeURIComponent(state.actionType)}` +
      `&startDate=${state.startDate}&endDate=${state.endDate}` +
      `&sort=${state.sort}&dir=${state.dir}`;
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
      body.innerHTML = '<tr><td colspan="7" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => `
      <tr>
        <td>${esc(fmtDt(r.regDt))}</td>
        <td>${esc(r.userId)}</td>
        <td>${esc(r.userName)}</td>
        <td>${esc(r.actionTypeName || r.actionType)}</td>
        <td>${esc(r.menuName)}</td>
        <td style="text-align:left">${esc(r.actionDetail)}</td>
        <td style="text-align:left">${esc(r.remark)}</td>
      </tr>`).join('');
  }

  // 처음 « / 이전 ‹ / 번호(최대 5) / 다음 › / 마지막 »
  function renderPaging(page, totalPages) {
    const box = $('paging');
    if (!totalPages || totalPages <= 1) { box.innerHTML = ''; return; }
    const WIN = 5;
    let start = Math.max(1, page - Math.floor(WIN / 2));
    let end = Math.min(totalPages, start + WIN - 1);
    start = Math.max(1, end - WIN + 1);
    const btn = (label, target, disabled, active) =>
      `<button data-page="${target}"${disabled ? ' disabled' : ''}${active ? ' class="active"' : ''}>${label}</button>`;
    let html = '';
    html += btn('«', 1, page === 1);
    html += btn('‹', page - 1, page === 1);
    for (let i = start; i <= end; i++) html += btn(i, i, false, i === page);
    html += btn('›', page + 1, page === totalPages);
    html += btn('»', totalPages, page === totalPages);
    box.innerHTML = html;
  }

  function search() {
    state.keyword = $('keyword').value.trim();
    state.searchType = $('searchType').value;
    state.actionType = $('actionTypeFilter').value;
    applyPeriod();
    state.page = 1;
    load();
  }

  function reset() {
    $('keyword').value = '';
    $('searchType').value = 'all';
    $('actionTypeFilter').value = '';
    $('periodType').value = '1m'; // 초기화 시 다시 1개월
    $('startDate').value = '';
    $('endDate').value = '';
    onPeriodChange(); // date input 숨김
    $('pageSize').value = '30';
    Object.assign(state, {
      page: 1, size: 30, keyword: '', searchType: 'all',
      actionType: '', sort: 'regDt', dir: 'desc',
    });
    applyPeriod(); // state 에 1개월 범위 반영
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = col === 'regDt' ? 'desc' : 'asc'; }
    state.page = 1;
    load();
  }

  async function excelDownload() {
    const purpose = await promptModal.open({
      title: '엑셀 다운로드', label: '다운로드 목적',
      placeholder: '다운로드 목적을 입력해주세요', confirmText: '다운로드',
    });
    if (!purpose) return;
    const q =
      `?keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&actionType=${encodeURIComponent(state.actionType)}` +
      `&startDate=${state.startDate}&endDate=${state.endDate}` +
      `&sort=${state.sort}&dir=${state.dir}&purpose=${encodeURIComponent(purpose)}`;
    location.href = BASE + '/excel' + q;
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    $('btnExcel').addEventListener('click', excelDownload);
    $('periodType').addEventListener('change', onPeriodChange);
    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
    $('paging').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-page]');
      if (btn && !btn.disabled) { state.page = Number(btn.dataset.page); load(); }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    bind();
    applyPeriod(); // 기본 1개월 범위를 state 에 반영
    loadTypes();
    load();
  });
})();
