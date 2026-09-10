/* 임시인원등록·장기출입등록 — 방문객 카드 태깅.
   방문객 탭이 열려 있는 동안 내 리더에 카드를 대면, 대는 대로 위에서부터 방문객에게 카드가 채워진다.
   [SCAN] 은 한 장에 버튼 한 번이라 다섯 명이면 다섯 번을 눌러야 한다 — 그 왕복을 없앤다.

   visitCardTag.init({ base, params, assign })   화면당 1회
     base    '/visitor/visitor' 또는 '/visitor/longterm'
     params  () => ({ acGroupIds })  판정 기준은 '지금 화면에 고른' 출입그룹이다
     assign  (card) => true|false    다음 빈 방문객에 넣는다. 넣을 자리가 없으면 false
   visitCardTag.on()  / .off()                    방문객 탭 진입·이탈, 모달 닫기   (docs/frontend.md) */
window.visitCardTag = (function () {
  'use strict';
  let cfg = null;
  let stream = null;
  let chain = Promise.resolve(); // 한 장씩 차례로 — 연달아 대면 조회 응답이 뒤섞여 2번 사람이 먼저 받는다

  function on() {
    if (!cfg || stream) return;
    stream = new EventSource(cfg.base + '/card/tag/stream');
    stream.addEventListener('card', (e) => {
      let cardNo = null;
      try { cardNo = (JSON.parse(e.data) || {}).cardNo; } catch (err) { console.warn('카드 태깅 해석 실패', err); }
      // 한 건이 깨져도 다음 태깅은 계속 받아야 한다 — 리스너에서 예외가 나가면 그 뒤가 조용히 멈춘다
      if (cardNo) chain = chain.then(() => handle(cardNo)).catch((err) => console.warn('카드 태깅 처리 실패', err));
    });
    /* 리더가 지정되지 않았거나 장비 연결이 끊기면 아무 일도 일어나지 않는다 —
       그대로 두면 "카드를 댔는데 왜 안 되지" 로만 보여 무엇을 고칠지 알 수 없다. */
    stream.addEventListener('status', (e) => {
      let st = {};
      try { st = JSON.parse(e.data) || {}; } catch (err) { return; }
      if (st.error) window.toast.warning('카드 리더 연결: ' + st.error);
    });
    // EventSource 는 스스로 다시 붙는다 — 여기서 닫으면 망이 한 번 출렁일 때 태깅이 영영 멈춘다
    stream.onerror = () => console.warn('카드 태깅 스트림 재연결 중');
  }

  function off() {
    if (stream) { stream.close(); stream = null; }
  }

  /* 판정은 [SCAN]·[선택] 과 같은 조회를 그대로 쓴다 — 후보에 없으면 그 카드는 이 방문에 못 쓴다.
     규칙을 화면에 다시 적으면 세 자리가 조용히 갈린다. */
  async function handle(cardNo) {
    const areas = (cfg.params() || {}).acGroupIds || [];
    if (!areas.length) {
      window.toast.warning('사용자 출입그룹을 먼저 선택하세요. 어느 구역 카드를 써야 할지 정해지지 않았습니다.');
      return;
    }
    const q = new URLSearchParams({ keyword: cardNo });
    areas.forEach((id) => q.append('acGroupIds', id));
    const rows = (await window.api.get(cfg.base + '/cards/unassigned?' + q.toString())) || [];
    // keyword 는 부분일치라 여러 장이 걸릴 수 있다 — 읽은 번호와 정확히 같은 카드만 쓴다
    const card = rows.find((c) => String(c.biostarCardValue) === String(cardNo));
    if (!card) {
      window.toast.warning(`카드(${cardNo})는 이 방문에 쓸 수 없습니다. 구역이 다르거나 이미 발급된 카드입니다.`);
      return;
    }
    if (!cfg.assign(card)) {
      window.toast.warning('카드를 받을 방문객이 없습니다. 방문객을 추가하세요.');
    }
  }

  return {
    init(options) { cfg = options; },
    on,
    off,
  };
})();
