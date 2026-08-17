/* 주차 조회(tb_parking_event) 화면 — 조회 전용(입력/수정/삭제 없음).
   주차서버가 밀어 준 입·출차 이력을 기간·구분·차량번호로 본다. */
(function () {
  const BASE = '/carInfo/parkingEvent';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all',
    direction: '', notOpen: false, startDate: '', endDate: '', sort: 'eventDt', dir: 'desc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' ').slice(0, 19));

  /* 주차서버가 주는 차량유형 — passType1~8 은 정기권 종별, 그 밖은 일반/방문예약. */
  const PASS_LABEL = { normal: '일반', visitor: '방문예약' };
  const passLabel = (v) => (v == null || v === '' ? '-'
    : PASS_LABEL[v] || (/^passType[1-8]$/.test(v) ? '정기권 ' + v.slice(8) : v));

  /* 번호를 읽지 못한 이벤트는 이 문자열로 온다(빈 값이 아니다). */
  const carLabel = (v) => (v === 'No_Detection' ? '미인식' : v);

  /* eventType 여섯 가지를 화면 두 칸으로 나눈다: 입/출차 + 차단기 열림 여부. */
  const isIn = (t) => String(t || '').startsWith('Entered');
  const opened = (t) => !String(t || '').endsWith('NotOpen');
  const isRear = (t) => String(t || '').includes('Rear');

  let periodCtl; // 공통 기간 프리셋 컨트롤러(core/period.js)
  function applyPeriod() {
    const r = periodCtl.value();
    state.startDate = r.start;
    state.endDate = r.end;
  }

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&direction=${state.direction}&notOpenOnly=${state.notOpen}` +
      `&startDate=${state.startDate}&endDate=${state.endDate}` +
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
      body.innerHTML = '<tr><td colspan="8" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => `
      <tr>
        <td>${esc(fmtDt(r.eventDt))}</td>
        <td>${isIn(r.eventType) ? '입차' : '출차'}${isRear(r.eventType) ? ' (후면)' : ''}</td>
        <td>${esc(carLabel(r.carNo))}</td>
        <td>${esc(r.carName || '-')}</td>
        <td style="text-align:left">${esc(r.companyName || '-')}</td>
        <td>${esc(passLabel(r.passType))}</td>
        <td>${opened(r.eventType) ? '열림' : '미개방'}</td>
        <td>${esc(fmtDt(r.inDt)) || '-'}</td>
      </tr>`).join('');
  }

  function renderPaging(page, totalPages) {
    pager.render($('paging'), page, totalPages, (p) => { state.page = p; load(); });
  }

  function search() {
    state.keyword = $('keyword').value.trim();
    state.searchType = $('searchType').value;
    state.direction = $('directionFilter').value;
    state.notOpen = $('openFilter').value === 'notOpen';
    applyPeriod();
    state.page = 1;
    load();
  }

  function reset() {
    $('keyword').value = '';
    $('searchType').value = 'all';
    $('directionFilter').value = '';
    $('openFilter').value = '';
    periodCtl.reset('1m'); // 다시 1개월, date input 숨김
    $('pageSize').value = '30';
    Object.assign(state, {
      page: 1, size: 30, keyword: '', searchType: 'all',
      direction: '', notOpen: false, sort: 'eventDt', dir: 'desc',
    });
    applyPeriod(); // state 에 1개월 범위 반영
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = col === 'eventDt' ? 'desc' : 'asc'; }
    state.page = 1;
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => {
    bind();
    periodCtl = period.attach($('periodType'), $('dateRange'), $('startDate'), $('endDate'));
    applyPeriod(); // 기본 1개월 범위를 state 에 반영
    load();
  });
})();
