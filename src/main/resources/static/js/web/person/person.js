/* 정규인원등록 화면 — 골든 샘플(loginUser) 구조 + 탭(사용자정보/신원보안/사용자권한/카드정보).
   성명·생년월일·연락처는 서버에서 ARIA 암호화. 얼굴은 파일 업로드/장치 촬영을 서버가 BiostarX 로 중계한다.
   등록/수정/삭제 시 BiostarX 사용자도 동기화된다(실패해도 인원은 저장되고 경고 토스트). 카드는 추후. */
(function () {
  const BASE = '/person/person';
  const AC_TREE = 'acTree'; // 공용 출입권한 트리 컨테이너 id
  const CARD_LIST = 'cardList'; // 공용 카드 목록 컨테이너 id
  const INIT = { page: 1, size: 30, keyword: '', searchType: 'all', companyCode: '',
    statusCode: '', faceYn: '', cardYn: '', sort: 'personId', dir: 'asc' };
  const state = { ...INIT };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const fmtDt = (v) => (v == null ? '' : String(v).replace('T', ' '));

  /* 저장하면 얼굴이 지워지는 상태 — 공통코드(PS.code_tag)가 원천이라 서버가 내려준다.
     화면에 코드를 박으면 현장에서 상태를 추가했을 때 안내가 조용히 빠진다. */
  const DISABLED_STATUS = window.PAGE_DISABLED_STATUS || [];

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  const MAX_ACCESS_END_DT = window.MAX_ACCESS_END_DT || '2037-12-31T23:59'; // 저장 상한 — 장비가 못 받는 값
  const DEFAULT_ACCESS_END_DT = window.DEFAULT_ACCESS_END_DT || MAX_ACCESS_END_DT; // 등록 기본값 — 계약 기간(넘겨도 저장됨)
  const TITLE_ALLOWED = /^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ\s]+$/; // 직위: 특수문자 금지
  const PERSON_ID_ALLOWED = /^[0-9A-Za-z]+$/; // 인원ID: 영문·숫자만(BiostarX 사용자ID 와 같은 키)

  // 서버로 그대로 전송하는 입력 필드(= PersonForm 속성명). 첨부문서는 fileField, 얼굴은 face 가 따로 담당.
  const FORM_FIELDS = ['personId', 'personName', 'birthDate', 'personPhone', 'companyCode', 'titleCode',
    'statusCode', 'mainTask', 'accessStartDt', 'accessEndDt', 'remark',
    'idCheckDt', 'securityEduDt', 'securityEduScore', 'finalApproveDt'];
  const VIEW_FIELDS = ['companyName', 'titleName', 'statusName', 'regDt']; // 화면 표시 전용(미전송)

  let face = { photo: null, image: null, t9: null, t5: null }; // photo=원본 사진 / image=정규화 얼굴(인증용)

  // 현재 일시를 datetime-local 형식("YYYY-MM-DDTHH:mm")으로 (로컬 시간 기준)
  function nowLocal() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  // ---- 목록 ----
  async function load() {
    const q =
      `?page=${state.page}&size=${state.size}` +
      `&keyword=${encodeURIComponent(state.keyword)}&searchType=${state.searchType}` +
      `&companyCode=${encodeURIComponent(state.companyCode)}&statusCode=${state.statusCode}` +
      `&faceYn=${state.faceYn}&cardYn=${state.cardYn}&sort=${state.sort}&dir=${state.dir}`;
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
      body.innerHTML = '<tr><td colspan="11" class="empty">조회 결과가 없습니다.</td></tr>';
      syncSelection();
      return;
    }
    body.innerHTML = rows.map((r) => {
      const period = [fmtDt(r.accessStartDt), fmtDt(r.accessEndDt)].filter(Boolean).join(' ~ ');
      return `
      <tr${PERM.canCreate ? ' class="row-click" data-json=\'' + esc(JSON.stringify(r)) + '\'' : ''}>
        <td><input type="checkbox" class="row-chk" data-id="${esc(r.personId)}"/></td>
        <td>${esc(r.personId)}</td><td>${esc(r.personName)}</td><td>${esc(r.companyCode)}</td>
        <td>${esc(r.companyName)}</td><td>${esc(r.titleName)}</td><td>${esc(r.birthDate)}</td>
        <td>${esc(period)}</td>
        <td>${badge.personStatus(r.statusCode, r.statusName)}</td>
        <td>${r.faceYn === 'Y' ? badge.of('등록', 'success') : badge.none('미등록')}</td>
        <td>${r.cardCount > 0 ? badge.count(r.cardCount, '장') : badge.none('없음')}</td>
      </tr>`;
    }).join('');
    syncSelection();
  }

  function renderPaging(page, totalPages) {
    pager.render($('paging'), page, totalPages, (p) => { state.page = p; load(); });
  }

  function search() {
    Object.assign(state, { page: 1, keyword: $('keyword').value.trim(), searchType: $('searchType').value,
      companyCode: $('companyFilter').value, statusCode: $('statusFilter').value,
      faceYn: $('faceYnFilter').value, cardYn: $('cardYnFilter').value });
    load();
  }

  function reset() {
    $('searchType').value = 'all';
    $('pageSize').value = '30';
    ['keyword', 'companyFilter', 'companyFilterName', 'statusFilter', 'statusFilterName',
      'faceYnFilter', 'cardYnFilter'].forEach((id) => { $(id).value = ''; });
    Object.assign(state, INIT);
    load();
  }

  function toggleSort(col) {
    if (state.sort === col) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = col; state.dir = 'asc'; }
    state.page = 1;
    load();
  }

  // ---- 선택(체크박스) — 1건 이상 선택되면 '선택 삭제' 버튼이 등록 왼쪽에 나타난다 ----
  function selectedIds() {
    return [...$('gridBody').querySelectorAll('.row-chk:checked')].map((c) => c.dataset.id);
  }

  function syncSelection() {
    const ids = selectedIds();
    const boxes = $('gridBody').querySelectorAll('.row-chk');
    const del = $('btnDeleteSel');
    const prt = $('btnPrintSel');
    if (del) { del.style.display = ids.length ? '' : 'none'; del.textContent = `선택 삭제 (${ids.length})`; }
    if (prt) { prt.style.display = ids.length ? '' : 'none'; prt.textContent = `카드 출력 (${ids.length})`; }
    $('checkAll').checked = boxes.length > 0 && ids.length === boxes.length;
  }

  // ---- 탭 ----
  function showTab(name) {
    document.querySelectorAll('.tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
    document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === 'tab-' + name));
  }

  // ---- 얼굴(파일 업로드 / 장치 촬영) ----
  function setFace(photo, image, t9, t5) {
    face = { photo: photo || null, image: image || null, t9: t9 || null, t5: t5 || null };
    const img = $('facePreview');
    if (photo) img.src = 'data:image/jpeg;base64,' + photo;
    else img.removeAttribute('src'); // src="" 는 페이지 URL 재요청 → 깨진 아이콘
    $('facePreviewBox').classList.toggle('has-face', !!photo);
  }

  function fileToBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  // 원본은 사진으로, 인증용 얼굴은 응답의 정규화 이미지로 — 원본을 인증용에 넣으면 템플릿과 짝이 안 맞아 인증이 실패한다
  async function useFaceImage(b64) {
    const res = await api.post(BASE + '/face/upload', { image: b64 }); // {success,message,image,template9/5}
    if (!res || !res.success) { toast.error((res && res.message) || '사진 등록에 실패했습니다.'); return; }
    setFace(b64, res.image, res.template9, res.template5);
    toast.success('사진을 등록했습니다.');
  }

  async function onFaceFile(e) {
    const file = e.target.files && e.target.files[0];
    if (file) await useFaceImage(await fileToBase64(file)); // 형식·용량 검증은 서버(PersonFaceService)
  }

  async function onCapture() {
    const res = await api.get(BASE + '/face/capture');
    if (!res || !res.success) { toast.error((res && res.message) || '얼굴 촬영에 실패했습니다.'); return; }
    setFace(res.image, res.image, res.template9, res.template5); // 장치는 정규화 이미지만 준다
    toast.success('얼굴을 촬영했습니다.');
  }

  // ---- 등록/수정 모달 ----
  let editMode = 'create';
  let prevStatus = null; // 모달을 열 때의 상태 — '정지로 바뀌는 순간'만 묻기 위해 들고 있는다
  const SUSPENDED = window.PAGE_SUSPENDED_STATUS || ''; // 인원상태 [정지] (서버 주입)

  async function openModal(mode, row) {
    editMode = mode;
    prevStatus = row ? row.statusCode : null;
    $('modalTitle').textContent = mode === 'create' ? '정규인원 등록' : '정규인원 수정';
    [...FORM_FIELDS, ...VIEW_FIELDS].forEach((id) => { $(id).value = ''; });
    fileField.set('idCheckFile', null, null);
    fileField.set('approveFile', null, null);
    $('faceFile').value = '';
    setFace(null);
    showTab('info');
    $('personId').readOnly = mode === 'edit'; // PK 는 수정 불가
    if ($('btnDelete')) $('btnDelete').style.display = mode === 'edit' ? '' : 'none';
    if (mode === 'create') { // 초기값: 인원ID 자동 채번, 시작=현재 일시, 종료=기본값(상한 아님)
      $('accessStartDt').value = nowLocal();
      $('accessEndDt').value = DEFAULT_ACCESS_END_DT;
      $('personId').value = (await api.get(BASE + '/nextId')) || '';
    }
    acGroupTree.set(AC_TREE, []); // 체크 초기화
    cardList.set(CARD_LIST, []);
    $('editModal').classList.add('open');
    if (mode !== 'edit' || !row) return;

    [...FORM_FIELDS, ...VIEW_FIELDS].forEach((id) => { $(id).value = row[id] != null ? row[id] : ''; });
    $('regDt').value = String(row.regDt || '').slice(0, 10); // 시스템 등록일은 날짜만
    const q = `?personId=${encodeURIComponent(row.personId)}`;
    const dl = (type) => `${BASE}/file${q}&fileType=${type}`;
    fileField.set('idCheckFile', row.idCheckFile, dl('ID_CHECK'));
    fileField.set('approveFile', row.approveFile, dl('APPROVE'));
    // 기존 얼굴·출입권한 로드(얼굴 템플릿은 저장하지 않으므로 이미지만 — 손대지 않으면 변경으로 보지 않는다)
    const [photo, acIds, cards] = await Promise.all([
      api.get(BASE + '/photo' + q),
      api.get(BASE + '/personAcGroups' + q),
      api.get(BASE + '/cards' + q),
    ]);
    if (photo) setFace(photo, null, null, null); // 저장된 사진만 복원(템플릿은 없음 — 재전송하지 않는다)
    acGroupTree.set(AC_TREE, acIds || []);
    cardList.set(CARD_LIST, cards || []);
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
    closeModal();
    load();
  }

  async function removeSelected() {
    const ids = selectedIds();
    if (!PERM.canDelete || !ids.length) return;
    const ok = await confirmModal.open({
      title: '선택 삭제 확인',
      message: `선택한 ${ids.length}건을 삭제하시겠습니까? BiostarX 사용자도 함께 삭제됩니다.`,
      confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(BASE + '/bulk', ids);
    load();
  }

  async function save() {
    if (!PERM.canCreate) return;
    const payload = { acGroupIds: acGroupTree.get(AC_TREE), cards: cardList.get(CARD_LIST), facePhoto: face.photo, faceImage: face.image, faceTemplate9: face.t9, faceTemplate5: face.t5 };
    FORM_FIELDS.forEach((id) => { payload[id] = $(id).value.trim() || null; });
    payload.securityEduScore = payload.securityEduScore ? Number(payload.securityEduScore) : null;
    const idCheck = fileField.get('idCheckFile');
    const approve = fileField.get('approveFile');
    payload.idCheckFile = idCheck.name;
    payload.idCheckFileData = idCheck.data;
    payload.approveFile = approve.name;
    payload.approveFileData = approve.data;

    const required = [
      [payload.personId, '인원ID'], [payload.personName, '성명'], [payload.birthDate, '생년월일'],
      [payload.companyCode, '기관'], [payload.statusCode, '상태'],
      [payload.accessStartDt, '출입시작일'], [payload.accessEndDt, '출입종료일'],
    ].find(([v]) => !v);
    if (required) { toast.warning(`${required[1]}은(는) 필수입니다.`); return; }
    if (!PERSON_ID_ALLOWED.test(payload.personId)) { toast.warning('인원ID 는 영문·숫자만 사용할 수 있습니다.'); return; }
    if (!birthDate.isValid(payload.birthDate)) { toast.warning(birthDate.HINT); return; }
    if (payload.accessEndDt > MAX_ACCESS_END_DT) { toast.warning(`출입종료일은 ${MAX_ACCESS_END_DT.replace('T', ' ')} 까지만 지정할 수 있습니다. BiostarX 가 받을 수 있는 마지막 날짜입니다.`); return; }
    if (payload.accessStartDt > payload.accessEndDt) { toast.warning('출입시작일은 출입종료일보다 늦을 수 없습니다.'); return; }
    const titleName = $('titleName').value.trim();
    if (titleName && !TITLE_ALLOWED.test(titleName)) { toast.warning('직위에 특수문자를 사용할 수 없습니다.'); return; }
    // 카드 발급 시 출입구역이 없으면 실제로 못 여는 무효 카드가 되므로 구역 선택을 강제한다
    if (payload.cards.length && !payload.acGroupIds.length) { toast.warning('카드를 발급하려면 출입구역을 선택하세요.'); return; }
    // 비활성 상태(정지·퇴사·회수·분실)로 저장하면 얼굴이 지워진다 — 되돌릴 수 없으므로 먼저 알린다.
    // 지울 얼굴이 없으면 묻지 않는다(없는 것을 지운다고 겁줄 이유가 없다).
    if (DISABLED_STATUS.includes(payload.statusCode) && (face.photo || face.image)) {
      const st = $('statusName').value || payload.statusCode;
      const ok = await confirmModal.open({ title: '얼굴 정보 삭제', confirmText: '삭제하고 저장',
        message: `'${st}' 상태로 저장하면 등록된 얼굴 정보가 삭제됩니다. BiostarX 장비의 얼굴도 함께 지워지며 되돌릴 수 없습니다.` });
      if (!ok) return;
    }
    // 상태를 [정지] 로 바꾸는 순간에만 묻는다 — 이미 정지였던 사람을 다시 저장할 때는 묻지 않는다.
    // 확인을 받지 않으면 상태만 바뀌고 제재인원에는 오르지 않는다(서버가 이 값으로 갈린다).
    if (SUSPENDED && payload.statusCode === SUSPENDED && prevStatus !== SUSPENDED) {
      payload.addToBlacklist = await confirmModal.open({
        title: '제재인원 추가',
        confirmText: '추가',
        message: `'${payload.personName}' 님을 제재인원에 추가하겠습니까?
추가하면 임시·장기·정규 등록이 막힙니다(정지기간은 제재인원관리에서 지정).`,
      });
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
    $('companyFilterName').addEventListener('click', async () => {
      const sel = await companyPicker.open();
      if (!sel) return;
      $('companyFilter').value = sel.companyCode;
      $('companyFilterName').value = sel.companyName;
      search();
    });
    const filterWrap = $('companyFilterName').closest('.picker-wrap');
    if (filterWrap) {
      const clearBtn = filterWrap.querySelector('.picker-clear');
      if (clearBtn) clearBtn.addEventListener('click', () => setTimeout(search, 0));
    }
    $('pageSize').addEventListener('change', (e) => { state.size = Number(e.target.value); state.page = 1; load(); });
    if ($('btnNew')) $('btnNew').addEventListener('click', () => openModal('create', null));
    if ($('btnExcelImport')) $('btnExcelImport').addEventListener('click', () => excelImport.open({
      baseUrl: BASE,
      hint: ['양식을 내려받아 인원 정보를 채운 뒤 업로드하세요.', '<b>기관코드·성명</b>은 필수입니다.',
        '사용자권한·카드정보는 제외되고, 인원ID 를 비우면 자동 채번됩니다.', '2행은 예시이니 지우거나 덮어써서 입력하세요.',
        '<b>기존 인원 갱신</b>을 켜면 이미 있는 인원ID 행은 엑셀에 적은 열만 바뀌고 빈 칸은 그대로 둡니다(예: ID·성명·생년월일만 적으면 그 둘만 갱신).'],
      option: { name: 'updateExisting', label: '기존 인원 갱신', hint: '이미 있는 인원의 정보를 수정합니다' }, onDone: load,
    }));
    if ($('btnDeleteSel')) $('btnDeleteSel').addEventListener('click', removeSelected);
    if ($('btnPrintSel')) $('btnPrintSel').addEventListener('click', () => window.cardPrint.bulk(selectedIds()));
    if ($('btnDelete')) $('btnDelete').addEventListener('click', () => remove($('personId').value));

    // 전체선택 / 행 클릭 → 수정 (체크박스 클릭은 선택 토글만)
    $('checkAll').addEventListener('click', (e) => {
      $('gridBody').querySelectorAll('.row-chk').forEach((c) => { c.checked = e.target.checked; });
      syncSelection();
    });
    $('gridBody').addEventListener('click', (e) => {
      if (e.target.closest('.row-chk')) { syncSelection(); return; }
      const tr = e.target.closest('tr[data-json]');
      if (tr && PERM.canCreate) openModal('edit', JSON.parse(tr.dataset.json));
    });

    document.querySelectorAll('.tab-btn').forEach((b) =>
      b.addEventListener('click', () => showTab(b.dataset.tab)));

    // 기관(등록모달): 공용 기관 팝업
    $('companyName').addEventListener('click', async () => {
      const sel = await companyPicker.open();
      if (sel) { $('companyCode').value = sel.companyCode; $('companyName').value = sel.companyName; }
    });

    // 직위(UT)·상태(PS)는 공통 코드팝업
    $('titleName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'UT', cmmName: '직위' });
      if (sel) { $('titleCode').value = sel.codeId; $('titleName').value = sel.codeName; }
    });
    // 검색조건 상태·얼굴·카드 — 고르면 즉시 재조회(기관 필터와 같다)
    $('statusFilterName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'PS', cmmName: '상태' });
      if (sel) { $('statusFilter').value = sel.codeId; $('statusFilterName').value = sel.codeName; search(); }
    });
    ['faceYnFilter', 'cardYnFilter'].forEach((id) => $(id).addEventListener('change', search));
    $('statusName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'PS', cmmName: '상태' });
      if (sel) { $('statusCode').value = sel.codeId; $('statusName').value = sel.codeName; }
    });

    // 입력 포맷 고정 — 인원ID는 영문·숫자만, 생년월일은 YYYY-MM-DD 자동 하이픈
    $('personId').addEventListener('input', (e) => {
      e.target.value = e.target.value.replace(/[^0-9A-Za-z]/g, '');
    });
    birthDate.bindInput($('birthDate')); // 입력 보정은 공용 규칙(core/birth-date.js)

    $('faceFile').addEventListener('change', onFaceFile);
    $('btnCapture').addEventListener('click', onCapture);
    if ($('btnFaceCam')) $('btnFaceCam').addEventListener('click', () => window.faceCam.open(useFaceImage));
    $('btnFaceClear').addEventListener('click', () => { $('faceFile').value = ''; setFace(null, null); });

    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);

    document.querySelectorAll('th.sortable').forEach((th) =>
      th.addEventListener('click', () => toggleSort(th.dataset.sort)));
  }

  document.addEventListener('DOMContentLoaded', () => {
    bind();
    acGroupTree.init(AC_TREE, BASE + '/acGroups');
    cardList.init(CARD_LIST, { baseUrl: BASE, cardTypeName: '인원',
      print: (row) => { // 프린트: 얼굴+카드 등록 인원만
        if (!face.photo) { toast.warning('얼굴이 등록된 인원만 출력할 수 있습니다.'); return; }
        window.cardPrint.open($('personId').value, row.cardId);
      } });
    load();
  });
})();
