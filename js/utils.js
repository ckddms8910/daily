/* ==========================================================================
 * 유틸리티: 숫자 포맷 (한국식 억/만), SVG 차트 렌더러
 * ========================================================================== */

/* 1,234,567 형태 */
function comma(n) {
  if (n == null || isNaN(n)) return '0';
  return Math.round(n).toLocaleString('ko-KR');
}

/* 큰 금액을 억/만 단위 한글로 요약: 1234567890 -> "12억 3,456만" */
function won(n) {
  if (n == null || isNaN(n)) return '0원';
  const neg = n < 0;
  n = Math.abs(Math.round(n));
  if (n === 0) return '0원';

  const jo = Math.floor(n / 1_0000_0000_0000);
  const eok = Math.floor((n % 1_0000_0000_0000) / 1_0000_0000);
  const man = Math.floor((n % 1_0000_0000) / 1_0000);
  const rest = n % 1_0000;

  const parts = [];
  if (jo) parts.push(jo.toLocaleString('ko-KR') + '조');
  if (eok) parts.push(eok.toLocaleString('ko-KR') + '억');
  if (man) parts.push(man.toLocaleString('ko-KR') + '만');
  if (!jo && !eok && !man && rest) parts.push(rest.toLocaleString('ko-KR'));
  let s = parts.slice(0, 2).join(' ') + '원';
  return (neg ? '-' : '') + s;
}

/* 짧은 축약: 1.2억, 3,400만, 12조 (차트 축/카드용) */
function wonShort(n) {
  if (n == null || isNaN(n)) return '0';
  const neg = n < 0;
  n = Math.abs(n);
  let s;
  if (n >= 1_0000_0000_0000) s = (n / 1_0000_0000_0000).toFixed(1).replace(/\.0$/, '') + '조';
  else if (n >= 1_0000_0000) s = (n / 1_0000_0000).toFixed(1).replace(/\.0$/, '') + '억';
  else if (n >= 1_0000) s = Math.round(n / 1_0000).toLocaleString('ko-KR') + '만';
  else s = Math.round(n).toString();
  return (neg ? '-' : '') + s;
}

function pct(n) {
  if (n == null || isNaN(n)) return '0%';
  return (n >= 0 ? '+' : '') + n.toFixed(1) + '%';
}

/* ---- SVG 라인/영역 차트 ---------------------------------------------------
 * series: [{year, value}]  누적 자산 곡선
 * -------------------------------------------------------------------------- */
function areaChart(series, opts = {}) {
  const W = opts.w || 340, H = opts.h || 180;
  const padL = 8, padR = 8, padT = 16, padB = 22;
  const iw = W - padL - padR, ih = H - padT - padB;
  if (!series.length) return '';

  const xs = series.map((d) => d.year);
  const ys = series.map((d) => d.value);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const maxY = Math.max(...ys, 1);
  const minY = 0;

  const X = (x) => padL + ((x - minX) / (maxX - minX || 1)) * iw;
  const Y = (y) => padT + ih - ((y - minY) / (maxY - minY || 1)) * ih;

  let line = '', area = '';
  series.forEach((d, i) => {
    const px = X(d.year).toFixed(1), py = Y(d.value).toFixed(1);
    line += (i === 0 ? 'M' : 'L') + px + ' ' + py + ' ';
  });
  area = line + `L${X(maxX).toFixed(1)} ${(padT + ih).toFixed(1)} L${X(minX).toFixed(1)} ${(padT + ih).toFixed(1)} Z`;

  // y 그리드 3줄
  let grid = '';
  for (let g = 1; g <= 3; g++) {
    const gy = padT + (ih / 4) * g;
    const gv = maxY * (1 - g / 4);
    grid += `<line x1="${padL}" y1="${gy}" x2="${W - padR}" y2="${gy}" class="grid"/>`;
    grid += `<text x="${padL}" y="${gy - 3}" class="gridlbl">${wonShort(gv)}</text>`;
  }

  // x 라벨 (시작, 중간, 끝, 은퇴)
  const marks = opts.marks || [minX, Math.round((minX + maxX) / 2), maxX];
  let xlbl = '';
  marks.forEach((yr) => {
    if (yr < minX || yr > maxX) return;
    xlbl += `<text x="${X(yr).toFixed(1)}" y="${H - 6}" class="xlbl">${yr}</text>`;
  });

  // 은퇴 마커
  let retire = '';
  if (opts.retireYear && opts.retireYear >= minX && opts.retireYear <= maxX) {
    const rx = X(opts.retireYear).toFixed(1);
    retire = `<line x1="${rx}" y1="${padT}" x2="${rx}" y2="${padT + ih}" class="retire"/>`;
  }

  const color = opts.color || '#4f8cff';
  const gid = 'g' + Math.random().toString(36).slice(2, 7);
  return `<svg viewBox="0 0 ${W} ${H}" class="chart" preserveAspectRatio="none">
    <defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${color}" stop-opacity="0.35"/>
      <stop offset="1" stop-color="${color}" stop-opacity="0"/>
    </linearGradient></defs>
    ${grid}${retire}
    <path d="${area}" fill="url(#${gid})"/>
    <path d="${line}" fill="none" stroke="${color}" stroke-width="2.5" stroke-linejoin="round"/>
    ${xlbl}
  </svg>`;
}

/* ---- 도넛(비중) 차트 ------------------------------------------------------ */
function donutChart(items, opts = {}) {
  const size = opts.size || 150, r = size / 2 - 12, cx = size / 2, cy = size / 2;
  const total = items.reduce((s, it) => s + Math.max(0, it.value), 0) || 1;
  let a0 = -Math.PI / 2;
  let paths = '';
  items.forEach((it) => {
    const frac = Math.max(0, it.value) / total;
    if (frac <= 0) return;
    const a1 = a0 + frac * Math.PI * 2;
    const large = frac > 0.5 ? 1 : 0;
    const x0 = cx + r * Math.cos(a0), y0 = cy + r * Math.sin(a0);
    const x1 = cx + r * Math.cos(a1), y1 = cy + r * Math.sin(a1);
    paths += `<path d="M${cx} ${cy} L${x0.toFixed(2)} ${y0.toFixed(2)} A${r} ${r} 0 ${large} 1 ${x1.toFixed(2)} ${y1.toFixed(2)} Z" fill="${it.color}" stroke="var(--bg-card)" stroke-width="2"/>`;
    a0 = a1;
  });
  const innerR = r * 0.6;
  return `<svg viewBox="0 0 ${size} ${size}" class="donut">
    ${paths}
    <circle cx="${cx}" cy="${cy}" r="${innerR}" fill="var(--bg-card)"/>
  </svg>`;
}

function el(html) {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}
