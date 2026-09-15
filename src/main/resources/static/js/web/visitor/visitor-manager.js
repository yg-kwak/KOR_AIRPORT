/* 임시인원등록·장기출입등록 — 인솔자 탭 전부(목록 + 선택 팝업).
   visitor.js 가 400줄 제한(code-lint)에 닿아 분리했다. '인솔자'는 그 화면 안에서 스스로 닫히는 일이라
   떼어내기 좋다 — 목록을 여기가 쥐고, 저장할 값만 visitor.js 가 get() 으로 가져간다.

   visitManagers.init('{후보목록URL}')  화면당 1회(버튼·팝업 이벤트를 묶는다)
   visitManagers.reset()               등록 모달을 열 때 비운다
   visitManagers.set(rows)             상세를 불러올 때 채운다
   visitManagers.get()                 저장 payload — [{personId, phone}]
   visitManagers.close()               모달을 닫을 때 팝업도 함께 닫는다   (docs/frontend.md) */
(function () {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  let listUrl = '';
  let managers = []; // [{personId, personName, affiliation, phone}]
  let rows = []; // 팝업 검색 결과
  let picked = null;

  /* 연락처는 정규인원에서 당겨오지 않는다 — 같은 사람이라도 방문마다 연락 받을 번호가 다르다.
     소속은 반대로 그 사람의 '지금' 값이라 표기만 하고 방문에는 저장하지 않는다. */
  function render() {
    if ($('mgrCount')) $('mgrCount').textContent = `( ${managers.length} )`;
    $('mgrBody').innerHTML = managers.length
      ? managers.map((m, i) => `<tr><td>${esc(m.personId)}</td><td>${esc(m.personName)}</td>
          <td>${esc(m.affiliation) || '-'}</td>
          <td><input type="text" class="input w-180" data-act="mgr-phone" data-idx="${i}" value="${esc(m.phone || '')}" placeholder="연락처" autocomplete="off"/></td>
          <td><button class="btn btn-sm btn-danger" data-act="mgr-del" data-idx="${i}">제거</button></td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">인솔자가 없습니다.</td></tr>';
  }

  function open() { picked = null; $('mgrModal').classList.add('open'); load(); }
  function close() { $('mgrModal').classList.remove('open'); }

  /* 소속을 함께 보여준다 — 성명 완전일치로 찾으므로 동명이인이 나란히 나오는 일이 흔하고,
     그때 누구를 고를지 가릴 단서가 소속뿐이다(키오스크 인솔자 검색과 같은 규칙). */
  async function load() {
    const kw = encodeURIComponent($('mgrKeyword').value.trim());
    rows = (await window.api.get(listUrl + '?keyword=' + kw)) || [];
    $('mgrPickBody').innerHTML = rows.length
      ? rows.map((p, i) => `<tr class="row-click mgr-row" data-idx="${i}">
          <td><input type="radio" name="mp" value="${i}"/></td><td>${esc(p.personId)}</td>
          <td style="text-align:left">${esc(p.personName)}</td>
          <td style="text-align:left">${esc(p.affiliation) || '-'}</td></tr>`).join('')
      : '<tr><td colspan="4" class="empty">정규인원이 없습니다.</td></tr>';
  }

  /** [확인] — 고른 사람을 목록에 더한다. 고르지 않았으면 false(팝업을 열어 둔다). */
  function pick() {
    if (!picked) return false;
    if (managers.some((m) => m.personId === picked.personId)) {
      window.toast.warning('이미 추가된 인솔자입니다.');
      return true; // 팝업은 닫는다 — 같은 사람을 두 번 고른 것뿐이다
    }
    managers.push({ personId: picked.personId, personName: picked.personName,
      affiliation: picked.affiliation, phone: '' });
    render();
    return true;
  }

  window.visitManagers = {
    init(url) {
      listUrl = url;
      $('btnAddMgr').addEventListener('click', open);
      $('mgrBody').addEventListener('click', (e) => {
        const b = e.target.closest('button[data-act="mgr-del"]');
        if (b) { managers.splice(b.dataset.idx, 1); render(); }
      });
      // 연락처는 치는 대로 배열에 담는다 — 다시 그릴 때 사라지지 않게
      $('mgrBody').addEventListener('input', (e) => {
        const ph = e.target.closest('input[data-act="mgr-phone"]');
        if (ph && managers[ph.dataset.idx]) managers[ph.dataset.idx].phone = ph.value;
      });
      $('mgrSearch').addEventListener('click', load);
      $('mgrKeyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') load(); });
      $('mgrPickBody').addEventListener('click', (e) => {
        const row = e.target.closest('.mgr-row');
        if (!row) return;
        picked = rows[Number(row.dataset.idx)] || null;
        row.querySelector('input[type=radio]').checked = true;
      });
      $('mgrOk').addEventListener('click', () => (pick() ? close() : window.toast.warning('인원을 선택하세요.')));
      $('mgrCancel').addEventListener('click', close);
      $('mgrClose').addEventListener('click', close);
    },
    reset() { managers = []; render(); },
    set(list) {
      managers = (list || []).map((m) => ({ personId: m.personId, personName: m.personName || '',
        affiliation: m.affiliation || '', phone: m.phone || '' }));
      render();
    },
    /** 저장 payload — 소속은 표기 전용이라 넘기지 않는다(정규인원이 원천). */
    get() { return managers.map((m) => ({ personId: m.personId, phone: (m.phone || '').trim() })); },
    close,
  };
})();
