/* 엑셀 일괄등록 모달 컴포넌트 — 조각 fragments/components/excel-import-modal 과 1:1. (docs/frontend.md)
   화면 스크립트에서 excelImport.open({baseUrl, hint, onDone}) 로 연다. 양식 다운로드/파일 선택/업로드를
   한 모달에서 처리한다. 파일은 선택 즉시 보내지 않고 [업로드] 를 눌러야 전송(오작동 방지). */
window.excelImport = (function () {
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
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
    // hint 는 문자열 또는 배열(배열이면 항목별 불릿 목록으로 들여쓰기 렌더)
    $('importHint').innerHTML = Array.isArray(cfg.hint)
      ? '<ul class="hint-list">' + cfg.hint.map((s) => `<li>${s}</li>`).join('') + '</ul>'
      : (cfg.hint || '');
    clearResult();
    $('importModal').classList.add('open');
  }
  function close() { $('importModal').classList.remove('open'); }

  async function upload() {
    if (!picked) { toast.warning('업로드할 파일을 선택하세요.'); return; }
    const fd = new FormData();
    fd.append('file', picked);
    const res = await window.busy.wrap(fetch(cfg.baseUrl + '/excel/import', {
      method: 'POST', headers: { 'X-Requested-With': 'XMLHttpRequest' }, body: fd,
    })); // 행 수가 많으면 오래 걸린다 — 진행 중임을 알린다
    const json = await res.json().catch(() => null);
    if (!res.ok || !json || json.success === false) {
      toast.error((json && json.message) || '엑셀 업로드에 실패했습니다.');
      return;
    }
    const r = json.data;
    if (r.fail === 0) {
      toast.success(`등록 ${r.success}건 완료`);
      close();
      if (cfg.onDone) cfg.onDone();
      return;
    }
    // 실패가 있으면 모달을 열어 둔 채 사유를 전부 보여준다 — 토스트로 흘리면 어느 행을 고칠지 알 수 없다
    showResult(r);
    toast.warning(`등록 ${r.success}건 / 실패 ${r.fail}건 — 아래 목록을 확인하세요.`);
    if (r.success > 0 && cfg.onDone) cfg.onDone(); // 성공분은 즉시 목록에 반영
  }

  /** 업로드 결과(성공/실패 건수 + 실패 행 전체)를 모달 안에 남긴다. */
  function showResult(r) {
    $('importResultSummary').textContent =
      `등록 ${r.success}건 / 실패 ${r.fail}건 — 아래 행을 고쳐 다시 업로드하세요.`;
    $('importResultErrors').innerHTML = (r.errors || [])
      .map((e) => `<li>${esc(e)}</li>`).join('');
    $('importResult').style.display = '';
  }

  function clearResult() {
    $('importResult').style.display = 'none';
    $('importResultErrors').innerHTML = '';
  }

  function bind() {
    if (!$('importModal')) return; // 조각을 포함하지 않은 화면
    $('btnTemplate').addEventListener('click', () => { location.href = cfg.baseUrl + '/excel/template'; });
    $('btnPickExcel').addEventListener('click', () => $('excelFile').click());
    $('excelFile').addEventListener('change', (e) => {
      picked = e.target.files && e.target.files[0];
      setName(picked);
      clearResult(); // 새 파일을 고르면 이전 결과는 지운다
    });
    $('btnDoImport').addEventListener('click', upload);
    $('importCancel').addEventListener('click', close);
    $('importClose').addEventListener('click', close);
  }

  document.addEventListener('DOMContentLoaded', bind);
  return { open };
})();
