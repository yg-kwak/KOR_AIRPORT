/* 키오스크(무인증) 방문 신청 — 인솔자·방문구역·방문객 입력 후 저장. 임시·신청 상태로 접수되어
   관리자 임시인원등록에서 카드 발급. 관리자 UI(사이드바/헤더) 없이 독립 동작. */
(function () {
  const BASE = '/kiosk/visit';
  const AC_TREE = 'acTree';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  let managers = []; // [{personId, personName}]
  let visitors = []; // [{personName, birthDate, affiliation}]
  let cars = []; // [{carNo, carName, carType}]
  let carCodes = []; // tb_common(CAR) 차량구역
  let carTypes = []; // tb_common(CT) 차종
  const pad2 = (n) => String(n).padStart(2, '0');
  const todayAt = (hh, mm, now) => {
    const d = new Date();
    const date = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
    return `${date}T${now ? pad2(d.getHours()) + ':' + pad2(d.getMinutes()) : pad2(hh) + ':' + pad2(mm)}`;
  };

  function showForm(on) {
    $('landing').style.display = on ? 'none' : '';
    $('form').style.display = on ? '' : 'none';
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
          <td>${esc(p.personId)}</td><td style="text-align:left">${esc(p.personName)}</td></tr>`).join('')
      : '<tr><td colspan="3" class="empty">검색 결과가 없습니다.</td></tr>';
  }
  function mgrRender() {
    $('mgrBody').innerHTML = managers.length
      ? managers.map((m, i) => `<tr><td>${esc(m.personId)}</td><td>${esc(m.personName)}</td>
          <td><button type="button" class="btn btn-sm btn-danger" data-act="mgr-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="3" class="empty">선택된 인솔자가 없습니다.</td></tr>';
  }

  // ---- 방문객 ----
  function visRender() {
    $('visBody').innerHTML = visitors.length
      ? visitors.map((v, i) => `<tr>
          <td><input class="input" data-f="personName" data-i="${i}" value="${esc(v.personName)}"/></td>
          <td><input class="input" data-f="birthDate" data-i="${i}" placeholder="1990-01-01" value="${esc(v.birthDate)}"/></td>
          <td><input class="input" data-f="affiliation" data-i="${i}" value="${esc(v.affiliation)}"/></td>
          <td><button type="button" class="btn btn-sm btn-danger" data-act="vis-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">방문객이 없습니다.</td></tr>';
  }
  function collectVis() {
    $('visBody').querySelectorAll('input').forEach((el) => { visitors[el.dataset.i][el.dataset.f] = el.value; });
  }

  // ---- 차량구역(CAR) ----
  function carAcRender() {
    $('carAcBox').innerHTML = carCodes.length
      ? carCodes.map((c) => `<label class="ac-select-item"><input type="checkbox" value="${esc(c.codeId)}"/><span>${esc(c.codeName)}</span></label>`).join('')
      : '<div class="empty">등록된 차량구역이 없습니다.</div>';
  }
  const carAcSelected = () => [...$('carAcBox').querySelectorAll('input:checked')].map((c) => c.value);

  // ---- 차량 정보 ----
  function carTypeOptions(sel) {
    return '<option value="">선택</option>' + carTypes.map((c) => `<option value="${c.codeId}"${c.codeId === sel ? ' selected' : ''}>${esc(c.codeName)}</option>`).join('');
  }
  function carRender() {
    $('carBody').innerHTML = cars.length
      ? cars.map((c, i) => `<tr>
          <td><input class="input" data-f="carNo" data-i="${i}" value="${esc(c.carNo)}"/></td>
          <td><input class="input" data-f="carName" data-i="${i}" value="${esc(c.carName)}"/></td>
          <td><select class="input" data-f="carType" data-i="${i}">${carTypeOptions(c.carType)}</select></td>
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
      companyName: $('companyName').value.trim() || null,
      workPurpose: $('workPurpose').value.trim() || null,
      managerIds: managers.map((m) => m.personId),
      acGroupIds: acGroupTree.get(AC_TREE),
      carAcCodes,
      visitors: visitors.map((v) => ({
        personName: (v.personName || '').trim() || null,
        birthDate: (v.birthDate || '').trim() || null,
        affiliation: (v.affiliation || '').trim() || null,
      })),
      cars: cars.filter((c) => (c.carNo || '').trim()).map((c) => ({
        carNo: (c.carNo || '').trim(), carName: (c.carName || '').trim() || null, carType: c.carType || null,
      })),
    };
    if (!payload.workStartDt) { toast.warning('작업기간 시작을 입력하세요.'); return; }
    if (!payload.workEndDt) { toast.warning('작업기간 종료를 입력하세요.'); return; }
    if (!payload.companyName) { toast.warning('업체명을 입력하세요.'); return; }
    if (!payload.workPurpose) { toast.warning('작업목적을 입력하세요.'); return; }
    if (!payload.managerIds.length) { toast.warning('인솔자를 선택하세요.'); return; }
    if (!payload.acGroupIds.length) { toast.warning('방문구역을 선택하세요.'); return; }
    if (!payload.visitors.length || payload.visitors.some((v) => !v.personName)) {
      toast.warning('방문객 성명을 입력하세요.'); return;
    }
    if (payload.carAcCodes.length && !payload.cars.length) {
      toast.warning('차량구역을 선택하면 차량정보를 입력하세요.'); return;
    }
    await api.post(BASE, payload);
    reset();
  }

  function reset() {
    managers = []; visitors = []; cars = [];
    ['workStartDt', 'workEndDt', 'companyName', 'workPurpose'].forEach((id) => { $(id).value = ''; });
    $('mgrKeyword').value = '';
    $('mgrResultWrap').style.display = 'none';
    $('mgrResult').innerHTML = '';
    acGroupTree.set(AC_TREE, []);
    carAcRender();
    mgrRender(); visRender(); carRender();
    showForm(false);
  }

  async function loadCodes() {
    [carCodes, carTypes] = await Promise.all([
      api.get(BASE + '/codes?cmmId=CAR'),
      api.get(BASE + '/codes?cmmId=CT'),
    ]).then((a) => a.map((x) => x || []));
  }

  document.addEventListener('DOMContentLoaded', () => {
    acGroupTree.init(AC_TREE, BASE + '/acGroups');
    loadCodes().then(carAcRender);
    $('btnStart').addEventListener('click', () => {
      $('workStartDt').value = todayAt(0, 0, true); // 시작=오늘 현재시각
      $('workEndDt').value = todayAt(18, 0, false); // 종료=오늘 18:00
      showForm(true); mgrRender(); visRender(); carRender();
    });
    $('btnCancel').addEventListener('click', reset);
    $('btnMgrSearch').addEventListener('click', searchMgr);
    $('mgrKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') searchMgr(); });
    $('mgrResult').addEventListener('click', (e) => {
      const row = e.target.closest('.mgr-pick'); if (!row) return;
      const id = row.dataset.id;
      if (!managers.some((m) => m.personId === id)) managers.push({ personId: id, personName: row.dataset.name });
      mgrRender();
    });
    $('mgrBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="mgr-del"]'); if (b) { managers.splice(b.dataset.idx, 1); mgrRender(); }
    });
    $('btnAddVis').addEventListener('click', () => { collectVis(); visitors.push({ personName: '', birthDate: '', affiliation: '' }); visRender(); });
    $('visBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="vis-del"]'); if (b) { collectVis(); visitors.splice(b.dataset.idx, 1); visRender(); }
    });
    $('btnAddCar').addEventListener('click', () => { collectCars(); cars.push({ carNo: '', carName: '', carType: '' }); carRender(); });
    $('carBody').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-act="car-del"]'); if (b) { collectCars(); cars.splice(b.dataset.idx, 1); carRender(); }
    });
    $('btnSave').addEventListener('click', save);
  });
})();
