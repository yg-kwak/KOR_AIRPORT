/* 출입권한 선택 트리 컴포넌트 — 조각 fragments/components/ac-group-tree.html 과 1:1. (docs/frontend.md)
   tb_ac_group 트리를 체크박스로 그린다. biostar_ac_id 가 매핑된 노드만 선택 가능(최상위 구역은 라벨),
   상위를 체크/해제하면 하위 전체에 같은 값이 적용된다. */
window.acGroupTree = (function () {
  const trees = {}; // 컨테이너 id → 트리 데이터
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  function nodesHtml(nodes) {
    return (nodes || []).map((n) => {
      const kids = (n.children || []).length ? `<div class="ac-select-node">${nodesHtml(n.children)}</div>` : '';
      const row = n.biostarAcId != null
        ? `<label class="ac-select-item">
             <input type="checkbox" value="${esc(n.acGroupId)}"/><span>${esc(n.acGroupName)}</span>
           </label>`
        : `<div class="ac-select-item group">${esc(n.acGroupName)}</div>`;
      return `<div class="ac-node-wrap">${row}${kids}</div>`;
    }).join('');
  }

  /** 트리를 다시 그리고 checkedIds 를 체크한다(모달을 열 때마다 호출). */
  function set(id, checkedIds) {
    const box = document.getElementById(id);
    box.innerHTML = (trees[id] || []).length
      ? nodesHtml(trees[id])
      : '<div class="empty">선택 가능한 출입권한이 없습니다. (출입권한관리에서 BiostarX 출입그룹을 매핑하세요)</div>';
    if (!checkedIds || !checkedIds.length) return;
    const want = new Set(checkedIds.map(Number));
    box.querySelectorAll('input[type="checkbox"]').forEach((c) => { c.checked = want.has(Number(c.value)); });
  }

  /** 트리 데이터를 불러와 초기 렌더 + 연쇄 체크 동작을 붙인다(화면당 1회). */
  async function init(id, url) {
    trees[id] = (await api.get(url)) || [];
    set(id, []);
    document.getElementById(id).addEventListener('change', (e) => {
      const cb = e.target.closest('input[type="checkbox"]');
      if (!cb) return;
      const wrap = cb.closest('.ac-node-wrap'); // 상위 체크 → 하위 전체 동일 적용
      if (wrap) wrap.querySelectorAll('input[type="checkbox"]').forEach((c) => { c.checked = cb.checked; });
    });
  }

  /** 선택된 tb_ac_group.ac_group_id 목록. */
  function get(id) {
    return [...document.getElementById(id).querySelectorAll('input[type="checkbox"]:checked')]
      .map((c) => Number(c.value));
  }

  return { init, set, get };
})();
