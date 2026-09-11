/* 인증 결과 소리 — 상황실은 화면을 늘 보고 있지 않아 소리가 먼저 알린다.
   실시간 이벤트(901)와 이벤트 로그(902) 두 화면이 함께 쓴다.

   authSound.attach(버튼요소)   켜기/끄기 버튼을 묶고 저장된 설정을 적용한다(화면당 1회)
   authSound.play(granted)      인증 결과를 읽어 준다

   미리 만들어 둔 음성 파일을 쓴다. 브라우저 내장 음성(speechSynthesis)은 speak() 를 불러도
   실제 소리까지 1초 남짓 걸린다(엔진이 그때 말을 만든다 — 첫 발화 1240ms, 이후 평균 1055ms 실측).
   화면보다 눈에 띄게 늦는다. 파일은 미리 받아 디코딩해 두므로 재생 시작이 즉시다.
   파일을 못 받으면 내장 음성으로, 그것도 안 되면 알림음으로 내려간다 — 무음보다 낫다. */
window.authSound = (function () {
  'use strict';
  /* 설정은 이 브라우저에 남긴다 — 상황실 PC 마다 조건이 다르다(스피커 유무·야간 소음). */
  const SOUND_KEY = 'monitorSound';
  const SOUND_URL = { ok: '/sound/auth-granted.wav', deny: '/sound/auth-denied.wav' };

  let on = true;
  try { on = localStorage.getItem(SOUND_KEY) !== 'off'; } catch (e) { /* 저장이 막힌 브라우저 */ }
  let btn = null;
  let audioCtx = null;
  let koVoice = null; // 한국어 음성(미리 찾아 둔다 — 인증 순간에 찾으면 늦다)
  const buffers = {}; // 디코딩까지 끝내 둔다 — 인증 순간에 할 일을 남기지 않는다

  async function load() {
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      await Promise.all(Object.entries(SOUND_URL).map(async ([k, url]) => {
        const res = await fetch(url);
        if (!res.ok) return;
        buffers[k] = await audioCtx.decodeAudioData(await res.arrayBuffer());
      }));
    } catch (err) { /* 파일이 없으면 아래 대체 경로로 간다 */ }
  }

  /* 음성 목록은 페이지 로드보다 늦게 채워진다. 인증이 왔을 때 비어 있으면 한국어 음성이
     있는데도 알림음으로 새므로, 미리 찾아 두고 목록이 바뀌면 다시 찾는다. */
  function findVoice() {
    const synth = window.speechSynthesis;
    if (!synth) return;
    koVoice = synth.getVoices().find((v) => (v.lang || '').toLowerCase().startsWith('ko')) || null;
  }

  /* 음성 파일을 못 받은 경우의 대체 — 늦지만 무음보다는 낫다. */
  function byEngine(text, ok) {
    try {
      const synth = window.speechSynthesis;
      if (!synth || !koVoice) { beep(ok); return; }
      if (synth.speaking || synth.pending) synth.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.voice = koVoice;
      u.lang = koVoice.lang;
      synth.speak(u);
    } catch (err) { beep(ok); }
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

  function apply() {
    try { localStorage.setItem(SOUND_KEY, on ? 'on' : 'off'); } catch (e) { /* 저장이 막힌 브라우저 */ }
    if (!btn) return;
    btn.classList.toggle('off', !on);
    btn.setAttribute('aria-pressed', String(on));
  }

  return {
    attach(button) {
      btn = button;
      /* 버튼 클릭이 브라우저의 '사용자가 손댔다' 조건을 채운다 — 그 전에는 소리가 막혀
         인증 안내가 조용히 묻힌다. */
      if (btn) btn.addEventListener('click', () => { on = !on; apply(); });
      load(); // 미리 받아 디코딩해 둔다 — 인증 순간에 남는 일이 없어야 화면과 같이 난다
      if (window.speechSynthesis) {
        findVoice();
        window.speechSynthesis.addEventListener('voiceschanged', findVoice);
      }
      apply();
    },

    /** 인증 결과를 읽어 준다. 그리기보다 **먼저** 부른다 — 큐에 넣고 곧바로 돌아온다. */
    play(granted) {
      if (!on) return;
      const buf = buffers[granted ? 'ok' : 'deny'];
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
      byEngine(granted ? '인증 성공' : '인증 실패', granted);
    },
  };
})();
