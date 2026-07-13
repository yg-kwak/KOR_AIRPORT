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

  document.addEventListener('DOMContentLoaded', () => {
    if ($('btnSave')) $('btnSave').addEventListener('click', save);
    $('btnTest').addEventListener('click', test);
  });
})();
