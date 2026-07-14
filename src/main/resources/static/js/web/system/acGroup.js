/* 출입권한관리 — tb_common(AR) 동기화 트리(열고/닫기) + BiostarX 출입그룹 매핑.
   하위 그룹 추가(다중 선택) / 수정(그룹명·BiostarX 단건 선택) / 삭제(최상위 제외). */
(function () {
  const BASE = '/system/acGroup';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };
  let addParentId = null; // 하위 추가 대상 상위 노드
  let biostarMode = 'multi'; // 'multi'(하위추가) | 'single'(수정 매핑)

  // ---- 트리(중첩 + 접기/펼치기) ----
  async function loadTree() {
    const roots = (await api.get(BASE + '/tree')) || [];
    $('acTree').innerHTML = roots.length
      ? roots.map((r) => nodeHtml(r, 0)).join('')
      : '<div class="empty">출입구역(AR) 코드가 없습니다. 공통코드관리에서 AR 코드를 등록하세요.</div>';
  }

  function nodeHtml(n, depth) {
    const isTop = n.parentAcGroupId == null;
    const hasChildren = (n.children || []).length > 0;
    // 최상위: 코드(AR)+이름 / 하위: 이름만
    const label = isTop
      ? `<b>${esc(n.arCode || '')}</b>&nbsp;&nbsp;${esc(n.acGroupName)}`
      : esc(n.acGroupName);
    const caret = hasChildren
      ? '<span class="ac-caret" data-toggle="1">▼</span>'
      : '<span class="ac-caret-empty"></span>';
    const addBtn = PERM.canCreate
      ? `<button class="btn btn-sm" data-act="add" data-id="${esc(n.acGroupId)}">하위 그룹 추가</button>` : '';
    const editBtn = PERM.canCreate
      ? `<button class="btn btn-sm" data-act="edit" data-json='${esc(JSON.stringify(n))}'>수정</button>` : '';
    const childrenHtml = hasChildren
      ? `<div class="ac-children">${n.children.map((c) => nodeHtml(c, depth + 1)).join('')}</div>` : '';
    return `
      <div class="ac-node">
        <div class="tree-row">
          <span class="tree-label">${caret}${label}</span>
          <span class="ac-actions">${addBtn}${editBtn}</span>
        </div>
        ${childrenHtml}
      </div>`;
  }

  // ---- BiostarX 출입그룹 팝업 (multi=하위추가 / single=수정 매핑) ----
  async function openBiostar(mode, parentId) {
    biostarMode = mode;
    addParentId = parentId;
    $('biostarConfirm').style.display = mode === 'multi' ? '' : 'none';
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
    $('biostarInfo').textContent =
      `총 ${groups.length}개 — ${mode === 'multi' ? '하위로 추가할 출입그룹을 선택' : '매핑할 출입그룹 한 건을 선택'}하세요.`;
    if (!groups.length) {
      $('biostarList').innerHTML = '<tr><td colspan="3" class="empty">출입그룹이 없습니다.</td></tr>';
      return;
    }
    $('biostarList').innerHTML = groups.map((g) => {
      const cell = mode === 'multi'
        ? `<input type="checkbox" data-id="${esc(g.id)}" data-name="${esc(g.name)}"/>`
        : `<button class="btn btn-sm btn-primary" data-pick-id="${esc(g.id)}" data-pick-name="${esc(g.name)}">선택</button>`;
      return `<tr><td>${cell}</td><td>${esc(g.id)}</td><td style="text-align:left">${esc(g.name)}</td></tr>`;
    }).join('');
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
    $('acGroupName').value = n.acGroupName || '';
    $('biostarAcId').value = n.biostarAcId != null ? n.biostarAcId : '';
    $('biostarAcName').value = n.biostarAcName || '';
    if ($('btnDelete')) $('btnDelete').style.display = n.parentAcGroupId == null ? 'none' : ''; // 최상위 삭제 불가
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
      title: '삭제 확인', message: '선택한 출입그룹과 하위를 삭제하시겠습니까?', confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?acGroupId=${encodeURIComponent(id)}`);
    closeEdit();
    loadTree();
  }

  function bind() {
    $('acTree').addEventListener('click', (e) => {
      const caret = e.target.closest('[data-toggle]');
      if (caret) { caret.closest('.ac-node').classList.toggle('collapsed'); return; }
      const btn = e.target.closest('button[data-act]');
      if (!btn) return;
      if (btn.dataset.act === 'add') openBiostar('multi', Number(btn.dataset.id));
      else if (btn.dataset.act === 'edit') openEdit(JSON.parse(btn.dataset.json));
    });

    $('biostarClose').addEventListener('click', closeBiostar);
    $('biostarCancel').addEventListener('click', closeBiostar);
    $('biostarConfirm').addEventListener('click', confirmBiostar);
    // 단건 선택(수정 매핑) → 편집 폼에 반영
    $('biostarList').addEventListener('click', (e) => {
      const pick = e.target.closest('button[data-pick-id]');
      if (!pick) return;
      $('biostarAcId').value = pick.dataset.pickId;
      $('biostarAcName').value = pick.dataset.pickName;
      closeBiostar();
    });

    // 수정 모달: BiostarX 출입그룹ID 클릭 → 단건 선택 팝업
    $('biostarAcId').addEventListener('click', () => openBiostar('single', null));

    $('modalClose').addEventListener('click', closeEdit);
    $('btnCancel').addEventListener('click', closeEdit);
    if ($('btnSave')) $('btnSave').addEventListener('click', save);
    if ($('btnDelete')) $('btnDelete').addEventListener('click', remove);
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); loadTree(); });
})();
