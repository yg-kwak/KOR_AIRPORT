/* 권한메뉴관리 — 좌: 권한 목록(tb_menu_auth), 우: 권한 설정(전체 메뉴 트리 + 메뉴별 조회/입력·수정/삭제).
   메뉴 선택(좌측 체크)=조회 권한, 우측에서 입력·수정/삭제를 추가 부여. 저장=tb_menu_auth_detail. */
(function () {
  const BASE = '/system/menuAuth';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };
  const EDITABLE = PERM.canCreate;

  let selectedId = null; // null = 신규(등록) 모드
  const nodesById = {};  // menuId → { id, name, parentId, childIds:[] }
  let order = [];        // DFS 순서 menuId 배열

  // ---- 좌측: 권한 목록 ----
  async function loadAuths(selectId) {
    const data = await api.get(BASE + '/list?page=1&size=1000&sort=authId&dir=asc');
    renderAuthList(data.content || []);
    if (selectId != null) {
      const row = (data.content || []).find((r) => r.authId === selectId);
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

  // ---- 우측: 메뉴 트리 (선택 + 조회/입력수정/삭제) ----
  async function loadTree() {
    const roots = (await api.get(BASE + '/menus')) || [];
    order = [];
    Object.keys(nodesById).forEach((k) => delete nodesById[k]);
    const dis = EDITABLE ? '' : ' disabled';
    const rows = [
      `<div class="tree-row tree-head">
         <span class="tree-label">메뉴</span>
         <span class="tree-crud"><span class="crud-cell">조회</span><span class="crud-cell">입력·수정</span><span class="crud-cell">삭제</span></span>
       </div>`,
    ];
    const walk = (node, depth, parentId) => {
      nodesById[node.menuId] = { id: node.menuId, name: node.menuName, parentId, childIds: [] };
      if (parentId != null) nodesById[parentId].childIds.push(node.menuId);
      order.push(node.menuId);
      const cb = (role) => `<input type="checkbox" data-id="${esc(node.menuId)}" data-role="${role}"${dis}/>`;
      rows.push(`
        <div class="tree-row" style="padding-left:${8 + depth * 22}px">
          <label class="tree-label">
            ${depth > 0 ? '<span class="tree-branch">└</span>' : ''}
            ${cb('main')}<span>${esc(node.menuName)}</span>
          </label>
          <span class="tree-crud">
            <span class="crud-cell">${cb('read')}</span>
            <span class="crud-cell">${cb('create')}</span>
            <span class="crud-cell">${cb('del')}</span>
          </span>
        </div>`);
      (node.children || []).forEach((c) => walk(c, depth + 1, node.menuId));
    };
    roots.forEach((r) => walk(r, 0, null));
    $('permTree').innerHTML = rows.join('');
  }

  const cb = (id, role) => $('permTree').querySelector(`input[data-id="${id}"][data-role="${role}"]`);
  const on = (id, role) => { const c = cb(id, role); return !!(c && c.checked); };
  const set = (id, role, v) => { const c = cb(id, role); if (c) c.checked = v; };

  /** 입력·수정/삭제는 메뉴 선택(main) 상태에서만 활성. 미선택이면 해제. */
  function updateCrudDisabled(id) {
    const sel = on(id, 'main');
    ['create', 'del'].forEach((r) => {
      const c = cb(id, r);
      if (c) { c.disabled = !EDITABLE || !sel; if (!sel) c.checked = false; }
    });
  }

  /** 메뉴 선택(main=조회) 세팅 — 미선택이면 입력·수정/삭제도 해제. */
  function selectNode(id, sel) {
    set(id, 'main', sel);
    set(id, 'read', sel); // 규칙2: 선택=조회, 규칙3: 해제 시 조회도 해제
    if (!sel) { set(id, 'create', false); set(id, 'del', false); }
    updateCrudDisabled(id);
  }
  function cascadeSelect(id, sel) {
    selectNode(id, sel);
    (nodesById[id].childIds || []).forEach((c) => cascadeSelect(c, sel));
  }
  function refreshAncestors(id) {
    let p = nodesById[id].parentId;
    while (p != null) {
      const any = nodesById[p].childIds.some((c) => on(c, 'main'));
      selectNode(p, any);
      p = nodesById[p].parentId;
    }
  }
  function setAll(sel) {
    order.forEach((id) => { if (nodesById[id].parentId == null) cascadeSelect(id, sel); });
  }
  function checkedDetails() {
    return order.filter((id) => on(id, 'main')).map((id) => ({
      menuId: id,
      readAuth: on(id, 'read') ? 'Y' : 'N',
      createAuth: on(id, 'create') ? 'Y' : 'N',
      deleteAuth: on(id, 'del') ? 'Y' : 'N',
    }));
  }

  // ---- 선택/신규/저장/삭제 ----
  async function selectAuth(id, name) {
    selectedId = id;
    $('authName').value = name || '';
    markActive();
    setAll(false);
    const list = await api.get(BASE + '/detail?authId=' + encodeURIComponent(id));
    (list || []).forEach((d) => {
      set(d.menuId, 'main', true);
      set(d.menuId, 'read', d.readAuth === 'Y');
      set(d.menuId, 'create', d.createAuth === 'Y');
      set(d.menuId, 'del', d.deleteAuth === 'Y');
      updateCrudDisabled(d.menuId);
    });
    // 하위만 저장된 권한도 상위 그룹 체크가 보이도록 조상 갱신
    (list || []).forEach((d) => refreshAncestors(d.menuId));
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
    const payload = { authId: selectedId, authName, details: checkedDetails() };
    if (selectedId == null) await api.post(BASE, payload);
    else await api.put(BASE, payload);
    loadAuths(selectedId);
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
      const role = b.dataset.role;
      if (role === 'main' || role === 'read') {
        cascadeSelect(id, b.checked); // 선택/조회 = 메뉴 선택, 하위로 전파
        refreshAncestors(id);         // 하위 상태에 따라 상위 갱신
      }
      // create/del 은 main 활성 상태에서만 클릭 가능(별도 로직 불필요)
    });
  }

  document.addEventListener('DOMContentLoaded', async () => {
    bind();
    await loadTree();
    await loadAuths();
  });
})();
