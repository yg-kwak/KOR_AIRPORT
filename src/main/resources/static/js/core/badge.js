/* 상태 배지 마크업 생성 (CSS: style.css `.badge`).
   색만으로 구분하지 않는다 — 톤마다 점 모양·테두리도 달라진다(●유효 / ■완료 / ○점선=없음).
   badge.of('사용', 'success') / badge.none('미발급') / badge.visitStatus('VS03', '입실 중') */
window.badge = (function () {
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));

  // 방문상태(tb_common VS) → 톤. 신청=처리 대기, 입실 중=유효, 퇴실 완료=완료
  const VISIT_TONE = { VS01: 'warning', VS03: 'success', VS04: 'done' };
  // 카드상태(tb_common CS) → 톤. 정상=유효, 분실=차단(적), 정지=일시 차단(황), 반납=완료, 폐기=무효
  const CARD_TONE = { CS01: 'success', CS02: 'error', CS03: 'done', CS04: 'warning', CS05: 'none' };

  const of = (text, tone) => `<span class="badge badge-${tone || 'done'}">${esc(text)}</span>`;

  return {
    of,
    none: (text) => of(text, 'none'),
    count: (n, unit) => of(String(n) + (unit || ''), 'info'),
    visitStatus: (code, name) => of(name || code, VISIT_TONE[code] || 'done'),
    cardStatus: (code, name) => of(name || code, CARD_TONE[code] || 'done'),
  };
})();
