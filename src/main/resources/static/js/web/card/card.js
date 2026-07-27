/* 카드등록관리 화면 — 골든 샘플(car) 구조를 따른다.
   카드구분(CDT)·패스구분(PT)·카드상태(CS)는 공통 코드팝업. 등록 시 BiostarX 카드 생성까지 성공해야 저장된다.
   인원 부여/회수는 정규인원등록 화면 담당 — 여기서는 할당인원을 표시만 하고, 할당된 카드는 삭제할 수 없다. */
(function () {
  const BASE = '/card/card';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all',
    cardType: '', cardStatus: '', passType: '', assigned: '', sort: 'cardId', dir: 'desc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' ').slice(0, 19));

  // 메뉴 권한(서버 렌더 시 주입). 버튼 숨김은 1차 방어 — 서버가 생성/수정/삭제를 재검증한다.
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  const CARD_TYPE_CAR = 'CDT02'; // 차량 카드는 패스구분(사람의 출입 패스)을 쓰지 않는다

  // 폼 필드(= TbCard 속성명). *Name 은 화면 표시용이라 전송하지 않는다.
  const FORM_FIELDS = ['cardId', 'cardNo', 'cardType', 'passType', 'cardName', 'cardStatus',
    'feePaidDt', 'issueReason', 'remark'];
  const VIEW_FIELDS = ['cardTypeName', 'passTypeName', 'cardStatusName', 'personId'];

  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&cardType=${encodeURIComponent(state.cardType)}&cardStatus=${encodeURIComponent(state.cardStatus)}` +
      `&passType=${encodeURIComponent(state.passType)}` +
      `&assigned=${state.assigned}` +
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

  /** 카드의 발급 대상 — 인원ID 또는 '차량 {번호}'. 미발급이면 빈 문자열. */
  const holderOf = (c) => c.personId || (c.carNo || c.carId ? `차량 ${c.carNo || c.carId}` : '');

  function renderRows(rows) {
    const body = $('gridBody');
    if (!rows || rows.length === 0) {
      body.innerHTML = '<tr><td colspan="8" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    // 할당된 카드는 삭제 불가 — 정규인원등록에서 회수가 먼저다(서버도 거부한다)
    body.innerHTML = rows.map((r) => {
      const holder = holderOf(r); // 발급된 카드는 삭제할 수 없다 — 회수가 먼저다(서버도 거부)
      const actions = !PERM.canDelete ? '-'
        : holder
          ? '<span class="form-hint">발급중</span>'
          : `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.cardId)}">삭제</button>`;
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.biostarCardValue)}</td>
        <td>${esc(r.cardTypeName)}</td>
        <td>${esc(r.passTypeName)}</td>
        <td>${esc(r.cardName)}</td>
        <td>${esc(r.cardStatusName)}</td>
        <td>${esc(holder) || '<span class="form-hint">미발급</span>'}</td>
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
    state.cardType = $('typeFilter').value;
    state.cardStatus = $('statusFilter').value;
    state.passType = $('passFilter').value;
    state.assigned = $('assignedFilter').value;
    state.page = 1;
    load();
  }

  function reset() {
    $('searchType').value = 'all';
    $('keyword').value = '';
    $('typeFilter').value = '';
    $('typeFilterName').value = '';
    $('statusFilter').value = '';
    $('statusFilterName').value = '';
    $('passFilter').value = '';
    $('passFilterName').value = '';
    $('assignedFilter').value = '';
    $('pageSize').value = '30';
    Object.assign(state, {
      page: 1, size: 30, keyword: '', searchType: 'all',
      cardType: '', cardStatus: '', passType: '', assigned: '', sort: 'cardId', dir: 'desc',
    });
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 등록/수정 모달 ----
  let editMode = 'create';

  function openModal(mode, row) {
    editMode = mode;
    $('modalTitle').textContent = mode === 'create' ? '카드 등록' : '카드 수정';
    [...FORM_FIELDS, ...VIEW_FIELDS].forEach((id) => { $(id).value = ''; });
    // 카드번호는 실물 카드라 등록할 때만 입력한다
    $('cardNo').readOnly = mode === 'edit';
    $('btnScan').style.display = mode === 'create' ? '' : 'none';
    applyCardTypeRule();
    $('editModal').classList.add('open');
    if (mode !== 'edit' || !row) return;

    $('cardId').value = row.cardId;
    $('cardNo').value = row.biostarCardValue || '';
    ['cardType', 'cardTypeName', 'passType', 'passTypeName', 'cardName',
      'cardStatus', 'cardStatusName', 'feePaidDt', 'issueReason', 'remark']
      .forEach((id) => { $(id).value = row[id] != null ? row[id] : ''; });
    $('personId').value = holderOf(row); // 발급대상: 인원ID 또는 차량번호
    applyCardTypeRule();
  }
  /** 카드구분 규칙 — 차량 카드면 패스구분을 비우고 잠근다(필수값에서도 빠진다). */
  function applyCardTypeRule() {
    const isCar = $('cardType').value === CARD_TYPE_CAR;
    if (isCar) { $('passType').value = ''; $('passTypeName').value = ''; }
    $('passTypeName').disabled = isCar;
    $('passTypeName').placeholder = isCar ? '차량 카드는 사용하지 않습니다' : '클릭하여 선택';
    $('passTypeReq').style.display = isCar ? 'none' : '';
    const wrap = $('passTypeName').closest('.picker-wrap');
    if (wrap) wrap.querySelectorAll('.picker-clear').forEach((b) => { b.disabled = isCar; });
  }

  function closeModal() { $('editModal').classList.remove('open'); }

  async function scan() {
    const res = await api.post(BASE + '/scan', {});
    if (!res || !res.success) { toast.error((res && res.message) || '카드를 읽지 못했습니다.'); return; }
    $('cardNo').value = res.cardNo;
    toast.success('카드번호를 읽었습니다.');
  }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      cardId: $('cardId').value ? Number($('cardId').value) : null,
      biostarCardValue: $('cardNo').value.trim() || null,
      cardType: $('cardType').value || null,
      passType: $('passType').value || null,
      cardName: $('cardName').value.trim() || null,
      cardStatus: $('cardStatus').value || null,
      feePaidDt: $('feePaidDt').value || null,
      issueReason: $('issueReason').value.trim() || null,
      remark: $('remark').value.trim() || null,
    };
    const required = [
      [payload.biostarCardValue, '카드번호'], [payload.cardType, '카드구분'],
      // 차량 카드는 패스구분을 쓰지 않는다(서버도 같은 기준)
      ...(payload.cardType === CARD_TYPE_CAR ? [] : [[payload.passType, '패스구분']]),
      [payload.cardName, '카드명칭'], [payload.cardStatus, '카드상태'],
    ].find(([v]) => !v);
    if (required) { toast.warning(`${required[1]}은(는) 필수입니다.`); return; }

    if (editMode === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(cardId) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인',
      message: '선택한 카드를 삭제하시겠습니까? BiostarX 의 카드는 남습니다.',
      confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?cardId=${encodeURIComponent(cardId)}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('assignedFilter').addEventListener('change', search);
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));
    if ($('btnExcelImport')) $('btnExcelImport').addEventListener('click', () => excelImport.open({
      baseUrl: BASE,
      hint: '① 양식을 내려받아 카드정보를 채우고 ② 업로드하세요. <b>카드번호·카드구분·카드명칭</b>은 필수입니다.'
        + ' 카드구분·패스구분·카드상태는 공통코드 ID(예: CDT01, PT01, CS01)로 입력하고, 카드상태를 비우면 정상(CS01)으로 등록됩니다.'
        + ' 인원 카드는 BiostarX 장비 등록까지 성공해야 저장되며, 미발급 상태로 들어갑니다.',
      onDone: load,
    }));

    // 검색조건 코드팝업(카드구분·카드상태·패스구분) — 선택/삭제(전체) 시 즉시 재조회
    [['typeFilterName', 'typeFilter', 'CDT', '카드구분'],
      ['statusFilterName', 'statusFilter', 'CS', '카드상태'],
      ['passFilterName', 'passFilter', 'PT', '패스구분']].forEach(([nameId, codeId, cmmId, cmmName]) => {
      $(nameId).addEventListener('click', async () => {
        const sel = await codePicker.open({ cmmId, cmmName });
        if (!sel) return;
        $(codeId).value = sel.codeId;
        $(nameId).value = sel.codeName;
        search();
      });
      const wrap = $(nameId).closest('.picker-wrap');
      const clearBtn = wrap && wrap.querySelector('.picker-clear');
      if (clearBtn) clearBtn.addEventListener('click', () => setTimeout(search, 0));
    });

    // 행 클릭 → 수정, 삭제 버튼 → 삭제
    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { if (btn.dataset.act === 'del') remove(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });

    // 카드번호는 숫자만
    $('cardNo').addEventListener('input', (e) => { e.target.value = e.target.value.replace(/\D/g, ''); });
    $('btnScan').addEventListener('click', scan);

    // 카드구분(CDT)·패스구분(PT)·카드상태(CS)는 공통 코드팝업
    [['cardTypeName', 'cardType', 'CDT', '카드구분'],
      ['passTypeName', 'passType', 'PT', '패스구분'],
      ['cardStatusName', 'cardStatus', 'CS', '카드상태']].forEach(([nameId, codeId, cmmId, cmmName]) => {
      $(nameId).addEventListener('click', async () => {
        const sel = await codePicker.open({ cmmId, cmmName });
        if (sel) { $(codeId).value = sel.codeId; $(nameId).value = sel.codeName; }
        if (codeId === 'cardType') applyCardTypeRule();
      });
    });

    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); load(); });
})();
