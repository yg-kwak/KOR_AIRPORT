/* 이벤트 로그 모니터링 — 단말기를 여러 대 고르면 그 인증을 한 화면으로 받는다.
   왼쪽·가운데에 방금 인증한 사람, 오른쪽에 지난 인증을 세로로 쌓아 스크롤한다.
   실시간 이벤트(901)와 같은 이벤트를 다른 배치로 본다 — 서버·소리는 그쪽과 한 벌이다. */
(function () {
  const BASE = '/monitor/eventLog';
  const HISTORY = 15; // 오른쪽 목록에 '보이는' 최대 건수 — 넘으면 오래된 것부터 버린다
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  /* 로그인 세션 유지 주기 — SSE 는 요청 하나라 연결만으로는 세션이 갱신되지 않는다.
     그냥 두면 한 시간 뒤 세션이 만료되고, 그 뒤 재연결이 로그인으로 튕기면서 화면이 조용히 죽는다. */
  const KEEPALIVE_MS = 5 * 60 * 1000;

  /* 고른 단말기는 이 브라우저에 남긴다 — 다른 메뉴에 갔다 오면 화면이 새로 뜨는데,
     그때마다 다시 고르게 하면 늘 켜 두는 화면에서 가장 번거로운 일이 된다. */
  const DEVICE_KEY = 'monitorLogDevices';

  let stream = null;    // 현재 EventSource
  let keepAlive = null; // 세션 유지 타이머
  let devices = [];     // 보고 있는 단말기 [{id, name}]
  const history = [];   // 최근이 앞. 맨 앞(0번)은 지금 메인에 떠 있는 건이라 목록에서는 뺀다

  /* 카드 그림 — 카드로만 인증한 사람(임시·장기·상주·순찰·대여)은 장비가 얼굴을 찍지 않는다.
     빈칸으로 두면 '사진이 없는 것'과 '인증이 안 된 것'이 구분되지 않는다. */
  const CARD_ICON = `<svg class="monitor-card-icon" viewBox="0 0 96 62" aria-label="카드 인증">
      <rect x="1.5" y="1.5" width="93" height="59" rx="7"/>
      <line x1="1.5" y1="18" x2="94.5" y2="18"/>
      <rect x="60" y="34" width="24" height="14" rx="2"/>
      <text x="12" y="46">카드</text></svg>`;

  /* 사람 그림 — 얼굴이 있어야 정상인 정규인원의 빈 사진칸에 세운다.
     카드 그림으로 두면 '등록이 빠진 사람'과 '원래 얼굴을 안 찍는 사람'이 구분되지 않는다. */
  const FACE_ICON = `<svg class="monitor-face-icon" viewBox="0 0 96 96" aria-label="사진 없음">
      <circle cx="48" cy="33" r="19"/>
      <path d="M14 90c0-18.8 15.2-34 34-34s34 15.2 34 34"/></svg>`;

  const photo = (base64, alt, faceUser) => (base64
    ? `<img src="data:image/jpeg;base64,${base64}" alt="${esc(alt)}"/>`
    : (faceUser ? FACE_ICON : CARD_ICON));

  /* 허가 기간은 값이 길어 칸을 넘친다. 시작·종료를 각각 묶어 가운데 " ~ " 에서만 접히게 한다
     — 묶지 않으면 "2026-08-04" 의 붙임표 뒤에서 갈라져 날짜 하나가 두 줄에 나뉜다. */
  const periodHtml = (period) => {
    if (!period) return '-';
    const both = period.split(' ~ ');
    return both.length === 2
      ? `<span>${esc(both[0])}</span> ~ <span>${esc(both[1])}</span>`
      : esc(period);
  };

  function showMain(e) {
    $('logRegistered').innerHTML = photo(e.registeredPhoto, '등록 사진', e.faceUser);
    $('logAuth').innerHTML = photo(e.authPhoto, '인증 사진', e.faceUser);
    $('logName').textContent = e.personName || (e.personId ? e.personId : '미등록');
    $('logCompany').textContent = e.companyName || '-';
    $('logAreas').textContent = e.areas || '-';
    $('logPeriod').innerHTML = periodHtml(e.period);
    $('logResultLabel').textContent = e.resultLabel || '';
    // 여러 대를 한 화면에서 보므로 결과만으로는 어느 문인지 알 수 없다
    $('logResultDevice').textContent = e.deviceName || e.deviceId || '';
    $('monitorLog').classList.toggle('deny', !e.granted);
    $('monitorLog').classList.add('shown');
  }

  function onAuth(e) {
    // 소리를 먼저 시작한다 — 그리기가 끝난 뒤에 부르면 그만큼 발화가 늦어 화면과 어긋난다
    authSound.play(e.granted);
    history.unshift(e);
    if (history.length > HISTORY + 1) history.pop(); // 메인 1건 + 지난 15건
    showMain(e);
    renderHistory();
  }

  /* 최근이 위다. 15건을 넘기면 오래된 것부터 버리고, 넘치는 만큼은 스크롤로 본다.

     지금 메인에 떠 있는 건(0번)은 뺀다 — 같은 사람이 크게도 뜨고 목록 맨 위에도 있으면
     두 번 인증한 것처럼 읽힌다. 다음 사람이 지나가야 앞사람이 목록으로 내려간다. */
  function renderHistory() {
    const past = history.slice(1);
    $('logHistory').innerHTML = past.length
      ? past.map((e) => `
        <div class="monitor-log-item${e.granted ? '' : ' deny'}">
          <div class="monitor-log-photo">${photo(e.registeredPhoto, '등록 사진', e.faceUser)}</div>
          <dl class="monitor-log-info">
            <dt>이름</dt><dd>${esc(e.personName || e.personId || '-')}</dd>
            <dt>소속</dt><dd>${esc(e.companyName || '-')}</dd>
            <dt>허가 구역</dt><dd>${esc(e.areas || '-')}</dd>
            <dt>단말기</dt><dd class="monitor-log-device" title="${esc(e.deviceName || e.deviceId || '')}">${esc(e.deviceName || e.deviceId || '-')}</dd>
          </dl>
        </div>`).join('')
      : '<div class="monitor-log-empty">아직 인증이 없습니다.</div>';
  }

  /* 사유가 있으면 사유가 먼저다 — '연결됨'을 앞세우면 소켓만 열리고 이벤트는 안 오는 상태가
     "수신 중"으로 보인다. 화면을 켜 두는 용도라 그렇게 되면 아무도 이상을 눈치채지 못한다. */
  function onStatus(s) {
    const el = $('monitorState');
    if (s.message) { el.textContent = s.message; el.classList.add('warn'); }
    else if (s.connected) { el.textContent = '수신 중'; el.classList.remove('warn'); }
    else { el.textContent = 'BiostarX 연결 중'; el.classList.add('warn'); }
  }

  function stop() {
    if (stream) { stream.close(); stream = null; }
    if (keepAlive) { clearInterval(keepAlive); keepAlive = null; }
    $('btnStop').disabled = true;
    $('monitorState').textContent = '대기';
    $('monitorState').classList.remove('warn');
  }

  function start() {
    stop();
    if (!devices.length) return;
    const q = new URLSearchParams();
    devices.forEach((d) => q.append('deviceId', d.id)); // 여러 대를 반복 파라미터로 보낸다
    stream = new EventSource(BASE + '/stream?' + q.toString());
    /* 한 건이 깨져도 다음 인증은 계속 받아야 한다 — 리스너에서 예외가 나가면 그 뒤가 조용히 멈춘다 */
    const on = (name, fn) => stream.addEventListener(name, (m) => {
      try { fn(JSON.parse(m.data)); } catch (err) { console.warn('이벤트 처리 실패', err); }
    });
    on('auth', onAuth);
    on('status', onStatus);
    stream.onerror = () => {
      // EventSource 는 스스로 다시 붙는다. 사용자에게는 상태만 알린다
      $('monitorState').textContent = '연결 재시도 중';
      $('monitorState').classList.add('warn');
    };
    // 세션이 이미 끊겼으면 api 래퍼가 로그인 화면으로 보낸다 — 죽은 채로 남지 않는다
    keepAlive = setInterval(() => api.get(BASE + '/alive').catch(() => {}), KEEPALIVE_MS);
    $('btnStop').disabled = false;
    $('monitorState').textContent = '연결 중';
  }

  function showDevices() {
    const label = devices.length ? devices.map((d) => d.name || d.id).join(', ') : '단말기를 선택하세요';
    $('pickedDevices').textContent = label;
    $('pickedDevices').title = label; // 여러 대면 줄 끝에서 잘린다
  }

  function remember() {
    try { localStorage.setItem(DEVICE_KEY, JSON.stringify(devices)); } catch (e) { /* 저장이 막힌 브라우저 */ }
  }

  /* 다른 메뉴에 갔다 와도 보던 단말기를 그대로 다시 본다. 저장이 막혔거나 값이 깨졌으면
     선택 없이 시작한다 — 화면이 서지 않는 것이 더 나쁘다. */
  function restore() {
    try {
      const saved = JSON.parse(localStorage.getItem(DEVICE_KEY) || '[]');
      if (Array.isArray(saved)) {
        devices = saved.filter((d) => d && d.id).map((d) => ({ id: String(d.id), name: d.name || '' }));
      }
    } catch (e) { devices = []; }
  }

  /* 단말기 선택 — 공통 팝업의 여러 대 모드. 지금 보고 있는 것을 미리 체크해 연다. */
  async function pick() {
    const sel = await devicePicker.open(BASE + '/devices', {
      multiple: true, selected: devices.map((d) => d.id),
    });
    if (!sel) return; // 취소 — 보던 것을 그대로 둔다
    devices = sel.map((d) => ({ id: String(d.id), name: d.name || '' }));
    remember();
    showDevices();
    start();
  }

  function bind() {
    $('btnPick').addEventListener('click', pick);
    $('btnStop').addEventListener('click', () => {
      devices = [];
      remember();
      showDevices();
      stop();
    });
    authSound.attach($('btnSound')); // 소리 엔진은 실시간 이벤트 화면과 한 벌(core/auth-sound.js)
    window.addEventListener('beforeunload', stop); // 떠나면 서버도 구독을 정리한다
    restore();
    showDevices();
    renderHistory();
    start(); // 저장된 단말기가 있으면 바로 이어 받는다
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
