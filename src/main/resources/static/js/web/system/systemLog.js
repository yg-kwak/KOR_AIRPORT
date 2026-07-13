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

  let periodCtl; // 공통 기간 프리셋 컨트롤러(core/period.js)
  function applyPeriod() {
    const r = periodCtl.value();
    state.startDate = r.start;
    state.endDate = r.end;
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

  function renderPaging(page, totalPages) {
    pager.render($('paging'), page, totalPages, (p) => { state.page = p; load(); });
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
    periodCtl.reset('1m'); // 다시 1개월, date input 숨김
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
    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => {
    bind();
    periodCtl = period.attach($('periodType'), $('dateRange'), $('startDate'), $('endDate'));
    applyPeriod(); // 기본 1개월 범위를 state 에 반영
    loadTypes();
    load();
  });
})();
