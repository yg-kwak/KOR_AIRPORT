/* 권한메뉴관리 — 좌: 권한 목록(tb_menu_auth), 우: 권한 설정(전체 메뉴 트리 체크 = tb_menu_auth_detail).
   권한을 선택하면 상세에 따라 트리가 체크되고, 수정완료로 저장한다. 체크된 메뉴 = 접근 허용(read/create/delete 부여). */
(function () {
  const BASE = '/system/menuAuth';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };
  const EDITABLE = PERM.canCreate;

  let selectedId = null; // null = 신규(등록) 모드
  const nodesById = {};  // menuId → { id, name, parentId, childIds:[] }
  let order = [];        // DFS 순서 menuId 배열(렌더/수집용)

  // ---- 좌측: 권한 목록 ----
  async function loadAuths(selectId) {
    const data = await api.get(BASE + '/list?page=1&size=1000&sort=authId&dir=asc');
    renderAuthList(data.content || []);
    if (selectId != null) {
      const row = data.content.find((r) => r.authId === selectId);
      if (row) selectAuth(row.authId, row.authName);
    }
  }

  function renderAuthList(rows) {
    const body = $('authList');
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="2" class="empty">등록된 권한이 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => `
      <tr class="auth-row${r.authId === selectedId ? ' active' : ''}" data-id="${esc(r.authId)}" data-name="${esc(r.authName)}">
        <td style="text-align:left">${esc(r.authName)}</td>
        <td class="auth-gear">⚙</td>
      </tr>`).join('');
  }

  // ---- 우측: 메뉴 트리 ----
  async function loadTree() {
    const roots = (await api.get(BASE + '/menus')) || [];
    order = [];
    Object.keys(nodesById).forEach((k) => delete nodesById[k]);
    const rowsHtml = [];
    const walk = (node, depth, parentId) => {
      nodesById[node.menuId] = { id: node.menuId, name: node.menuName, parentId, childIds: [] };
      if (parentId != null) nodesById[parentId].childIds.push(node.menuId);
      order.push(node.menuId);
      rowsHtml.push(`
        <div class="tree-row" style="padding-left:${depth * 22}px">
          <label class="tree-label">
            ${depth > 0 ? '<span class="tree-branch">└</span>' : ''}
            <input type="checkbox" data-id="${esc(node.menuId)}"${EDITABLE ? '' : ' disabled'}/>
            <span>${esc(node.menuName)}</span>
          </label>
        </div>`);
      (node.children || []).forEach((c) => walk(c, depth + 1, node.menuId));
    };
    roots.forEach((r) => walk(r, 0, null));
    $('permTree').innerHTML = rowsHtml.join('') || '<div class="empty">메뉴가 없습니다.</div>';
  }

  const box = (id) => $('permTree').querySelector(`input[data-id="${id}"]`);

  function setSubtree(id, on) {
    const b = box(id);
    if (b) b.checked = on;
    (nodesById[id].childIds || []).forEach((c) => setSubtree(c, on));
  }
  function refreshAncestors(id) {
    let p = nodesById[id].parentId;
    while (p != null) {
      const any = nodesById[p].childIds.some((c) => box(c) && box(c).checked);
      const pb = box(p);
      if (pb) pb.checked = any;
      p = nodesById[p].parentId;
    }
  }
  function setAll(on) { order.forEach((id) => { if (nodesById[id].parentId == null) setSubtree(id, on); }); }
  function checkedMenuIds() { return order.filter((id) => box(id) && box(id).checked); }

  // ---- 선택/신규/저장/삭제 ----
  async function selectAuth(id, name) {
    selectedId = id;
    $('authName').value = name || '';
    markActive();
    setAll(false);
    const list = await api.get(BASE + '/detail?authId=' + encodeURIComponent(id));
    (list || []).forEach((d) => { const b = box(d.menuId); if (b) b.checked = true; });
  }

  function markActive() {
    document.querySelectorAll('#authList .auth-row').forEach((tr) =>
      tr.classList.toggle('active', Number(tr.dataset.id) === selectedId));
  }

  function newAuth() {
    selectedId = null;
    $('authName').value = '';
    setAll(false);
    markActive();
    $('authName').focus();
  }

  async function save() {
    if (!EDITABLE) return;
    const authName = $('authName').value.trim();
    if (!authName) { toast.warning('권한명은 필수입니다.'); return; }
    const details = checkedMenuIds().map((menuId) => ({
      menuId, readAuth: 'Y', createAuth: 'Y', deleteAuth: 'Y', // 체크 = 해당 메뉴 접근 허용
    }));
    const payload = { authId: selectedId, authName, details };
    if (selectedId == null) await api.post(BASE, payload);
    else await api.put(BASE, payload);
    loadAuths(selectedId); // 등록이면 목록 갱신(신규 모드 유지), 수정이면 재선택
  }

  async function removeAuth() {
    if (!PERM.canDelete) return;
    if (selectedId == null) { toast.warning('삭제할 권한을 선택해주세요.'); return; }
    const ok = await confirmModal.open({
      title: '권한 삭제', message: `선택한 권한(${$('authName').value})을 삭제하시겠습니까?`, confirmText: '삭제',
    });
    if (!ok) return;
    await api.del(`${BASE}?authId=${encodeURIComponent(selectedId)}`);
    newAuth();
    loadAuths();
  }

  function bind() {
    $('authList').addEventListener('click', (e) => {
      const tr = e.target.closest('tr.auth-row');
      if (tr) selectAuth(Number(tr.dataset.id), tr.dataset.name);
    });
    $('btnCancel').addEventListener('click', newAuth);
    if ($('btnNew')) $('btnNew').addEventListener('click', newAuth);
    if ($('btnSave')) $('btnSave').addEventListener('click', save);
    if ($('btnDelete')) $('btnDelete').addEventListener('click', removeAuth);
    if ($('btnCheckAll')) $('btnCheckAll').addEventListener('click', () => setAll(true));
    if ($('btnUncheckAll')) $('btnUncheckAll').addEventListener('click', () => setAll(false));

    $('permTree').addEventListener('change', (e) => {
      const b = e.target.closest('input[type="checkbox"]');
      if (!b) return;
      const id = Number(b.dataset.id);
      setSubtree(id, b.checked);
      refreshAncestors(id);
    });
  }

  document.addEventListener('DOMContentLoaded', async () => {
    bind();
    await loadTree();
    await loadAuths();
  });
})();
