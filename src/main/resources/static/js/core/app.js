/* 공통 뼈대: AJAX fetch 래퍼. 표준 응답(ApiResponse) 처리. (docs/frontend.md) */
window.api = (function () {
  /**
   * @param cfg.quiet true 면 실패해도 토스트를 띄우지 않고 null 을 돌려준다(예외도 삼킨다).
   *   본 동작에 곁들이는 <b>부가 조회</b>에만 쓴다 — 실패가 본 동작의 실패처럼 보이면 안 된다.
   *   (예: 카드 스캔 뒤 기존 카드 정보 조회 — 못 읽어도 번호는 읽은 것이다)
   */
  async function request(method, url, body, cfg) {
    const quiet = !!(cfg && cfg.quiet);
    const opts = {
      method,
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    if (window.busy) window.busy.start(); // 처리 중 표시 + 중복 클릭 차단(짧은 요청은 표시 안 됨)
    let res;
    try {
      res = await fetch(url, opts);
    } finally {
      if (window.busy) window.busy.end();
    }
    if (res.status === 401) {
      location.href = '/login';
      throw new Error('unauthorized');
    }
    const json = await res.json().catch(() => null);
    if (!res.ok || (json && json.success === false)) {
      const msg = (json && json.message) || '처리 중 오류가 발생했습니다.';
      if (quiet) return null;
      notify('error', msg);
      throw new Error(msg);
    }
    // 서버가 성공 메시지를 담아주면 자동으로 성공 토스트(시스템 return 문구)
    if (json && json.message) notify('success', json.message);
    return json ? json.data : null;
  }

  // 토스트가 로드돼 있으면 토스트로, 아니면 alert 폴백
  function notify(type, msg) {
    if (window.toast && typeof window.toast[type] === 'function') window.toast[type](msg);
    else alert(msg);
  }

  return {
    get: (url, cfg) => request('GET', url, undefined, cfg),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    del: (url, body) => request('DELETE', url, body), // body 는 일괄삭제(ID 목록) 등에만 사용
  };
})();
