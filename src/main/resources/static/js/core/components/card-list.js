/* 카드 목록 컴포넌트 — 조각 fragments/components/card-list(+cardPanel) 과 1:1. (docs/frontend.md)
   '카드 추가' 확인 시 BiostarX 에 카드를 즉시 등록하고(POST {baseUrl}/card/register) 결과를 목록에 담아둔다.
   tb_card 저장과 사용자 부여(cards[])는 인원 저장 시 한 번에 이뤄지므로, 저장하지 않으면 우리 DB 에는 남지 않는다.
   목록에서 '제외'한 카드는 삭제가 아니라 회수(미배정)라 다른 인원이 같은 카드번호로 다시 발급받을 수 있다.
   카드종류는 화면에서 고정 표시만 하고 실제 코드값은 서버(CardService)가 정한다. */
window.cardList = (function () {
  const state = {}; // 컨테이너 id → { baseUrl, cardTypeName, rows: [], editIdx }

  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function render(id) {
    const rows = state[id].rows;
    const body = document.getElementById(id).querySelector('.card-list-body');
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="5" class="empty">등록된 카드가 없습니다.</td></tr>';
      return;
    }
    const canPrint = !!state[id].print;
    body.innerHTML = rows.map((c, i) => `
      <tr class="row-click card-list-row" data-idx="${i}">
        <td>${esc(c.cardNo)}</td>
        <td>${esc(c.passTypeName)}</td>
        <td>${esc(c.cardName)}</td>
        <td>${esc(c.cardStatusName)}</td>
        <td>${canPrint && c.cardId ? `<button type="button" class="btn btn-sm card-list-print" data-idx="${i}">출력</button> ` : ''}<button type="button" class="btn btn-sm btn-danger card-list-del" data-idx="${i}">제외</button></td>
      </tr>`).join('');
  }

  /** 저장된 카드(TbCard)로 목록을 채운다(모달 열 때). rows 가 없으면 빈 목록. */
  function set(id, cards) {
    state[id].rows = (cards || []).map((c) => ({
      cardId: c.cardId, cardNo: c.biostarCardValue, biostarCardId: c.biostarCardId,
      cardName: c.cardName,
      cardStatus: c.cardStatus, cardStatusName: c.cardStatusName,
      passType: c.passType, passTypeName: c.passTypeName,
      feePaidDt: c.feePaidDt, issueReason: c.issueReason, remark: c.remark,
    }));
    closePanel();
    render(id);
  }

  /** 인원 저장 payload 의 cards[] — 화면 표시용 이름 필드는 빼고 보낸다. */
  function get(id) {
    return state[id].rows.map((c) => ({
      cardId: c.cardId, cardNo: c.cardNo, biostarCardId: c.biostarCardId,
      cardName: c.cardName, cardStatus: c.cardStatus, passType: c.passType,
      feePaidDt: c.feePaidDt || null, issueReason: c.issueReason || null, remark: c.remark || null,
    }));
  }

  function closePanel() { $('cardModal').classList.remove('open'); }

  /** idx 가 없으면 추가, 있으면 그 행을 보여준다(카드번호는 실물 카드라 수정 불가). */
  function openPanel(id, idx) {
    const s = state[id];
    const c = idx == null ? {} : s.rows[idx];
    s.editIdx = idx == null ? null : idx;
    $('cardModalTitle').textContent = idx == null ? '카드 추가' : '카드 정보';
    $('cardTypeName').value = s.cardTypeName || '';
    $('cardNo').value = c.cardNo || '';
    $('cardNo').readOnly = idx != null;
    $('btnCardScan').style.display = idx == null ? '' : 'none';
    $('btnCardAssign').style.display = idx == null ? '' : 'none';
    $('passType').value = c.passType || '';
    $('passTypeName').value = c.passTypeName || '';
    $('cardName').value = c.cardName || '';
    $('cardStatus').value = c.cardStatus || '';
    $('cardStatusName').value = c.cardStatusName || '';
    $('cardFeePaidDt').value = c.feePaidDt || '';
    $('cardIssueReason').value = c.issueReason || '';
    $('cardRemark').value = c.remark || '';
    $('cardModal').classList.add('open');
  }

  /** 화면 입력값 → 목록 행(카드번호·BiostarX 식별자 제외). */
  function inputs() {
    return {
      passType: $('passType').value || null, passTypeName: $('passTypeName').value,
      cardName: $('cardName').value.trim() || null,
      cardStatus: $('cardStatus').value || null, cardStatusName: $('cardStatusName').value,
      feePaidDt: $('cardFeePaidDt').value || null,
      issueReason: $('cardIssueReason').value.trim() || null,
      remark: $('cardRemark').value.trim() || null,
    };
  }

  // ---- 미할당 카드 선택 팝업(할당하기) ----
  let picked = null; // 팝업에서 고른 카드

  async function loadPicker(id) {
    const keyword = encodeURIComponent($('cardPickerKeyword').value.trim());
    const rows = (await api.get(`${state[id].baseUrl}/card/unassigned?keyword=${keyword}`)) || [];
    picked = null;
    $('cardPickerBody').innerHTML = rows.length
      ? rows.map((c, i) => `
        <tr class="row-click card-pick-row" data-idx="${i}">
          <td><input type="radio" name="cardPick" value="${i}"/></td>
          <td>${esc(c.biostarCardValue)}</td>
          <td>${esc(c.passTypeName)}</td>
          <td style="text-align:left">${esc(c.cardName)}</td>
          <td>${esc(c.cardStatusName)}</td>
        </tr>`).join('')
      : `<tr><td colspan="5" class="empty">${keyword
        ? '검색 결과가 없습니다.'
        : '할당할 수 있는 카드가 없습니다. (회수된 카드가 없습니다)'}</td></tr>`;
    $('cardPickerBody').dataset.rows = JSON.stringify(rows);
  }

  function openPicker(id) {
    $('cardPickerKeyword').value = '';
    $('cardPickerModal').classList.add('open');
    loadPicker(id);
  }
  function closePicker() { $('cardPickerModal').classList.remove('open'); }

  // 선택 → 카드번호와 기존 정보(패스구분·명칭·상태)를 입력칸에 채운다
  function applyPicked() {
    if (!picked) { toast.warning('카드를 선택하세요.'); return; }
    $('cardNo').value = picked.biostarCardValue || '';
    if (picked.passType) { $('passType').value = picked.passType; $('passTypeName').value = picked.passTypeName || ''; }
    if (picked.cardName) $('cardName').value = picked.cardName;
    if (picked.cardStatus) { $('cardStatus').value = picked.cardStatus; $('cardStatusName').value = picked.cardStatusName || ''; }
    closePicker();
  }

  function bindPicker(id) {
    $('btnCardAssign').addEventListener('click', () => openPicker(id));
    $('cardPickerKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadPicker(id); });
    $('cardPickerBody').addEventListener('click', (e) => {
      const row = e.target.closest('.card-pick-row');
      if (!row) return;
      row.querySelector('input[type="radio"]').checked = true;
      picked = JSON.parse($('cardPickerBody').dataset.rows)[Number(row.dataset.idx)];
    });
    $('cardPickerOk').addEventListener('click', applyPicked);
    $('cardPickerCancel').addEventListener('click', closePicker);
    $('cardPickerClose').addEventListener('click', closePicker);
  }

  /** 필수값 검사 — 서버(CardService)와 같은 기준. 통과하면 null. */
  function missingRequired() {
    const found = [
      [$('cardNo').value.trim(), '카드번호'], [$('cardTypeName').value, '카드구분'],
      [$('passType').value, '패스구분'], [$('cardName').value.trim(), '카드명칭'],
      [$('cardStatus').value, '카드상태'],
    ].find(([v]) => !v);
    return found ? found[1] : null;
  }

  // 확인 — 새 카드면 BiostarX 등록(즉시) 후 목록에 추가, 기존 카드면 입력값만 갱신
  async function confirmCard(id) {
    const s = state[id];
    const missing = missingRequired();
    if (missing) { toast.warning(`${missing}은(는) 필수입니다.`); return; }
    if (s.editIdx != null) {
      Object.assign(s.rows[s.editIdx], inputs());
      render(id);
      closePanel();
      return;
    }
    const cardNo = $('cardNo').value.trim();
    if (s.rows.some((c) => c.cardNo === cardNo)) {
      toast.warning('이미 목록에 있는 카드번호입니다.'); return;
    }
    const res = await api.post(s.baseUrl + '/card/register', { cardNo });
    if (!res || !res.success) { toast.error((res && res.message) || '카드 등록에 실패했습니다.'); return; }
    s.rows.push(Object.assign(
      { cardId: null, cardNo: res.cardNo, biostarCardId: res.biostarCardId }, inputs()));
    render(id);
    closePanel();
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
    state[id] = {
      baseUrl: opts.baseUrl, cardTypeName: opts.cardTypeName,
      print: opts.print || null, rows: [], editIdx: null,
    };
    const box = document.getElementById(id);
    box.querySelector('.card-list-add').addEventListener('click', () => openPanel(id, null));
    box.addEventListener('click', (e) => {
      const prt = e.target.closest('.card-list-print');
      if (prt) { state[id].print(state[id].rows[Number(prt.dataset.idx)]); return; }
      const del = e.target.closest('.card-list-del');
      if (del) {
        state[id].rows.splice(Number(del.dataset.idx), 1); // 인원 저장 시 회수(미배정)된다
        closePanel();
        render(id);
        return;
      }
      const row = e.target.closest('.card-list-row');
      if (row) openPanel(id, Number(row.dataset.idx));
    });
    $('cardNo').addEventListener('input', (e) => { e.target.value = e.target.value.replace(/\D/g, ''); });
    $('btnCardScan').addEventListener('click', () => scan(id));
    bindPicker(id);
    $('cardModalOk').addEventListener('click', () => confirmCard(id));
    $('cardModalCancel').addEventListener('click', closePanel);
    $('cardModalClose').addEventListener('click', closePanel);
    $('passTypeName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'PT', cmmName: '패스구분' });
      if (sel) { $('passType').value = sel.codeId; $('passTypeName').value = sel.codeName; }
    });
    $('cardStatusName').addEventListener('click', async () => {
      const sel = await codePicker.open({ cmmId: 'CS', cmmName: '카드상태' });
      if (sel) { $('cardStatus').value = sel.codeId; $('cardStatusName').value = sel.codeName; }
    });
    render(id);
  }

  return { init, set, get };
})();
