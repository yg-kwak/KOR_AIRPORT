/* 키오스크(무인증) 방문 신청 — 인솔자·방문구역·방문객 입력 후 저장. 임시·신청 상태로 접수되어
   관리자 임시인원등록에서 카드 발급. 관리자 UI(사이드바/헤더) 없이 독립 동작.
   [등록 수정]은 인솔자 인원ID·성명으로 자기 신청(신청 상태만)을 찾아 같은 폼으로 고친다. */
(function () {
  const BASE = '/kiosk/visit';
  const AC_TREE = 'acTree';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  let managers = []; // [{personId, personName, phone}]
  let visitors = []; // [{personId?, personName, birthDate, affiliation}] — personId 는 수정 때 유지용
  let cars = []; // [{carNo, carName, carType, affiliation}]
  /* 수정 중인 신청 — 인솔자 ID·성명은 저장 요청에도 다시 붙인다(서버가 매 요청 재확인) */
  let editing = null; // {visitNo, managerId, managerName}
  let carCodes = []; // tb_common(CAR) 차량구역
  let carTypes = []; // tb_common(CT) 차종
  const pad2 = (n) => String(n).padStart(2, '0');
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' '));
  const todayAt = (hh, mm, now) => {
    const d = new Date();
    const date = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
    return `${date}T${now ? pad2(d.getHours()) + ':' + pad2(d.getMinutes()) : pad2(hh) + ':' + pad2(mm)}`;
  };

  /* 화면은 셋 중 하나만 보인다 — 시작 / 신청 조회 / 폼 */
  function show(id) {
    ['landing', 'lookup', 'form'].forEach((s) => { $(s).style.display = s === id ? '' : 'none'; });
  }

  // ---- 인솔자 ----
  async function searchMgr() {
    const kw = $('mgrKeyword').value.trim();
    if (!kw) { toast.warning('검색어(인원ID 또는 성명)를 입력하세요.'); return; }
    const rows = (await api.get(BASE + '/managers?keyword=' + encodeURIComponent(kw))) || [];
    $('mgrResultWrap').style.display = '';
    $('mgrResult').innerHTML = rows.length
      ? rows.map((p) => `<tr class="row-click mgr-pick" data-id="${esc(p.personId)}" data-name="${esc(p.personName)}">
          <td><button type="button" class="btn btn-sm">선택</button></td>
          <td>${esc(p.personId)}</td><td style="text-align:left">${esc(p.personName)}</td>
          <td style="text-align:left">${esc(p.affiliation || '-')}</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">검색 결과가 없습니다.</td></tr>';
  }
  function mgrRender() {
    $('mgrCount').textContent = `( ${managers.length} )`;
    // 연락처는 방문마다 손으로 적는다 — 정규인원 정보에서 당겨오지 않는다
    $('mgrBody').innerHTML = managers.length
      ? managers.map((m, i) => `<tr><td>${esc(m.personId)}</td><td>${esc(m.personName)}</td>
          <td><input type="text" class="input" data-act="mgr-phone" data-idx="${i}" value="${esc(m.phone || '')}" placeholder="연락처" autocomplete="off"/></td>
          <td><button type="button" class="btn btn-sm btn-danger" data-act="mgr-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">선택된 인솔자가 없습니다.</td></tr>';
  }

  // ---- 방문객 ----
  function visRender() {
    $('visCount').textContent = `( ${visitors.length} )`;
    $('visBody').innerHTML = visitors.length
      ? visitors.map((v, i) => `<tr>
          <td><input class="input" data-f="personName" data-i="${i}" value="${esc(v.personName)}"/></td>
          <td><input class="input" data-f="birthDate" data-i="${i}" placeholder="900101" maxlength="10" inputmode="numeric" value="${esc(v.birthDate)}"/></td>
          <td><input class="input" data-f="affiliation" data-i="${i}" value="${esc(v.affiliation)}"/></td>
          <td><button type="button" class="btn btn-sm btn-danger" data-act="vis-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">방문객이 없습니다.</td></tr>';
  }
  function collectVis() {
    $('visBody').querySelectorAll('input').forEach((el) => { visitors[el.dataset.i][el.dataset.f] = el.value; });
  }

  // ---- 차량구역(CAR) ----
  function carAcRender(checked) {
    const on = new Set(checked || []);
    $('carAcBox').innerHTML = carCodes.length
      ? carCodes.map((c) => `<label class="ac-select-item"><input type="checkbox" value="${esc(c.codeId)}"${on.has(c.codeId) ? ' checked' : ''}/><span>${esc(c.codeName)}</span></label>`).join('')
      : '<div class="empty">등록된 차량구역이 없습니다.</div>';
  }
  const carAcSelected = () => [...$('carAcBox').querySelectorAll('input:checked')].map((c) => c.value);

  // ---- 차량 정보 ----
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
          <td><button type="button" class="btn btn-sm btn-danger" data-act="car-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">차량이 없습니다.</td></tr>';
  }
  function collectCars() {
    $('carBody').querySelectorAll('input,select').forEach((el) => { cars[el.dataset.i][el.dataset.f] = el.value; });
  }

  async function save() {
    collectVis(); collectCars();
    const carAcCodes = carAcSelected();
    const payload = {
      workStartDt: $('workStartDt').value || null,
      workEndDt: $('workEndDt').value || null,
      workPurpose: $('workPurpose').value.trim() || null,
      managers: managers.map((m) => ({ personId: m.personId, phone: (m.phone || '').trim() })),
      acGroupIds: acGroupTree.get(AC_TREE),
      carAcCodes,
      visitors: visitors.map((v) => ({
        personId: v.personId || null, // 수정 때 남은 방문객은 갱신, 빠진 방문객만 정리된다
        personName: (v.personName || '').trim() || null,
        birthDate: birthDate.normalize(v.birthDate) || null,
        affiliation: (v.affiliation || '').trim() || null,
      })),
      cars: cars.filter((c) => (c.carNo || '').trim()).map((c) => ({
        carNo: (c.carNo || '').trim(), carName: (c.carName || '').trim() || null, carType: c.carType || null,
        affiliation: (c.affiliation || '').trim() || null,
      })),
    };
    if (!payload.workStartDt) { toast.warning('작업기간 시작을 입력하세요.'); return; }
    if (!payload.workEndDt) { toast.warning('작업기간 종료를 입력하세요.'); return; }
    if (!payload.workPurpose) { toast.warning('작업목적을 입력하세요.'); return; }
    if (!payload.managers.length) { toast.warning('인솔자를 선택하세요.'); return; }
    if (payload.managers.some((m) => !m.phone)) { toast.warning('인솔자 연락처를 입력하세요.'); return; }
    visitKind.prune(payload); // 고르지 않은 쪽(감춰진 칸)의 값은 보내지 않는다
    const kindProblem = visitKind.problem(payload); if (kindProblem) { toast.warning(kindProblem); return; }
    if (visitKind.person(payload.visitKind) && !payload.acGroupIds.length) { toast.warning('인원 출입구역을 선택하세요.'); return; }
    if (visitKind.car(payload.visitKind) && !payload.carAcCodes.length) { toast.warning('차량 출입구역을 선택하세요.'); return; }
    if (payload.visitors.some((v) => !v.personName || !v.birthDate || !v.affiliation)) {
      toast.warning('방문객 성명·생년월일·소속은 필수입니다.'); return;
    }
    if (payload.visitors.some((v) => !birthDate.isValid(v.birthDate))) { toast.warning('방문객 ' + birthDate.HINT); return; }
    if (editing) {
      // 인솔자 ID·성명은 본문으로 — URL 에 성명이 남지 않게
      await api.put(BASE, { ...editing, form: payload });
    } else {
      await api.post(BASE, payload);
    }
    reset();
  }

  function reset() {
    managers = []; visitors = []; cars = []; editing = null;
    ['workStartDt', 'workEndDt', 'workPurpose', 'lookupMgrId', 'lookupMgrName'].forEach((id) => { $(id).value = ''; });
    $('mgrKeyword').value = '';
    $('mgrResultWrap').style.display = 'none';
    $('mgrResult').innerHTML = '';
    $('mineWrap').style.display = 'none';
    $('mineBody').innerHTML = '';
    $('editBanner').style.display = 'none';
    $('btnSave').textContent = '저장';
    acGroupTree.set(AC_TREE, []);
    carAcRender();
    mgrRender(); visRender(); carRender();
    show('landing');
  }

  // ---- 등록 수정 ----
  /* 인원ID·성명 둘 다 맞아야 목록이 온다 — 하나만으로 남의 신청을 볼 수 없게 서버가 막는다 */
  async function lookup() {
    const managerId = $('lookupMgrId').value.trim();
    const managerName = $('lookupMgrName').value.trim();
    if (!managerId || !managerName) { toast.warning('인솔자 인원ID와 성명을 모두 입력하세요.'); return; }
    const rows = (await api.post(BASE + '/mine', { managerId, managerName })) || [];
    $('mineWrap').style.display = '';
    $('mineBody').innerHTML = rows.length
      ? rows.map((r) => `<tr class="row-click mine-pick" data-no="${r.visitNo}">
          <td>${r.visitNo}</td>
          <td>${esc(fmtDt(r.workStartDt))} ~ ${esc(fmtDt(r.workEndDt))}</td>
          <td style="text-align:left">${esc(r.workPurpose || '')}</td>
          <td>${r.personCount || 0}명</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">신청 상태인 방문이 없습니다. 인원ID·성명을 확인하세요.</td></tr>';
  }

  /* 목록에서 고른 신청을 폼에 채운다 — 등록 폼을 그대로 쓰되 수정 중임을 위에 띄운다 */
  async function openEdit(visitNo) {
    const m = { visitNo, managerId: $('lookupMgrId').value.trim(), managerName: $('lookupMgrName').value.trim() };
    const d = await api.post(BASE + '/detail', m);
    if (!d) return;
    editing = m;
    const v = d.visit;
    $('workStartDt').value = v.workStartDt || '';
    $('workEndDt').value = v.workEndDt || '';
    $('workPurpose').value = v.workPurpose || '';
    managers = (d.managers || []).map((x) => ({ personId: x.personId, personName: x.personName, phone: x.phone || '' }));
    visitors = (d.visitors || []).map((x) => ({
      personId: x.personId, personName: x.personName || '', birthDate: x.birthDate || '', affiliation: x.affiliation || '',
    }));
    cars = (d.cars || []).map((x) => ({
      carNo: x.carNo || '', carName: x.carName || '', carType: x.carType || '', affiliation: x.affiliation || '',
    }));
    acGroupTree.set(AC_TREE, d.acGroupIds || []);
    carAcRender(d.carAcCodes || []);
    visitKind.set(v.visitKind || visitKind.infer(d.visitors, d.cars)); // 이 컬럼 이전의 방문은 명단으로 되짚는다
    $('editBanner').textContent = `방문번호 ${visitNo} 신청을 수정하고 있습니다. 고친 뒤 [수정 저장]을 누르세요.`;
    $('editBanner').style.display = '';
    $('btnSave').textContent = '수정 저장';
    show('form'); mgrRender(); visRender(); carRender();
  }

  async function loadCodes() {
    [carCodes, carTypes] = await Promise.all([
      api.get(BASE + '/codes?cmmId=CAR'),
      api.get(BASE + '/codes?cmmId=CT'),
    ]).then((a) => a.map((x) => x || []));
  }

  document.addEventListener('DOMContentLoaded', () => {
    acGroupTree.init(AC_TREE, BASE + '/acGroups');
    visitKind.init('visitKind');
    loadCodes().then(carAcRender);
    $('btnStart').addEventListener('click', () => {
      $('workStartDt').value = todayAt(0, 0, true); // 시작=오늘 현재시각
      $('workEndDt').value = todayAt(18, 0, false); // 종료=오늘 18:00
      visitKind.set(visitKind.PERSON); // 방문구분은 인원이 기본
      show('form'); mgrRender(); visRender(); carRender();
    });
    $('btnEdit').addEventListener('click', () => show('lookup'));
    $('btnLookupBack').addEventListener('click', reset);
    $('btnLookup').addEventListener('click', lookup);
    ['lookupMgrId', 'lookupMgrName'].forEach((id) => $(id).addEventListener('keydown', (e) => { if (e.key === 'Enter') lookup(); }));
    $('mineBody').addEventListener('click', (e) => {
      const row = e.target.closest('.mine-pick'); if (row) openEdit(Number(row.dataset.no));
    });
    $('btnCancel').addEventListener('click', reset);
    $('btnMgrSearch').addEventListener('click', searchMgr);
    $('mgrKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') searchMgr(); });
    $('mgrResult').addEventListener('click', (e) => {
      const row = e.target.closest('.mgr-pick'); if (!row) return;
      const id = row.dataset.id;
      if (!managers.some((m) => m.personId === id)) managers.push({ personId: id, personName: row.dataset.name, phone: '' });
      mgrRender();
    });
    $('mgrBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="mgr-del"]'); if (b) { managers.splice(b.dataset.idx, 1); mgrRender(); }
    });
    // 연락처는 치는 대로 배열에 담는다 — 다시 그릴 때 사라지지 않게
    $('mgrBody').addEventListener('input', (e) => {
      const ph = e.target.closest('input[data-act="mgr-phone"]');
      if (ph && managers[ph.dataset.idx]) managers[ph.dataset.idx].phone = ph.value;
    });
    birthDate.bindWithin($('visBody'), 'input[data-f="birthDate"]');
    $('btnAddVis').addEventListener('click', () => { collectVis(); visitors.push({ personName: '', birthDate: '', affiliation: '' }); visRender(); });
    $('visBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="vis-del"]'); if (b) { collectVis(); visitors.splice(b.dataset.idx, 1); visRender(); }
    });
    $('btnAddCar').addEventListener('click', () => { collectCars(); cars.push({ carNo: '', carName: '', carType: '', affiliation: '' }); carRender(); });
    $('carBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="car-del"]'); if (b) { collectCars(); cars.splice(b.dataset.idx, 1); carRender(); }
    });
    $('btnSave').addEventListener('click', save);
  });
})();
