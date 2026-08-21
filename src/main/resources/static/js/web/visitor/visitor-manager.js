/* 임시인원등록 — 인솔자 선택 팝업.
   visitor.js 가 400줄 제한(code-lint)에 닿아 분리했다. 팝업은 '정규인원 한 명을 고른다'는
   한 가지 일만 해서 떼어내기 좋다. 고른 결과만 돌려주고, 목록(managers)은 visitor.js 가 쥔다. */
(function () {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const esc = window.esc || ((v) => String(v == null ? '' : v));

  window.visitManagerPicker = {
    /**
     * 팝업을 열고 고른 사람을 onPick 으로 돌려준다.
     * @param {string} listUrl 인솔자 후보 목록 URL(뒤에 keyword 를 붙인다)
     * @param {function} onPick {personId, personName} 을 받는다
     */
    open(listUrl, onPick) {
      this._listUrl = listUrl;
      this._onPick = onPick;
      this._picked = null;
      $('mgrModal').classList.add('open');
      this.load();
    },

    close() {
      $('mgrModal').classList.remove('open');
    },

    async load() {
      const kw = encodeURIComponent($('mgrKeyword').value.trim());
      const rows = (await window.api.get(this._listUrl + '?keyword=' + kw)) || [];
      $('mgrPickBody').innerHTML = rows.length
        ? rows.map((p, i) => `<tr class="row-click mgr-row" data-idx="${i}">
            <td><input type="radio" name="mp" value="${i}"/></td><td>${esc(p.personId)}</td>
            <td style="text-align:left">${esc(p.personName)}</td></tr>`).join('')
        : '<tr><td colspan="3" class="empty">정규인원이 없습니다.</td></tr>';
      this._rows = rows;
    },

    /** 목록에서 한 행을 고른다(라디오 표시까지). */
    select(idx) {
      this._picked = this._rows[idx] || null;
      const radio = $('mgrPickBody').querySelector(`input[name="mp"][value="${idx}"]`);
      if (radio) radio.checked = true;
    },

    /** [확인] — 고른 사람이 없으면 아무 일도 하지 않는다. */
    confirm() {
      if (!this._picked) return false;
      this._onPick(this._picked);
      this.close();
      return true;
    },
  };
})();
