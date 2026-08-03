/* 임시인원등록(방문) — 그룹/인솔자/방문객/차량 탭. 카드는 검색 팝업 선택(방문객=스캔 지원, 차량=스캔 없음).
   방문객=tb_person, 차량=tb_car. 저장 시 방문객을 BiostarX 사용자로 편입하고 카드/출입그룹 전달(서버). */
(function () {
  const CFG = window.VISIT_CFG || {};
  const BASE = CFG.base || '/visitor/visitor'; // 임시=/visitor/visitor, 장기=/visitor/longterm
  const AC_TREE = 'acTree';
  // fixedType 있으면 방문유형 고정(임시=PT02), 없으면 화면 select 값 사용(장기=PTD03 선택)
  const VISIT_TYPE = CFG.fixedType ? { id: CFG.fixedType, name: CFG.fixedTypeName } : null;
  const state = { page: 1, size: 30, keyword: '', searchType: 'all', statusCode: '', sort: 'visitNo', dir: 'desc' };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' '));
  const pad2 = (n) => String(n).padStart(2, '0');
  // 오늘 날짜의 datetime-local 값. now=true 면 현재 시각, 아니면 hh:mm
  const todayAt = (hh, mm, now) => {
    const d = new Date();
    const date = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
    return `${date}T${now ? pad2(d.getHours()) + ':' + pad2(d.getMinutes()) : pad2(hh) + ':' + pad2(mm)}`;
  };
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  let carCodes = [], carTypes = [], editMode = 'create'; // 차량구역(CAR)·차종(CT) 공통코드 / 모달 모드

  // ---- 목록 ----
  async function load() {
    const q = `?page=${state.page}&size=${state.size}&keyword=${encodeURIComponent(state.keyword)}` +
      `&searchType=${state.searchType}&statusCode=${encodeURIComponent(state.statusCode)}` +
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
          <td>${badge.visitStatus(r.statusCode, r.statusName)}</td>
          <td>${r.statusCode === 'VS03' && PERM.canCreate ? `<button class="btn btn-sm" data-act="checkout" data-id="${r.visitNo}">퇴실</button>` : '-'}</td>
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
    state.searchType = $('searchType').value;
    state.statusCode = $('statusFilter').value;
    state.page = 1;
    load();
  }
  function reset() {
    ['keyword', 'statusFilter', 'statusFilterName'].forEach((id) => { $(id).value = ''; });
    $('searchType').value = 'all';
    Object.assign(state, { page: 1, size: 30, keyword: '', searchType: 'all', statusCode: '', sort: 'visitNo', dir: 'desc' });
    $('pageSize').value = '30';
    load();
  }

  // ---- 참조 데이터 ----
  async function loadRefs() {
    const refs = await Promise.all(['CAR', 'CT'].map((c) => api.get('/system/common/picker?cmmId=' + c)));
    [carCodes, carTypes] = refs.map((x) => x || []);
  }

  function carAcRender(checked) {
    const want = new Set(checked || []);
    $('carAcBox').innerHTML = carCodes.length
      ? carCodes.map((c) => `<label class="ac-select-item"><input type="checkbox" value="${esc(c.codeId)}"${want.has(c.codeId) ? ' checked' : ''}/><span>${esc(c.codeName)}</span></label>`).join('')
      : '<div class="empty">등록된 차량구역이 없습니다. (공통코드 CAR)</div>';
  }
  const carAcSelected = () => [...$('carAcBox').querySelectorAll('input:checked')].map((c) => c.value);

  // 카드 셀 — 선택된 카드번호 표시 + 선택 버튼(팝업). kind=vis|car
  function cardCell(obj, i, kind) {
    const label = obj.cardId ? esc(obj.cardLabel || obj.cardId) : badge.none(obj.lastCardNo ? `회수됨(${obj.lastCardNo})` : '카드 없음');
    return `<div class="file-field-row">
      <span class="card-picked" data-i="${i}" style="min-width:90px">${label}</span>
      <button type="button" class="btn btn-sm" data-act="${kind}-card" data-idx="${i}">선택</button></div>`;
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
          <td>${v.personId ? esc(v.personId) : badge.none('저장 후 부여')}</td>
          <td><input class="input" data-f="personName" data-i="${i}" value="${esc(v.personName)}"/></td>
          <td><input class="input" data-f="birthDate" data-i="${i}" placeholder="1990-01-01" value="${esc(v.birthDate)}"/></td>
          <td><input class="input" data-f="affiliation" data-i="${i}" value="${esc(v.affiliation)}"/></td>
          <td>${cardCell(v, i, 'vis')}</td>
          <td><button class="btn btn-sm btn-danger" data-act="vis-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="6" class="empty">방문객이 없습니다.</td></tr>';
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
          <td>${cardCell(c, i, 'car')}</td>
          <td><button class="btn btn-sm btn-danger" data-act="car-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">차량이 없습니다.</td></tr>';
  }

  // ---- 모달 ----
  async function openModal(mode, visitNo) {
    editMode = mode;
    $('modalTitle').textContent = mode === 'create' ? '방문 등록' : '방문 수정';
    ['visitNo', 'visitType', 'visitTypeName', 'statusCode', 'statusName', 'companyName', 'companyType',
      'workStartDt', 'workEndDt', 'permitDt', 'receiver', 'returner', 'workPurpose', 'remark']
      .forEach((id) => { const el = $(id); if (el) el.value = ''; });
    managers = []; visitors = []; cars = [];
    if (VISIT_TYPE) { $('visitType').value = VISIT_TYPE.id; if ($('visitTypeName')) $('visitTypeName').value = VISIT_TYPE.name; } // 임시 고정
    if (mode === 'create') { // 작업기간 기본: 시작=오늘 현재시각, 종료=오늘 18:00
      $('workStartDt').value = todayAt(0, 0, true);
      $('workEndDt').value = todayAt(18, 0, false);
    }
    if ($('btnDelete')) $('btnDelete').style.display = 'none'; // 삭제는 신청/신청취소일 때만(로드 후 노출)
    if ($('btnSave')) $('btnSave').style.display = ''; // 퇴실완료면 로드 후 숨김(읽기전용)
    $('editModal').querySelector('.visit-modal').classList.remove('readonly'); // 읽기전용 해제(VS04면 로드 후 재설정)
    showTab('group');
    await loadRefs();
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
      .forEach(([id, val]) => { const el = $(id); if (el) el.value = val != null ? val : ''; });
    // 삭제 버튼: 신청(VS01)·신청취소(VS02)만 노출 / 저장: 퇴실완료(VS04)면 숨김(수정 불가)
    if ($('btnDelete')) $('btnDelete').style.display = (v.statusCode === 'VS01' || v.statusCode === 'VS02') ? '' : 'none';
    if ($('btnSave')) $('btnSave').style.display = v.statusCode === 'VS04' ? 'none' : '';
    if (v.statusCode === 'VS04') { $('editModal').querySelector('.visit-modal').classList.add('readonly'); $('modalTitle').textContent = '방문 상세 (퇴실완료 — 수정 불가)'; } // 읽기전용
    acGroupTree.set(AC_TREE, d.acGroupIds || []);
    carAcRender(d.carAcCodes || []);
    managers = (d.managers || []).map((m) => ({ personId: m.personId, personName: m.personName || '' }));
    mgrRender();
    visitors = (d.visitors || []).map((x) => ({ ...x }));
    visRender();
    cars = (d.cars || []).map((x) => ({ ...x }));
    carRender();
  }
  function closeModal() { $('editModal').classList.remove('open'); closeMgr(); }

  function collectRows() {
    // 인라인 input 값을 모델에 반영
    $('visBody').querySelectorAll('input,select').forEach((el) => { visitors[el.dataset.i][el.dataset.f] = el.value; });
    $('carBody').querySelectorAll('input,select').forEach((el) => { cars[el.dataset.i][el.dataset.f] = el.value; });
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
    if (!payload.visitType) { toast.warning('방문유형을 선택하세요.'); return; }
    if (!payload.companyName) { toast.warning('업체명은 필수입니다.'); return; }
    if (payload.visitors.some((v) => !v.personName)) { toast.warning('방문객 성명은 필수입니다.'); return; }
    // 차량은 선택이지만, 행을 추가했으면 차량번호는 필수
    if (payload.cars.some((c) => !c.carNo)) { toast.warning('차량번호는 필수입니다.'); return; }
    const hasVis = payload.visitors.length > 0, hasCar = payload.cars.length > 0;
    if (payload.acGroupIds.length && !hasVis) { toast.warning('사용자 출입그룹을 선택하면 방문객을 입력해야 합니다.'); return; }
    if (payload.carAcCodes.length && !hasCar) { toast.warning('차량 출입그룹을 선택하면 차량을 입력해야 합니다.'); return; }
    if (hasVis && !payload.managerIds.length) { toast.warning('방문객이 있으면 인솔자를 지정해야 합니다.'); return; }
    // 카드 발급(cardId) 시 해당 출입구역 미선택이면 무효 카드가 되므로 구역 선택을 강제
    if (payload.visitors.some((v) => v.cardId) && !payload.acGroupIds.length) { toast.warning('방문객에게 카드를 발급하려면 인원 출입구역을 선택하세요.'); return; }
    if (payload.cars.some((c) => c.cardId) && !payload.carAcCodes.length) { toast.warning('차량에 카드를 발급하려면 차량 출입구역을 선택하세요.'); return; }
    if (editMode === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal(); load();
  }

  async function remove(visitNo) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({ title: '삭제 확인', message: `방문(${visitNo})을 삭제하시겠습니까?`, confirmText: '삭제' });
    if (!ok) return;
    await api.del(`${BASE}?visitNo=${visitNo}`);
    closeModal(); load();
  }

  // 퇴실 — 입실중 방문만. BiostarX 비활성화 + 카드 회수(재대여 가능)
  async function checkout(visitNo) {
    if (!PERM.canCreate) return;
    const ok = await confirmModal.open({ title: '퇴실 확인', confirmText: '퇴실',
      message: `방문(${visitNo})을 퇴실 처리하시겠습니까? 카드가 회수되고 BiostarX 사용자가 비활성화됩니다.` });
    if (!ok) return;
    await api.post(`${BASE}/checkout?visitNo=${visitNo}`, {});
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

  // ---- 카드 선택 팝업 (방문객=검색+스캔 / 차량=검색만) ----
  let cardPick = { kind: null, index: -1, chosen: null };
  function openCardPicker(kind, index) {
    collectRows();
    cardPick = { kind, index, chosen: null };
    $('vcpTitle').textContent = kind === 'vis' ? '방문객 카드 선택' : '차량 카드 선택';
    $('vcpScan').style.display = kind === 'vis' ? '' : 'none'; // 차량카드는 스캔 없음
    $('vcpKeyword').value = '';
    $('vcpModal').classList.add('open'); loadCardPick();
  }
  function closeCardPicker() { $('vcpModal').classList.remove('open'); }
  async function loadCardPick() {
    const base = cardPick.kind === 'car' ? BASE + '/cards/unassigned/car' : BASE + '/cards/unassigned';
    const all = (await api.get(base + '?keyword=' + encodeURIComponent($('vcpKeyword').value.trim()))) || [];
    // 이미 고른 카드는 뺀다(한 실물 카드는 한 사람에게만 — 서버도 거부). 편집 중인 행의 카드는 남긴다
    const mine = ((cardPick.kind === 'car' ? cars : visitors)[cardPick.index] || {}).cardId;
    const used = new Set([...visitors, ...cars].map((o) => o.cardId).filter((id) => id != null && id !== mine));
    const rows = all.filter((c) => !used.has(c.cardId));
    $('vcpBody').innerHTML = rows.length
      ? rows.map((c, i) => `<tr class="row-click vcp-row" data-idx="${i}">
          <td><input type="radio" name="vcp" value="${i}"/></td>
          <td>${esc(c.biostarCardValue)}</td>
          <td style="text-align:left">${esc(c.cardName)}</td>
          <td>${badge.cardStatus(c.cardStatus, c.cardStatusName)}</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">미할당 카드가 없습니다.</td></tr>';
    $('vcpBody').dataset.rows = JSON.stringify(rows);
  }
  async function scanCardPick() {
    const res = await api.post(BASE + '/card/scan', {});
    if (!res || !res.success) { toast.warning((res && res.message) || '카드 스캔에 실패했습니다.'); return; }
    $('vcpKeyword').value = res.cardNo || '';
    await loadCardPick();
    const rows = JSON.parse($('vcpBody').dataset.rows || '[]');
    const idx = rows.findIndex((c) => String(c.biostarCardValue) === String(res.cardNo));
    const tr = idx >= 0 && $('vcpBody').querySelector(`.vcp-row[data-idx="${idx}"]`);
    if (tr) { tr.querySelector('input[type=radio]').checked = true; cardPick.chosen = rows[idx]; }
    else toast.warning('스캔한 카드가 미할당 목록에 없습니다. (카드관리에서 등록·회수 여부 확인)');
  }
  function applyCardPick(card) {
    const row = (cardPick.kind === 'car' ? cars : visitors)[cardPick.index];
    if (!row) return;
    row.cardId = card ? Number(card.cardId) : null;
    row.cardLabel = card ? card.biostarCardValue : '';
    (cardPick.kind === 'car' ? carRender : visRender)(); closeCardPicker();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create'));
    // 검색조건 코드팝업
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
      if (btn) { if (btn.dataset.act === 'checkout') checkout(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-no]');
      if (tr && PERM.canCreate) openModal('edit', tr.dataset.no);
    });

    document.querySelectorAll('.tab-btn').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));
    // 방문유형·상태는 사용자가 변경 불가(고정/서버관리) — 모달 코드팝업 없음

    // 인솔자/방문객/차량 목록 조작
    $('btnAddMgr').addEventListener('click', openMgr);
    $('mgrBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="mgr-del"]'); if (b) { managers.splice(b.dataset.idx, 1); mgrRender(); }
    });
    $('btnAddVis').addEventListener('click', () => { collectRows(); visitors.push({ personName: '', birthDate: '', affiliation: '', cardId: null, cardLabel: '' }); visRender(); });
    $('visBody').addEventListener('click', (e) => {
      const del = e.target.closest('button[data-act="vis-del"]'); if (del) { collectRows(); visitors.splice(del.dataset.idx, 1); visRender(); return; }
      const card = e.target.closest('button[data-act="vis-card"]'); if (card) openCardPicker('vis', Number(card.dataset.idx));
    });
    $('btnAddCar').addEventListener('click', () => { collectRows(); cars.push({ carNo: '', carName: '', carType: '', cardId: null, cardLabel: '' }); carRender(); });
    $('carBody').addEventListener('click', (e) => {
      const del = e.target.closest('button[data-act="car-del"]'); if (del) { collectRows(); cars.splice(del.dataset.idx, 1); carRender(); return; }
      const card = e.target.closest('button[data-act="car-card"]'); if (card) openCardPicker('car', Number(card.dataset.idx));
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

    // 카드 선택 팝업
    $('vcpSearch').addEventListener('click', loadCardPick);
    $('vcpKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadCardPick(); });
    $('vcpScan').addEventListener('click', scanCardPick);
    $('vcpBody').addEventListener('click', (e) => {
      const row = e.target.closest('.vcp-row'); if (!row) return;
      row.querySelector('input[type=radio]').checked = true;
      cardPick.chosen = JSON.parse($('vcpBody').dataset.rows)[Number(row.dataset.idx)];
    });
    $('vcpOk').addEventListener('click', () => cardPick.chosen ? applyCardPick(cardPick.chosen) : toast.warning('카드를 선택하세요.'));
    $('vcpClear').addEventListener('click', () => applyCardPick(null));
    $('vcpCancel').addEventListener('click', closeCardPicker);
    $('vcpClose').addEventListener('click', closeCardPicker);

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
