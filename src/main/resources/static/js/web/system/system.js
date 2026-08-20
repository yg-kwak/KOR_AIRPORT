/* 설정관리(tb_system) — 단일 폼 + 저장 + BiostarX 연결 테스트 + 가져오기. 안내는 공통 toast(서버 return 문구). */
(function () {
  const BASE = '/system/system';
  const PERM = window.PAGE_PERM || { canCreate: false };
  const $ = (id) => document.getElementById(id);
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

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
    await withBusy($('btnTest'), '테스트 중...', () => api.post(BASE + '/test', p));
  }

  /* 버튼을 잠그고 라벨을 바꿔 두 번 눌리는 것을 막는다(가져오기는 되돌릴 수 없다). */
  async function withBusy(btn, busyLabel, fn) {
    const label = btn.textContent;
    btn.disabled = true;
    btn.textContent = busyLabel;
    try {
      return await fn();
    } catch (e) {
      return null; /* 실패 토스트는 api 래퍼가 이미 표시 */
    } finally {
      btn.disabled = false;
      btn.textContent = label;
    }
  }

  /* ── 가져오기 대상 선택 ───────────────────────────────── */

  let candidates = []; // 불러온 전체. 검색은 서버를 다시 부르지 않고 여기서 거른다
  const picked = new Set(); // 고른 사용자ID — 검색으로 행이 가려져도 선택은 유지한다
  let outcome = {}; // 미리보기 결과: 사용자ID → 신규|갱신|변경없음
  let details = {}; // 미리보기 결과: 사용자ID → 바뀔 내용(비고 열)

  /* 목록의 '구분' 열이자 필터 값. 미리보기를 돌리기 전에는 등록 여부만 알 수 있고,
     돌린 뒤에는 실제로 무엇이 일어날지(갱신인지 변경없음인지)까지 갈린다. */
  const stateOf = (c) => outcome[c.userId] || (c.registered ? '등록됨' : '신규');

  /* 장비의 카드·얼굴 보유로 거른다. 무엇을 가져올지 고를 때
     "얼굴이 없는 사람만" 같은 식으로 부분만 집어낼 수 있어야 한다. */
  function matchHas(c, has) {
    if (!has) return true;
    if (has === 'card') return c.cardCount > 0;
    if (has === 'nocard') return !c.cardCount;
    if (has === 'face') return c.faceCount > 0;
    if (has === 'noface') return !c.faceCount;
    if (has === 'both') return c.cardCount > 0 && c.faceCount > 0;
    return true;
  }

  function visible() {
    const kw = $('impKeyword').value.trim().toLowerCase();
    const state = $('impStateFilter').value;
    const has = $('impHasFilter').value;
    return candidates.filter((c) => {
      if (state && stateOf(c) !== state) return false;
      if (!matchHas(c, has)) return false;
      if (!kw) return true;
      return (c.userId || '').toLowerCase().includes(kw)
        || (c.userName || '').toLowerCase().includes(kw);
    });
  }

  /* 한 쪽에 그릴 행 수. 현장 장비에 4000명이 넘어 전부 그리면 브라우저가 멈춘다.
     검색·선택은 '보이는 전체'(visible) 기준이라 쪽을 넘겨도 선택은 유지된다. */
  const PAGE_SIZE = 100;
  let page = 1;

  function renderCandidates() {
    const rows = visible();
    const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
    if (page > pages) page = pages;
    const shown = rows.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
    const body = $('impBody');
    if (!shown.length) {
      body.innerHTML = '<tr><td colspan="8" class="empty">대상이 없습니다.</td></tr>';
    } else {
      body.innerHTML = shown.map((c) => `
        <tr>
          <td><input type="checkbox" class="imp-pick" value="${esc(c.userId)}"
                     ${picked.has(c.userId) ? 'checked' : ''}
                     ${c.importable ? '' : 'disabled'}/></td>
          <td>${esc(c.userId)}</td>
          <td>${esc(c.userName || '-')}</td>
          <td style="text-align:left">${esc(c.companyName || '-')}</td>
          <td>${c.cardCount > 0 ? c.cardCount : '-'}</td>
          <td>${c.faceCount > 0 ? c.faceCount : '-'}</td>
          <td>${esc(stateOf(c))}</td>
          <td style="text-align:left">${esc(details[c.userId] || c.reason || '')}</td>
        </tr>`).join('');
    }
    $('impTotal').textContent =
      `대상 ${candidates.length}명 · 표시 ${rows.length}명 · 선택 ${picked.size}명`;
    $('impPageInfo').textContent = `${page} / ${pages}`;
    $('impPrev').disabled = page <= 1;
    $('impNext').disabled = page >= pages;
    syncCheckAll();
  }

  /* 조건이 바뀌면 첫 쪽부터 — 3쪽을 보다 검색하면 결과가 없어 빈 화면이 된다 */
  function refilter() {
    page = 1;
    renderCandidates();
  }

  /* 전체선택 체크박스는 '보이는 것 중 고를 수 있는 행'만 대변한다. */
  function pickable() {
    return visible().filter((c) => c.importable);
  }

  function syncCheckAll() {
    const list = pickable();
    $('impCheckAll').checked = list.length > 0 && list.every((c) => picked.has(c.userId));
  }

  /* 그룹 목록은 화면을 열 때 한 번만 채운다. 실패해도 '전체'로 쓸 수 있어야 하므로 조용히 넘어간다. */
  async function loadGroups() {
    const sel = $('impGroup');
    if (!sel) return;
    const list = await api.get(BASE + '/import/groups', { quiet: true });
    if (!list || !list.length) return;
    sel.innerHTML = '<option value="">전체</option>'
      + list.map((g) => {
        const label = g.companyName ? `${g.companyName} (${g.groupName})` : `${g.groupName} — 기관 미연결`;
        return `<option value="${g.groupId}">${esc(label)}</option>`;
      }).join('');
  }

  async function loadCandidates() {
    const groupId = $('impGroup') ? $('impGroup').value : '';
    const url = BASE + '/import/candidates' + (groupId ? `?groupId=${encodeURIComponent(groupId)}` : '');
    const list = await withBusy($('btnImportLoad'), '불러오는 중...', () => api.get(url));
    if (!list) return;
    candidates = list;
    picked.clear();
    outcome = {}; // 목록을 새로 받았으니 지난 미리보기 결과는 버린다
    details = {};
    $('importPick').style.display = '';
    $('importResult').style.display = 'none';
    renderCandidates();
    if (!list.length) toast.warning('가져올 수 있는 대상이 없습니다. 발급구분(PTD01)과 기관 매핑을 확인하세요.');
  }

  /* ── 실행 ────────────────────────────────────────────── */

  function importPayload() {
    return {
      userIds: [...picked],
      cards: $('impCards').checked,
      face: $('impFace').checked,
      acGroups: $('impAcGroups').checked,
    };
  }

  /* 목록의 구분 열·필터가 미리보기 결과를 따르도록 표시한다 — 신규/갱신을 나눠 대상자를 찾는다. */
  function applyOutcome(r) {
    outcome = {};
    details = r.details || {};
    (r.newUserIds || []).forEach((id) => { outcome[id] = '신규'; });
    (r.updatedUserIds || []).forEach((id) => { outcome[id] = '갱신'; });
    (r.unchangedUserIds || []).forEach((id) => { outcome[id] = '변경없음'; });
    renderCandidates();
  }

  /* 결과 상자에는 숫자만 둔다 — 수천 명을 나열하면 읽을 수 없다.
     사람별로 무엇이 바뀌는지는 목록의 '비고' 열에 붙고, 신규/갱신은 '구분' 열과 필터로 찾는다. */
  function showResult(r) {
    const box = $('importResult');
    box.textContent = [
      r.preview ? '[미리보기] 실제로 가져오지 않았습니다. 사람별 내용은 목록의 비고 열에 표시됩니다.' : '가져오기 완료',
      `선택 ${r.total}명 · 신규 ${r.imported} · 갱신 ${r.updated} · 변경없음 ${r.unchanged} · 건너뜀 ${r.skipped}`,
      `카드 ${r.cards} · 얼굴 +${r.faces}/-${r.facesRemoved} · 출입권한 ${r.acGroups}`,
    ].join('\n');
    box.style.display = '';
  }

  async function runImport(preview) {
    if (!picked.size) { toast.warning('가져올 사용자를 선택해주세요.'); return; }
    if (!preview) {
      const ok = await confirmModal.open({
        title: 'BiostarX 가져오기',
        message: `선택한 ${picked.size}명을 Biostar X 기준으로 맞춥니다. 출입관리시스템에만 있던 카드·출입권한은 사라지고,`
          + ' Biostar X에 얼굴이 없으면 등록사진도 지워집니다. 되돌릴 수 없습니다. 진행할까요?',
        confirmText: '가져오기',
      });
      if (!ok) return;
    }
    const btn = $(preview ? 'btnImportPreview' : 'btnImportRun');
    const r = await withBusy(btn, '처리 중...', () =>
      api.post(BASE + (preview ? '/import/preview' : '/import'), importPayload()));
    if (!r) return;
    showResult(r);
    if (preview) {
      applyOutcome(r); // 목록에서 신규/갱신을 걸러 볼 수 있게 한다
    } else {
      await loadCandidates(); // 반영 뒤에는 등록 여부를 서버에서 다시 읽는다
    }
  }

  function bindPick() {
    $('impBody').addEventListener('change', (e) => {
      const box = e.target.closest('.imp-pick');
      if (!box) return;
      if (box.checked) picked.add(box.value); else picked.delete(box.value);
      $('impTotal').textContent =
        `대상 ${candidates.length}명 · 표시 ${visible().length}명 · 선택 ${picked.size}명`;
      syncCheckAll();
    });
    $('impCheckAll').addEventListener('change', (e) => {
      pickable().forEach((c) => (e.target.checked ? picked.add(c.userId) : picked.delete(c.userId)));
      renderCandidates();
    });
    $('impKeyword').addEventListener('input', refilter);
    $('impStateFilter').addEventListener('change', refilter);
    $('impHasFilter').addEventListener('change', refilter);
    $('impPrev').addEventListener('click', () => { page -= 1; renderCandidates(); });
    $('impNext').addEventListener('click', () => { page += 1; renderCandidates(); });
  }

  function bind() {
    if ($('btnSave')) $('btnSave').addEventListener('click', save); // 권한 없으면 버튼 없음(가드)
    $('btnTest').addEventListener('click', test);
    if (!$('btnImportLoad')) return; // 가져오기 영역 자체가 권한으로 감춰진다
    $('btnImportLoad').addEventListener('click', loadCandidates);
    loadGroups();
    $('btnImportPreview').addEventListener('click', () => runImport(true));
    $('btnImportRun').addEventListener('click', () => runImport(false));
    bindPick();
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
