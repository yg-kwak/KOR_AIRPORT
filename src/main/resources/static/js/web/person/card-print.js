/* 카드 프린트 미리보기·인쇄 (정규인원). 얼굴+카드가 모두 등록된 인원만 출력한다.
   서버가 card_project 템플릿으로 앞/뒤 카드 이미지를 렌더한다(미리보기 후 인쇄). */
(function () {
  const BASE = '/person/person';
  const $ = (id) => document.getElementById(id);
  let ctx = { personId: null, cardId: null };

  async function open(personId, cardId) {
    if (!personId || !cardId) { toast.warning('먼저 인원과 카드를 저장한 뒤 출력하세요.'); return; }
    ctx = { personId, cardId };
    $('cpImages').innerHTML = '<div class="empty">미리보기 생성 중...</div>';
    $('cardPrintModal').classList.add('open');
    try {
      const imgs = await api.post(BASE + '/card/print/preview', ctx);
      $('cpImages').innerHTML = (imgs || []).map((u, i) =>
        `<figure class="cp-side"><img src="${u}" alt="카드"/><figcaption>${i === 0 ? '앞면' : '뒷면'}</figcaption></figure>`).join('');
    } catch (e) {
      close(); // 서버 검증 실패(얼굴/카드 없음 등) 토스트는 api 계층이 표시
    }
  }
  function close() { $('cardPrintModal').classList.remove('open'); }
  async function doPrint() {
    await api.post(BASE + '/card/print', ctx);
    close();
  }

  document.addEventListener('DOMContentLoaded', () => {
    if (!$('cardPrintModal')) return;
    $('cpPrint').addEventListener('click', doPrint);
    $('cpCancel').addEventListener('click', close);
    $('cpClose').addEventListener('click', close);
  });

  window.cardPrint = { open };
})();
