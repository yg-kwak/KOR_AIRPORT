/* 그래픽맵(모니터링 903) — 평면도 위 출입문(단말기)의 인증을 평면도·로그·사진으로 본다.
   이 파일은 '보기'(맵 고르기·평면도 그리기·확대/이동·실시간 이벤트)를 맡고,
   맵 추가·출입문 배치 같은 '편집'은 graphicMap-edit.js 가 window.gmap 을 통해 이어 받는다.
   이벤트 스트림은 이벤트 로그(902)와 같은 서버 스트림이다 — 지금 맵에 놓인 단말기들을 구독할 뿐이다. */
(function () {
  const BASE = '/monitor/graphicMap';
  const PHOTO_MAX = 15;   // 오른쪽 사진 — 최근이 위
  const PHOTO_KEEP = 60;  // [모두 보기]를 끄면 맵의 문 것만 다시 그리므로 보이는 것보다 넉넉히 들고 있는다
  const EVENT_MAX = 500;  // 아래 이벤트(모든 종류) — 넘으면 오래된 것부터 버린다
  const FLASH_MS = 4000;  // 인증한 문이 반짝이는 시간
  const KEEPALIVE_MS = 5 * 60 * 1000; // 세션 유휴 만료(1시간)보다 짧게 — 늘 켜 두는 화면이라 스트림과 무관하게 계속 두드린다
  const MAP_KEY = 'graphicMapId';     // 마지막에 본 맵 — 늘 켜 두는 화면이라 다시 열면 이어서 본다
  const SPLIT_KEY = 'graphicMapEventsH'; // 아래 이벤트 높이(경계를 끌어 바꾼 값)
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const state = {
    maps: [], mapId: null, doors: [], // doors: [{doorId, doorName, deviceId(입구 단말기), posX, posY}]
    zoom: 1, panX: 0, panY: 0, fitW: 0, fitH: 0, natW: 0, natH: 0,
    editing: false,
    showAll: false, // [모두 보기] — 기본은 이 맵에 놓인 문의 이벤트·인증 사진만
  };
  let stream = null, wasConnected = false;
  const events = [], photos = []; // 받은 것 전부(최근이 앞) — [모두 보기]·맵이 바뀌면 걸러 다시 그린다
  let seq = 0, allFrom = 0;       // 받은 순번 — [모두 보기]는 누른 뒤에 받은 것(순번 > allFrom)만 보인다
  /* 출입문 잠금 상태 — doorId → true(개방) / false(잠금). 처음엔 장비에 묻고(doors/status), 그 뒤엔 소켓의 UNLOCKED·LOCKED 로 바꾼다 */
  const unlocked = new Map();
  const PERM = window.PAGE_PERM || { canCreate: false, canDelete: false };

  /* ---- 맵 ---- */
  async function loadMaps(selectId) {
    state.maps = (await api.get(BASE + '/maps')) || [];
    renderMapList();
    let want = selectId;
    if (want == null) { try { want = Number(localStorage.getItem(MAP_KEY)) || null; } catch (e) { want = null; } }
    const found = state.maps.find((m) => m.mapId === want) || state.maps[0];
    if (found) await selectMap(found.mapId); else clearMap();
  }

  function renderMapList() {
    $('mapList').innerHTML = state.maps.length
      ? state.maps.map((m) => `<li class="gmap-item${m.mapId === state.mapId ? ' active' : ''}" data-map="${m.mapId}">
          <span class="gmap-item-name">${esc(m.mapName)}</span><span class="gmap-item-sub">문 ${m.doorCount || 0}</span></li>`).join('')
      : '<li class="gmap-empty">맵이 없습니다.</li>';
    $('mapActions').hidden = state.mapId == null;
  }

  async function selectMap(mapId) {
    if (state.editing && window.gmapEdit) window.gmapEdit.cancel();
    state.mapId = mapId;
    try { localStorage.setItem(MAP_KEY, String(mapId)); } catch (e) { /* 저장이 막힌 브라우저 */ }
    const m = state.maps.find((x) => x.mapId === mapId);
    $('mapTitle').textContent = m ? m.mapName : '';
    renderMapList();
    state.doors = ((await api.get(`${BASE}/doors?mapId=${mapId}`)) || []).map(normDoor);
    loadImage(mapId);
    unlocked.clear();
    renderDoors();
    renderFeeds();
    subscribe();
    loadDoorStatus();
  }

  /* 처음 상태 — 화면을 열 때·맵을 바꿀 때·소켓이 다시 붙을 때(끊긴 사이 이벤트를 놓쳤을 수 있다) */
  async function loadDoorStatus() {
    if (state.mapId == null || !state.doors.length) return;
    const rows = (await api.get(`${BASE}/doors/status?mapId=${state.mapId}`, { quiet: true })) || [];
    rows.forEach((r) => setLock(r.doorId, r.unlocked));
  }

  function clearMap() {
    state.mapId = null; state.doors = [];
    $('mapTitle').textContent = '맵을 선택하세요';
    $('planeImg').removeAttribute('src');
    $('stageEmpty').hidden = false;
    renderMapList(); renderDoors(); renderFeeds(); subscribe(); // 맵이 없어도 아래 이벤트는 받는다
  }

  const normDoor = (d) => ({ doorId: Number(d.doorId), doorName: d.doorName || '', deviceId: d.deviceId ? String(d.deviceId) : null,
    posX: Number(d.posX), posY: Number(d.posY) });

  /* ---- 평면도 · 확대/이동 ---- */
  function loadImage(mapId) {
    const img = $('planeImg');
    img.onload = () => { state.natW = img.naturalWidth; state.natH = img.naturalHeight; fit(); };
    img.src = `${BASE}/image?mapId=${mapId}&v=${Date.now()}`; // 교체 직후에도 새 그림이 보이게
    $('stageEmpty').hidden = true;
  }

  /* 100% = 무대에 꼭 맞는 크기. 그보다 키우면 끌어서 옮긴다 */
  function fit() {
    const st = $('stage').getBoundingClientRect();
    if (!state.natW || !st.width) return;
    const r = Math.min(st.width / state.natW, st.height / state.natH);
    state.fitW = state.natW * r; state.fitH = state.natH * r;
    state.zoom = 1; state.panX = 0; state.panY = 0;
    applyView();
  }

  function applyView() {
    const st = $('stage').getBoundingClientRect();
    const w = state.fitW * state.zoom, h = state.fitH * state.zoom;
    const plane = $('plane');
    plane.style.width = w + 'px'; plane.style.height = h + 'px';
    plane.style.left = ((st.width - w) / 2 + state.panX) + 'px';
    plane.style.top = ((st.height - h) / 2 + state.panY) + 'px';
    $('zoomLabel').textContent = Math.round(state.zoom * 100) + '%';
  }

  function zoomBy(f) {
    closeMenu();
    state.zoom = Math.min(8, Math.max(0.25, state.zoom * f));
    state.panX *= f; state.panY *= f; // 가운데를 기준으로 키운다
    applyView();
  }

  /* 평면도·이벤트 경계 — 위아래로 끌어 이벤트 높이를 바꾼다. 평면도는 남은 자리에 다시 맞춘다 */
  const refit = () => { if (state.natW) { const z = state.zoom; fit(); state.zoom = z; applyView(); } };
  function setEventsH(h) {
    const max = $('gmap').clientHeight - 48 - 120; // 머리줄·평면도 최소 높이는 남긴다
    const v = Math.round(Math.max(120, Math.min(max, h)));
    $('gmap').style.setProperty('--g-events-h', v + 'px');
    refit();
    return v;
  }
  function bindSplit() {
    const bar = $('eventSplit');
    let saved = null;
    try { saved = Number(localStorage.getItem(SPLIT_KEY)) || null; } catch (e) { saved = null; }
    if (saved) setEventsH(saved);
    let dragging = false;
    bar.addEventListener('pointerdown', (e) => { dragging = true; bar.setPointerCapture(e.pointerId); bar.classList.add('active'); e.preventDefault(); });
    bar.addEventListener('pointermove', (e) => {
      if (dragging) setEventsH($('gmap').getBoundingClientRect().bottom - e.clientY);
    });
    const end = () => {
      if (!dragging) return;
      dragging = false; bar.classList.remove('active');
      try { localStorage.setItem(SPLIT_KEY, String($('gmap').querySelector('.gmap-events').offsetHeight)); } catch (e) { /* 저장이 막힌 브라우저 */ }
    };
    bar.addEventListener('pointerup', end); bar.addEventListener('pointercancel', end);
    bar.addEventListener('dblclick', () => { setEventsH(240); try { localStorage.removeItem(SPLIT_KEY); } catch (e) { /* 무시 */ } });
  }

  function bindPan() {
    const stage = $('stage');
    let drag = null;
    stage.addEventListener('pointerdown', (e) => {
      // 문·확대 버튼·제어 메뉴 위에서는 끌기를 시작하지 않는다(포인터를 잡으면 그 클릭이 무대로 넘어간다). 놓는 중엔 클릭이 '놓기'다
      if (e.target.closest('.gmap-door, .gmap-zoom, .gmap-door-menu') || stage.classList.contains('placing')) return;
      drag = { x: e.clientX, y: e.clientY, px: state.panX, py: state.panY };
      stage.setPointerCapture(e.pointerId);
    });
    stage.addEventListener('pointermove', (e) => {
      if (!drag) return;
      state.panX = drag.px + (e.clientX - drag.x); state.panY = drag.py + (e.clientY - drag.y);
      applyView();
    });
    const end = () => { drag = null; };
    stage.addEventListener('pointerup', end); stage.addEventListener('pointercancel', end);
    stage.addEventListener('wheel', (e) => { e.preventDefault(); zoomBy(e.deltaY < 0 ? 1.15 : 1 / 1.15); }, { passive: false });
  }

  /* ---- 출입문 ---- */
  const DOOR_ICON = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      stroke-linecap="round" stroke-linejoin="round"><path d="M4 21h16"/><path d="M6 21V4h12v17"/>
      <circle cx="14.5" cy="12.5" r="1"/></svg>`;

  /* 잠금 — 닫힌 자물쇠 / 개방 — 열린 자물쇠. 상태를 모르면(장비 응답 전) 문 그림 */
  const LOCK_ICON = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
      stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>`;
  const UNLOCK_ICON = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
      stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 7.6-1.7"/></svg>`;
  const lockOf = (doorId) => (unlocked.has(doorId) ? (unlocked.get(doorId) ? 'unlocked' : 'locked') : '');
  const iconOf = (st) => (st === 'unlocked' ? UNLOCK_ICON : st === 'locked' ? LOCK_ICON : DOOR_ICON);
  const LOCK_TEXT = { locked: '잠금', unlocked: '개방', '': '상태 확인 중' };

  /* 한 문의 상태를 바꾼다 — 통째로 다시 그리지 않는다(반짝이는 중인 문이 꺼지지 않게) */
  function setLock(doorId, isUnlocked) {
    const id = Number(doorId);
    if (!state.doors.some((d) => d.doorId === id)) return;
    unlocked.set(id, !!isUnlocked);
    const st = lockOf(id);
    document.querySelectorAll(`.gmap-door[data-id="${id}"]`).forEach((el) => {
      el.classList.remove('locked', 'unlocked'); el.classList.add(st);
      el.querySelector('.gmap-door-dot').innerHTML = iconOf(st);
      el.title = `${el.dataset.name} — ${LOCK_TEXT[st]}`;
    });
  }

  function renderDoors() {
    $('doorLayer').innerHTML = state.doors.map((d) => { const st = lockOf(d.doorId); return `
      <div class="gmap-door ${st}" data-id="${d.doorId}" data-name="${esc(d.doorName || d.doorId)}" data-device="${esc(d.deviceId || '')}"
           style="left:${d.posX * 100}%;top:${d.posY * 100}%" title="${esc(d.doorName || d.doorId)} — ${LOCK_TEXT[st]}">
        <span class="gmap-door-dot">${iconOf(st)}</span>
        <span class="gmap-door-name">${esc(d.doorName || d.doorId)}</span>
        ${state.editing ? '<button type="button" class="gmap-door-del" aria-label="빼기">×</button>' : ''}
      </div>`; }).join('');
  }

  /* 왼쪽 출입문 목록은 배치 중에만 있다(놓을 BiostarX 출입문) */
  function renderDoorList() {
    if (state.editing && window.gmapEdit) window.gmapEdit.renderDevices();
  }

  /* 인증한 문을 잠깐 밝힌다 — 통과는 초록, 거부는 빨강. 이벤트는 단말기 ID 로 오므로 그 단말기가 입구인 문을 찾는다 */
  function flashEls(els, granted) {
    els.forEach((el) => {
      el.classList.remove('ok', 'deny'); void el.offsetWidth; // 연달아 와도 다시 반짝이게
      el.classList.add(granted ? 'ok' : 'deny');
      clearTimeout(el._t); el._t = setTimeout(() => el.classList.remove('ok', 'deny'), FLASH_MS);
    });
  }
  const flashDevice = (deviceId, granted) =>
    flashEls(document.querySelectorAll(`.gmap-door[data-device="${CSS.escape(String(deviceId))}"]`), granted);

  /* ---- 놓인 문 원격 제어 — 개방·잠금·해제 ---- */
  let menuDoor = null;
  function openMenu(el) {
    const d = state.doors.find((x) => String(x.doorId) === el.dataset.id);
    if (!d || !PERM.canCreate) return;
    menuDoor = d;
    const st = $('stage').getBoundingClientRect(), r = el.getBoundingClientRect(), m = $('doorMenu');
    $('doorMenuTitle').textContent = d.doorName || `문 ${d.doorId}`;
    m.hidden = false;
    m.style.left = Math.min(st.width - m.offsetWidth - 8, r.right - st.left + 6) + 'px';
    m.style.top = Math.max(8, r.top - st.top) + 'px';
  }
  function closeMenu() { $('doorMenu').hidden = true; menuDoor = null; }
  async function control(action) {
    const d = menuDoor; closeMenu();
    if (!d) return;
    await api.post(`${BASE}/doors/control?mapId=${state.mapId}&doorId=${d.doorId}&action=${action}`, {}); // 결과 문구는 서버가 토스트로
  }

  /* ---- 실시간 이벤트 ---- */
  function stop() {
    if (stream) { stream.close(); stream = null; }
    setState('대기', false);
  }

  function subscribe() {
    stop();
    // 인증(사진·문 반짝임)은 놓인 문의 입구 단말기만 — [모두 보기]면 모든 장치(서버가 인증마다 사진을 조회하므로 켤 때만 넓힌다).
    // 아래 이벤트 표는 서버가 장치·종류를 거르지 않고 모두 보낸다(log) — 맵의 문만 보일지는 여기서 거른다
    const q = new URLSearchParams();
    if (state.showAll) q.append('all', 'true');
    else [...new Set(state.doors.map((d) => d.deviceId).filter(Boolean))].forEach((id) => q.append('deviceId', id));
    stream = new EventSource(BASE + '/stream' + (q.toString() ? '?' + q.toString() : ''));
    const on = (name, fn) => stream.addEventListener(name, (m) => {
      try { fn(JSON.parse(m.data)); } catch (err) { console.warn('이벤트 처리 실패', err); }
    });
    on('auth', onAuth);
    on('log', addEvent);
    on('status', (s) => {
      if (s.message) setState(s.message, true);
      else setState(s.connected ? '수신 중' : 'BiostarX 연결 중', !s.connected);
      // 다시 붙었으면 상태를 새로 묻는다 — 끊긴 사이의 개방·잠금 이벤트는 오지 않는다
      if (s.connected && !wasConnected) loadDoorStatus();
      wasConnected = !!s.connected;
    });
    stream.onerror = () => setState('연결 재시도 중', true);
    setState('연결 중', false);
  }

  function setState(text, warn) {
    $('monitorState').textContent = text;
    $('monitorState').classList.toggle('warn', !!warn);
  }

  /* 이 맵에 놓인 문의 것인가 — 이벤트가 문을 실어 오면 그 문, 아니면 그 단말기가 입구인 놓인 문 */
  const placedOf = (e) => state.doors.find((x) => e.deviceId && x.deviceId === String(e.deviceId));
  const onMap = (e) => (e.doorId != null && e.doorId !== '' && state.doors.some((x) => String(x.doorId) === String(e.doorId)))
    || !!placedOf(e);
  const shown = (e) => (state.showAll ? e._seq > allFrom : onMap(e));

  const doorName = (e) => { const d = placedOf(e); return (d && d.doorName) || e.deviceName || e.deviceId || '-'; };

  function onAuth(e) {
    flashDevice(e.deviceId, e.granted);
    e._seq = ++seq;
    photos.unshift(e); if (photos.length > PHOTO_KEEP) photos.length = PHOTO_KEEP;
    if (shown(e)) addPhoto(e); // 이벤트 표는 'log' 가 채운다 — 여기서도 넣으면 인증이 두 줄이 된다
  }

  /* 걸러 다시 그린다 — [모두 보기]를 바꿀 때·맵을 바꿀 때 */
  function renderFeeds() {
    const rows = events.filter(shown).slice(0, EVENT_MAX);
    $('eventBody').innerHTML = rows.length ? '' : '<tr><td colspan="5" class="empty">결과 없음</td></tr>';
    rows.forEach((e) => $('eventBody').append(eventRow(e)));
    const pics = photos.filter(shown).slice(0, PHOTO_MAX);
    $('photoList').innerHTML = pics.length ? '' : '<div class="gmap-empty">아직 인증이 없습니다.</div>';
    pics.reverse().forEach(addPhoto); // addPhoto 는 위에 붙인다 — 오래된 것부터
  }

  function toggleAll() {
    state.showAll = !state.showAll;
    if (state.showAll) allFrom = seq; // 누른 뒤의 것부터 — 끄면 다시 이 맵의 문 것(앞서 받은 것 포함)
    $('btnShowAll').setAttribute('aria-pressed', String(state.showAll));
    renderFeeds();
    subscribe(); // 인증 사진의 범위가 바뀐다
  }

  /* 모든 이벤트 한 줄 — 인증·문 열림/잠김·운영자 조작·장치 연결까지. 출입문은 이벤트가 문을 실어 오면 그것,
     아니면 이 맵에 놓인 문 중 그 단말기가 입구인 문 */
  function addEvent(e) {
    // 문 상태가 바뀌는 이벤트 — 개방(UNLOCKED·릴레이 켜짐) / 잠금(LOCKED·릴레이 꺼짐). 문이 실려 오지 않으면 그 단말기가 입구인 문
    if (e.eventName === 'UNLOCKED' || e.eventName === 'LOCKED') {
      const byDevice = state.doors.filter((x) => e.deviceId && x.deviceId === String(e.deviceId)).map((x) => x.doorId);
      (e.doorId ? [e.doorId] : byDevice).forEach((id) => setLock(id, e.eventName === 'UNLOCKED'));
    }
    e._seq = ++seq;
    events.unshift(e); if (events.length > EVENT_MAX) events.length = EVENT_MAX;
    if (!shown(e)) return;
    const body = $('eventBody');
    if (body.querySelector('td.empty')) body.innerHTML = '';
    body.prepend(eventRow(e));
    while (body.rows.length > EVENT_MAX) body.deleteRow(body.rows.length - 1);
  }

  function eventRow(e) {
    const placed = placedOf(e);
    const door = e.doorName || (placed && placed.doorName) || '-';
    const tr = document.createElement('tr');
    tr.className = e.tone === 'error' ? 'deny' : '';
    tr.title = e.eventName || '';
    tr.innerHTML = `<td>${esc(e.eventTime || '')}</td><td>${esc(door)}</td><td>${esc(e.deviceName || e.deviceId || '-')}</td>
      <td>${esc(e.userName || e.userId || '-')}</td><td>${esc(e.label || e.eventName || '')}</td>`;
    return tr;
  }

  const FACE_ICON = `<svg class="monitor-face-icon" viewBox="0 0 96 96" aria-label="사진 없음">
      <circle cx="48" cy="33" r="19"/><path d="M14 90c0-18.8 15.2-34 34-34s34 15.2 34 34"/></svg>`;

  /* 인증 사진 — 등록 사진(tb_person_photo). 없으면 빈 얼굴 — 장비가 찍은 사진은 쓰지 않는다 */
  function addPhoto(e) {
    const list = $('photoList');
    const empty = list.querySelector('.gmap-empty'); if (empty) empty.remove();
    const pic = e.registeredPhoto;
    const div = document.createElement('div');
    div.className = 'gmap-photo' + (e.granted ? '' : ' deny');
    div.innerHTML = `<div class="gmap-photo-img">${pic ? `<img src="data:image/jpeg;base64,${pic}" alt="인증 사진"/>` : FACE_ICON}</div>
      <dl><dt>이름</dt><dd>${esc(e.personName || e.personId || '미등록')}</dd>
        <dt>소속</dt><dd>${esc(e.companyName || '-')}</dd>
        <dt>출입문</dt><dd>${esc(doorName(e))}</dd>
        <dt>시각</dt><dd>${esc(e.eventTime || '')} · ${esc(e.resultLabel || '')}</dd></dl>`;
    list.prepend(div);
    while (list.children.length > PHOTO_MAX) list.lastElementChild.remove();
  }

  /* ---- 묶기 ---- */
  function bind() {
    $('mapList').addEventListener('click', (e) => {
      const li = e.target.closest('[data-map]'); if (li) selectMap(Number(li.dataset.map));
    });
    // 놓인 문 누르기 — 보기에서는 제어 메뉴, 편집에서는 끌기(graphicMap-edit.js)
    $('doorLayer').addEventListener('click', (e) => {
      const el = e.target.closest('.gmap-door');
      if (el && !state.editing) { e.stopPropagation(); openMenu(el); }
    });
    $('doorMenu').addEventListener('click', (e) => {
      const b = e.target.closest('button[data-action]'); if (b) control(b.dataset.action);
    });
    document.addEventListener('pointerdown', (e) => { if (!e.target.closest('#doorMenu, .gmap-door')) closeMenu(); });
    $('btnZoomIn').addEventListener('click', () => zoomBy(1.25));
    $('btnZoomOut').addEventListener('click', () => zoomBy(1 / 1.25));
    $('btnZoomFit').addEventListener('click', fit);
    $('btnShowAll').addEventListener('click', toggleAll);
    $('btnEventClear').addEventListener('click', () => { events.length = 0; renderFeeds(); }); // 사진은 그대로
    window.addEventListener('resize', refit);
    window.addEventListener('beforeunload', stop);
    // 세션 유지 — 늘 켜 두는 상황판이다. 스트림이 없을 때(문이 없는 맵·편집 중)도 계속 두드려야
    // 유휴 1시간 뒤 로그인 화면으로 튕기지 않는다. 세션이 이미 끊겼으면 api 래퍼가 로그인으로 보낸다
    setInterval(() => api.get(BASE + '/alive').catch(() => {}), KEEPALIVE_MS);
    bindPan();
    bindSplit();
    loadMaps();
  }

  window.gmap = { BASE, state, $, esc, loadMaps, selectMap, renderDoors, renderDoorList, subscribe, stop, applyView, fit, closeMenu };
  document.addEventListener('DOMContentLoaded', bind);
})();
