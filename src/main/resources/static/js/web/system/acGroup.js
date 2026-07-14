/* 출입권한관리 — tb_common(AR) 동기화된 트리(최상위=출입구역) + BiostarX 출입그룹 하위 매핑.
   하위 그룹 추가(BiostarX 출입그룹 선택) / 수정(이름·매핑) / 삭제(최상위 제외). */
(function () {
  const BASE = '/system/acGroup';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };
  let addParentId = null; // 하위 추가 대상 상위 노드

  // ---- 트리 ----
  async function loadTree() {
    const roots = (await api.get(BASE + '/tree')) || [];
    const rows = [];
    const walk = (n, depth) => {
      const isTop = n.parentAcGroupId == null;
      const code = isTop ? (n.arCode || '') : (n.biostarAcId != null ? n.biostarAcId : '');
      const addBtn = PERM.canCreate
        ? `<button class="btn btn-sm" data-act="add" data-id="${esc(n.acGroupId)}">하위 그룹 추가</button>` : '';
      const editBtn = PERM.canCreate
        ? `<button class="btn btn-sm" data-act="edit" data-json='${esc(JSON.stringify(n))}'>수정</button>` : '';
      rows.push(`
        <div class="tree-row" style="padding-left:${8 + depth * 22}px">
          <span class="tree-label">
            ${depth > 0 ? '<span class="tree-branch">└</span>' : ''}
            <b>${esc(code)}</b>&nbsp;&nbsp;${esc(n.acGroupName)}
          </span>
          <span class="ac-actions">${addBtn}${editBtn}</span>
        </div>`);
      (n.children || []).forEach((c) => walk(c, depth + 1));
    };
    roots.forEach((r) => walk(r, 0));
    $('acTree').innerHTML = rows.join('') || '<div class="empty">출입구역(AR) 코드가 없습니다. 공통코드관리에서 AR 코드를 등록하세요.</div>';
  }

  // ---- BiostarX 출입그룹 선택 팝업 ----
  async function openBiostarPicker(parentId) {
    addParentId = parentId;
    $('biostarInfo').textContent = 'BiostarX 출입그룹을 불러오는 중...';
    $('biostarList').innerHTML = '<tr><td colspan="3" class="empty">불러오는 중...</td></tr>';
    $('biostarModal').classList.add('open');
    const res = await api.get(BASE + '/biostarGroups'); // {success,message,groups}
    if (!res || !res.success) {
      $('biostarInfo').textContent = (res && res.message) || 'BiostarX 조회 실패';
      $('biostarList').innerHTML = '<tr><td colspan="3" class="empty">조회 실패</td></tr>';
      return;
    }
    const groups = res.groups || [];
    $('biostarInfo').textContent = `총 ${groups.length}개 — 하위로 추가할 출입그룹을 선택하세요.`;
    $('biostarList').innerHTML = groups.length
      ? groups.map((g) => `
        <tr>
          <td><input type="checkbox" data-id="${esc(g.id)}" data-name="${esc(g.name)}"/></td>
          <td>${esc(g.id)}</td>
          <td style="text-align:left">${esc(g.name)}</td>
        </tr>`).join('')
      : '<tr><td colspan="3" class="empty">출입그룹이 없습니다.</td></tr>';
  }
  function closeBiostar() { $('biostarModal').classList.remove('open'); }

  async function confirmBiostar() {
    const groups = [];
    $('biostarList').querySelectorAll('input[type="checkbox"]:checked').forEach((c) => {
      groups.push({ biostarAcId: Number(c.dataset.id), biostarAcName: c.dataset.name });
    });
    if (!groups.length) { toast.warning('추가할 출입그룹을 선택해주세요.'); return; }
    await api.post(BASE + '/children', { parentId: addParentId, groups });
    closeBiostar();
    loadTree();
  }

  // ---- 수정/삭제 ----
  function openEdit(n) {
    $('acGroupId').value = n.acGroupId;
    $('isTop').value = n.parentAcGroupId == null ? '1' : '0';
    $('acGroupName').value = n.acGroupName || '';
    $('biostarAcId').value = n.biostarAcId != null ? n.biostarAcId : '';
    $('biostarAcName').value = n.biostarAcName || '';
    // 최상위는 삭제 불가
    if ($('btnDelete')) $('btnDelete').style.display = n.parentAcGroupId == null ? 'none' : '';
    $('editModal').classList.add('open');
  }
  function closeEdit() { $('editModal').classList.remove('open'); }

  async function save() {
    if (!PERM.canCreate) return;
    const name = $('acGroupName').value.trim();
    if (!name) { toast.warning('그룹명은 필수입니다.'); return; }
    const payload = {
      acGroupId: Number($('acGroupId').value),
      acGroupName: name,
      biostarAcId: $('biostarAcId').value ? Number($('biostarAcId').value) : null,
      biostarAcName: $('biostarAcName').value.trim() || null,
    };
    await api.put(BASE, payload);
    closeEdit();
    loadTree();
  }

  async function remove() {
    if (!PERM.canDelete) return;
    const id = Number($('acGroupId').value);
    const ok = await confirmModal.open({
      title: '삭제 확인', message: `선택한 출입그룹과 하위를 삭제하시겠습니까?`, confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?acGroupId=${encodeURIComponent(id)}`);
    closeEdit();
    loadTree();
  }

  function bind() {
    $('acTree').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-act]');
      if (!btn) return;
      if (btn.dataset.act === 'add') openBiostarPicker(Number(btn.dataset.id));
      else if (btn.dataset.act === 'edit') openEdit(JSON.parse(btn.dataset.json));
    });
    $('biostarClose').addEventListener('click', closeBiostar);
    $('biostarCancel').addEventListener('click', closeBiostar);
    $('biostarConfirm').addEventListener('click', confirmBiostar);
    $('modalClose').addEventListener('click', closeEdit);
    $('btnCancel').addEventListener('click', closeEdit);
    if ($('btnSave')) $('btnSave').addEventListener('click', save);
    if ($('btnDelete')) $('btnDelete').addEventListener('click', remove);
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadTree(); });
})();
