/* 임시인원등록(방문) — 그룹/인솔자/방문객/차량 탭. 사용자출입그룹은 공용 ac-group-tree, 차량출입그룹은 CAR 체크박스.
   방문객=tb_person(person_type=방문유형), 차량=tb_car, 카드는 미할당 카드 select. 저장 시 BiostarX 편입(서버). */
(function () {
  const BASE = '/visitor/visitor';
  const AC_TREE = 'acTree';
  const state = { page: 1, size: 30, keyword: '', visitType: '', statusCode: '', sort: 'visitNo', dir: 'desc' };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' '));
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  let carCodes = []; // tb_common(CAR)
  let carTypes = []; // tb_common(CT)
  let freeCards = []; // 미할당 카드
  let editMode = 'create';

  // ---- 목록 ----
  async function load() {
    const q = `?page=${state.page}&size=${state.size}&keyword=${encodeURIComponent(state.keyword)}` +
      `&visitType=${encodeURIComponent(state.visitType)}&statusCode=${encodeURIComponent(state.statusCode)}` +
      `&sort=${state.sort}&dir=${state.dir}`;
    const data = await api.get(BASE + '/list' + q);
    const body = $('gridBody');
    if (!data.content || !data.content.length) {
      body.innerHTML = '<tr><td colspan="8" class="empty">조회 결과가 없습니다.</td></tr>';
    } else {
      body.innerHTML = data.content.map((r) => {
        const period = [fmtDt(r.workStartDt), fmtDt(r.workEndDt)].filter(Boolean).join(' ~ ');
        return `<tr class="row-click" data-no="${r.visitNo}">
          <td>${r.visitNo}</td><td>${esc(r.visitTypeName)}</td><td>${esc(r.companyName)}</td>
          <td>${esc(period)}</td><td>${r.personCount || 0}</td><td>${r.carCount || 0}</td>
          <td>${esc(r.statusName)}</td>
          <td>${PERM.canDelete ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${r.visitNo}">삭제</button>` : '-'}</td>
        </tr>`;
      }).join('');
    }
    pager.render($('paging'), data.page, data.totalPages, (p) => { state.page = p; load(); });
    $('totalInfo').textContent = `조회결과 ${data.total.toLocaleString()}`;
    renderSort();
  }

  function renderSort() {
    document.querySelectorAll('th.sortable').forEach((th) => {
      const ind = th.querySelector('.sort-ind');
      if (th.dataset.sort === state.sort) { ind.textContent = state.dir === 'asc' ? ' ▲' : ' ▼'; th.classList.add('sorted'); }
      else { ind.textContent = ''; th.classList.remove('sorted'); }
    });
  }

  function search() {
    state.keyword = $('keyword').value.trim();
    state.visitType = $('typeFilter').value;
    state.statusCode = $('statusFilter').value;
    state.page = 1;
    load();
  }
  function reset() {
    ['keyword', 'typeFilter', 'typeFilterName', 'statusFilter', 'statusFilterName'].forEach((id) => { $(id).value = ''; });
    Object.assign(state, { page: 1, size: 30, keyword: '', visitType: '', statusCode: '', sort: 'visitNo', dir: 'desc' });
    $('pageSize').value = '30';
    load();
  }

  // ---- 참조 데이터 ----
  async function loadRefs() {
    [carCodes, carTypes, freeCards] = await Promise.all([
      api.get('/system/common/picker?cmmId=CAR'),
      api.get('/system/common/picker?cmmId=CT'),
      api.get(BASE + '/cards/unassigned?keyword='),
    ]).then((a) => a.map((x) => x || []));
  }

  function carAcRender(checked) {
    const want = new Set(checked || []);
    $('carAcBox').innerHTML = carCodes.length
      ? carCodes.map((c) => `<label class="ac-select-item"><input type="checkbox" value="${esc(c.codeId)}"${want.has(c.codeId) ? ' checked' : ''}/><span>${esc(c.codeName)}</span></label>`).join('')
      : '<div class="empty">등록된 차량구역이 없습니다. (공통코드 CAR)</div>';
  }
  const carAcSelected = () => [...$('carAcBox').querySelectorAll('input:checked')].map((c) => c.value);

  // 카드 select 옵션 — 미할당 카드 + 현재 배정된 카드(id/label)
  function cardOptions(currentId, currentLabel) {
    let html = '<option value="">카드 없음</option>';
    if (currentId && !freeCards.some((c) => c.cardId === currentId)) {
      html += `<option value="${currentId}" selected>${esc(currentLabel || currentId)}</option>`;
    }
    html += freeCards.map((c) => `<option value="${c.cardId}"${c.cardId === currentId ? ' selected' : ''}>${esc(c.biostarCardValue)}</option>`).join('');
    return html;
  }

  // ---- 탭 ----
  function showTab(name) {
    document.querySelectorAll('.tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === 'tab-' + name));
  }

  // ---- 인솔자 ----
  let managers = []; // [{personId, personName}]
  function mgrRender() {
    $('mgrBody').innerHTML = managers.length
      ? managers.map((m, i) => `<tr><td>${esc(m.personId)}</td><td>${esc(m.personName)}</td>
          <td><button class="btn btn-sm btn-danger" data-act="mgr-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="3" class="empty">인솔자가 없습니다.</td></tr>';
  }

  // ---- 방문객 ----
  let visitors = []; // [{personId?, personName, birthDate, affiliation, cardId}]
  function visRender() {
    $('visBody').innerHTML = visitors.length
      ? visitors.map((v, i) => `<tr>
          <td><input class="input" data-f="personName" data-i="${i}" value="${esc(v.personName)}"/></td>
          <td><input class="input" data-f="birthDate" data-i="${i}" placeholder="1990-01-01" value="${esc(v.birthDate)}"/></td>
          <td><input class="input" data-f="affiliation" data-i="${i}" value="${esc(v.affiliation)}"/></td>
          <td><select class="input" data-f="cardId" data-i="${i}">${cardOptions(v.cardId, v.cardLabel)}</select></td>
          <td><button class="btn btn-sm btn-danger" data-act="vis-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">방문객이 없습니다.</td></tr>';
  }

  // ---- 차량 ----
  let cars = []; // [{carId?, carNo, carName, carType, cardId}]
  function carTypeOptions(sel) {
    return '<option value="">선택</option>' + carTypes.map((c) => `<option value="${c.codeId}"${c.codeId === sel ? ' selected' : ''}>${esc(c.codeName)}</option>`).join('');
  }
  function carRender() {
    $('carBody').innerHTML = cars.length
      ? cars.map((c, i) => `<tr>
          <td><input class="input" data-f="carNo" data-i="${i}" value="${esc(c.carNo)}"/></td>
          <td><input class="input" data-f="carName" data-i="${i}" value="${esc(c.carName)}"/></td>
          <td><select class="input" data-f="carType" data-i="${i}">${carTypeOptions(c.carType)}</select></td>
          <td><select class="input" data-f="cardId" data-i="${i}">${cardOptions(c.cardId, c.cardLabel)}</select></td>
          <td><button class="btn btn-sm btn-danger" data-act="car-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">차량이 없습니다.</td></tr>';
  }

  // ---- 모달 ----
  async function openModal(mode, visitNo) {
    editMode = mode;
    $('modalTitle').textContent = mode === 'create' ? '임시인원 등록' : '임시인원 수정';
    ['visitNo', 'visitType', 'visitTypeName', 'statusCode', 'statusName', 'companyName', 'companyType',
      'workStartDt', 'workEndDt', 'permitDt', 'receiver', 'returner', 'workPurpose', 'remark'].forEach((id) => { $(id).value = ''; });
    managers = []; visitors = []; cars = [];
    if ($('btnDelete')) $('btnDelete').style.display = mode === 'edit' ? '' : 'none';
    showTab('group');
    if (!freeCards.length && !carCodes.length) await loadRefs(); else await loadRefs();
    acGroupTree.set(AC_TREE, []);
    carAcRender([]);
    mgrRender(); visRender(); carRender();
    $('editModal').classList.add('open');
    if (mode !== 'edit') return;

    const d = await api.get(BASE + '/detail?visitNo=' + visitNo);
    const v = d.visit;
    $('visitNo').value = v.visitNo;
    [['visitType', v.visitType], ['visitTypeName', v.visitTypeName], ['statusCode', v.statusCode],
      ['statusName', v.statusName], ['companyName', v.companyName], ['companyType', v.companyType],
      ['workStartDt', v.workStartDt], ['workEndDt', v.workEndDt], ['permitDt', v.permitDt],
      ['receiver', v.receiver], ['returner', v.returner], ['workPurpose', v.workPurpose], ['remark', v.remark]]
      .forEach(([id, val]) => { $(id).value = val != null ? val : ''; });
    acGroupTree.set(AC_TREE, d.acGroupIds || []);
    carAcRender(d.carAcCodes || []);
    managers = (d.managerIds || []).map((pid) => ({ personId: pid, personName: '' }));
    mgrRender();
    visitors = (d.visitors || []).map((x) => ({ ...x }));
    visRender();
    cars = (d.cars || []).map((x) => ({ ...x }));
    carRender();
  }
  function closeModal() { $('editModal').classList.remove('open'); closeMgr(); }

  function collectRows() {
    // 인라인 input 값을 모델에 반영
    $('visBody').querySelectorAll('input,select').forEach((el) => {
      visitors[el.dataset.i][el.dataset.f] = el.value;
    });
    $('carBody').querySelectorAll('input,select').forEach((el) => {
      cars[el.dataset.i][el.dataset.f] = el.value;
    });
  }

  async function save() {
    if (!PERM.canCreate) return;
    collectRows();
    const payload = {
      visitNo: $('visitNo').value ? Number($('visitNo').value) : null,
      visitType: $('visitType').value || null, statusCode: $('statusCode').value || null,
      companyName: $('companyName').value.trim() || null, companyType: $('companyType').value.trim() || null,
      workStartDt: $('workStartDt').value || null, workEndDt: $('workEndDt').value || null,
      permitDt: $('permitDt').value || null, receiver: $('receiver').value.trim() || null,
      returner: $('returner').value.trim() || null, workPurpose: $('workPurpose').value.trim() || null,
      remark: $('remark').value.trim() || null,
      managerIds: managers.map((m) => m.personId),
      acGroupIds: acGroupTree.get(AC_TREE),
      carAcCodes: carAcSelected(),
      visitors: visitors.map((v) => ({ personId: v.personId || null, personName: (v.personName || '').trim() || null,
        birthDate: (v.birthDate || '').trim() || null, affiliation: (v.affiliation || '').trim() || null,
        cardId: v.cardId ? Number(v.cardId) : null })),
      cars: cars.map((c) => ({ carId: c.carId || null, carNo: (c.carNo || '').trim() || null,
        carName: (c.carName || '').trim() || null, carType: c.carType || null,
        cardId: c.cardId ? Number(c.cardId) : null })),
    };
    if (!payload.visitType) { toast.warning('방문유형은 필수입니다.'); return; }
    if (!payload.companyName) { toast.warning('업체명은 필수입니다.'); return; }
    if (payload.visitors.some((v) => !v.personName)) { toast.warning('방문객 성명은 필수입니다.'); return; }
    if (payload.cars.some((c) => !c.carNo)) { toast.warning('차량번호는 필수입니다.'); return; }

    if (editMode === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(visitNo) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({ title: '삭제 확인', message: `방문(${visitNo})을 삭제하시겠습니까?`, confirmText: '삭제' });
    if (!ok) return;
    await api.del(`${BASE}?visitNo=${visitNo}`);
    closeModal();
    load();
  }

  // ---- 인솔자 선택 팝업 ----
  let mgrPicked = null;
  async function openMgr() {
    mgrPicked = null;
    $('mgrModal').classList.add('open');
    loadMgrPick();
  }
  function closeMgr() { $('mgrModal').classList.remove('open'); }
  async function loadMgrPick() {
    const rows = (await api.get(BASE + '/managers?keyword=' + encodeURIComponent($('mgrKeyword').value.trim()))) || [];
    $('mgrPickBody').innerHTML = rows.length
      ? rows.map((p, i) => `<tr class="row-click mgr-row" data-idx="${i}">
          <td><input type="radio" name="mp" value="${i}"/></td><td>${esc(p.personId)}</td>
          <td style="text-align:left">${esc(p.personName)}</td></tr>`).join('')
      : '<tr><td colspan="3" class="empty">정규인원이 없습니다.</td></tr>';
    $('mgrPickBody').dataset.rows = JSON.stringify(rows);
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create'));
    // 검색조건 코드팝업
    $('typeFilterName').addEventListener('click', async () => {
      const s = await codePicker.open({ cmmId: 'PT', cmmName: '방문유형' });
      if (!s) return; $('typeFilter').value = s.codeId; $('typeFilterName').value = s.codeName; search();
    });
    $('statusFilterName').addEventListener('click', async () => {
      const s = await codePicker.open({ cmmId: 'VS', cmmName: '상태' });
      if (!s) return; $('statusFilter').value = s.codeId; $('statusFilterName').value = s.codeName; search();
    });
    document.querySelectorAll('.search-field .picker-field').forEach((el) => {
      const clr = el.closest('.picker-wrap') && el.closest('.picker-wrap').querySelector('.picker-clear');
      if (clr) clr.addEventListener('click', () => setTimeout(search, 0));
    });

    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { if (btn.dataset.act === 'del') remove(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-no]');
      if (tr && PERM.canCreate) openModal('edit', tr.dataset.no);
    });

    document.querySelectorAll('.tab-btn').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

    // 그룹 탭 코드팝업
    $('visitTypeName').addEventListener('click', async () => {
      const s = await codePicker.open({ cmmId: 'PT', cmmName: '방문유형' });
      if (s) { $('visitType').value = s.codeId; $('visitTypeName').value = s.codeName; }
    });
    $('statusName').addEventListener('click', async () => {
      const s = await codePicker.open({ cmmId: 'VS', cmmName: '상태' });
      if (s) { $('statusCode').value = s.codeId; $('statusName').value = s.codeName; }
    });

    // 인솔자/방문객/차량 목록 조작
    $('btnAddMgr').addEventListener('click', openMgr);
    $('mgrBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="mgr-del"]'); if (b) { managers.splice(b.dataset.idx, 1); mgrRender(); }
    });
    $('btnAddVis').addEventListener('click', () => { collectRows(); visitors.push({ personName: '', birthDate: '', affiliation: '', cardId: '' }); visRender(); });
    $('visBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="vis-del"]'); if (b) { collectRows(); visitors.splice(b.dataset.idx, 1); visRender(); }
    });
    $('btnAddCar').addEventListener('click', () => { collectRows(); cars.push({ carNo: '', carName: '', carType: '', cardId: '' }); carRender(); });
    $('carBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="car-del"]'); if (b) { collectRows(); cars.splice(b.dataset.idx, 1); carRender(); }
    });

    // 인솔자 팝업
    $('mgrKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadMgrPick(); });
    $('mgrPickBody').addEventListener('click', (e) => {
      const row = e.target.closest('.mgr-row'); if (!row) return;
      row.querySelector('input[type=radio]').checked = true;
      mgrPicked = JSON.parse($('mgrPickBody').dataset.rows)[Number(row.dataset.idx)];
    });
    $('mgrOk').addEventListener('click', () => {
      if (!mgrPicked) { toast.warning('인원을 선택하세요.'); return; }
      if (managers.some((m) => m.personId === mgrPicked.personId)) { toast.warning('이미 추가된 인솔자입니다.'); closeMgr(); return; }
      managers.push({ personId: mgrPicked.personId, personName: mgrPicked.personName }); mgrRender(); closeMgr();
    });
    $('mgrCancel').addEventListener('click', closeMgr);
    $('mgrClose').addEventListener('click', closeMgr);

    if ($('btnSave')) $('btnSave').addEventListener('click', save);
    if ($('btnDelete')) $('btnDelete').addEventListener('click', () => remove($('visitNo').value));
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);
    document.querySelectorAll('th.sortable').forEach((th) => th.addEventListener('click', () => {
      if (state.sort === th.dataset.sort) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
      else { state.sort = th.dataset.sort; state.dir = 'asc'; }
      state.page = 1; load();
    }));
  }

  document.addEventListener('DOMContentLoaded', () => {
    bind();
    acGroupTree.init(AC_TREE, BASE + '/acGroups');
    load();
  });
})();
