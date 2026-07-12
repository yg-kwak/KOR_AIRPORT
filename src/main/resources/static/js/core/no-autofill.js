/* 모든 입력창에서 브라우저 자동완성/입력이력 드롭다운 노출 방지 (전 페이지 공통).
   서버렌더 입력은 물론, 이후 동적으로 추가되는 입력(모달 등)도 처리. docs/frontend.md */
(function () {
  var SKIP = { checkbox: 1, radio: 1, hidden: 1, file: 1, range: 1, color: 1, submit: 1, button: 1 };

  function hardenEl(el) {
    // 템플릿에서 autocomplete 를 명시한 입력(로그인 자격증명 등)은 개발자 의도이므로 건드리지 않는다.
    if (el.hasAttribute('autocomplete')) return;
    if (el.tagName === 'FORM') {
      el.setAttribute('autocomplete', 'off');
      return;
    }
    var type = (el.getAttribute('type') || 'text').toLowerCase();
    if (SKIP[type]) return;
    el.setAttribute('autocomplete', 'off');
    el.setAttribute('autocorrect', 'off');
    el.setAttribute('autocapitalize', 'off');
    el.setAttribute('spellcheck', 'false');
  }

  function harden(root) {
    var scope = root && root.querySelectorAll ? root : document;
    scope.querySelectorAll('form, input, textarea').forEach(hardenEl);
  }

  document.addEventListener('DOMContentLoaded', function () {
    harden(document);
    // 동적으로 추가되는 입력(모달·리스트 등)도 커버
    new MutationObserver(function (muts) {
      muts.forEach(function (m) {
        m.addedNodes.forEach(function (n) {
          if (n.nodeType !== 1) return;
          if (n.matches && n.matches('form, input, textarea')) hardenEl(n);
          if (n.querySelectorAll) harden(n);
        });
      });
    }).observe(document.body, { childList: true, subtree: true });
  });
})();
