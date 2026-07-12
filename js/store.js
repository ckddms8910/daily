/* ==========================================================================
 * 가상 투자 계획 — 데이터 저장소 & 시뮬레이션 엔진
 * 순수 바닐라 JS. localStorage 에 모든 데이터 보관 (오프라인 동작).
 * ========================================================================== */

const STORE_KEY = 'vip.data.v1';

/* ---- 기본(시드) 데이터: 사용자의 ISA 엑셀 계획을 반영 ---------------------- */
function seedData() {
  return {
    settings: {
      startYear: 2026,
      endYear: 2071,        // 시뮬레이션 종료 연도
      retireYear: 2048,     // 은퇴(퇴직) 연도
      birthYear: 1991,      // 나이 표시용 (60세 = 2051 근처)
      currency: '원',
    },
    accounts: [
      {
        id: 'acc_pensionA',
        name: '연금계좌A',
        asset: 'S&P500',
        type: '연금',
        color: '#4f8cff',
        startYear: 2026,
        initial: 4538160,          // 2026 예상 평가금액
        growth: 8,                 // 연 성장률 %
        monthly: 200000,           // 기본 월 납입
        overrides: [
          { year: 2030, monthly: 260000, note: '' },
          { year: 2034, monthly: 400000, note: '' },
          { year: 2036, monthly: 500000, note: '' },
          { year: 2047, lump: -15000000, monthly: 0, note: '1,500만 원 출금 + B로 이전' },
        ],
      },
      {
        id: 'acc_pensionB',
        name: '연금계좌B',
        asset: 'S&P500',
        type: '연금',
        color: '#22c1a4',
        startYear: 2026,
        initial: 120000,
        growth: 7,
        monthly: 10000,
        overrides: [
          { year: 2041, monthly: 1000000, note: '월 100만원 납입' },
          { year: 2046, monthly: 1500000, note: '월 150만원 납입' },
          { year: 2062, lump: 3000000000, monthly: 0, note: 'ISA 만기금 30억 추가' },
        ],
      },
      {
        id: 'acc_isa',
        name: 'ISA',
        asset: '나스닥',
        type: 'ISA',
        color: '#ff8c42',
        startYear: 2026,
        initial: 2970900,
        growth: 10,
        monthly: 300000,
        overrides: [
          { year: 2029, monthly: 2000000, note: '월 200만원' },
          { year: 2034, monthly: 2400000, lump: 20000000, note: '월 240만 + 내일적금 2천만' },
          { year: 2040, monthly: 3100000, note: '월 310만원' },
          { year: 2041, monthly: 0, note: '납입 종료 (거치)' },
          { year: 2061, lump: 0, monthly: 0, note: 'ISA 해지' },
        ],
      },
      {
        id: 'acc_secGOOG',
        name: '증권-GOOG',
        asset: '구글',
        type: '증권',
        color: '#a06bff',
        startYear: 2026,
        initial: 313500,
        growth: 10,
        monthly: 31250,
        overrides: [],
      },
      {
        id: 'acc_secTSLA',
        name: '증권-TSLA',
        asset: '테슬라',
        type: '증권',
        color: '#ff5d73',
        startYear: 2026,
        initial: 318000,
        growth: 10,
        monthly: 31250,
        overrides: [],
      },
      {
        id: 'acc_secSPACEX',
        name: '증권-SpaceX',
        asset: 'SpaceX',
        type: '증권',
        color: '#3ec7ff',
        startYear: 2026,
        initial: 192000,
        growth: 10,
        monthly: 31250,
        overrides: [],
      },
    ],
  };
}

/* ---- 로드 / 저장 --------------------------------------------------------- */
function loadData() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) {
      const d = seedData();
      saveData(d);
      return d;
    }
    const d = JSON.parse(raw);
    // 방어적 기본값 채우기
    d.settings = Object.assign(seedData().settings, d.settings || {});
    d.accounts = (d.accounts || []).map(normalizeAccount);
    return d;
  } catch (e) {
    console.error('데이터 로드 실패, 초기화합니다', e);
    const d = seedData();
    saveData(d);
    return d;
  }
}

