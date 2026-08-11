/* 사이드바 공통 동작 — 접기/펼치기(localStorage 유지), 그룹 아코디언,
   접힘 상태 아이콘 클릭 시 하위 플라이아웃, level1 아이콘 렌더, 현재 URL active 표시. */
(function () {
  // level 1 그룹 아이콘 (menu_icon 키 → SVG). 새 그룹 추가 시 여기에 키를 등록한다.
  const ICONS = {
    settings:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
    card:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>',
    door:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>',
    ban:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>',
    log:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>',
    guard:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>',
    car:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13l1.5-4.5A2 2 0 0 1 8.4 7h7.2a2 2 0 0 1 1.9 1.5L19 13v5a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-1H8v1a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z"/><circle cx="7.5" cy="16.5" r="1"/><circle cx="16.5" cy="16.5" r="1"/><path d="M5 13h14"/></svg>',
    company:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18"/><path d="M5 21V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v16"/><path d="M14 9h4a1 1 0 0 1 1 1v11"/><path d="M8 8h2M8 12h2M8 16h2"/></svg>',
    monitor:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>',
    _default:
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>',
  };

  const sb = () => document.getElementById('lnbSidebar');

  function fillIcons() {
    document.querySelectorAll('.lnb-ic').forEach((el) => {
      el.innerHTML = ICONS[el.dataset.icon] || ICONS._default;
    });
  }

  function setCollapsed(collapsed) {
    sb().classList.toggle('collapsed', collapsed);
    try { localStorage.setItem('lnbCollapsed', collapsed ? '1' : '0'); } catch (e) {}
    if (!collapsed) closeFlyouts();
  }

  function closeFlyouts() {
    sb().querySelectorAll('.lnb-group.flyout').forEach((g) => g.classList.remove('flyout'));
  }

  function markActive() {
    const path = location.pathname;
    sb().querySelectorAll('.lnb-link').forEach((a) => {
      if (a.dataset.url && a.dataset.url === path) {
        a.classList.add('active');
        const grp = a.closest('.lnb-group');
        if (grp) { grp.classList.add('active', 'open'); }
      }
    });
  }

  function bind() {
    const s = sb();
    if (!s) return;

    document.getElementById('lnbToggle').addEventListener('click', () =>
      setCollapsed(!s.classList.contains('collapsed')));

    s.querySelectorAll('.lnb-group').forEach((g) => {
      g.querySelector('.lnb-group-btn').addEventListener('click', () => {
        if (s.classList.contains('collapsed')) {
          const open = g.classList.contains('flyout');
          closeFlyouts();
          if (!open) g.classList.add('flyout'); // 접힘: 아이콘 클릭 → 하위 플라이아웃
        } else {
          g.classList.toggle('open'); // 펼침: 아코디언
        }
      });
    });

    // 바깥 클릭 시 플라이아웃 닫기
    document.addEventListener('click', (e) => {
      if (s.classList.contains('collapsed') && !e.target.closest('.lnb-group')) closeFlyouts();
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    if (!sb()) return;
    fillIcons();
    let collapsed = false;
    try { collapsed = localStorage.getItem('lnbCollapsed') === '1'; } catch (e) {}
    sb().classList.toggle('collapsed', collapsed);
    bind();
    markActive();
  });
})();
