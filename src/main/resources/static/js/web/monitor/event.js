/* 실시간 이벤트 모니터링 — 단말기를 고르면 SSE 로 인증 이벤트를 받아 화면에 띄운다.
   MAIN(1) 에 방금 인증한 사람, 아래 띠에 지난 인증 6건(오른쪽이 최근). */
(function () {
  const BASE = '/monitor/event';
  const HISTORY = 6; // MAIN(1) 뒤의 2~7번 자리
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  /* 로그인 세션 유지 주기 — SSE 는 요청 하나라 연결만으로는 세션이 갱신되지 않는다.
     그냥 두면 한 시간 뒤 세션이 만료되고, 그 뒤 재연결이 로그인으로 튕기면서 화면이 조용히 죽는다. */
  const KEEPALIVE_MS = 5 * 60 * 1000;

  /* 마지막 인증을 MAIN 에 붙잡아 두는 시간. 이 시간이 지나면 아래 띠로 내리고 MAIN 을 비운다.
     지나간 사람이 계속 커다랗게 떠 있으면, 방금 지나간 사람으로 오독된다. */
  const MAIN_HOLD_MS = 60 * 1000;

  /* 소리 설정은 이 브라우저에 남긴다 — 상황실 PC 마다 조건이 다르다(스피커 유무·야간 소음). */
  const SOUND_KEY = 'monitorSound';

  let stream = null;      // 현재 EventSource
  let keepAlive = null;   // 세션 유지 타이머
  let holdTimer = null;   // MAIN 유지 타이머
  let deviceId = null;    // 보고 있는 단말기
  let mainHeld = false;   // MAIN 이 지금 한 건을 붙잡고 있는가(그 건은 띠에서 뺀다)
  let soundOn = localStorage.getItem(SOUND_KEY) !== 'off'; // 기본은 켜짐
  let koVoice = null;     // 한국어 음성(미리 찾아 둔다 — 인증 순간에 찾으면 늦다)
  const history = [];     // 최근 → 과거 순. 화면은 뒤집어 그린다

  /* 카드 그림 — 얼굴이 없는 자리에 세운다. 카드로만 인증한 사람(임시·장기·상주·순찰·대여)은
     장비가 얼굴을 찍지 않아 인증 사진이 없다. 빈칸으로 두면 '사진이 없는 것'과 '인증이 안 된 것'이 구분되지 않는다. */
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

  /* 사진 없음도 자리를 지켜야 한다 — 칸이 무너지면 옆 사진이 밀려 누구 것인지 헷갈린다.
     무엇으로 채울지는 서버가 내려준 faceUser 가 정한다(인원 구분은 화면이 알 일이 아니다). */
  const photo = (base64, alt, faceUser) => (base64
    ? `<img src="data:image/jpeg;base64,${base64}" alt="${esc(alt)}"/>`
    : (faceUser ? FACE_ICON : CARD_ICON));

  /* 허가 기간은 값이 길어 좁은 화면에서 두 줄로 접힌다. 시작·종료를 각각 묶어
     가운데 " ~ " 에서만 접히게 한다 — 묶지 않으면 "2026-08-04" 의 붙임표 뒤에서 갈라져
     날짜 하나가 두 줄에 나뉜다(붙임표는 줄바꿈 자리로 쓰인다). */
  const periodHtml = (period) => {
    if (!period) return '-';
    const both = period.split(' ~ ');
    return both.length === 2
      ? `<span>${esc(both[0])}</span> ~ <span>${esc(both[1])}</span>`
      : esc(period);
  };

  function showMain(e) {
    $('mainRegistered').innerHTML = photo(e.registeredPhoto, '등록 사진', e.faceUser);
    $('mainAuth').innerHTML = photo(e.authPhoto, '인증 사진', e.faceUser);
    $('mainName').textContent = e.personName || (e.personId ? e.personId : '미등록');
    $('mainCompany').textContent = e.companyName || '-';
    $('mainAreas').textContent = e.areas || '-';
    $('mainPeriod').innerHTML = periodHtml(e.period);
    $('mainResult').textContent = e.resultLabel || '';
    // 출입거부면 초록이던 곳이 통째로 붉어진다 — 멀리서 모니터만 봐도 구분되어야 한다
    $('monitorMain').classList.toggle('deny', !e.granted);
    $('monitorMain').classList.add('shown');
  }

  /* 인증마다 결과를 소리로 알린다 — 상황실은 화면을 늘 보고 있지 않다.

     미리 만들어 둔 음성 파일을 쓴다. 브라우저 내장 음성(speechSynthesis)은 speak() 를
     불러도 실제 소리가 나기까지 **1초 남짓** 걸린다(엔진이 그때 말을 만든다) — 실제로
     재 보니 첫 발화 1240ms, 이후에도 평균 1055ms 였다. 화면보다 눈에 띄게 늦는다.
     파일은 미리 받아 디코딩해 두므로 재생 시작이 즉시다.

     파일을 못 받으면 내장 음성으로, 그것도 안 되면 알림음으로 내려간다 — 무음보다 낫다. */
  const SOUND_URL = { ok: '/sound/auth-granted.wav', deny: '/sound/auth-denied.wav' };
  let audioCtx = null;
  const buffers = {}; // 디코딩까지 끝내 둔다 — 인증 순간에 할 일을 남기지 않는다

  async function loadSounds() {
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      await Promise.all(Object.entries(SOUND_URL).map(async ([k, url]) => {
        const res = await fetch(url);
        if (!res.ok) return;
        buffers[k] = await audioCtx.decodeAudioData(await res.arrayBuffer());
      }));
    } catch (err) { /* 파일이 없으면 아래 대체 경로로 간다 */ }
  }

  function speak(text, ok) {
    if (!soundOn) return;
    const buf = buffers[ok ? 'ok' : 'deny'];
    if (audioCtx && buf) {
      try {
        // 브라우저가 소리를 막아 둔 상태면 깨운다(사용자가 화면을 손댄 뒤에는 통과한다)
        if (audioCtx.state === 'suspended') audioCtx.resume();
        const src = audioCtx.createBufferSource();
        src.buffer = buf;
        src.connect(audioCtx.destination);
        src.start();
        return;
      } catch (err) { /* 아래 대체 경로로 */ }
    }
    speakByEngine(text, ok);
  }

  /* 음성 파일을 못 받은 경우의 대체 — 늦지만 무음보다는 낫다. */
  function speakByEngine(text, ok) {
    try {
      const synth = window.speechSynthesis;
      if (!synth || !koVoice) { beep(ok); return; }
      if (synth.speaking || synth.pending) synth.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.voice = koVoice;
      u.lang = koVoice.lang;
      synth.speak(u);
    } catch (err) {
      beep(ok);
    }
  }

  /* 음성 목록은 페이지 로드보다 늦게 채워진다. 인증이 왔을 때 비어 있으면 한국어 음성이
     있는데도 알림음으로 새므로, 미리 찾아 두고 목록이 바뀌면 다시 찾는다. */
  function findVoice() {
    const synth = window.speechSynthesis;
    if (!synth) return;
    koVoice = synth.getVoices().find((v) => (v.lang || '').toLowerCase().startsWith('ko')) || null;
  }

  /* 음성 파일도 내장 음성도 없을 때 — 성공은 높고 짧게, 실패는 낮고 길게. */
  function beep(ok) {
    try {
      const ctx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain); gain.connect(ctx.destination);
      osc.frequency.value = ok ? 880 : 320;
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + (ok ? 0.25 : 0.6));
      osc.start();
      osc.stop(ctx.currentTime + (ok ? 0.25 : 0.6));
    } catch (err) { /* 소리를 못 내도 화면은 그대로 돈다 */ }
  }

  function applySound() {
    localStorage.setItem(SOUND_KEY, soundOn ? 'on' : 'off');
    $('btnSound').classList.toggle('off', !soundOn);
    $('btnSound').setAttribute('aria-pressed', String(soundOn));
  }

  function onAuth(e) {
    // 소리를 먼저 시작한다 — 그리기(사진 2장 + 지난 인증 6칸 다시 그리기)가 끝난 뒤에
    // 부르면 그만큼 발화가 늦어 화면과 어긋난다. speak 는 큐에 넣고 곧바로 돌아오므로
    // 그리는 동안 음성 엔진이 준비되어 결과적으로 화면과 같이 나온다.
    speak(e.granted ? '인증 성공' : '인증 실패', e.granted);
    history.unshift(e);
    if (history.length > HISTORY + 1) history.pop(); // MAIN 1건 + 지난 6건
    mainHeld = true;
    showMain(e);
    renderHistory();
    clearTimeout(holdTimer);
    holdTimer = setTimeout(demoteMain, MAIN_HOLD_MS);
  }

  /* 1분간 아무도 안 지나갔다 — MAIN 을 비우고 그 사람을 띠로 내린다.
     기록이 사라지는 것이 아니라 자리를 옮기는 것이다. */
  function demoteMain() {
    mainHeld = false;
    $('mainRegistered').innerHTML = '';
    $('mainAuth').innerHTML = '';
    $('mainName').textContent = '-';
    $('mainCompany').textContent = '-';
    $('mainAreas').textContent = '-';
    $('mainPeriod').textContent = '-';
    $('mainResult').textContent = '';
    $('monitorMain').classList.remove('shown', 'deny');
    renderHistory();
  }

  /* MAIN 에 올라간 1건은 띠에서 뺀다 — 같은 사람이 두 칸에 겹쳐 보이면 두 번 인증한 것처럼 읽힌다.
     배열은 최근이 앞이고 화면은 왼쪽이 오래된 것 — 뒤집어 그려야 7 6 5 4 3 2 가 된다.
     아직 6건이 안 찼으면 왼쪽을 빈칸으로 메운다. 그래야 처음부터 오른쪽(2번 자리)부터 차오르고,
     최근 인증이 늘 같은 자리에 있어 눈이 그 칸만 보면 된다. */
  function renderHistory() {
    const past = history.slice(mainHeld ? 1 : 0, (mainHeld ? 1 : 0) + HISTORY).reverse();
    const blanks = '<div class="monitor-card monitor-card-blank"></div>'.repeat(HISTORY - past.length);
    $('monitorHistory').innerHTML = blanks + past.map((e) => `
      <div class="monitor-card${e.granted ? '' : ' deny'}">
        <div class="monitor-card-photos">
          ${photo(e.registeredPhoto, '등록 사진', e.faceUser)}
          ${photo(e.authPhoto, '인증 사진', e.faceUser)}
        </div>
        <div class="monitor-card-name">${esc(e.personName || e.personId || '-')}</div>
        <div class="monitor-card-company">${esc(e.companyName || '-')}</div>
        <div class="monitor-card-areas">${esc(e.areas || '-')}</div>
        <div class="monitor-card-result">${esc(e.resultLabel || '')}</div>
      </div>`).join('');
  }

  /* 사유가 있으면 사유가 먼저다 — '연결됨'을 앞세우면 소켓만 열리고 이벤트는 안 오는 상태가
     "수신 중"으로 보인다. 화면을 켜 두는 용도라 그렇게 되면 아무도 이상을 눈치채지 못한다. */
  function onStatus(s) {
    const el = $('monitorState');
    if (s.message) {
      el.textContent = s.message;
      el.classList.add('warn');
    } else if (s.connected) {
      el.textContent = '수신 중';
      el.classList.remove('warn');
    } else {
      el.textContent = 'BiostarX 연결 중';
      el.classList.add('warn');
    }
  }

  function stop() {
    if (stream) { stream.close(); stream = null; }
    if (keepAlive) { clearInterval(keepAlive); keepAlive = null; }
    clearTimeout(holdTimer); holdTimer = null;
    $('btnStop').disabled = true;
    $('monitorState').textContent = '대기';
    $('monitorState').classList.remove('warn');
  }

  function start(id) {
    stop();
    if (!id) return;
    stream = new EventSource(BASE + '/stream?deviceId=' + encodeURIComponent(id));
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

  /* 단말기 선택 — 사용자관리와 같은 공통 팝업(검색 가능). 고른 뒤 장치ID·장치명을 함께 보여 준다.
     같은 장치를 다시 고르면 흐르던 수신을 끊지 않는다(재구독은 감사·재연결만 늘린다). */
  async function pickDevice() {
    const sel = await devicePicker.open(BASE + '/devices');
    if (!sel || sel.id === deviceId) return;
    deviceId = sel.id;
    const label = sel.id + (sel.name ? ' · ' + sel.name : '');
    $('deviceField').value = label;
    $('deviceField').title = label; // 칸이 좁아 잘리면 마우스를 올려 확인한다
    start(sel.id);
  }

  function bind() {
    $('deviceField').addEventListener('click', pickDevice);
    $('btnStop').addEventListener('click', () => {
      deviceId = null;
      $('deviceField').value = '';
      $('deviceField').title = '';
      stop();
    });
    /* 켤 때 한 번 읽어 준다 — 브라우저는 사용자가 손대기 전에는 소리를 막는다.
       이 클릭이 그 '손댐'이 되어, 이후 인증 안내가 조용히 묻히지 않는다. */
    $('btnSound').addEventListener('click', () => {
      soundOn = !soundOn;
      applySound();
      //if (soundOn) speak('소리 켜짐', true);
    });
    loadSounds(); // 미리 받아 디코딩해 둔다 — 인증 순간에 남는 일이 없어야 화면과 같이 난다
    if (window.speechSynthesis) {
      findVoice();
      window.speechSynthesis.addEventListener('voiceschanged', findVoice);
    }
    applySound();
    window.addEventListener('beforeunload', stop); // 떠나면 서버도 구독을 정리한다
    renderHistory(); // 빈칸 6개를 먼저 세워 둔다 — 어디가 채워질 자리인지 보이게
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
