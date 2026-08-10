/* 설정관리(tb_system) — 단일 폼 + 저장 + BiostarX 연결 테스트. 안내는 공통 toast(서버 return 문구). */
(function () {
  const BASE = '/system/system';
  const PERM = window.PAGE_PERM || { canCreate: false };
  const $ = (id) => document.getElementById(id);

  function payload() {
    return {
      biostarIp: $('biostarIp').value.trim(),
      biostarId: $('biostarId').value.trim(),
      biostarPw: $('biostarPw').value, // 공백이면 서버가 기존값 유지/사용
    };
  }

  async function save() {
    if (!PERM.canCreate) return;
    const p = payload();
    if (!p.biostarIp) { toast.warning('BiostarX IP를 입력해주세요.'); return; }
    if (!p.biostarId) { toast.warning('BiostarX ID를 입력해주세요.'); return; }
    await api.post(BASE, p); // 성공 시 서버 메시지 자동 토스트
    $('biostarPw').value = ''; // 저장 후 비밀번호 필드 비움
  }

  async function test() {
    const p = payload();
    if (!p.biostarIp) { toast.warning('BiostarX IP를 입력해주세요.'); return; }
    if (!p.biostarId) { toast.warning('BiostarX ID를 입력해주세요.'); return; }
    const btn = $('btnTest');
    btn.disabled = true;
    const label = btn.textContent;
    btn.textContent = '테스트 중...';
    try {
      await api.post(BASE + '/test', p); // 성공/실패 모두 서버 메시지로 자동 토스트
    } catch (e) {
      /* 실패 토스트는 api 래퍼가 이미 표시 */
    } finally {
      btn.disabled = false;
      btn.textContent = label;
    }
  }

  /* 가져오기 결과를 사람이 읽을 문장으로. 건너뛴 사유가 제일 중요하다 — 왜 안 들어왔는지 모르면 손쓸 수 없다. */
  function showResult(r) {
    const box = $('importResult');
    const head = r.preview ? '[미리보기] 실제로 가져오지 않았습니다.' : '가져오기 완료';
    const lines = [head,
      `장비 ${r.total}명 · 대상 ${r.target}명 · ${r.preview ? '가져올 수 있음' : '가져옴'} ${r.preview ? r.target - r.skipped : r.imported}명 · 건너뜀 ${r.skipped}명`];
    if (!r.preview) lines.push(`카드 ${r.cards} · 얼굴 ${r.faces} · 출입권한 ${r.acGroups}`);
    if (r.skippedReasons && r.skippedReasons.length) {
      lines.push('', '건너뛴 인원:');
      r.skippedReasons.forEach((x) => lines.push('  · ' + x));
    }
    box.textContent = lines.join('\n');
    box.style.display = '';
  }

  async function runImport(preview) {
    const btn = $(preview ? 'btnImportPreview' : 'btnImportRun');
    if (!preview) {
      const ok = await confirmModal.open({
        title: 'BiostarX 가져오기',
        message: '장비의 정규 사용자를 우리 시스템으로 가져옵니다. 장비 데이터는 바뀌지 않습니다. 진행할까요?',
        confirmText: '가져오기',
      });
      if (!ok) return;
    }
    const label = btn.textContent;
    btn.disabled = true; btn.textContent = '처리 중...';
    try {
      const q = `cards=${$('impCards').checked}&face=${$('impFace').checked}&acGroups=${$('impAcGroups').checked}`;
      const r = preview ? await api.get(BASE + '/import/preview') : await api.post(BASE + '/import?' + q);
      if (r) showResult(r);
    } catch (e) {
      /* 실패 토스트는 api 래퍼가 표시 */
    } finally {
      btn.disabled = false; btn.textContent = label;
    }
  }

  function bind() {
    if ($('btnSave')) $('btnSave').addEventListener('click', save); // 권한 없으면 버튼 없음(가드)
    $('btnTest').addEventListener('click', test);
    if ($('btnImportPreview')) $('btnImportPreview').addEventListener('click', () => runImport(true));
    if ($('btnImportRun')) $('btnImportRun').addEventListener('click', () => runImport(false));
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
