/* 엑셀 일괄등록 모달 컴포넌트 — 조각 fragments/components/excel-import-modal 과 1:1. (docs/frontend.md)
   화면 스크립트에서 excelImport.open({baseUrl, hint, onDone}) 로 연다. 양식 다운로드/파일 선택/업로드를
   한 모달에서 처리한다. 파일은 선택 즉시 보내지 않고 [업로드] 를 눌러야 전송(오작동 방지). */
window.excelImport = (function () {
  const $ = (id) => document.getElementById(id);
  let cfg = null; // { baseUrl, hint, onDone }
  let picked = null;

  function setName(file) {
    $('excelFileName').textContent = file ? file.name : '선택된 파일 없음';
    $('excelFileName').classList.toggle('empty-text', !file);
  }

  /** 모달을 연다. baseUrl 은 {baseUrl}/excel/template · /excel/import 로 쓰인다. */
  function open(opts) {
    cfg = opts || {};
    picked = null;
    $('excelFile').value = '';
    setName(null);
    $('importHint').innerHTML = cfg.hint || '';
    $('importModal').classList.add('open');
  }
  function close() { $('importModal').classList.remove('open'); }

  async function upload() {
    if (!picked) { toast.warning('업로드할 파일을 선택하세요.'); return; }
    const fd = new FormData();
    fd.append('file', picked);
    const res = await fetch(cfg.baseUrl + '/excel/import', {
      method: 'POST', headers: { 'X-Requested-With': 'XMLHttpRequest' }, body: fd,
    });
    const json = await res.json().catch(() => null);
    if (!res.ok || !json || json.success === false) {
      toast.error((json && json.message) || '엑셀 업로드에 실패했습니다.');
      return;
    }
    const r = json.data;
    if (r.fail === 0) {
      toast.success(`등록 ${r.success}건 완료`);
    } else {
      toast.warning(`등록 ${r.success}건 / 실패 ${r.fail}건 — ${r.errors.slice(0, 5).join(' · ')}` +
        (r.errors.length > 5 ? ` 외 ${r.errors.length - 5}건` : ''));
    }
    if (r.success > 0) {
      close();
      if (cfg.onDone) cfg.onDone();
    }
  }

  function bind() {
    if (!$('importModal')) return; // 조각을 포함하지 않은 화면
    $('btnTemplate').addEventListener('click', () => { location.href = cfg.baseUrl + '/excel/template'; });
    $('btnPickExcel').addEventListener('click', () => $('excelFile').click());
    $('excelFile').addEventListener('change', (e) => {
      picked = e.target.files && e.target.files[0];
      setName(picked);
    });
    $('btnDoImport').addEventListener('click', upload);
    $('importCancel').addEventListener('click', close);
    $('importClose').addEventListener('click', close);
  }

  document.addEventListener('DOMContentLoaded', bind);
  return { open };
})();
