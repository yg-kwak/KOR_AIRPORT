/* 그래픽맵(모니터링 903) — 평면도 위 출입문(단말기)의 인증을 평면도·로그·사진으로 본다.
   이 파일은 '보기'(맵 고르기·평면도 그리기·확대/이동·실시간 이벤트)를 맡고,
   맵 추가·출입문 배치 같은 '편집'은 graphicMap-edit.js 가 window.gmap 을 통해 이어 받는다.
   이벤트 스트림은 이벤트 로그(902)와 같은 서버 스트림이다 — 지금 맵에 놓인 단말기들을 구독할 뿐이다. */
(function () {
  const BASE = '/monitor/graphicMap';
  const PHOTO_MAX = 15;   // 오른쪽 사진 — 최근이 위
  const EVENT_MAX = 200;  // 아래 로그 — 넘으면 오래된 것부터 버린다
  const FLASH_MS = 4000;  // 인증한 문이 반짝이는 시간
  const KEEPALIVE_MS = 5 * 60 * 1000; // 세션 유휴 만료(1시간)보다 짧게 — 늘 켜 두는 화면이라 스트림과 무관하게 계속 두드린다
  const MAP_KEY = 'graphicMapId';     // 마지막에 본 맵 — 늘 켜 두는 화면이라 다시 열면 이어서 본다
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  const state = {
    maps: [], mapId: null, doors: [], // doors: [{doorId, doorName, deviceId(입구 단말기), posX, posY}]
    zoom: 1, panX: 0, panY: 0, fitW: 0, fitH: 0, natW: 0, natH: 0,
    editing: false,
  };
  let stream = null, eventCount = 0;
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
    renderDoors();
    renderDoorList();
    subscribe();
  }

  function clearMap() {
    state.mapId = null; state.doors = [];
    $('mapTitle').textContent = '맵을 선택하세요';
    $('planeImg').removeAttribute('src');
    $('stageEmpty').hidden = false;
    renderMapList(); renderDoors(); renderDoorList(); stop();
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

  function renderDoors() {
    $('doorLayer').innerHTML = state.doors.map((d) => `
      <div class="gmap-door" data-id="${d.doorId}" data-device="${esc(d.deviceId || '')}" style="left:${d.posX * 100}%;top:${d.posY * 100}%"
           title="${esc(d.doorName || d.doorId)}">
        <span class="gmap-door-dot">${DOOR_ICON}</span>
        <span class="gmap-door-name">${esc(d.doorName || d.doorId)}</span>
        ${state.editing ? '<button type="button" class="gmap-door-del" aria-label="빼기">×</button>' : ''}
      </div>`).join('');
  }

  function renderDoorList() {
    if (state.editing && window.gmapEdit) { window.gmapEdit.renderDevices(); return; }
    $('doorsHead').textContent = `출입문 (${state.doors.length})`;
    $('doorList').innerHTML = state.mapId == null
      ? '<li class="gmap-empty">맵을 선택하세요.</li>'
      : state.doors.length
        ? state.doors.map((d) => `<li class="gmap-item" data-door="${d.doorId}">
            <span class="gmap-item-name">${esc(d.doorName || d.doorId)}</span><span class="gmap-item-sub">문 ${d.doorId}</span></li>`).join('')
        : '<li class="gmap-empty">놓인 출입문이 없습니다.</li>';
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
  const flashDoor = (doorId) => flashEls(document.querySelectorAll(`.gmap-door[data-id="${Number(doorId)}"]`), true);

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
    if (!state.doors.length) { setState('놓인 출입문 없음', false); return; }
    const q = new URLSearchParams();
    [...new Set(state.doors.map((d) => d.deviceId).filter(Boolean))].forEach((id) => q.append('deviceId', id));
    if (!q.toString()) { setState('입구 단말기가 있는 문이 없음', true); return; }
    stream = new EventSource(BASE + '/stream?' + q.toString());
    const on = (name, fn) => stream.addEventListener(name, (m) => {
      try { fn(JSON.parse(m.data)); } catch (err) { console.warn('이벤트 처리 실패', err); }
    });
    on('auth', onAuth);
    on('status', (s) => {
      if (s.message) setState(s.message, true);
      else setState(s.connected ? '수신 중' : 'BiostarX 연결 중', !s.connected);
    });
    stream.onerror = () => setState('연결 재시도 중', true);
    setState('연결 중', false);
  }

  function setState(text, warn) {
    $('monitorState').textContent = text;
    $('monitorState').classList.toggle('warn', !!warn);
  }

  const doorName = (e) => {
    const d = state.doors.find((x) => x.deviceId === String(e.deviceId));
    return (d && d.doorName) || e.deviceName || e.deviceId || '-';
  };

  function onAuth(e) {
    authSound.play(e.granted);
    flashDevice(e.deviceId, e.granted);
    addEvent(e);
    addPhoto(e);
  }

  function addEvent(e) {
    const body = $('eventBody');
    if (!eventCount) body.innerHTML = '';
    const today = new Date();
    const date = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    const who = e.personName || e.personId || '미등록';
    const tr = document.createElement('tr');
    tr.className = e.granted ? '' : 'deny';
    tr.innerHTML = `<td>${esc(date)} ${esc(e.eventTime || '')}</td><td>${esc(doorName(e))}</td><td>${esc(e.deviceId)}</td>
      <td>${esc(who)}${e.companyName ? ` <span class="gmap-sub">(${esc(e.companyName)})</span>` : ''}</td>
      <td>${esc(e.resultLabel || '')}</td><td>${badge.of(e.granted ? '통과' : '거부', e.granted ? 'success' : 'error')}</td>`;
    body.prepend(tr);
    while (body.rows.length > EVENT_MAX) body.deleteRow(body.rows.length - 1);
    eventCount += 1;
    $('eventCount').textContent = String(eventCount);
  }

  const FACE_ICON = `<svg class="monitor-face-icon" viewBox="0 0 96 96" aria-label="사진 없음">
      <circle cx="48" cy="33" r="19"/><path d="M14 90c0-18.8 15.2-34 34-34s34 15.2 34 34"/></svg>`;

  /* 인증 사진 — 장비가 찍은 사진이 있으면 그것, 없으면 등록 사진 */
  function addPhoto(e) {
    const list = $('photoList');
    const empty = list.querySelector('.gmap-empty'); if (empty) empty.remove();
    const pic = e.authPhoto || e.registeredPhoto;
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
    document.querySelectorAll('.gmap-tab').forEach((t) => t.addEventListener('click', () => {
      document.querySelectorAll('.gmap-tab').forEach((x) => x.classList.toggle('active', x === t));
      $('tab-maps').hidden = t.dataset.tab !== 'maps';
      $('tab-doors').hidden = t.dataset.tab !== 'doors';
    }));
    $('mapList').addEventListener('click', (e) => {
      const li = e.target.closest('[data-map]'); if (li) selectMap(Number(li.dataset.map));
    });
    $('doorList').addEventListener('click', (e) => { // 목록에서 고르면 평면도의 그 문을 반짝여 찾게 한다
      const li = e.target.closest('[data-door]'); if (li && !state.editing) flashDoor(li.dataset.door);
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
    $('btnEventClear').addEventListener('click', () => {
      eventCount = 0; $('eventCount').textContent = '0';
      $('eventBody').innerHTML = '<tr><td colspan="6" class="empty">결과 없음</td></tr>';
    });
    window.addEventListener('resize', () => { if (state.natW) { const z = state.zoom; fit(); state.zoom = z; applyView(); } });
    window.addEventListener('beforeunload', stop);
    authSound.attach($('btnSound'));
    // 세션 유지 — 늘 켜 두는 상황판이다. 스트림이 없을 때(문이 없는 맵·편집 중)도 계속 두드려야
    // 유휴 1시간 뒤 로그인 화면으로 튕기지 않는다. 세션이 이미 끊겼으면 api 래퍼가 로그인으로 보낸다
    setInterval(() => api.get(BASE + '/alive').catch(() => {}), KEEPALIVE_MS);
    bindPan();
    loadMaps();
  }

  window.gmap = { BASE, state, $, esc, loadMaps, selectMap, renderDoors, renderDoorList, subscribe, stop, applyView, fit, closeMenu };
  document.addEventListener('DOMContentLoaded', bind);
})();
