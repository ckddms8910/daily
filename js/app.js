/* ==========================================================================
 * 가상 투자 계획 — 앱 로직 / 라우터 / 뷰 렌더링
 * ========================================================================== */

let DATA = loadData();
let CURRENT = 'dashboard';
let DASH_YEAR = null; // 대시보드에서 선택한 연도

const app = () => document.getElementById('view');

function save() { saveData(DATA); }

/* ---- 라우터 -------------------------------------------------------------- */
function navigate(route) {
  CURRENT = route;
  document.querySelectorAll('.nav-item').forEach((b) => {
    b.classList.toggle('active', b.dataset.route === route);
  });
  render();
}

function render() {
  const v = app();
  v.scrollTop = 0;
  if (CURRENT === 'dashboard') v.innerHTML = viewDashboard();
  else if (CURRENT === 'accounts') v.innerHTML = viewAccounts();
  else if (CURRENT === 'plan') v.innerHTML = viewPlan();
  else if (CURRENT === 'settings') v.innerHTML = viewSettings();
  bindView();
}

/* ==========================================================================
 * 1. 대시보드
 * ========================================================================== */
function viewDashboard() {
  const s = DATA.settings;
  const proj = projectPortfolio(DATA);
  const totals = proj.totals;
  const nowYear = new Date().getFullYear();
  const year = DASH_YEAR || Math.max(s.startYear, Math.min(nowYear, s.endYear));
  const snap = snapshotAt(DATA, year);

  const endRow = totals[totals.length - 1];
  const retireIdx = Math.max(0, Math.min(s.retireYear - s.startYear, totals.length - 1));
  const retireRow = totals[retireIdx];

  // 선택 연도 카드
  const curRow = totals[Math.max(0, Math.min(year - s.startYear, totals.length - 1))];
  const gainPct = curRow.principal > 0 ? ((curRow.value - curRow.principal) / curRow.principal) * 100 : 0;

  // 연도 선택 슬라이더 옵션
  const age = s.birthYear ? (year - s.birthYear) : null;

  let yearOptions = '';
  for (let y = s.startYear; y <= s.endYear; y++) {
    yearOptions += `<option value="${y}" ${y === year ? 'selected' : ''}>${y}년${s.birthYear ? ' · ' + (y - s.birthYear) + '세' : ''}</option>`;
  }

  // 계좌별 리스트
  const items = snap.items.slice().sort((a, b) => b.value - a.value);
  const donutItems = items.map((it) => ({ value: it.value, color: it.account.color }));

  let accRows = items.map((it) => {
    const frac = snap.total > 0 ? (it.value / snap.total) * 100 : 0;
    return `<div class="asset-row">
      <span class="dot" style="background:${it.account.color}"></span>
      <div class="asset-meta">
        <div class="asset-name">${esc(it.account.name)} <small>${esc(it.account.asset)}</small></div>
        <div class="bar"><i style="width:${frac.toFixed(1)}%;background:${it.account.color}"></i></div>
      </div>
      <div class="asset-val">
        <b>${wonShort(it.value)}</b>
        <small>${frac.toFixed(1)}%</small>
      </div>
    </div>`;
  }).join('');

  return `
  <header class="page-head">
    <h1>대시보드</h1>
    <p class="sub">${s.startYear} → ${s.endYear} · 목표 자산 로드맵</p>
  </header>

  <div class="hero card">
    <div class="hero-top">
      <select id="dashYear" class="year-select">${yearOptions}</select>
      ${age != null ? `<span class="chip">${age}세</span>` : ''}
    </div>
    <div class="hero-value">${won(curRow.value)}</div>
    <div class="hero-sub">
      <span>총 원금 ${wonShort(curRow.principal)}</span>
      <span class="${gainPct >= 0 ? 'up' : 'down'}">평가수익 ${pct(gainPct)}</span>
    </div>
    <div class="chart-wrap">
      ${areaChart(totals.map((t) => ({ year: t.year, value: t.value })), {
        w: 340, h: 170, color: '#4f8cff', retireYear: s.retireYear,
        marks: [s.startYear, s.retireYear, s.endYear],
      })}
    </div>
    <div class="chart-legend">
      <span><i class="lg-line"></i> 총 자산</span>
      <span><i class="lg-retire"></i> 은퇴 ${s.retireYear}</span>
    </div>
  </div>

  <div class="stat-grid">
    <div class="card stat">
      <span class="stat-lbl">은퇴 시점 (${s.retireYear})</span>
      <b class="stat-num">${wonShort(retireRow.value)}</b>
      <small>월 ${wonShort(estMonthlyWithdraw(retireRow.value))} 인출 가능</small>
    </div>
    <div class="card stat">
      <span class="stat-lbl">최종 (${s.endYear})</span>
      <b class="stat-num">${wonShort(endRow.value)}</b>
      <small>원금 대비 ${endRow.principal > 0 ? (endRow.value / endRow.principal).toFixed(1) : 0}배</small>
    </div>
  </div>

  <div class="card">
    <div class="card-head"><h2>자산 구성 · ${year}년</h2></div>
    <div class="donut-wrap">
      <div class="donut-box">
        ${donutChart(donutItems, { size: 140 })}
        <div class="donut-center">
          <small>합계</small>
          <b>${wonShort(snap.total)}</b>
        </div>
      </div>
      <div class="asset-list">${accRows || '<p class="empty">계좌가 없습니다</p>'}</div>
    </div>
  </div>

  <button class="btn-ghost" onclick="navigate('plan')">📈 연도별 상세 계획 보기</button>
  `;
}

