/* 첨부파일 필드 컴포넌트 — 조각 fragments/components/file-field.html 과 1:1. (docs/frontend.md)
   파일을 BASE64 로 읽어 두었다가 화면 폼과 함께 전송한다(별도 업로드 요청 없음 → 저장 전 취소해도 서버에 남지 않는다).
   다운로드 버튼은 '이미 저장된 파일'에만 나타난다(새로 고른 파일은 아직 서버에 없으므로). */
window.fileField = (function () {
  const MAX_BYTES = 5 * 1024 * 1024;
  const store = {}; // fieldId → { name, data(BASE64|null), url(다운로드 URL|null) }

  const box = (id) => document.getElementById(id);
  const part = (id, cls) => box(id).querySelector('.' + cls);

  function toBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  function render(id) {
    const s = store[id] || {};
    const name = part(id, 'file-field-name');
    name.textContent = s.name || '선택된 파일 없음';
    name.classList.toggle('empty-text', !s.name);
    part(id, 'file-field-down').style.display = s.name && s.url && !s.data ? '' : 'none';
    part(id, 'file-field-clear').style.display = s.name ? '' : 'none';
  }

  /** 저장된 파일명·다운로드 URL 을 표시한다(모달 열 때). name 이 없으면 빈 상태. */
  function set(id, name, downloadUrl) {
    store[id] = { name: name || null, data: null, url: downloadUrl || null };
    part(id, 'file-input').value = '';
    render(id);
  }

  /** 폼 전송용 값 — { name, data }. data 가 null 이면 '기존 파일 유지'를 뜻한다. */
  function get(id) {
    const s = store[id] || {};
    return { name: s.name || null, data: s.data || null };
  }

  async function onPick(id, input) {
    const file = input.files && input.files[0];
    if (!file) return;
    if (file.size > MAX_BYTES) {
      toast.warning('첨부파일은 5MB 를 초과할 수 없습니다.');
      input.value = '';
      return;
    }
    store[id] = { name: file.name, data: await toBase64(file), url: null };
    render(id);
  }

  function bindAll() {
    document.querySelectorAll('.file-field').forEach((el) => {
      const id = el.id;
      const input = el.querySelector('.file-input');
      if (!store[id]) set(id, null, null);
      el.querySelector('.file-field-pick').addEventListener('click', () => input.click());
      input.addEventListener('change', () => onPick(id, input));
      el.querySelector('.file-field-down').addEventListener('click', () => {
        const s = store[id];
        if (s && s.url) window.location.href = s.url;
      });
      el.querySelector('.file-field-clear').addEventListener('click', () => set(id, null, null));
    });
  }

  document.addEventListener('DOMContentLoaded', bindAll);
  return { set, get };
})();
