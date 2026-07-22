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

  let face = { image: null, t9: null, t5: null }; // 얼굴(정규화 이미지 + 템플릿 2종)
  let acTreeData = []; // 출입권한 트리(tb_ac_group)

  // ---- 참조 데이터(기관 옵션) ----
  async function loadRefs() {
    const data = await api.get(BASE + '/refs');
    const opts = (data.companies || [])
      .map((c) => `<option value="${esc(c.companyCode)}">${esc(c.companyName)}</option>`).join('');
    $('companyFilter').insertAdjacentHTML('beforeend', opts);
    $('companyCode').insertAdjacentHTML('beforeend', opts);
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
      body.innerHTML = '<tr><td colspan="7" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      const period = [r.accessStartDt || '', r.accessEndDt || ''].filter(Boolean).join(' ~ ');
      return `
      <tr>
        <td>${esc(r.personId)}</td>
        <td>${esc(r.personName)}</td>
        <td>${esc(r.companyName)}</td>
        <td>${esc(r.titleName)}</td>
        <td>${esc(r.statusName)}</td>
        <td>${esc(period)}</td>
        <td>${r.biostarUserId ? '연동' : '-'}</td>
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

  // biostar_ac_id 가 매핑된 노드만 선택 가능(최상위 구역은 라벨)
  function acNodesHtml(nodes) {
    return (nodes || []).map((n) => {
      const kids = (n.children || []).length ? `<div class="ac-select-node">${acNodesHtml(n.children)}</div>` : '';
      const row = n.biostarAcId != null
        ? `<label class="ac-select-item">
             <input type="checkbox" value="${esc(n.acGroupId)}"/>
             <span>${esc(n.acGroupName)}</span>
           </label>`
        : `<div class="ac-select-item group">${esc(n.acGroupName)}</div>`;
      return row + kids;
    }).join('');
  }

  function renderAcTree() {
    $('acTree').innerHTML = acTreeData.length
      ? acNodesHtml(acTreeData)
      : '<div class="empty">선택 가능한 출입권한이 없습니다. (출입권한관리에서 BiostarX 출입그룹을 매핑하세요)</div>';
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

  // ---- 등록 모달 ----
  function openModal() {
    ['personId', 'personName', 'birthDate', 'personPhone', 'mainTask', 'remark',
      'titleCode', 'titleName', 'statusCode', 'statusName', 'accessStartDt', 'accessEndDt']
      .forEach((id) => { $(id).value = ''; });
    $('companyCode').value = '';
    $('useYn').value = 'Y';
    $('faceFile').value = '';
    setFace(null);
    showTab('info');
    renderAcTree(); // 체크 초기화
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

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
      useYn: $('useYn').value,
      acGroupIds: selectedAcGroupIds(),
      faceImage: face.image,
      faceTemplate9: face.t9,
      faceTemplate5: face.t5,
    };
    if (!payload.personId) { toast.warning('인원ID는 필수입니다.'); return; }
    if (!payload.personName) { toast.warning('성명은 필수입니다.'); return; }
    await api.post(BASE, payload); // 서버 메시지(연동 경고 포함) 자동 토스트
    closeModal();
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', search);
    $('btnReset').addEventListener('click', reset);
    $('keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') search(); });
    $('companyFilter').addEventListener('change', search);
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', openModal);

    document.querySelectorAll('.tab-btn').forEach((b) =>
      b.addEventListener('click', () => showTab(b.dataset.tab)));

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