function estMonthlyWithdraw(value) {
  // 연 4% 룰 기준 월 인출 가능액
  return (value * 0.04) / 12;
}

/* ==========================================================================
 * 2. 계좌 목록
 * ========================================================================== */
function viewAccounts() {
  const s = DATA.settings;
  const snap = snapshotAt(DATA, new Date().getFullYear());
  const byType = {};
  DATA.accounts.forEach((a) => {
    (byType[a.type] = byType[a.type] || []).push(a);
  });

  let sections = Object.keys(byType).map((type) => {
    const rows = byType[type].map((a) => {
      const proj = projectAccount(a, s.startYear, s.endYear);
      const last = proj[proj.length - 1];
      const cur = proj[Math.max(0, Math.min(new Date().getFullYear() - s.startYear, proj.length - 1))];
      return `<button class="acc-card" onclick="editAccount('${a.id}')">
        <span class="acc-color" style="background:${a.color}"></span>
        <div class="acc-info">
          <div class="acc-title">${esc(a.name)}</div>
          <div class="acc-desc">${esc(a.asset || type)} · 월 ${wonShort(a.monthly)} · 연 ${a.growth}%</div>
        </div>
        <div class="acc-vals">
          <b>${wonShort(cur.value)}</b>
          <small>→ ${wonShort(last.value)}</small>
        </div>
      </button>`;
    }).join('');
    return `<div class="type-section"><h2 class="type-title">${esc(type)}</h2>${rows}</div>`;
  }).join('');

  return `
  <header class="page-head">
    <h1>계좌</h1>
    <p class="sub">총 ${DATA.accounts.length}개 · 현재 평가액 ${won(snap.total)}</p>
  </header>
  ${sections || '<p class="empty">계좌를 추가해 보세요</p>'}
  <button class="btn-primary" onclick="editAccount(null)">+ 계좌 추가</button>
  `;
}

/* ==========================================================================
 * 3. 연도별 계획 (시뮬레이션 테이블)
 * ========================================================================== */
