/* 임시인원등록(방문) — 방문 그룹 정보/인솔자/방문구역/방문객/차량을 한 화면에 차례로. 카드는 검색 팝업 선택(방문객=스캔 지원, 차량=스캔 없음).
   방문객=tb_person, 차량=tb_car. 저장 시 방문객을 BiostarX 사용자로 편입하고 카드/출입그룹 전달(서버). */
(function () {
  const CFG = window.VISIT_CFG || {};
  const BASE = CFG.base || '/visitor/visitor'; // 임시=/visitor/visitor, 장기=/visitor/longterm
  const AC_TREE = 'acTree';
  const HOLDING = ['VS03', 'VS05']; // 카드 보유 상태 — 퇴실로만 벗어난다(서버 규칙과 동일)
  // fixedType 있으면 방문유형 고정(임시=PT02), 없으면 화면 select 값 사용(장기=PTD03 선택)
  const VISIT_TYPE = CFG.fixedType ? { id: CFG.fixedType, name: CFG.fixedTypeName } : null;
  const state = { page: 1, size: 30, keyword: '', searchType: 'all', statusCode: '', startDate: '', endDate: '', sort: 'visitNo', dir: 'desc' };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' '));
  const pad2 = (n) => String(n).padStart(2, '0');
  // 오늘 날짜의 일시 값(서버 형식 YYYY-MM-DDTHH:mm — 일시 칸이 24시간 문자열로 보여 준다). now=true 면 현재 시각, 아니면 hh:mm
  const todayAt = (hh, mm, now) => {
    const d = new Date();
    const date = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
    return `${date}T${now ? pad2(d.getHours()) + ':' + pad2(d.getMinutes()) : pad2(hh) + ':' + pad2(mm)}`;
  };
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  let carCodes = [], carTypes = [], editMode = 'create'; // 차량구역(CAR)·차종(CT) 공통코드 / 모달 모드
  let periodCtl; // 출입시작 기간 프리셋(core/period.js). 기본 1개월 — 'all' 이면 빈값이라 서버가 기간을 안 건다
  const PERIOD_DEF = '1m';
  const applyPeriod = () => { const r = periodCtl.value(); state.startDate = r.start; state.endDate = r.end; };

  // ---- 목록 ----
  async function load() {
    const q = `?page=${state.page}&size=${state.size}&keyword=${encodeURIComponent(state.keyword)}` +
      `&searchType=${state.searchType}&statusCode=${encodeURIComponent(state.statusCode)}` +
      `&startDate=${state.startDate}&endDate=${state.endDate}&sort=${state.sort}&dir=${state.dir}`;
    const data = await api.get(BASE + '/list' + q);
    const body = $('gridBody');
    if (!data.content || !data.content.length) {
      body.innerHTML = '<tr><td colspan="10" class="empty">조회 결과가 없습니다.</td></tr>';
    } else {
      body.innerHTML = data.content.map((r) => {
        const period = [fmtDt(r.workStartDt), fmtDt(r.workEndDt)].filter(Boolean).join(' ~ ');
        return `<tr class="row-click" data-no="${r.visitNo}">
          <td>${r.visitNo}</td><td>${esc(r.visitTypeName)}</td>
          <td>${esc(r.managerName) || '-'}</td><td>${esc(r.managerAffiliation) || '-'}</td>
          <td style="text-align:left">${esc(r.workPurpose) || '-'}</td>
          <td>${esc(period)}</td><td>${r.personCount || 0}</td><td>${r.carCount || 0}</td>
          <td>${badge.visitStatus(r.statusCode, r.statusName)}</td>
          <td>${HOLDING.includes(r.statusCode) && PERM.canCreate ? `<button class="btn btn-sm" data-act="checkout" data-id="${r.visitNo}">퇴실</button>` : '-'}</td>
        </tr>`;
      }).join('');
    }
    pager.render($('paging'), data.page, data.totalPages, (p) => { state.page = p; load(); });
    // 미반납 = 작업기간이 끝났는데 카드가 회수되지 않은 방문. 몇 건 밀렸는지 목록 위에 늘 보인다
    const un = data.unreturned || 0, cnt = String(un).padStart(2, '0');
    $('totalInfo').innerHTML = `조회결과 ${data.total.toLocaleString()}` +
      ` - (미반납 ${un ? `<span class="unreturned">${cnt}</span>` : cnt} 건)`;
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
    applyPeriod();
    state.page = 1;
    load();
  }
  function reset() {
    ['keyword', 'statusFilter', 'statusFilterName'].forEach((id) => { $(id).value = ''; });
    $('searchType').value = 'all';
    periodCtl.reset(PERIOD_DEF);
    Object.assign(state, { page: 1, size: 30, keyword: '', searchType: 'all', statusCode: '', sort: 'visitNo', dir: 'desc' });
    applyPeriod();
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

  /* 카드 셀 — 고른 카드 표시 + 선택 버튼(팝업). kind=vis|car. 퇴실한 방문객은 재발급 불가라 버튼을 뺀다.
     방문객은 카드명칭(임시234-0001)을 보여준다 — 그 카드가 어느 구역용인지가 번호에는 안 드러난다.
     회수 표시도 명칭이다 — 스냅샷은 번호뿐이라 서버가 카드표에서 되찾아 준다(지워진 카드면 번호). */
  function cardCell(obj, i, kind) {
    const picked = kind === 'car' ? obj.cardLabel : obj.cardName || obj.cardLabel;
    const label = obj.cardId ? esc(picked || obj.cardId) : badge.none(obj.lastCardNo ? `회수됨(${obj.lastCardName || obj.lastCardNo})` : '카드 없음');
    const btn = obj.checkoutDt ? ''
      : `<button type="button" class="btn btn-sm" data-act="${kind}-card" data-idx="${i}">선택</button>`;
    return `<div class="file-field-row">
      <span class="card-picked" data-i="${i}" style="min-width:90px">${label}</span>${btn}</div>`;
  }

  /** 태깅한 카드를 다음 빈 방문객에게 — 위에서부터 차례로. 자리가 없으면 false. */
  function assignTaggedCard(card) {
    collectRows();
    if (visitors.some((v) => v.cardId === Number(card.cardId))) { toast.warning('이미 이 화면에서 선택한 카드입니다.'); return true; }
    const row = visitors.find((v) => !v.cardId && !v.checkoutDt);
    if (!row) return false;
    row.cardId = Number(card.cardId);
    row.cardLabel = card.biostarCardValue;
    row.cardName = card.cardName;
    visRender();
    return true;
  }

  // ---- 방문객 ----
  let visitors = []; // [{personId?, personName, birthDate, affiliation, cardId}]
  function visRender() {
    $('visCount').textContent = `( ${visitors.length} )`;
    $('visBody').innerHTML = visitors.length
      ? visitors.map((v, i) => `<tr>
          <td>${v.biostarUserId ? esc(v.biostarUserId) : badge.none('등록 전')}</td>
          <td><input class="input" data-f="personName" data-i="${i}" value="${esc(v.personName)}"/></td>
          <td><input class="input" data-f="birthDate" data-i="${i}" placeholder="900101" maxlength="10" inputmode="numeric" value="${esc(v.birthDate)}"/></td>
          <td><input class="input" data-f="affiliation" data-i="${i}" value="${esc(v.affiliation)}"/></td>
          <td>${cardCell(v, i, 'vis')}</td>
          <td>${visActions(v, i)}</td></tr>`).join('')
      : '<tr><td colspan="6" class="empty">방문객이 없습니다.</td></tr>';
  }

  /* 관리 버튼 — 퇴실했으면 표시만, [퇴실]은 '카드 보유 상태 + 저장된 카드' 일 때만 나온다.
     화면에서 방금 고른 카드(미저장)로는 바뀌지 않는다 — 저장돼 BiostarX 동기화까지 끝나야 퇴실 대상이다. */
  function visActions(v, i) {
    if (v.checkoutDt) return `<div class="checkout-cell">${badge.of('퇴실', 'done')}<span>${esc(v.checkoutDt.slice(5, 16))}</span></div>`;
    if (HOLDING.includes($('statusCode').value) && v.issuedCardId) {
      return `<button class="btn btn-sm" data-act="vis-out" data-idx="${i}">퇴실</button>`;
    }
    return `<button class="btn btn-sm btn-danger" data-act="vis-del" data-idx="${i}">제거</button>`;
  }

  // ---- 차량 ----
  let cars = []; // [{carId?, carNo, carName, carType, affiliation, cardId}]
  function carTypeOptions(sel) {
    return '<option value="">선택</option>' + carTypes.map((c) => `<option value="${c.codeId}"${c.codeId === sel ? ' selected' : ''}>${esc(c.codeName)}</option>`).join('');
  }
  function carRender() {
    $('carCount').textContent = `( ${cars.length} )`;
    $('carBody').innerHTML = cars.length
      ? cars.map((c, i) => `<tr>
          <td><input class="input" data-f="carNo" data-i="${i}" value="${esc(c.carNo)}"/></td>
          <td><input class="input" data-f="carName" data-i="${i}" value="${esc(c.carName)}"/></td>
          <td><select class="input" data-f="carType" data-i="${i}">${carTypeOptions(c.carType)}</select></td>
          <td><input class="input" data-f="affiliation" data-i="${i}" maxlength="100" value="${esc(c.affiliation)}"/></td>
          <td>${cardCell(c, i, 'car')}</td>
          <td><button class="btn btn-sm btn-danger" data-act="car-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">차량이 없습니다.</td></tr>';
  }

  // ---- 모달 ----
  async function openModal(mode, visitNo) {
    editMode = mode;
    $('modalTitle').textContent = mode === 'create' ? '방문 등록' : '방문 수정';
    ['visitNo', 'visitType', 'visitTypeName', 'statusCode', 'statusName', 'companyType',
      'workStartDt', 'workEndDt', 'permitDt', 'receiver', 'returner', 'workPurpose', 'remark']
      .forEach((id) => { const el = $(id); if (el) el.value = ''; });
    visitManagers.reset(); visitors = []; cars = [];
    if (VISIT_TYPE) { $('visitType').value = VISIT_TYPE.id; if ($('visitTypeName')) $('visitTypeName').value = VISIT_TYPE.name; } // 임시 고정
    if (mode === 'create') { // 작업기간 기본: 시작=오늘 현재시각, 종료=오늘 18:00. 방문구분은 인원이 기본
      $('workStartDt').value = todayAt(0, 0, true);
      $('workEndDt').value = todayAt(18, 0, false);
      visitKind.set(visitKind.PERSON);
    }
    if ($('btnDelete')) $('btnDelete').style.display = 'none'; // 삭제는 신청일 때만(로드 후 노출)
    if ($('btnSave')) $('btnSave').style.display = ''; // 퇴실완료면 로드 후 숨김(읽기전용)
    $('editModal').querySelector('.visit-modal').classList.remove('readonly'); visitKind.setDisabled(false); // 읽기전용 해제(VS04면 로드 후 재설정)
    await loadRefs();
    acGroupTree.set(AC_TREE, []);
    carAcRender([]);
    visRender(); carRender();
    $('editModal').classList.add('open');
    if (mode !== 'edit') return;

    const d = await api.get(BASE + '/detail?visitNo=' + visitNo);
    const v = d.visit;
    $('visitNo').value = v.visitNo;
    [['visitType', v.visitType], ['visitTypeName', v.visitTypeName], ['statusCode', v.statusCode],
      ['statusName', v.statusName], ['companyType', v.companyType],
      ['workStartDt', v.workStartDt], ['workEndDt', v.workEndDt], ['permitDt', v.permitDt],
      ['receiver', v.receiver], ['returner', v.returner], ['workPurpose', v.workPurpose], ['remark', v.remark]]
      .forEach(([id, val]) => { const el = $(id); if (el) el.value = val != null ? val : ''; });
    // 신청(VS01)이면 삭제 가능·신청서는 아직(출입증번호 없음). 신청서는 '임시출입허가' 양식이라 임시 화면에서만.
    const applied = v.statusCode === 'VS01';
    for (const [id, show] of [['btnDelete', applied], ['btnPermit', !applied && !!VISIT_TYPE], ['btnSave', v.statusCode !== 'VS04']]) { const el = $(id); if (el) el.style.display = show ? '' : 'none'; }
    if (v.statusCode === 'VS04') { $('editModal').querySelector('.visit-modal').classList.add('readonly'); visitKind.setDisabled(true); $('modalTitle').textContent = '방문 상세 (퇴실완료 — 수정 불가)'; } // 읽기전용
    acGroupTree.set(AC_TREE, d.acGroupIds || []);
    carAcRender(d.carAcCodes || []);
    visitKind.set(v.visitKind || visitKind.infer(d.visitors, d.cars)); // 이 컬럼 이전의 방문은 명단으로 되짚는다
    visitManagers.set(d.managers);
    // issuedCardId = 저장된 카드(서버 응답 기준). 화면에서 방금 고른 카드와 구분해 퇴실 버튼 노출을 판단한다
    visitors = (d.visitors || []).map((x) => ({ ...x, issuedCardId: x.cardId || null }));
    visRender();
    cars = (d.cars || []).map((x) => ({ ...x }));
    carRender();
  }
  function closeModal() { $('editModal').classList.remove('open'); visitManagers.close(); visitCardTag.off(); }

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
      companyType: $('companyType').value.trim() || null,
      workStartDt: $('workStartDt').value || null, workEndDt: $('workEndDt').value || null,
      permitDt: $('permitDt').value || null, receiver: $('receiver').value.trim() || null,
      returner: $('returner').value.trim() || null, workPurpose: $('workPurpose').value.trim() || null,
      remark: $('remark').value.trim() || null,
      managers: visitManagers.get(),
      acGroupIds: acGroupTree.get(AC_TREE),
      carAcCodes: carAcSelected(),
      visitors: visitors.map((v) => ({ personId: v.personId || null, personName: (v.personName || '').trim() || null,
        birthDate: birthDate.normalize(v.birthDate) || null, affiliation: (v.affiliation || '').trim() || null,
        cardId: v.cardId ? Number(v.cardId) : null })),
      cars: cars.map((c) => ({ carId: c.carId || null, carNo: (c.carNo || '').trim() || null,
        carName: (c.carName || '').trim() || null, carType: c.carType || null,
        affiliation: (c.affiliation || '').trim() || null,
        cardId: c.cardId ? Number(c.cardId) : null })),
    };
    visitKind.prune(payload); // 고르지 않은 쪽(감춰진 탭)의 값은 보내지 않는다
    if (!payload.visitType || !payload.workStartDt || !payload.workEndDt || !payload.workPurpose) { toast.warning('방문유형·작업기간·작업목적은 필수입니다.'); return; }
    const kindProblem = visitKind.problem(payload); if (kindProblem) { toast.warning(kindProblem); return; }
    if (payload.workStartDt > payload.workEndDt) { toast.warning('작업기간 시작은 종료보다 늦을 수 없습니다.'); return; }
    if (payload.visitors.some((v) => !v.personName || !v.birthDate || !v.affiliation)) { toast.warning('방문객 성명·생년월일·소속은 필수입니다.'); return; }
    if (payload.visitors.some((v) => !birthDate.isValid(v.birthDate))) { toast.warning('방문객 ' + birthDate.HINT); return; }
    // 차량은 선택이지만, 행을 추가했으면 차량번호는 필수
    if (payload.cars.some((c) => !c.carNo)) { toast.warning('차량번호는 필수입니다.'); return; }
    // 출입그룹↔대상 짝은 방문구분이 보장한다(고른 쪽은 있어야 하고, 고르지 않은 쪽은 prune 이 비운다)
    if (payload.visitors.length && !payload.managers.length) { toast.warning('방문객이 있으면 인솔자를 지정해야 합니다.'); return; }
    if (payload.managers.some((m) => !m.phone)) { toast.warning('인솔자 연락처를 입력하세요.'); return; }
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

  // ---- 카드 선택 팝업 — 구현은 공용 컴포넌트(core/components/card-picker-visit) ----
  /* 방문객 카드는 이름이 곧 용도다 — 임시234-0001·상주234-0001 은 2·3·4 구역 전용, 대여-0001 은 구역이 없어 어디에나 쓴다.
     방문유형은 보지 않는다(실물 카드는 3종뿐이고 어느 방문에서나 같은 카드를 쓴다) — 서버가 고른 출입그룹으로 후보를 좁힌다. */
  const visCardParams = () => ({ acGroupIds: acGroupTree.get(AC_TREE) });
  function openCardPicker(kind, index) {
    collectRows();
    const rows = kind === 'car' ? cars : visitors;
    const row = rows[index];
    if (!row) return;
    if (kind === 'vis' && row.checkoutDt) { toast.warning('퇴실한 방문객에게는 카드를 발급할 수 없습니다.'); return; }
    const p = visCardParams();
    if (kind === 'vis' && !p.acGroupIds.length) { toast.warning('사용자 출입그룹을 먼저 선택하세요. 어느 구역 카드를 써야 할지 정해지지 않았습니다.'); return; }
    visitCardPicker.open({
      kind,
      listUrl: BASE + (kind === 'car' ? '/cards/unassigned/car' : '/cards/unassigned'),
      scanUrl: BASE + '/card/scan',
      params: kind === 'vis' ? visCardParams : null, // 차량 카드는 구역 규칙이 없다
      // 이 화면에서 다른 행이 이미 고른 카드는 뺀다(편집 중인 행의 카드는 남긴다)
      exclude: [...visitors, ...cars].map((o) => o.cardId).filter((id) => id != null && id !== row.cardId),
      onPick: (card) => {
        row.cardId = card ? Number(card.cardId) : null;
        row.cardLabel = card ? card.biostarCardValue : '';
        row.cardName = card ? card.cardName : '';
        (kind === 'car' ? carRender : visRender)();
      },
    });
  }

  /** 방문객 개별 퇴실 — 카드를 발급받은 방문객은 제거 대신 퇴실로 내보낸다(퇴실하면 재발급 불가). */
  async function checkoutVisitor(index) {
    collectRows();
    const v = visitors[index];
    if (!v || !v.personId) return;
    const ok = await confirmModal.open({
      title: '방문객 퇴실',
      message: `${v.personName || v.personId} 님을 퇴실 처리합니다. 카드가 회수되고 다시 발급할 수 없습니다.`,
      confirmText: '퇴실',
    });
    if (!ok) return;
    await api.post(`${BASE}/visitor/checkout?visitNo=${$('visitNo').value}&personId=${encodeURIComponent(v.personId)}`);
    await openModal('edit', Number($('visitNo').value)); // 최신 상태로 다시 불러온다
    load();
  }

  function bind() {
    periodCtl = period.attach($('periodType'), $('dateRange'), $('startDate'), $('endDate'));
    applyPeriod(); // 첫 조회부터 기본 기간(1개월)이 걸리도록
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

    // 방문객 칸이 보일 때만 리더를 듣는다 — 차량만인 방문에서 지나가며 찍힌 카드가 배정되면 안 된다
    visitKind.init('visitKind', async (k, prev) => {
      if (visitKind.person(k)) { visitCardTag.on(); return; }
      visitCardTag.off();
      // 카드가 발급돼 BiostarX 에 올라간 방문객이 있으면 — '차량'으로 저장하는 순간 그 사람들이 장비에서도 지워진다
      const synced = visitors.filter((v) => v.biostarUserId).length, carded = visitors.filter((v) => v.issuedCardId).length;
      if (prev && synced && !(await confirmModal.open({ title: '방문구분 변경', confirmText: '변경',
        message: `카드가 발급되어 BiostarX 에 등록된 방문객이 ${synced}명 있습니다. '차량'으로 저장하면 연동된 방문객 정보가 삭제되고 BiostarX 에서도 제거됩니다. 계속하시겠습니까?`,
        note: `(발급된 카드 ${carded} 장을 모두 회수해 주시길 바랍니다)` }))) visitKind.set(prev);
    });
    // 카드를 고른 뒤 출입그룹을 바꾸면 그 카드가 새 구역과 맞지 않을 수 있다 — 되돌리지 않고 알리기만 한다
    $(AC_TREE).addEventListener('change', () => {
      collectRows();
      if (!visitors.some((v) => v.cardId)) return;
      confirmModal.open({ title: '출입그룹 변경', confirmText: '확인',
        message: '이미 고른 방문객 카드가 있습니다. 출입그룹을 바꾸면 그 카드가 새 구역과 맞지 않을 수 있으니, 카드를 다시 선택하세요.' });
    });
    // 방문유형·상태는 사용자가 변경 불가(고정/서버관리) — 모달 코드팝업 없음

    // 방문객/차량 목록 조작 (인솔자 탭은 visitor-manager.js 가 통째로 쥔다)
    $('btnAddVis').addEventListener('click', () => { collectRows(); visitors.push({ personName: '', birthDate: '', affiliation: '', cardId: null, cardLabel: '', cardName: '' }); visRender(); });
    birthDate.bindWithin($('visBody'), 'input[data-f="birthDate"]'); // 입력 보정 — 표는 다시 그려지므로 위임
    $('visBody').addEventListener('click', (e) => {
      const out = e.target.closest('button[data-act="vis-out"]'); if (out) { checkoutVisitor(Number(out.dataset.idx)); return; }
      const del = e.target.closest('button[data-act="vis-del"]'); if (del) { collectRows(); visitors.splice(del.dataset.idx, 1); visRender(); return; }
      const card = e.target.closest('button[data-act="vis-card"]'); if (card) openCardPicker('vis', Number(card.dataset.idx));
    });
    $('btnAddCar').addEventListener('click', () => { collectRows(); cars.push({ carNo: '', carName: '', carType: '', affiliation: '', cardId: null, cardLabel: '' }); carRender(); });
    $('carBody').addEventListener('click', (e) => {
      const del = e.target.closest('button[data-act="car-del"]'); if (del) { collectRows(); cars.splice(del.dataset.idx, 1); carRender(); return; }
      const card = e.target.closest('button[data-act="car-card"]'); if (card) openCardPicker('car', Number(card.dataset.idx));
    });

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
    visitManagers.init(BASE + '/managers');
    visitCardTag.init({ base: BASE, params: visCardParams, assign: assignTaggedCard });
    load();
  });
})();
