/* 공통 뼈대: AJAX fetch 래퍼. 표준 응답(ApiResponse) 처리. (docs/frontend.md) */
window.api = (function () {
  async function request(method, url, body) {
    const opts = {
      method,
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    if (res.status === 401) {
      location.href = '/login';
      throw new Error('unauthorized');
    }
    const json = await res.json().catch(() => null);
    if (!res.ok || (json && json.success === false)) {
      const msg = (json && json.message) || '처리 중 오류가 발생했습니다.';
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
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    del: (url) => request('DELETE', url),
  };
})();