function viewPlan() {
  const s = DATA.settings;
  const proj = projectPortfolio(DATA);
  const totals = proj.totals;

  let rows = totals.map((t) => {
    const isRetire = t.year === s.retireYear;
    const age = s.birthYear ? (t.year - s.birthYear) : '';
    return `<tr class="${isRetire ? 'retire-row' : ''}">
      <td class="c-year">${t.year}<small>${age ? age + '세' : ''}</small></td>
      <td class="c-contrib ${t.contribution < 0 ? 'neg' : ''}">${t.contribution >= 0 ? '' : ''}${wonShort(t.contribution)}</td>
      <td class="c-growth">${wonShort(t.growth)}</td>
      <td class="c-value">${wonShort(t.value)}</td>
    </tr>`;
  }).join('');

  return `
  <header class="page-head">
    <h1>연도별 계획</h1>
    <p class="sub">계좌 합산 시뮬레이션 · 성장률 복리 반영</p>
  </header>

  <div class="card chart-card">
    ${areaChart(totals.map((t) => ({ year: t.year, value: t.value })), {
      w: 340, h: 160, color: '#22c1a4', retireYear: s.retireYear,
      marks: [s.startYear, s.retireYear, s.endYear],
    })}
  </div>

  <div class="card table-card">
    <table class="plan-table">
      <thead><tr>
        <th>연도</th><th>순납입</th><th>성장</th><th>평가액</th>
      </tr></thead>
      <tbody>${rows}</tbody>
    </table>
  </div>
  <p class="note">💡 계좌 화면에서 각 계좌의 월 납입·성장률·출금 계획을 수정하면 이 표가 즉시 반영됩니다.</p>
  `;
}

/* ==========================================================================
 * 4. 설정
 * ========================================================================== */
function viewSettings() {
  const s = DATA.settings;
  return `
  <header class="page-head">
    <h1>설정</h1>
    <p class="sub">시뮬레이션 기간 · 데이터 관리</p>
  </header>

  <div class="card form">
    <label class="field">
      <span>시작 연도</span>
      <input type="number" id="setStart" value="${s.startYear}">
    </label>
    <label class="field">
      <span>종료 연도</span>
      <input type="number" id="setEnd" value="${s.endYear}">
    </label>
    <label class="field">
      <span>은퇴(퇴직) 연도</span>
      <input type="number" id="setRetire" value="${s.retireYear}">
    </label>
    <label class="field">
      <span>출생 연도 (나이 표시)</span>
      <input type="number" id="setBirth" value="${s.birthYear || ''}">
    </label>
    <button class="btn-primary" onclick="saveSettings()">저장</button>
  </div>

  <div class="card form">
    <h2 class="card-h">데이터</h2>
    <button class="btn-ghost" onclick="exportData()">⬇️ 백업 내보내기 (JSON)</button>
    <button class="btn-ghost" onclick="document.getElementById('importFile').click()">⬆️ 백업 불러오기</button>
    <input type="file" id="importFile" accept="application/json" style="display:none" onchange="importData(event)">
    <button class="btn-danger" onclick="resetData()">초기 데이터로 재설정</button>
  </div>

  <p class="note">모든 데이터는 이 휴대폰에만 저장됩니다. 앱을 홈 화면에 추가하면 오프라인에서도 사용할 수 있어요.</p>
  <p class="ver">가상 투자 계획 v1.0</p>
  `;
}

function saveSettings() {
  DATA.settings.startYear = +document.getElementById('setStart').value || 2026;
  DATA.settings.endYear = +document.getElementById('setEnd').value || 2071;
  DATA.settings.retireYear = +document.getElementById('setRetire').value || 2048;
  DATA.settings.birthYear = +document.getElementById('setBirth').value || null;
  if (DATA.settings.endYear <= DATA.settings.startYear) DATA.settings.endYear = DATA.settings.startYear + 30;
  save();
  toast('설정이 저장되었습니다');
  navigate('dashboard');
}

/* ==========================================================================
 * 계좌 편집 모달
 * ========================================================================== */
