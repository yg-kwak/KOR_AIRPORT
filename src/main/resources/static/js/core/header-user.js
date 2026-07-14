/* 헤더 계정 메뉴 — 사용자명 드롭다운(시작메뉴 변경 / 비밀번호 변경 / 로그아웃). 모든 화면 공통. (docs/frontend.md) */
(function () {
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const openModal = (id) => { const m = $(id); if (m) m.classList.add('open'); };
  const closeModal = (id) => { const m = $(id); if (m) m.classList.remove('open'); };

  // ── 드롭다운 ──
  function toggleDropdown(force) {
    const dd = $('hdrUserDropdown');
    const open = force != null ? force : !dd.classList.contains('open');
    dd.classList.toggle('open', open);
    $('hdrUserBtn').setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  // 메뉴 트리 재귀 렌더 — 화면(menuUrl) 있는 하위만 라디오 선택, 그룹은 헤더(선택 불가). depth 로 들여쓰기.
  function renderMenuNodes(nodes, current, depth) {
    return (nodes || []).map((n) => {
      const pad = 12 + depth * 18;
      const children = (n.children || []).length ? renderMenuNodes(n.children, current, depth + 1) : '';
      if (n.menuUrl != null) {
        return `
          <label class="start-menu-opt" style="padding-left:${pad}px">
            <input type="radio" name="hdrStartMenu" value="${esc(n.menuId)}"${n.menuId === current ? ' checked' : ''}/>
            <span>${esc(n.menuName)}</span>
          </label>${children}`;
      }
      return `<div class="start-menu-group" style="padding-left:${pad}px">${esc(n.menuName)}</div>${children}`;
    }).join('');
  }

  // ── 시작메뉴 변경 ──
  async function openStartMenu() {
    toggleDropdown(false);
    openModal('hdrStartMenuModal');
    const list = $('hdrStartMenuList');
    list.innerHTML = '<div class="empty">불러오는 중...</div>';
    const data = await api.get('/account/menus'); // {items(트리), current}
    const tree = (data && data.items) || [];
    const html = renderMenuNodes(tree, data && data.current, 0);
    list.innerHTML = html || '<div class="empty">선택 가능한 메뉴가 없습니다.</div>';
  }
  async function saveStartMenu() {
    const sel = $('hdrStartMenuList').querySelector('input[name="hdrStartMenu"]:checked');
    if (!sel) { toast.warning('메뉴를 선택하세요.'); return; }
    await api.post('/account/startMenu', { startMenuId: Number(sel.value) });
    closeModal('hdrStartMenuModal');
  }

  // ── 비밀번호 변경 ──
  function openPw() {
    toggleDropdown(false);
    $('hdrPwOld').value = ''; $('hdrPwNew').value = ''; $('hdrPwConfirm').value = '';
    openModal('hdrPwModal');
  }
  async function savePw() {
    const oldPw = $('hdrPwOld').value, newPw = $('hdrPwNew').value, confirmPw = $('hdrPwConfirm').value;
    if (!oldPw || !newPw || !confirmPw) { toast.warning('모든 항목을 입력하세요.'); return; }
    if (newPw !== confirmPw) { toast.warning('변경 비밀번호가 일치하지 않습니다.'); return; }
    await api.post('/account/password', { oldPassword: oldPw, newPassword: newPw, confirmPassword: confirmPw });
    closeModal('hdrPwModal');
  }

  // ── 로그아웃 ──
  const openLogout = () => { toggleDropdown(false); openModal('hdrLogoutModal'); };
  const doLogout = () => { location.href = '/logout'; };

  document.addEventListener('DOMContentLoaded', () => {
    if (!$('hdrUserBtn')) return; // 헤더 사용자 메뉴 없음(로그인 페이지 등)

    $('hdrUserBtn').addEventListener('click', (e) => { e.stopPropagation(); toggleDropdown(); });
    document.addEventListener('click', (e) => {
      if (!$('hdrUserMenu').contains(e.target)) toggleDropdown(false);
    });
    $('hdrUserDropdown').addEventListener('click', (e) => {
      const item = e.target.closest('[data-act]');
      if (!item) return;
      const act = item.dataset.act;
      if (act === 'startMenu') openStartMenu();
      else if (act === 'password') openPw();
      else if (act === 'logout') openLogout();
    });

    $('hdrStartMenuClose').addEventListener('click', () => closeModal('hdrStartMenuModal'));
    $('hdrStartMenuCancel').addEventListener('click', () => closeModal('hdrStartMenuModal'));
    $('hdrStartMenuSave').addEventListener('click', saveStartMenu);

    $('hdrPwClose').addEventListener('click', () => closeModal('hdrPwModal'));
    $('hdrPwCancel').addEventListener('click', () => closeModal('hdrPwModal'));
    $('hdrPwSave').addEventListener('click', savePw);

    $('hdrLogoutClose').addEventListener('click', () => closeModal('hdrLogoutModal'));
    $('hdrLogoutCancel').addEventListener('click', () => closeModal('hdrLogoutModal'));
    $('hdrLogoutOk').addEventListener('click', doLogout);

    // 오버레이 클릭으로 닫기
    ['hdrStartMenuModal', 'hdrPwModal', 'hdrLogoutModal'].forEach((id) => {
      const m = $(id);
      if (m) m.addEventListener('click', (e) => { if (e.target === m) closeModal(id); });
    });
  });
})();
