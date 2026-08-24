/* 보호구역 임시출입허가 신청서 출력 — 서버가 준 값으로 양식을 그려 브라우저 인쇄에 넘긴다.
   프린터는 클라이언트 PC 라 서버에서 PDF 를 만들지 않는다(카드 출력과 같은 방식).
   확인자·용무확인 칸은 시스템이 보관하지 않는 값이라 비운다 — 인쇄 후 손으로 적는다. */
window.permitPrint = (function () {
  const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
  const dt = (v) => (v == null ? '' : String(v).replace('T', ' '));

  /* "2026-07-02" → "2026년  7월  2일". 양식이 한글 날짜라 그대로 맞춘다. */
  function korDate(v) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v || '');
    if (!m) return '&nbsp;';
    return `${m[1]}년 &nbsp;${Number(m[2])}월 &nbsp;${Number(m[3])}일`;
  }

  /* 여러 명이면 행을 늘린다 — perRow 명씩 끊어 빈 칸은 그대로 비워 둔다. */
  function chunk(list, perRow) {
    const rows = [];
    for (let i = 0; i < list.length; i += perRow) rows.push(list.slice(i, i + perRow));
    return rows.length ? rows : [[]];
  }

  const cell = (v) => `<td>${esc(v) || '&nbsp;'}</td>`;

  /* 출입자 — 한 행에 2명(성명/생년월일/출입증번호=카드명칭/소속).
     주소는 입력란이 없어 소속만 적는다 — 빈 주소 자리에 '/' 를 남기면 지저분하다. */
  function visitorRows(list) {
    return chunk(list, 2).map((pair) => {
      const cells = [0, 1].map((i) => {
        const p = pair[i];
        return p ? cell(p.name) + cell(p.birthDate) + cell(p.cardName) + cell(p.affiliation)
          : '<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>';
      }).join('');
      return `<tr>${cells}</tr>`;
    }).join('');
  }

  /* 차량 — 한 행에 1대(출입자소속/차량번호/차종/차량출입증번호).
     운전자성명·생년월일·주소 칸은 뺐다 — 시스템이 보관하지 않는 값이라 늘 빈 칸으로 나갔다.
     출입자소속은 방문(그룹)의 업체명이다. */
  function carRows(list) {
    return chunk(list, 1).map((one) => {
      const c = one[0];
      if (!c) return '<tr><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>';
      return `<tr>${cell(c.affiliation)}${cell(c.carNo)}${cell(c.carTypeName)}${cell(c.cardName)}</tr>`;
    }).join('');
  }

  /* 인솔자 — 한 행에 2명(소속/성명/출입증번호/연락처) */
  function managerRows(list) {
    return chunk(list, 2).map((pair) => {
      const cells = [0, 1].map((i) => {
        const m = pair[i];
        return m ? cell(m.company) + cell(m.name) + cell(m.cardName) + cell(m.phone)
          : '<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td>';
      }).join('');
      return `<tr>${cells}</tr>`;
    }).join('');
  }

  function html(d) {
    return `
<div class="permit-sheet">
  <h1 class="permit-title">청주국제공항 내 보호구역1일 임시출입허가 신청서</h1>
  <table class="permit-table">
    <tr>
      <th rowspan="2" class="w-18">출입시간</th>
      <th colspan="2">출입구역</th>
      <th rowspan="3" class="w-14">출입자의<br/> 용무확인</th>
      <th rowspan="2" class="w-16">확인자소속</th>
      <th rowspan="2" class="w-14">성명</th>
    </tr>
    <!-- 둘째 줄은 '출입구역' 아래 차량/인원 뿐이다. 용무확인·확인자소속·성명은 위에서 rowspan 으로
         내려와 여기에 칸이 없다 — 빈 칸을 두면 적을 곳이 두 개로 갈려 보인다. -->
    <tr><th class="w-11">차량</th><th class="w-16">인원</th></tr>
    <tr class="permit-tall">
      <td class="permit-period">${esc(dt(d.accessStart))}<br/>~ ${esc(dt(d.accessEnd))}</td>
      <td>${esc(d.carAreas)}</td><td>${esc(d.personAreas)}</td>
      <!-- '출입자의 용무확인' 은 위에서 rowspan 으로 내려와 이 줄에 칸이 없다 —
           칸을 나누지 않아야 확인자가 라벨 아래에 그대로 적을 수 있다 -->
      <td>&nbsp;</td><td>&nbsp;</td>
    </tr>
    <tr><th>출입목적</th><td colspan="5" class="permit-left">${esc(d.purpose)}</td></tr>
  </table>

  <table class="permit-table">
    <tr>
      <th>출입자<br/>성명</th><th>생년월일</th><th>출입증<br/>번호</th><th>출입자소속</th>
      <th>출입자<br/>성명</th><th>생년월일</th><th>출입증<br/>번호</th><th>출입자소속</th>
    </tr>
    ${visitorRows(d.visitors || [])}
  </table>

  <table class="permit-table">
    <tr>
      <th>출입자소속</th><th>차량번호</th><th>차종</th><th>차량출입증<br/>번호</th>
    </tr>
    ${carRows(d.cars || [])}
  </table>

  <table class="permit-table">
    <tr>
      <th>인솔자 소속</th><th>인솔자<br/>성명</th><th>출입증<br/>번호</th><th>연락처</th>
      <th>인솔자 소속</th><th>인솔자<br/>성명</th><th>출입증<br/>번호</th><th>연락처</th>
    </tr>
    ${managerRows(d.managers || [])}
  </table>

  <p class="permit-body">위와 같이 보호구역출입증규정 제23조 제5항, 제24조 제3항의 규정에 의거 청주국제공항내
    보호구역 임시출입을 신청하오니 허가하여 주시기 바랍니다.</p>
  <p class="permit-center">${korDate(d.applyDate)}</p>
  <p class="permit-center">신청인 &nbsp;&nbsp;${esc(d.applicantCompany)} &nbsp;&nbsp;성명 &nbsp;&nbsp;${esc(d.applicantName)} &nbsp;&nbsp;(인)</p>

  <h2 class="permit-approve">위와 같이 보호구역 임시출입을 허가하였음을 증명함</h2>
  <p class="permit-center">${korDate(d.applyDate)}</p>
  <p class="permit-right">한 국 공 항 공 사 청 주 공 항 장</p>
  <p class="permit-right">출 입 증 담 당 관</p>
</div>`;
  }

  /** 신청서를 그려 인쇄 대화상자를 연다. */
  async function open(base, visitNo) {
    const d = await api.get(`${base}/permit?visitNo=${visitNo}`);
    if (!d) return;
    document.getElementById('permitPrintArea').innerHTML = html(d);
    window.print();
  }

  /* 버튼 연결은 여기서 한다 — 인쇄는 이 모듈의 일이고, visitor.js 는 이미 꽉 찼다(JS 400줄 제한).
     노출 여부(신청 상태면 숨김)는 상세를 아는 visitor.js 가 정한다. */
  document.addEventListener('DOMContentLoaded', () => {
    const btn = document.getElementById('btnPermit');
    if (!btn) return;
    btn.addEventListener('click', () => {
      const base = (window.VISIT_CFG || {}).base || '/visitor/visitor';
      open(base, Number(document.getElementById('visitNo').value));
    });
  });

  return { open };
})();