function editAccount(id) {
  const isNew = !id;
  const acc = isNew ? normalizeAccount({ id: uid(), name: '', startYear: DATA.settings.startYear })
    : DATA.accounts.find((a) => a.id === id);
  const draft = JSON.parse(JSON.stringify(acc));

  const modal = el(`<div class="modal-backdrop"><div class="modal"></div></div>`);
  document.body.appendChild(modal);
  const box = modal.querySelector('.modal');

  function renderModal() {
    const ovRows = (draft.overrides || []).map((o, i) => `
      <div class="ov-row">
        <input type="number" placeholder="연도" value="${o.year ?? ''}" data-ov="${i}" data-k="year">
        <input type="number" placeholder="월납입" value="${o.monthly ?? ''}" data-ov="${i}" data-k="monthly">
        <input type="number" placeholder="일시금(±)" value="${o.lump ?? ''}" data-ov="${i}" data-k="lump">
        <button class="ov-del" data-del="${i}">✕</button>
        <input type="text" class="ov-note" placeholder="메모 (예: 1500만 출금)" value="${esc(o.note || '')}" data-ov="${i}" data-k="note">
      </div>`).join('');

    box.innerHTML = `
      <div class="modal-head">
        <button class="modal-x" id="mClose">✕</button>
        <h2>${isNew ? '계좌 추가' : '계좌 수정'}</h2>
        ${isNew ? '' : '<button class="modal-del" id="mDelete">삭제</button>'}
      </div>
      <div class="modal-body">
        <label class="field"><span>계좌 이름</span>
          <input type="text" id="fName" value="${esc(draft.name)}" placeholder="예: 연금계좌A"></label>
        <div class="field-row">
          <label class="field"><span>유형</span>
            <select id="fType">
              ${['연금', 'ISA', '증권', '적금', '기타'].map((t) => `<option ${draft.type === t ? 'selected' : ''}>${t}</option>`).join('')}
            </select></label>
          <label class="field"><span>자산/종목</span>
            <input type="text" id="fAsset" value="${esc(draft.asset)}" placeholder="S&P500"></label>
        </div>
        <div class="field-row">
          <label class="field"><span>시작 연도</span>
            <input type="number" id="fStart" value="${draft.startYear}"></label>
          <label class="field"><span>연 성장률 %</span>
            <input type="number" id="fGrowth" value="${draft.growth}" step="0.5"></label>
        </div>
        <div class="field-row">
          <label class="field"><span>시작 평가액 (원)</span>
            <input type="number" id="fInit" value="${draft.initial}"></label>
          <label class="field"><span>월 납입액 (원)</span>
            <input type="number" id="fMonthly" value="${draft.monthly}"></label>
        </div>
        <label class="field"><span>색상</span>
          <div class="color-row">
            ${['#4f8cff', '#22c1a4', '#ff8c42', '#a06bff', '#ff5d73', '#3ec7ff', '#ffcb2d', '#8ce563'].map((c) =>
              `<button class="swatch ${draft.color === c ? 'sel' : ''}" style="background:${c}" data-color="${c}"></button>`).join('')}
          </div>
        </label>

        <div class="ov-section">
          <div class="ov-head"><h3>연도별 변경 계획</h3>
            <button class="ov-add" id="mAddOv">+ 추가</button></div>
          <p class="ov-hint">특정 연도부터 월납입 변경, 또는 일시 입금/출금(음수)</p>
          ${ovRows || '<p class="ov-empty">변경 계획 없음</p>'}
        </div>

        <div class="preview" id="mPreview"></div>
      </div>
      <div class="modal-foot">
        <button class="btn-primary" id="mSave">저장</button>
      </div>`;

    updatePreview();
    bindModal();
  }

  function collect() {
    draft.name = document.getElementById('fName').value.trim() || '새 계좌';
    draft.type = document.getElementById('fType').value;
    draft.asset = document.getElementById('fAsset').value.trim();
    draft.startYear = +document.getElementById('fStart').value || DATA.settings.startYear;
    draft.growth = parseFloat(document.getElementById('fGrowth').value) || 0;
    draft.initial = +document.getElementById('fInit').value || 0;
    draft.monthly = +document.getElementById('fMonthly').value || 0;
  }

  function updatePreview() {
    const rows = projectAccount(draft, DATA.settings.startYear, DATA.settings.endYear);
    const last = rows[rows.length - 1];
    const retIdx = Math.max(0, Math.min(DATA.settings.retireYear - DATA.settings.startYear, rows.length - 1));
    const pv = document.getElementById('mPreview');
    if (pv) pv.innerHTML = `
      <div class="pv-row"><span>은퇴(${DATA.settings.retireYear}) 예상</span><b>${won(rows[retIdx].value)}</b></div>
      <div class="pv-row"><span>최종(${DATA.settings.endYear}) 예상</span><b>${won(last.value)}</b></div>`;
  }

  function bindModal() {
    document.getElementById('mClose').onclick = close;
    document.getElementById('mSave').onclick = saveAcc;
    const del = document.getElementById('mDelete');
    if (del) del.onclick = () => {
      if (confirm('이 계좌를 삭제할까요?')) {
        DATA.accounts = DATA.accounts.filter((a) => a.id !== draft.id);
        save(); close(); render();
      }
    };
    document.getElementById('mAddOv').onclick = () => {
      collect();
      draft.overrides = draft.overrides || [];
      draft.overrides.push({ year: DATA.settings.startYear, monthly: null, lump: null, note: '' });
      renderModal();
    };
    box.querySelectorAll('[data-color]').forEach((b) => {
      b.onclick = () => { collect(); draft.color = b.dataset.color; renderModal(); };
    });
    box.querySelectorAll('[data-del]').forEach((b) => {
      b.onclick = () => { collect(); draft.overrides.splice(+b.dataset.del, 1); renderModal(); };
    });
    box.querySelectorAll('[data-ov]').forEach((inp) => {
      inp.oninput = () => {
        const i = +inp.dataset.ov, k = inp.dataset.k;
        const val = k === 'note' ? inp.value : (inp.value === '' ? null : +inp.value);
        draft.overrides[i][k] = val;
        updatePreview();
      };
    });
    ['fInit', 'fMonthly', 'fGrowth', 'fStart'].forEach((id) => {
      const e = document.getElementById(id);
      if (e) e.oninput = () => { collect(); updatePreview(); };
    });
  }

  function saveAcc() {
    collect();
    // override 정리 (연도 있는 것만)
    draft.overrides = (draft.overrides || []).filter((o) => o.year).sort((a, b) => a.year - b.year);
    const idx = DATA.accounts.findIndex((a) => a.id === draft.id);
    if (idx >= 0) DATA.accounts[idx] = draft;
    else DATA.accounts.push(draft);
    save(); close(); render();
    toast('저장되었습니다');
  }

  function close() { modal.remove(); }
  modal.addEventListener('click', (e) => { if (e.target === modal) close(); });
  renderModal();
}

