/* 공통 입력 모달 (fragments/components/prompt-modal.html 과 한 쌍).
   const v = await promptModal.open({ title, label, placeholder, confirmText });
   // v = 입력값(공백 불가) | null(닫기/취소) */
window.promptModal = (function () {
  let resolver = null;
  const el = (id) => document.getElementById(id);

  function close(value) {
    const m = el('promptModal');
    if (m) m.classList.remove('open');
    if (resolver) { resolver(value); resolver = null; }
  }

  function open({ title = '입력', label = '', placeholder = '', confirmText = '확인' } = {}) {
    el('promptModalTitle').textContent = title;
    el('promptModalLabel').textContent = label;
    const input = el('promptModalInput');
    input.value = '';
    input.placeholder = placeholder;
    el('promptModalOk').textContent = confirmText;
    el('promptModal').classList.add('open');
    setTimeout(() => input.focus(), 0);
    return new Promise((resolve) => { resolver = resolve; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const m = el('promptModal');
    if (!m) return; // 화면에 fragment 미포함 시 no-op
    el('promptModalClose').addEventListener('click', () => close(null));
    m.addEventListener('click', (e) => { if (e.target === m) close(null); });
    el('promptModalOk').addEventListener('click', () => {
      const v = el('promptModalInput').value.trim();
      if (!v) { el('promptModalInput').focus(); return; } // 필수 입력
      close(v);
    });
    el('promptModalInput').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') el('promptModalOk').click();
    });
  });

  return { open };
})();
