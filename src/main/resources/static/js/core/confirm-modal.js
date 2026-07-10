/* 공통 확인 모달 (fragments/components/confirm-modal.html 과 한 쌍).
   const ok = await confirmModal.open({ title, message, confirmText });  // true=확인, false=닫기 */
window.confirmModal = (function () {
  let resolver = null;
  const el = (id) => document.getElementById(id);

  function close(result) {
    const m = el('confirmModal');
    if (m) m.classList.remove('open');
    if (resolver) { resolver(result); resolver = null; }
  }

  function open({ title = '확인', message = '', confirmText = '확인' } = {}) {
    el('confirmModalTitle').textContent = title;
    el('confirmModalMessage').textContent = message;
    el('confirmModalOk').textContent = confirmText;
    el('confirmModal').classList.add('open');
    return new Promise((resolve) => { resolver = resolve; });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const m = el('confirmModal');
    if (!m) return; // 화면에 fragment 미포함 시 no-op
    el('confirmModalClose').addEventListener('click', () => close(false));
    el('confirmModalOk').addEventListener('click', () => close(true));
    m.addEventListener('click', (e) => { if (e.target === m) close(false); }); // 오버레이 클릭 닫기
  });

  return { open };
})();