function saveData(d) {
  localStorage.setItem(STORE_KEY, JSON.stringify(d));
}

function normalizeAccount(a) {
  return Object.assign({
    id: a.id || uid(),
    name: '새 계좌',
    asset: '',
    type: '기타',
    color: '#4f8cff',
    startYear: 2026,
    initial: 0,
    growth: 8,
    monthly: 0,
    overrides: [],
  }, a);
}

function uid() {
  return 'acc_' + Math.random().toString(36).slice(2, 9);
}

/* ==========================================================================
 * 시뮬레이션 엔진
 *  - 매년: 평가액 = 이전 * (1+성장률) + (월납입 * 12) + 일시금(lump)
 *  - 월납입 복리 근사를 위해 연 성장의 절반을 납입금에 적용
 *  - override 로 해당 연도의 월납입/일시금을 지정 (음수 = 출금)
 * ========================================================================== */
function projectAccount(acc, startYear, endYear) {
  const rows = [];
  let value = 0;
  let curMonthly = acc.monthly || 0;
  let principal = 0; // 누적 원금(순납입)

  // override 를 연도별 맵으로
  const ovMap = {};
  (acc.overrides || []).forEach((o) => { ovMap[o.year] = o; });

  for (let year = startYear; year <= endYear; year++) {
    if (year < acc.startYear) {
      rows.push({ year, value: 0, contribution: 0, growth: 0, principal: 0, note: '' });
      continue;
    }

    let note = '';
    let lump = 0;

    // override 적용 (해당 연도부터 월납입 변경 / 일시금)
    if (ovMap[year]) {
      const o = ovMap[year];
      if (typeof o.monthly === 'number') curMonthly = o.monthly;
      if (typeof o.lump === 'number') lump = o.lump;
      note = o.note || '';
    }

    // 시작 연도: 초기 평가액을 그해 말 평가액으로 사용 (엑셀과 동일)
    if (year === acc.startYear) {
      value = acc.initial || 0;
      principal = acc.initial || 0;
      rows.push({
        year, value, contribution: acc.initial || 0, monthly: curMonthly,
        lump: 0, growth: 0, principal, note,
      });
      continue;
    }

    const annualContribution = curMonthly * 12;
    const startValue = value;

    // 평가액 = 이전 * (1+성장률) + 연납입 + 일시금  (엑셀 모델)
    const growthAmt = startValue * (acc.growth / 100);
    value = startValue + growthAmt + annualContribution + lump;
    if (value < 0) value = 0;

    principal += annualContribution + lump;

    rows.push({
      year,
      value,
      contribution: annualContribution + lump,
      monthly: curMonthly,
      lump,
      growth: growthAmt,
      principal,
      note,
    });
  }
  return rows;
}

/* 전체 포트폴리오: 계좌별 projection 합산 */
function projectPortfolio(data) {
  const { startYear, endYear } = data.settings;
  const perAccount = {};
  data.accounts.forEach((a) => {
    perAccount[a.id] = projectAccount(a, startYear, endYear);
  });

  const totals = [];
  for (let i = 0, year = startYear; year <= endYear; year++, i++) {
    let value = 0, contribution = 0, principal = 0, growth = 0;
    data.accounts.forEach((a) => {
      const r = perAccount[a.id][i];
      value += r.value;
      contribution += r.contribution;
      principal += r.principal;
      growth += r.growth;
    });
    totals.push({ year, value, contribution, principal, growth });
  }
  return { perAccount, totals };
}

/* 특정 연도의 계좌별 스냅샷 */
function snapshotAt(data, year) {
  const { startYear, endYear } = data.settings;
  const idx = year - startYear;
  const proj = projectPortfolio(data);
  const items = data.accounts.map((a) => {
    const rows = proj.perAccount[a.id];
    const r = rows[Math.max(0, Math.min(idx, rows.length - 1))];
    return { account: a, value: r ? r.value : 0, row: r };
  });
  const total = items.reduce((s, it) => s + it.value, 0);
  return { items, total, year, proj };
}
