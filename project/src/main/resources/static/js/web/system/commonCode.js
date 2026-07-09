/* 공통코드관리 화면 — 골든 샘플. 신규 CRUD 화면 스크립트는 이 구조를 따른다. */
(function () {
  const BASE = '/system/commonCode';
  const state = { page: 1, size: 10, keyword: '' };

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  async function load() {
    const q = `?page=${state.page}&size=${state.size}&keyword=${encodeURIComponent(state.keyword)}`;
    const data = await api.get(BASE + '/list' + q);
    renderRows(data.content);
    renderPaging(data.page, data.totalPages);
  }

  function renderRows(rows) {
    const body = $('gridBody');
    if (!rows || rows.length === 0) {
      body.innerHTML = '<tr><td colspan="6" class="empty">조회 결과가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => `
      <tr>
        <td>${esc(r.cmmId)}</td>
        <td>${esc(r.cmmName)}</td>
        <td>${esc(r.codeId)}</td>
        <td>${esc(r.codeName)}</td>
        <td>${r.useYn === 'Y' ? '사용' : '미사용'}</td>
        <td>
          <button class="btn btn-sm" data-act="edit" data-json='${esc(JSON.stringify(r))}'>수정</button>
          <button class="btn btn-sm btn-danger" data-act="del" data-cmm="${esc(r.cmmId)}" data-code="${esc(r.codeId)}">삭제</button>
        </td>
      </tr>`).join('');
  }

  function renderPaging(page, totalPages) {
    const box = $('paging');
    if (!totalPages || totalPages <= 1) { box.innerHTML = ''; return; }
    let html = '';
    for (let i = 1; i <= totalPages; i++) {
      html += `<button data-page="${i}" class="${i === page ? 'active' : ''}">${i}</button>`;
    }
    box.innerHTML = html;
  }

  function openModal(mode, row) {
    $('mode').value = mode;
    $('modalTitle').textContent = mode === 'create' ? '공통코드 등록' : '공통코드 수정';
    $('cmmId').value = row ? row.cmmId : '';
    $('cmmName').value = row ? row.cmmName || '' : '';
    $('codeId').value = row ? row.codeId : '';
    $('codeName').value = row ? row.codeName || '' : '';
    $('useYn').value = row ? row.useYn || 'Y' : 'Y';
    // 수정 시 PK 잠금
    $('cmmId').readOnly = mode === 'edit';
    $('codeId').readOnly = mode === 'edit';
    $('editModal').classList.add('open');
  }
  function closeModal() { $('editModal').classList.remove('open'); }

  async function save() {
    const payload = {
      cmmId: $('cmmId').value.trim(),
      cmmName: $('cmmName').value.trim(),
      codeId: $('codeId').value.trim(),
      codeName: $('codeName').value.trim(),
      useYn: $('useYn').value,
    };
    if (!payload.cmmId || !payload.codeId) { alert('코드구분ID와 코드ID는 필수입니다.'); return; }
    if ($('mode').value === 'create') await api.post(BASE, payload);
    else await api.put(BASE, payload);
    closeModal();
    load();
  }

  async function remove(cmmId, codeId) {
    if (!confirm('삭제하시겠습니까?')) return;
    await api.del(`${BASE}?cmmId=${encodeURIComponent(cmmId)}&codeId=${encodeURIComponent(codeId)}`);
    load();
  }

  function bind() {
    $('btnSearch').addEventListener('click', () => { state.keyword = $('keyword').value.trim(); state.page = 1; load(); });
    $('btnNew').addEventListener('click', () => openModal('create', null));
    $('btnSave').addEventListener('click', save);
    $('btnCancel').addEventListener('click', closeModal);
    $('modalClose').addEventListener('click', closeModal);
    $('gridBody').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (!btn) return;
      if (btn.dataset.act === 'edit') openModal('edit', JSON.parse(btn.dataset.json));
      if (btn.dataset.act === 'del') remove(btn.dataset.cmm, btn.dataset.code);
    });
    $('paging').addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (btn) { state.page = Number(btn.dataset.page); load(); }
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); load(); });
})();
