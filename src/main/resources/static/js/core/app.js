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
      alert(msg);
      throw new Error(msg);
    }
    return json ? json.data : null;
  }

  return {
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    del: (url) => request('DELETE', url),
  };
})();
