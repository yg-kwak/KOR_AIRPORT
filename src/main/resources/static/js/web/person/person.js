/* 정규인원등록 화면 — 골든 샘플(loginUser) 구조 + 탭(사용자정보/사용자권한/카드정보).
   성명·생년월일·연락처는 서버에서 ARIA 암호화. 얼굴은 파일 업로드/장치 촬영을 서버가 BiostarX 로 중계한다.
   등록 시 BiostarX 사용자도 생성된다(실패해도 인원은 저장되고 경고 토스트). 수정/삭제·카드는 추후. */
(function () {
  const BASE = '/person/person';
  const state = {
    page: 1, size: 30, keyword: '', searchType: 'all', companyCode: '', sort: 'personId', dir: 'asc',
  };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  const MAX_ACCESS_END_DT = '2037-12-31'; // BiostarX expiry 상한(2037-12-31T23:59)
  const TITLE_ALLOWED = /^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ\s]+$/; // 직위: 특수문자 금지

  let face = { image: null, t9: null, t5: null }; // 얼굴(정규화 이미지 + 템플릿 2종)
  let acTreeData = []; // 출입권한 트리(tb_ac_group)
  let companies = []; // 기관 목록(검색조건 + 등록모달 공용)
  let companyPickTarget = 'form'; // 기관 팝업을 연 곳: 'form'(등록모달) | 'filter'(검색조건)

  // ---- 참조 데이터(기관) ----
  async function loadRefs() {
    const data = await api.get(BASE + '/refs');
    companies = data.companies || [];
  }

  // ---- 기관 선택 팝업 (검색조건·등록모달 공용) ----
  function openCompanyModal(target) {
    companyPickTarget = target;
    $('companyFilterKw').value = '';
    renderCompanyList();
    $('companyModal').classList.add('open');
  }

  function renderCompanyList() {
    const kw = $('companyFilterKw').value.trim().toLowerCase();
    const rows = kw
      ? companies.filter((c) => (c.companyCode || '').toLowerCase().includes(kw)
          || (c.companyName || '').toLowerCase().includes(kw))
      : companies;
    $('companyInfo').textContent = `총 ${rows.length}개 — 기관 1건을 선택하고 [선택]을 누르세요.`;
    $('companyList').innerHTML = rows.length
      ? rows.map((c) => `
        <tr>
          <td><input type="radio" name="companyPick" data-code="${esc(c.companyCode)}" data-name="${esc(c.companyName)}"/></td>
          <td>${esc(c.companyCode)}</td>
          <td style="text-align:left">${esc(c.companyName)}</td>
        </tr>`).join('')
      : '<tr><td colspan="3" class="empty">검색 결과가 없습니다.</td></tr>';
  }

  function closeCompanyModal() { $('companyModal').classList.remove('open'); }

  function confirmCompany() {
    const sel = $('companyList').querySelector('input[name="companyPick"]:checked');
    if (!sel) { toast.warning('기관을 선택해주세요.'); return; }
    if (companyPickTarget === 'filter') {
      $('companyFilter').value = sel.dataset.code;
      $('companyFilterName').value = sel.dataset.name;
      closeCompanyModal();
      search(); // 검색조건은 선택 즉시 재조회
      return;
    }
    $('companyCode').value = sel.dataset.code;
    $('companyName').value = sel.dataset.name;
    closeCompanyModal();
  }

  // ---- 목록 ----
  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&companyCode=${encodeURIComponent(state.companyCode)}` +
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
    body.innerHTML = rows.map((r) => {
      const period = [r.accessStartDt || '', r.accessEndDt || ''].filter(Boolean).join(' ~ ');
      const actions = PERM.canDelete
        ? `<button class="btn btn-sm btn-danger" data-act="del" data-id="${esc(r.personId)}">삭제</button>`
        : '-';
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td>${esc(r.personId)}</td>
        <td>${esc(r.personName)}</td>
        <td>${esc(r.companyName)}</td>
        <td>${esc(r.titleName)}</td>
        <td>${esc(r.statusName)}</td>
        <td>${esc(period)}</td>
        <td>${r.biostarUserId ? '연동' : '-'}</td>
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
    state.companyCode = $('companyFilter').value;
    state.page = 1;
    load();
  }

  function reset() {
    $('searchType').value = 'all';
    $('keyword').value = '';
    $('companyFilter').value = '';
    $('companyFilterName').value = '';
    $('pageSize').value = '30';
    Object.assign(state, {
      page: 1, size: 30, keyword: '', searchType: 'all', companyCode: '', sort: 'personId', dir: 'asc',
    });
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 탭 ----
  function showTab(name) {
    document.querySelectorAll('.tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === 'tab-' + name));
  }

  // ---- 사용자 권한(출입권한 트리) ----
  async function loadAcTree() {
    acTreeData = (await api.get(BASE + '/acGroups')) || [];
    renderAcTree();
  }

  // biostar_ac_id 가 매핑된 노드만 선택 가능(최상위 구역은 라벨).
  // 노드를 .ac-node-wrap 으로 감싸 상위 체크 시 하위까지 연쇄 적용한다.
  function acNodesHtml(nodes) {
    return (nodes || []).map((n) => {
      const kids = (n.children || []).length ? `<div class="ac-select-node">${acNodesHtml(n.children)}</div>` : '';
      const row = n.biostarAcId != null
        ? `<label class="ac-select-item">
             <input type="checkbox" value="${esc(n.acGroupId)}"/>
             <span>${esc(n.acGroupName)}</span>
           </label>`
        : `<div class="ac-select-item group">${esc(n.acGroupName)}</div>`;
      return `<div class="ac-node-wrap">${row}${kids}</div>`;
    }).join('');
  }

  function renderAcTree(checkedIds) {
    $('acTree').innerHTML = acTreeData.length
      ? acNodesHtml(acTreeData)
      : '<div class="empty">선택 가능한 출입권한이 없습니다. (출입권한관리에서 BiostarX 출입그룹을 매핑하세요)</div>';
    if (checkedIds && checkedIds.length) {
      const set = new Set(checkedIds.map(Number));
      $('acTree').querySelectorAll('input[type="checkbox"]')
        .forEach((c) => { c.checked = set.has(Number(c.value)); });
    }
  }

  function selectedAcGroupIds() {
    return [...$('acTree').querySelectorAll('input[type="checkbox"]:checked')].map((c) => Number(c.value));
  }

  // ---- 얼굴(파일 업로드 / 장치 촬영) ----
  function setFace(image, t9, t5) {
    face = { image: image || null, t9: t9 || null, t5: t5 || null };
    $('facePreview').src = image ? 'data:image/jpeg;base64,' + image : '';
  }

  function fileToBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  async function onFaceFile(e) {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const b64 = await fileToBase64(file);
    const res = await api.post(BASE + '/face/upload', { image: b64 }); // {success,message,image,...}
    if (!res || !res.success) { toast.error((res && res.message) || '사진 업로드에 실패했습니다.'); return; }
    setFace(res.image, res.template9, res.template5);
    toast.success('사진을 등록했습니다.');
  }

  async function onCapture() {
    const res = await api.get(BASE + '/face/capture');
    if (!res || !res.success) { toast.error((res && res.message) || '얼굴 촬영에 실패했습니다.'); return; }
    setFace(res.image, res.template9, res.template5);
    toast.success('얼굴을 촬영했습니다.');
  }

  // ---- 등록/수정 모달 ----
  let editMode = 'create';

  async function openModal(mode, row) {
    editMode = mode;
    $('modalTitle').textContent = mode === 'create' ? '정규인원 등록' : '정규인원 수정';
    ['personId', 'personName', 'birthDate', 'personPhone', 'mainTask', 'remark',
      'companyCode', 'companyName', 'titleCode', 'titleName', 'statusCode', 'statusName',
      'accessStartDt', 'accessEndDt']
      .forEach((id) => { $(id).value = ''; });
    $('faceFile').value = '';
    setFace(null);
    showTab('info');
    $('personId').readOnly = mode === 'edit'; // PK 는 수정 불가
    renderAcTree([]); // 체크 초기화
    $('editModal').classList.add('open');
    if (mode !== 'edit' || !row) return;

    $('personId').value = row.personId;
    $('personName').value = row.personName || '';
    $('birthDate').value = row.birthDate || '';
    $('personPhone').value = row.personPhone || '';
    $('companyCode').value = row.companyCode || '';
    $('companyName').value = row.companyName || '';
    $('titleCode').value = row.titleCode || '';
    $('titleName').value = row.titleName || '';
    $('statusCode').value = row.statusCode || '';
    $('statusName').value = row.statusName || '';
    $('mainTask').value = row.mainTask || '';
    $('accessStartDt').value = row.accessStartDt || '';
    $('accessEndDt').value = row.accessEndDt || '';
    $('remark').value = row.remark || '';
    // 기존 얼굴·출입권한 로드(얼굴 템플릿은 저장하지 않으므로 이미지만 — 손대지 않으면 변경으로 보지 않는다)
    const q = `?personId=${encodeURIComponent(row.personId)}`;
    const [photo, acIds] = await Promise.all([
      api.get(BASE + '/photo' + q),
      api.get(BASE + '/personAcGroups' + q),
    ]);
    if (photo) setFace(photo, null, null);
    renderAcTree(acIds || []);
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  async function remove(personId) {
    if (!PERM.canDelete) return;
    const ok = await confirmModal.open({
      title: '삭제 확인',
      message: `선택한 인원(${personId})을 삭제하시겠습니까? BiostarX 사용자도 함께 삭제됩니다.`,
      confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?personId=${encodeURIComponent(personId)}`);
    load();
  }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = {
      personId: $('personId').value.trim(),
      personName: $('personName').value.trim(),
      birthDate: $('birthDate').value.trim() || null,
      personPhone: $('personPhone').value.trim() || null,
      companyCode: $('companyCode').value || null,
      titleCode: $('titleCode').value || null,
      statusCode: $('statusCode').value || null,
      mainTask: $('mainTask').value.trim() || null,
      accessStartDt: $('accessStartDt').value || null,
      accessEndDt: $('accessEndDt').value || null,
      remark: $('remark').value.trim() || null,
      acGroupIds: selectedAcGroupIds(),
      faceImage: face.image,
      faceTemplate9: face.t9,
      faceTemplate5: face.t5,
    };
    const required = [
      [payload.personId, '인원ID'], [payload.personName, '성명'],
      [payload.companyCode, '기관'], [payload.statusCode, '상태'],
      [payload.accessStartDt, '출입시작일'], [payload.accessEndDt, '출입종료일'],
    ].find(([v]) => !v);
    if (required) { toast.warning(`${required[1]}은(는) 필수입니다.`); return; }
    if (payload.accessEndDt > MAX_ACCESS_END_DT) {
      toast.warning(`출입종료일은 ${MAX_ACCESS_END_DT} 23:59 를 초과할 수 없습니다.`); return;
    }
    if (payload.accessStartDt > payload.accessEndDt) {
      toast.warning('출입시작일은 출입종료일보다 늦을 수 없습니다.'); return;
    }
    const titleName = $('titleName').value.trim();
    if (titleName && !TITLE_ALLOWED.test(titleName)) {
      toast.warning('직위에 특수문자를 사용할 수 없습니다.'); return;
    }
    // 서버 메시지(연동 경고 포함) 자동 토스트. 수정은 변경분만 BiostarX 로 전송된다.
    if (editMode === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    // 검색조건 기관: 등록모달과 같은 선택 팝업. 삭제(전체)로 비우면 즉시 재조회
    $('companyFilterName').addEventListener('click', () => openCompanyModal('filter'));
    const filterWrap = $('companyFilterName').closest('.picker-wrap');
    if (filterWrap) {
      const clearBtn = filterWrap.querySelector('.picker-clear');
      if (clearBtn) clearBtn.addEventListener('click', () => setTimeout(search, 0));
    }
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));

    // 행 클릭 → 수정, 삭제 버튼 → 삭제
    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { if (btn.dataset.act === 'del') remove(btn.dataset.id); return; }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });

    document.querySelectorAll('.tab-btn').forEach((b) =>
      b.addEventListener('click', () => showTab(b.dataset.tab)));

    // 기관(등록모달): 별도 선택 팝업(tb_company)
    $('companyName').addEventListener('click', () => openCompanyModal('form'));
    $('companyClose').addEventListener('click', closeCompanyModal);
    $('companyCancel').addEventListener('click', closeCompanyModal);
    $('companyConfirm').addEventListener('click', confirmCompany);
    $('companyFilterKw').addEventListener('input', renderCompanyList);
    $('companyModal').addEventListener('click', (e) => { if (e.target === $('companyModal')) closeCompanyModal(); });
    $('companyList').addEventListener('click', (e) => {
      if (e.target.closest('input[type="radio"]')) return;
      const radio = e.target.closest('tr')?.querySelector('input[type="radio"]');
      if (radio) radio.checked = true;
    });

    // 출입권한: 상위를 체크/해제하면 하위 전체에 동일 적용
    $('acTree').addEventListener('change', (e) => {
      const cb = e.target.closest('input[type="checkbox"]');
      if (!cb) return;
      const wrap = cb.closest('.ac-node-wrap');
      if (wrap) wrap.querySelectorAll('input[type="checkbox"]').forEach((c) => { c.checked = cb.checked; });
    });

    // 직위(UT)·상태(PS)는 공통 코드팝업
    $('titleName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'UT', cmmName: '직위' });
      if (sel) { $('titleCode').value = sel.codeId; $('titleName').value = sel.codeName; }
    });
    $('statusName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'PS', cmmName: '상태' });
      if (sel) { $('statusCode').value = sel.codeId; $('statusName').value = sel.codeName; }
    });

    $('faceFile').addEventListener('change', onFaceFile);
    $('btnCapture').addEventListener('click', onCapture);
    $('btnFaceClear').addEventListener('click', () => { $('faceFile').value = ''; setFace(null); });

    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadRefs(); loadAcTree(); load(); });
})();
