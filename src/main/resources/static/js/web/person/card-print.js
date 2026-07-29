/* 카드 프린트 미리보기·인쇄 (정규인원). 얼굴+카드가 모두 등록된 인원만 출력한다.
   서버가 card_project 템플릿으로 앞/뒤 카드 이미지를 렌더한다(미리보기 후 인쇄). */
(function () {
  const BASE = '/person/person';
  const $ = (id) => document.getElementById(id);
  let ctx = { personId: null, cardId: null };
  let lastImgs = []; // 현재 미리보기 이미지(앞/뒤) — 인쇄에 재사용

  // 프린터가 클라이언트 PC 에 있으므로 브라우저 인쇄(@page margin:0 + object-fit:fill)로 여백 없이 출력한다.
  function browserPrint(urls) {
    const area = $('cardPrintArea');
    if (!area || !urls || !urls.length) { toast.warning('출력할 카드가 없습니다.'); return; }
    area.innerHTML = urls.map((u) =>
      `<div class="print-card-page"><img class="print-card-img" src="${u}" alt="카드"/></div>`).join('');
    const imgs = [...area.querySelectorAll('img')];
    window.addEventListener('afterprint', () => { area.innerHTML = ''; }, { once: true });
    Promise.all(imgs.map((im) => (im.complete ? Promise.resolve()
      : new Promise((r) => { im.onload = r; im.onerror = r; })))).then(() => window.print());
  }

  async function open(personId, cardId) {
    if (!personId || !cardId) { toast.warning('먼저 인원과 카드를 저장한 뒤 출력하세요.'); return; }
    ctx = { personId, cardId };
    lastImgs = [];
    $('cpImages').innerHTML = '<div class="empty">미리보기 생성 중...</div>';
    $('cardPrintModal').classList.add('open');
    try {
      const imgs = await api.post(BASE + '/card/print/preview', ctx);
      lastImgs = imgs || [];
      $('cpImages').innerHTML = lastImgs.map((u, i) =>
        `<figure class="cp-side"><img src="${u}" alt="카드"/><figcaption>${i === 0 ? '앞면' : '뒷면'}</figcaption></figure>`).join('');
    } catch (e) {
      close(); // 서버 검증 실패(얼굴/카드 없음 등) 토스트는 api 계층이 표시
    }
  }
  function close() { $('cardPrintModal').classList.remove('open'); }
  async function doPrint() {
    browserPrint(lastImgs); // 브라우저로 인쇄
    try { await api.post(BASE + '/card/print', ctx); } catch (e) { /* 감사 로그 실패는 무시 */ }
    close();
  }

  // 목록 일괄 출력 — 대상 명단 확인 후 출력. 카드 1장·얼굴 보유자만 대상(전량 검증).
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  let bulkIds = [];

  async function bulk(ids) {
    if (!ids || !ids.length) { toast.warning('인원을 선택하세요.'); return; }
    bulkIds = ids;
    const c = await api.post(BASE + '/card/print/bulk/check', ids);
    const problems = [
      ['2장 이상 카드 보유', c.multi], ['카드 없음', c.noCard], ['얼굴 없음', c.noFace],
    ].filter(([, v]) => v && v.length);
    $('bpWarn').innerHTML = problems.length
      ? '<div class="form-error">문제 인원이 있어 출력할 수 없습니다.<br>'
        + problems.map(([t, v]) => `· ${t} ( 인원ID : ${esc(v.join(', '))} )`).join('<br>') + '</div>'
      : '';
    $('bpInfo').textContent = `출력 대상 ${c.targets.length}명`;
    $('bpBody').innerHTML = c.targets.length
      ? c.targets.map((t) => `<tr><td>${esc(t.personId)}</td><td>${esc(t.personName)}</td><td>${esc(t.cardName)}</td></tr>`).join('')
      : '<tr><td colspan="3" class="empty">출력 대상이 없습니다.</td></tr>';
    const printable = !problems.length && c.targets.length > 0;
    $('bpPrint').disabled = !printable;
    $('bulkPrintModal').classList.add('open');
  }
  function closeBulk() { $('bulkPrintModal').classList.remove('open'); }
  async function doBulkPrint() {
    closeBulk();
    try {
      const imgs = await api.post(BASE + '/card/print/bulk', bulkIds); // 대상 전원 앞/뒤 이미지 + 감사
      browserPrint(imgs);
    } catch (e) { /* 서버 메시지 토스트는 api 계층 */ }
  }

  document.addEventListener('DOMContentLoaded', () => {
    if ($('cardPrintModal')) {
      $('cpPrint').addEventListener('click', doPrint);
      $('cpCancel').addEventListener('click', close);
      $('cpClose').addEventListener('click', close);
    }
    if ($('bulkPrintModal')) {
      $('bpPrint').addEventListener('click', doBulkPrint);
      $('bpCancel').addEventListener('click', closeBulk);
      $('bpClose').addEventListener('click', closeBulk);
    }
  });

  window.cardPrint = { open, bulk };
})();
