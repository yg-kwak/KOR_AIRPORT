/* 카드 목록 컴포넌트 — 조각 fragments/components/card-list(+cardModal) 과 1:1. (docs/frontend.md)
   '카드 추가' 확인 시 BiostarX 에 카드를 즉시 등록하고(POST {baseUrl}/card/register) 결과를 목록에 담아둔다.
   tb_card 저장과 사용자 부여(cards[])는 인원 저장 시 한 번에 이뤄지므로, 저장하지 않으면 우리 DB 에는 남지 않는다. */
window.cardList = (function () {
  const state = {}; // 컨테이너 id → { baseUrl, rows: [] }

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function render(id) {
    const rows = state[id].rows;
    const body = document.getElementById(id).querySelector('.card-list-body');
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="6" class="empty">등록된 카드가 없습니다.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((c, i) => `
      <tr>
        <td>${esc(c.cardNo)}</td>
        <td>${esc(c.cardTypeName)}</td>
        <td>${esc(c.cardName)}</td>
        <td>${esc(c.cardStatusName)}</td>
        <td>${esc(c.feePaidDt)}</td>
        <td><button type="button" class="btn btn-sm btn-danger card-list-del" data-idx="${i}">제외</button></td>
      </tr>`).join('');
  }

  /** 저장된 카드(TbCard)로 목록을 채운다(모달 열 때). rows 가 없으면 빈 목록. */
  function set(id, cards) {
    state[id].rows = (cards || []).map((c) => ({
      cardId: c.cardId, cardNo: c.biostarCardValue, biostarCardId: c.biostarCardId,
      cardType: c.cardType, cardTypeName: c.cardTypeName, cardName: c.cardName,
      cardStatus: c.cardStatus, cardStatusName: c.cardStatusName,
      feePaidDt: c.feePaidDt, issueReason: c.issueReason, remark: c.remark,
    }));
    render(id);
  }

  /** 인원 저장 payload 의 cards[] — 화면 표시용 이름 필드는 빼고 보낸다. */
  function get(id) {
    return state[id].rows.map((c) => ({
      cardId: c.cardId, cardNo: c.cardNo, biostarCardId: c.biostarCardId,
      cardType: c.cardType, cardName: c.cardName, cardStatus: c.cardStatus,
      feePaidDt: c.feePaidDt || null, issueReason: c.issueReason || null, remark: c.remark || null,
    }));
  }

  function openModal() {
    ['cardNo', 'cardType', 'cardTypeName', 'cardName', 'cardStatus', 'cardStatusName',
      'cardFeePaidDt', 'cardIssueReason', 'cardRemark'].forEach((f) => { $(f).value = ''; });
    $('cardModal').classList.add('open');
  }
  function closeModal() { $('cardModal').classList.remove('open'); }

  // 확인 → BiostarX 카드 등록(즉시) → 목록에 추가
  async function confirmCard(id) {
    const cardNo = $('cardNo').value.trim();
    if (!cardNo) { toast.warning('카드번호는 필수입니다.'); return; }
    if (state[id].rows.some((c) => c.cardNo === cardNo)) {
      toast.warning('이미 목록에 있는 카드번호입니다.'); return;
    }
    const res = await api.post(state[id].baseUrl + '/card/register', { cardNo });
    if (!res || !res.success) { toast.error((res && res.message) || '카드 등록에 실패했습니다.'); return; }
    state[id].rows.push({
      cardId: null, cardNo: res.cardNo, biostarCardId: res.biostarCardId,
      cardType: $('cardType').value || null, cardTypeName: $('cardTypeName').value,
      cardName: $('cardName').value.trim() || null,
      cardStatus: $('cardStatus').value || null, cardStatusName: $('cardStatusName').value,
      feePaidDt: $('cardFeePaidDt').value || null,
      issueReason: $('cardIssueReason').value.trim() || null,
      remark: $('cardRemark').value.trim() || null,
    });
    render(id);
    closeModal();
    toast.success('카드를 등록했습니다. 인원을 저장해야 사용자에게 부여됩니다.');
  }

  async function scan(id) {
    const res = await api.post(state[id].baseUrl + '/card/scan', {});
    if (!res || !res.success) { toast.error((res && res.message) || '카드를 읽지 못했습니다.'); return; }
    $('cardNo').value = res.cardNo;
    toast.success('카드번호를 읽었습니다.');
  }

  /** 컨테이너와 엔드포인트를 연결한다(화면당 1회). */
  function init(id, opts) {
    state[id] = { baseUrl: opts.baseUrl, rows: [] };
    const box = document.getElementById(id);
    box.querySelector('.card-list-add').addEventListener('click', openModal);
    box.addEventListener('click', (e) => {
      const del = e.target.closest('.card-list-del');
      if (!del) return;
      state[id].rows.splice(Number(del.dataset.idx), 1); // 저장 전 목록에서만 제외
      render(id);
    });
    $('cardNo').addEventListener('input', (e) => { e.target.value = e.target.value.replace(/\D/g, ''); });
    $('btnCardScan').addEventListener('click', () => scan(id));
    $('cardModalOk').addEventListener('click', () => confirmCard(id));
    $('cardModalCancel').addEventListener('click', closeModal);
    $('cardModalClose').addEventListener('click', closeModal);
    $('cardTypeName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CDT', cmmName: '카드구분' });
      if (sel) { $('cardType').value = sel.codeId; $('cardTypeName').value = sel.codeName; }
    });
    $('cardStatusName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CS', cmmName: '카드상태' });
      if (sel) { $('cardStatus').value = sel.codeId; $('cardStatusName').value = sel.codeName; }
    });
    render(id);
  }

  return { init, set, get };
})();