/* ==========================================================================
 * 데이터 내보내기 / 불러오기 / 초기화
 * ========================================================================== */
function exportData() {
  const blob = new Blob([JSON.stringify(DATA, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `투자계획_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
}

function importData(ev) {
  const file = ev.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const d = JSON.parse(reader.result);
      d.settings = Object.assign(seedData().settings, d.settings || {});
      d.accounts = (d.accounts || []).map(normalizeAccount);
      DATA = d; save(); navigate('dashboard');
      toast('불러오기 완료');
    } catch (e) { alert('올바른 백업 파일이 아닙니다'); }
  };
  reader.readAsText(file);
}

function resetData() {
  if (confirm('초기 예시 데이터로 되돌립니다. 현재 데이터는 사라집니다.')) {
    DATA = seedData(); save(); navigate('dashboard');
    toast('초기화되었습니다');
  }
}

/* ---- 뷰 바인딩 (렌더 후 이벤트 연결) ------------------------------------- */
function bindView() {
  const dy = document.getElementById('dashYear');
  if (dy) dy.onchange = () => { DASH_YEAR = +dy.value; render(); };
}

/* ---- 토스트 -------------------------------------------------------------- */
let toastTimer;
function toast(msg) {
  let t = document.getElementById('toast');
  if (!t) { t = el('<div id="toast"></div>'); document.body.appendChild(t); }
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 1800);
}

/* ---- 초기화 -------------------------------------------------------------- */
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.nav-item').forEach((b) => {
    b.onclick = () => navigate(b.dataset.route);
  });
  navigate('dashboard');

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
});
