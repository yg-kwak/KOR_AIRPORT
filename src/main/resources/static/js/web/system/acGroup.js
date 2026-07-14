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
  const usedBiostarIds = new Set(); // 이미 트리에 매핑된 biostar_ac_id

  // ---- 트리(중첩 + 접기/펼치기) ----
  async function loadTree() {
    const roots = (await api.get(BASE + '/tree')) || [];
    usedBiostarIds.clear();
    collectUsed(roots);
    $('acTree').innerHTML = roots.length
      ? roots.map((r) => nodeHtml(r, 0)).join('')
      : '<div class="empty">출입구역(AR) 코드가 없습니다. 공통코드관리에서 AR 코드를 등록하세요.</div>';
  }

  function collectUsed(nodes) {
    (nodes || []).forEach((n) => {
      if (n.biostarAcId != null) usedBiostarIds.add(Number(n.biostarAcId));
      collectUsed(n.children);
    });
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
    const unmapped = n.biostarAcId == null ? ' unmapped' : ''; // biostar 미매핑 표시
    const addBtn = PERM.canCreate
      ? `<button class="btn btn-sm" data-act="add" data-id="${esc(n.acGroupId)}">하위 그룹 추가</button>` : '';
    const editBtn = PERM.canCreate
      ? `<button class="btn btn-sm" data-act="edit" data-json='${esc(JSON.stringify(n))}'>수정</button>` : '';
    const childrenHtml = hasChildren
      ? `<div class="ac-children">${n.children.map((c) => nodeHtml(c, depth + 1)).join('')}</div>` : '';
    return `
      <div class="ac-node">
        <div class="tree-row">
          <span class="tree-label${unmapped}">${caret}${label}</span>
          <span class="ac-actions">${addBtn}${editBtn}</span>
        </div>
        ${childrenHtml}
      </div>`;
  }

  function toggleAll(collapsed) {
    $('acTree').querySelectorAll('.ac-node').forEach((node) => {
      const hasChildren = node.querySelector(':scope > .ac-children');
      if (hasChildren) node.classList.toggle('collapsed', collapsed);
    });
  }

  // ---- BiostarX 출입그룹 팝업 (multi=하위추가 / single=수정 매핑) ----
  async function openBiostar(mode, parentId) {
    biostarMode = mode;
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
    $('biostarInfo').textContent =
      `총 ${groups.length}개 — ${mode === 'multi' ? '하위로 추가할 출입그룹(다중)을 선택' : '매핑할 출입그룹 1건을 선택'}하고 확인하세요.`;
    if (!groups.length) {
      $('biostarList').innerHTML = '<tr><td colspan="3" class="empty">출입그룹이 없습니다.</td></tr>';
      return;
    }
    // 두 모드 모두 체크박스. single 은 change 시 1건만 유지(radio 처럼).
    // 하위추가(multi)에서 이미 매핑된 출입그룹은 비활성(중복 추가 방지).
    $('biostarList').innerHTML = groups.map((g) => {
      const used = biostarMode === 'multi' && usedBiostarIds.has(Number(g.id));
      const cb = `<input type="checkbox" data-id="${esc(g.id)}" data-name="${esc(g.name)}"${used ? ' disabled' : ''}/>`;
      const nameCell = used ? `${esc(g.name)} <span class="form-hint">(이미 추가됨)</span>` : esc(g.name);
      return `<tr class="${used ? 'ac-used' : ''}"><td>${cb}</td><td>${esc(g.id)}</td><td style="text-align:left">${nameCell}</td></tr>`;
    }).join('');
  }
  function closeBiostar() { $('biostarModal').classList.remove('open'); }

  async function confirmBiostar() {
    const checked = [...$('biostarList').querySelectorAll('input[type="checkbox"]:checked')];
    if (!checked.length) { toast.warning('출입그룹을 선택해주세요.'); return; }
    if (biostarMode === 'single') {
      // 수정 매핑: 1건만 반영
      $('biostarAcId').value = checked[0].dataset.id;
      $('biostarAcName').value = checked[0].dataset.name;
      closeBiostar();
      return;
    }
    const groups = checked.map((c) => ({ biostarAcId: Number(c.dataset.id), biostarAcName: c.dataset.name }));
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

    $('btnExpandAll').addEventListener('click', () => toggleAll(false));
    $('btnCollapseAll').addEventListener('click', () => toggleAll(true));

    $('biostarClose').addEventListener('click', closeBiostar);
    $('biostarCancel').addEventListener('click', closeBiostar);
    $('biostarConfirm').addEventListener('click', confirmBiostar);
    // 행 클릭 → 체크 토글(체크박스 직접 클릭은 기본 동작)
    $('biostarList').addEventListener('click', (e) => {
      if (e.target.closest('input[type="checkbox"]')) return;
      const box = e.target.closest('tr')?.querySelector('input[type="checkbox"]');
      if (box && !box.disabled) { box.checked = !box.checked; box.dispatchEvent(new Event('change', { bubbles: true })); }
    });
    // single 모드: 체크는 1건만 유지(radio 처럼)
    $('biostarList').addEventListener('change', (e) => {
      const cb = e.target.closest('input[type="checkbox"]');
      if (!cb || biostarMode !== 'single' || !cb.checked) return;
      $('biostarList').querySelectorAll('input[type="checkbox"]').forEach((o) => {
        if (o !== cb) o.checked = false;
      });
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
