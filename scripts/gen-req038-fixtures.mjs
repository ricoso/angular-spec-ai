// Generates the REQ-038 E2E seed fixtures (playwright/fixtures/req038/{full,thin,fail}/).
// Deterministic (seeded PRNG) — re-running produces byte-identical files.
//
// Token grammar (resolved at runtime by StatisticsE2eSeedLoader):
//   @day[+-N]      ISO date today +- N days
//   @wd[+-N]       Mo–Sa workday index: @wd-0 = first Mo–Sa day >= today, @wd-1 = last Mo–Sa day < today
//   @wk[+-N]-DD    weekday DD (MO..SU) of the ISO week N weeks before/after the current one
//   @month[+-N]    yyyy-MM
//   @year[+-N]     yyyy
// The loader drops date-keyed entries >= today and < the collecting start (week tokens overshoot).
//
// Usage (repository root): node scripts/gen-req038-fixtures.mjs
// Every invariant the view models rely on is checked before a file is written (day/branch sums,
// slot grid + slotUnknown = bookings, off-hours by weekday, monotone funnel per device, drop-off
// margins = visits - bookings). REQ038_FIXTURE_OUT overrides the output directory (e.g. to diff).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = process.env.REQ038_FIXTURE_OUT ?? path.join(ROOT, 'playwright/fixtures/req038');
const setup = JSON.parse(fs.readFileSync(path.join(ROOT, 'server/config/setup.json'), 'utf8'));
const NAME_TO_ID = Object.fromEntries(setup.branches.map(b => [b.name, b.branchId]));

// ---------------------------------------------------------------- PRNG
function mulberry32(seed) {
  return function () {
    seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hash(...parts) {
  let h = 2166136261;
  for (const p of parts.join('|')) { h ^= p.charCodeAt(0); h = Math.imul(h, 16777619); }
  return h >>> 0;
}
const rnd = (...parts) => mulberry32(hash(...parts));
function pick(r, weights) {
  const total = Object.values(weights).reduce((a, b) => a + b, 0);
  let x = r() * total;
  for (const [k, w] of Object.entries(weights)) { x -= w; if (x < 0) return k; }
  return Object.keys(weights).at(-1);
}
/** Largest-remainder split of n into integer parts proportional to weights. */
function split(n, weights) {
  const keys = Object.keys(weights);
  const total = keys.reduce((a, k) => a + weights[k], 0);
  const raw = keys.map(k => (n * weights[k]) / total);
  const base = raw.map(Math.floor);
  let rest = n - base.reduce((a, b) => a + b, 0);
  const order = raw.map((v, i) => [v - base[i], i]).sort((a, b) => b[0] - a[0] || a[1] - b[1]);
  for (let j = 0; j < rest; j++) base[order[j][1]]++;
  return Object.fromEntries(keys.map((k, i) => [k, base[i]]));
}

// ---------------------------------------------------------------- catalogue
const WEEKDAYS = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'];
const SLOT_WEEKDAYS = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA'];
const SLOT_DAYPARTS = ['early', 'morning', 'midday', 'afternoon', 'late'];
const LEAD = ['0-1', '2-3', '4-7', '8-14', '15-30', '31+', 'unknown'];
const GROUPS = ['inspection', 'tuv', 'brake-fluid', 'tire-change', 'actions-checks', 'repair',
  'bodywork', 'extras', 'pickup', 'unknown'];
const DEVICES = ['mobile', 'tablet', 'desktop', 'unknown'];
const LEVERKUSEN = new Set(['Audi Zentrum Leverkusen', 'Volkswagen Zentrum Leverkusen']);

// monthly online bookings (expected), bookings per 100 service views
const BRANCH_PROFILE = {
  'Audi Zentrum Düsseldorf': [58, 15],
  'Volkswagen Zentrum Düsseldorf': [52, 14],
  'Audi Zentrum Essen': [44, 16],
  'Volkswagen Zentrum Essen': [40, 13],
  'Volkswagen Zentrum Wuppertal': [34, 14],
  'Audi Zentrum Duisburg': [30, 12],
  'ŠKODA Centrum Düsseldorf': [27, 15],
  'Volkswagen Neuss': [25, 13],
  'Audi Zentrum Neuss': [18, 12],
  'Audi Zentrum Wuppertal': [16, 11],
  'Volkswagen Solingen': [15, 13],
  'Audi Solingen': [13, 12],
  'Volkswagen Mettmann': [12, 10],
  'Audi Mettmann': [11, 12],
  'Volkswagen Erkrath': [10, 11],
  'Volkswagen Hagen': [10, 9],
  'Volkswagen Mülheim': [9, 10],
  'Audi Zentrum Mülheim': [9, 11],
  'Volkswagen Düsseldorf-Benrath': [8, 10],
  'Volkswagen Essen-Kray': [7, 9],
  'Volkswagen Velbert': [7, 8],
  'Volkswagen Dormagen': [6.5, 9],
  'Volkswagen Grevenbroich': [2.8, 6],
  'Audi Zentrum Leverkusen': [2.2, 5],
  'Volkswagen Mülheim Saarn': [2.0, 6],
  'Volkswagen & ŠKODA Service Wickenburg': [1.2, 4],
  'Volkswagen Zentrum Leverkusen': [0.6, 3],
};
for (const name of Object.keys(BRANCH_PROFILE)) {
  if (!NAME_TO_ID[name]) throw new Error('unknown branch ' + name);
}
if (Object.keys(BRANCH_PROFILE).length !== setup.branches.length) throw new Error('branch count');
const BRANCHES = Object.entries(BRANCH_PROFILE).map(([name, [monthly, conv]], i) => ({
  name, id: NAME_TO_ID[name], monthly, conv, phase: rnd('phase', name)(), index: i,
}));

const BOOKING_WEEKDAY_FACTOR = { MO: 1.27, TU: 1.21, WE: 1.16, TH: 1.1, FR: 0.99, SA: 0.61, SU: 0.66 };

// ---------------------------------------------------------------- insights
function bookingAttributes(r, branchName) {
  const bookingDaypart = pick(r, { day: 72, evening: 22, night: 6 });
  const slotKnown = r() >= 0.015;
  const slotWeekday = slotKnown ? pick(r, { MO: 19, TU: 18, WE: 17, TH: 17, FR: 17, SA: 12 }) : null;
  const slotDaypart = slotKnown
    ? pick(r, { early: 17, morning: 35, midday: 14, afternoon: 20, late: 14 }) : null;
  const leadTime = slotKnown
    ? pick(r, { '0-1': 9, '2-3': 21, '4-7': 30, '8-14': 22, '15-30': 13, '31+': 5 }) : 'unknown';
  const groups = new Set();
  if (LEVERKUSEN.has(branchName)) {
    groups.add('unknown'); // groups without tags → „nicht zuordenbar" (E9, §19 question 6)
  } else {
    groups.add(pick(r, { inspection: 32, tuv: 11, 'brake-fluid': 4, 'tire-change': 26,
      'actions-checks': 9, repair: 8, bodywork: 4, unknown: 1 }));
  }
  if (r() < 0.22) groups.add('extras');
  if (r() < 0.07) groups.add('pickup');
  const mobility = r() < 0.11;
  return { bookingDaypart, slotWeekday, slotDaypart, leadTime, groups: [...groups], mobility };
}

/** Funnel reached-counters per device, monotone per device, booking total fixed. */
function funnelFor(r, visits, bookings) {
  const services = Math.round(visits * (0.69 + r() * 0.04));
  const notes = Math.round(services * (0.76 + r() * 0.035));
  const search = Math.max(bookings, Math.round(notes * (0.84 + r() * 0.035)));
  const w = {
    visits: { mobile: 645, tablet: 10, desktop: 338, unknown: 7 },
    services: { mobile: 638, tablet: 10, desktop: 345, unknown: 7 },
    notes: { mobile: 633, tablet: 10, desktop: 350, unknown: 7 },
    search: { mobile: 628, tablet: 10, desktop: 355, unknown: 7 },
    booking: { mobile: 552, tablet: 10, desktop: 430, unknown: 8 },
  };
  const s = {
    visits: split(visits, w.visits), services: split(services, w.services),
    notes: split(notes, w.notes), search: split(search, w.search), booking: split(bookings, w.booking),
  };
  const byDevice = {};
  for (const d of DEVICES) {
    const booking = s.booking[d];
    const searchD = Math.max(s.search[d], booking);
    const notesD = Math.max(s.notes[d], searchD);
    const servicesD = Math.max(s.services[d], notesD);
    const visitsD = Math.max(s.visits[d], servicesD);
    byDevice[d] = { visits: visitsD, services: servicesD, notes: notesD, search: searchD, booking };
  }
  const all = { visits: 0, services: 0, notes: 0, search: 0, booking: 0 };
  for (const d of DEVICES) for (const k of Object.keys(all)) all[k] += byDevice[d][k];
  return { all, byDevice };
}

// share of all drop-offs, stage distribution
const SCENARIO_RATES = {
  'no-slots': [0.079, { 4: 1 }],
  'server-error': [0.048, { 1: 0.35, 2: 0.2, 3: 0.15, 4: 0.3 }],
  'many-searches': [0.045, { 4: 1 }],
  'slow-response': [0.032, { 2: 0.2, 3: 0.2, 4: 0.6 }],
  'few-slots': [0.031, { 4: 1 }],
  'error-then-quit': [0.016, { 1: 0.2, 2: 0.3, 3: 0.2, 4: 0.3 }],
  'slot-expired': [0.015, { 4: 1 }],
  'requests-lost': [0.010, { 1: 0.3, 2: 0.2, 3: 0.2, 4: 0.3 }],
  'search-rejected': [0.009, { 3: 1 }],
  'access-denied': [0.0065, { 1: 0.4, 2: 0.2, 3: 0.1, 4: 0.3 }],
  'slot-conflict': [0.006, { 4: 1 }],
  'booking-rejected': [0.005, { 4: 1 }],
};

function dropoffFor(r, funnel, cumulative) {
  const reached = funnel.all;
  const dropped = {
    1: reached.visits - reached.services, 2: reached.services - reached.notes,
    3: reached.notes - reached.search, 4: reached.search - reached.booking,
  };
  const total = dropped[1] + dropped[2] + dropped[3] + dropped[4];
  const byScenarioStep = {};
  const named = { 1: 0, 2: 0, 3: 0, 4: 0 };
  for (const [scenario, [rate, stages]] of Object.entries(SCENARIO_RATES)) {
    for (const [stage, share] of Object.entries(stages)) {
      const key = scenario + '@' + stage;
      const before = cumulative[key] ?? rnd('dphase', key)();
      const after = before + total * rate * share;
      cumulative[key] = after;
      let n = Math.floor(after) - Math.floor(before);
      n = Math.min(n, dropped[stage] - named[stage]);
      if (n > 0) {
        named[stage] += n;
        (byScenarioStep[scenario] ??= {})[stage] = n;
      }
    }
  }
  for (const stage of [1, 2, 3, 4]) {
    const quiet = dropped[stage] - named[stage];
    if (quiet > 0) byScenarioStep['quiet-' + stage] = { [stage]: quiet };
  }
  // full stage maps (1..4) per scenario for readability
  for (const s of Object.keys(byScenarioStep)) {
    byScenarioStep[s] = Object.fromEntries([1, 2, 3, 4].map(k => [String(k), byScenarioStep[s][k] ?? 0]));
  }
  // device margin: zip a shuffled scenario list with a shuffled device list (exact margins)
  const scenarioUnits = [];
  for (const [s, stages] of Object.entries(byScenarioStep)) {
    const t = Object.values(stages).reduce((a, b) => a + b, 0);
    for (let i = 0; i < t; i++) scenarioUnits.push(s);
  }
  const deviceUnits = [];
  for (const d of DEVICES) {
    const bd = funnel.byDevice[d];
    for (let i = 0; i < bd.visits - bd.booking; i++) deviceUnits.push(d);
  }
  if (scenarioUnits.length !== deviceUnits.length || scenarioUnits.length !== total) {
    throw new Error('dropoff margins inconsistent');
  }
  shuffle(r, scenarioUnits);
  shuffle(r, deviceUnits);
  const byScenarioDevice = {};
  for (const s of Object.keys(byScenarioStep)) {
    byScenarioDevice[s] = { mobile: 0, tablet: 0, desktop: 0, unknown: 0 };
  }
  for (let i = 0; i < total; i++) byScenarioDevice[scenarioUnits[i]][deviceUnits[i]]++;
  return { byScenarioStep, byScenarioDevice };
}
function shuffle(r, a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
}

function emptyMap(keys) { return Object.fromEntries(keys.map(k => [k, 0])); }

/**
 * Builds days + branchDays for the given week range (oldest first).
 * @param opts {weeks, volume, seed, dropoffFromWeek}
 */
function buildInsightsDays({ weeks, volume, seed, dropoffFromWeek, funnelFromWeek }) {
  const days = [];
  const branchDays = [];
  const cumBookings = {};
  const cumDropoff = {};
  for (let w = weeks; w >= 0; w--) {
    for (const dd of WEEKDAYS) {
      const token = `@wk-${w}-${dd}`;
      const r = rnd(seed, 'day', token);
      const noise = 0.75 + r() * 0.5;
      const factor = BOOKING_WEEKDAY_FACTOR[dd] * noise * volume;
      const weekend = dd === 'SA' || dd === 'SU';

      const global = {
        bookings: 0,
        bookingDaypart: { day: 0, evening: 0, night: 0 },
        slotGrid: Object.fromEntries(SLOT_WEEKDAYS.map(wd => [wd, emptyMap(SLOT_DAYPARTS)])),
        slotUnknown: 0,
        leadTime: emptyMap(LEAD),
        groups: emptyMap(GROUPS),
        mobility: 0,
        serviceViews: 0,
      };
      const perBranch = {};
      for (const b of BRANCHES) {
        const before = cumBookings[b.id] ?? b.phase;
        const after = before + (b.monthly / 30.4) * factor;
        cumBookings[b.id] = after;
        const count = Math.floor(after) - Math.floor(before);
        const rb = rnd(seed, 'branch', token, b.id);
        const viewsBase = (b.monthly / 30.4) * factor * (100 / b.conv) * (0.8 + rb() * 0.4);
        const counts = { bookings: count, serviceViews: 0, groups: {}, mobility: 0, offHours: 0, slotLate: 0 };
        for (let k = 0; k < count; k++) {
          const a = bookingAttributes(rnd(seed, 'booking', token, b.id, k), b.name);
          global.bookings++;
          global.bookingDaypart[a.bookingDaypart]++;
          if (a.slotWeekday) global.slotGrid[a.slotWeekday][a.slotDaypart]++;
          else global.slotUnknown++;
          global.leadTime[a.leadTime]++;
          for (const g of a.groups) {
            global.groups[g]++;
            counts.groups[g] = (counts.groups[g] ?? 0) + 1;
          }
          if (a.mobility) { global.mobility++; counts.mobility++; }
          if (weekend || a.bookingDaypart !== 'day') counts.offHours++;
          if (a.slotDaypart === 'late') counts.slotLate++;
        }
        counts.serviceViews = Math.max(count, Math.round(viewsBase));
        global.serviceViews += counts.serviceViews;
        // sort group keys in catalogue order
        counts.groups = Object.fromEntries(GROUPS.filter(g => counts.groups[g]).map(g => [g, counts.groups[g]]));
        perBranch[b.id] = counts;
      }

      let funnel = null;
      if (w <= funnelFromWeek) {
        const visits = Math.round(global.bookings / 0.134 * (0.9 + r() * 0.2)) + Math.round(3 * volume);
        const f = funnelFor(r, Math.max(visits, global.bookings), global.bookings);
        funnel = { all: f.all, byDevice: f.byDevice, dropoff: null };
        if (w <= dropoffFromWeek) funnel.dropoff = dropoffFor(r, f, cumDropoff);
      }
      global.funnel = funnel;
      days.push([token, global]);
      branchDays.push([token, perBranch]);
    }
  }
  return { days, branchDays };
}

// ---------------------------------------------------------------- consistency checks
function checkInsights({ days, branchDays }) {
  const bd = Object.fromEntries(branchDays);
  for (const [token, d] of days) {
    const sum = (o) => Object.values(o).reduce((a, b) => a + b, 0);
    if (sum(d.bookingDaypart) !== d.bookings) throw new Error('daypart ' + token);
    const grid = Object.values(d.slotGrid).reduce((a, m) => a + sum(m), 0);
    if (grid + d.slotUnknown !== d.bookings) throw new Error('slot grid ' + token);
    if (sum(d.leadTime) !== d.bookings) throw new Error('lead ' + token);
    if (d.leadTime.unknown !== d.slotUnknown) throw new Error('lead unknown ' + token);
    const branches = Object.values(bd[token]);
    const bSum = (f) => branches.reduce((a, b) => a + b[f], 0);
    if (bSum('bookings') !== d.bookings) throw new Error('branch bookings ' + token);
    if (bSum('serviceViews') !== d.serviceViews) throw new Error('branch views ' + token);
    if (bSum('mobility') !== d.mobility) throw new Error('branch mobility ' + token);
    const lateGlobal = Object.values(d.slotGrid).reduce((a, m) => a + m.late, 0);
    if (bSum('slotLate') !== lateGlobal) throw new Error('slot late ' + token);
    const weekend = token.endsWith('-SA') || token.endsWith('-SU');
    const offGlobal = weekend ? d.bookings : d.bookingDaypart.evening + d.bookingDaypart.night;
    if (bSum('offHours') !== offGlobal) throw new Error('off hours ' + token);
    for (const g of GROUPS) {
      const gs = branches.reduce((a, b) => a + (b.groups[g] ?? 0), 0);
      if (gs !== d.groups[g]) throw new Error('groups ' + g + ' ' + token);
    }
    if (d.funnel) {
      const f = d.funnel;
      const stages = ['visits', 'services', 'notes', 'search', 'booking'];
      for (const scope of [f.all, ...Object.values(f.byDevice)]) {
        for (let i = 1; i < stages.length; i++) {
          if (scope[stages[i]] > scope[stages[i - 1]]) throw new Error('funnel monotone ' + token);
        }
      }
      for (const s of stages) {
        if (DEVICES.reduce((a, dv) => a + f.byDevice[dv][s], 0) !== f.all[s]) throw new Error('funnel dev ' + token);
      }
      if (f.all.booking !== d.bookings) throw new Error('funnel booking ' + token);
      if (f.dropoff) {
        const drops = f.all.visits - f.all.booking;
        const stepSum = Object.values(f.dropoff.byScenarioStep).reduce((a, m) => a + sum(m), 0);
        const devSum = Object.values(f.dropoff.byScenarioDevice).reduce((a, m) => a + sum(m), 0);
        if (stepSum !== drops || devSum !== drops) throw new Error('dropoff sum ' + token);
        const reach = [f.all.visits, f.all.services, f.all.notes, f.all.search, f.all.booking];
        for (let k = 1; k <= 4; k++) {
          const atK = Object.values(f.dropoff.byScenarioStep).reduce((a, m) => a + m[k], 0);
          if (atK !== reach[k - 1] - reach[k]) throw new Error('dropoff stage ' + k + ' ' + token);
        }
        for (const dv of DEVICES) {
          const perDev = Object.values(f.dropoff.byScenarioDevice).reduce((a, m) => a + m[dv], 0);
          if (perDev !== f.byDevice[dv].visits - f.byDevice[dv].booking) throw new Error('dropoff dev ' + token);
        }
        for (const [s, m] of Object.entries(f.dropoff.byScenarioStep)) {
          if (sum(m) !== sum(f.dropoff.byScenarioDevice[s])) throw new Error('dropoff scenario ' + s);
        }
      }
    }
  }
}

// ---------------------------------------------------------------- serialisation
function line(obj) { return JSON.stringify(obj); }
function writeInsights(file, header, { days, branchDays }, extra = {}) {
  const out = [];
  out.push('{');
  out.push(`  "schemaVersion": 1,`);
  for (const [k, v] of Object.entries(header)) out.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)},`);
  for (const [k, v] of Object.entries(extra)) out.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)},`);
  out.push('  "days": {');
  out.push(days.map(([t, d]) => `    ${JSON.stringify(t)}: ${line(d)}`).join(',\n'));
  out.push('  },');
  out.push('  "branchDays": {');
  out.push(branchDays.map(([t, m]) => {
    const inner = Object.entries(m).map(([id, c]) => `      ${JSON.stringify(id)}: ${line(c)}`).join(',\n');
    return `    ${JSON.stringify(t)}: {\n${inner}\n    }`;
  }).join(',\n'));
  out.push('  },');
  out.push('  "branchMonths": {}');
  out.push('}');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, out.join('\n') + '\n');
}

// ---------------------------------------------------------------- capacity
// Pattern per branch over 12 scans (oldest first). F = free on target day, 1-4 = first free in
// n workdays, B = >= 5 workdays, N = none found, X = error (softnet-unavailable), T = timeout,
// U = unexpected-response, L = no-reference-service, '.' = whole scan failed (filled per set).
const CAPACITY_PATTERN = {
  'Audi Zentrum Düsseldorf':            'FFFFF.FF1FFF',
  'Volkswagen Zentrum Düsseldorf':      'F1FFF.FFFF2F',
  'Audi Zentrum Essen':                 'FFF2F.FFFFFF',
  'Volkswagen Zentrum Essen':           '2F1F2.21F3F1',
  'Volkswagen Zentrum Wuppertal':       'BB3B4.BBNB4B',
  'Audi Zentrum Duisburg':              'F2FF1.FFFF1F',
  'ŠKODA Centrum Düsseldorf':           '1F2F3.F1F2F1',
  'Volkswagen Neuss':                   '3B4BB.B3BBBN',
  'Audi Zentrum Neuss':                 'FFFFF.1FFFFF',
  'Audi Zentrum Wuppertal':             '2F3FF.1F2F1F',
  'Volkswagen Solingen':                'F1F2F.1FF2F3',
  'Audi Solingen':                      'FFF1F.FFFFF1',
  'Volkswagen Mettmann':                '4B3BB.4BB3BB',
  'Audi Mettmann':                      '1F1F2.FF1F2F',
  'Volkswagen Erkrath':                 'FF1FF.F2FFFF',
  'Volkswagen Hagen':                   'F2F1F.F3F1FT',
  'Volkswagen Mülheim':                 'BBNBB.BBBNBB',
  'Audi Zentrum Mülheim':               '2F2F1.2F1F2F',
  'Volkswagen Düsseldorf-Benrath':      '3F2F4.F1F3F2',
  'Volkswagen Essen-Kray':              'B4BB3.BB4BBB',
  'Volkswagen Velbert':                 'F3F2F.FF1FFF',
  'Volkswagen Dormagen':                'NBBNB.BNBBNN',
  'Volkswagen Grevenbroich':            'F1FFF.FF2FFF',
  'Volkswagen Mülheim Saarn':           '2F1F3.1F2F1B',
  'Volkswagen & ŠKODA Service Wickenburg': 'UFTUX.UF1UTF',
  'Audi Zentrum Leverkusen':            'LLLLL.LLLLLL',
  'Volkswagen Zentrum Leverkusen':      'LLLLL.LLLLLL',
};
for (const name of Object.keys(CAPACITY_PATTERN)) {
  if (!NAME_TO_ID[name]) throw new Error('unknown branch ' + name);
  if (CAPACITY_PATTERN[name].length !== 12) throw new Error('pattern length ' + name);
}
if (Object.keys(CAPACITY_PATTERN).length !== setup.branches.length) throw new Error('pattern count');

function wdToken(n) { return n > 0 ? `@wd-${n}` : n === 0 ? '@wd-0' : `@wd+${-n}`; }
const REASON = { X: 'softnet-unavailable', T: 'timeout', U: 'unexpected-response', L: 'no-reference-service' };

/**
 * @param failedIndex scan index (0 = oldest) that failed as a whole, or -1
 * @param scans number of scans (the newest is always @wd-1)
 * @param only restrict to the last n scans (thin set)
 */
function buildCapacity({ failedIndexes, seed, keepLast = 12 }) {
  const scans = [];
  for (let i = 12 - keepLast; i < 12; i++) {
    const k = 12 - i;                    // scan at @wd-k, target @wd-(k-1)
    const r = rnd(seed, 'scan', i);
    const failed = failedIndexes.includes(i);
    const branches = {};
    let errors = 0;
    let measured = 0;
    for (const b of setup.branches.slice().sort((a, c) => a.name.localeCompare(c.name, 'de'))) {
      let code = CAPACITY_PATTERN[b.name][i];
      if (failed) code = 'X';
      if (code === '.') code = 'F';
      if (REASON[code]) {
        branches[b.branchId] = { status: 'error', reason: REASON[code] };
        errors++;
      } else if (code === 'N') {
        branches[b.branchId] = { status: 'none' };
        measured++;
      } else {
        const w = code === 'F' ? 0 : code === 'B' ? 5 + Math.floor(r() * 4) : Number(code);
        const entry = { status: 'ok', firstFreeDate: wdToken(k - 1 - w), workdaysToFirstFree: w };
        entry.freeSlotsTargetDay = w === 0 ? 2 + Math.floor(r() * 12) : 0;
        branches[b.branchId] = entry;
        measured++;
      }
    }
    const state = measured === 0 ? 'failed' : errors > 0 ? 'partial' : 'complete';
    const startSec = 5 + Math.floor(r() * 50);
    const startMin = 30 + Math.floor(r() * 3);
    const durMin = failed ? 6 : 4 + Math.floor(r() * 2);
    const pad = (n) => String(n).padStart(2, '0');
    scans.push([wdToken(k), {
      state,
      startedAt: `${wdToken(k)}T20:${pad(startMin)}:${pad(startSec)}Z`,
      finishedAt: `${wdToken(k)}T20:${pad(startMin + durMin)}:${pad((startSec + 17) % 60)}Z`,
      targetDay: wdToken(k - 1),
      branches,
    }]);
  }
  return scans;
}

function writeCapacity(file, scans) {
  const out = ['{', '  "schemaVersion": 1,', '  "referenceService": "wiper-blades",', '  "scans": {'];
  out.push(scans.map(([t, s]) => {
    const { branches, ...head } = s;
    const headJson = JSON.stringify(head).slice(1, -1);
    const inner = Object.entries(branches).map(([id, p]) => `        ${JSON.stringify(id)}: ${line(p)}`).join(',\n');
    return `    ${JSON.stringify(t)}: {${headJson},\n      "branches": {\n${inner}\n      }\n    }`;
  }).join(',\n'));
  out.push('  }', '}');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, out.join('\n') + '\n');
}

// ---------------------------------------------------------------- sets
const WEEKS = 13;

// full: 13+ weeks of history, funnel from the start, drop-off from 9 weeks ago, 12 scans with one
// failed scan in the middle; every scan is partial because both Leverkusen branches lack the
// reference service (§5.6).
const full = buildInsightsDays({ weeks: WEEKS, volume: 1, seed: 'full', funnelFromWeek: WEEKS, dropoffFromWeek: 9 });
checkInsights(full);
writeInsights(path.join(OUT, 'full/usage-insights.json'), {
  updatedAt: '@dayT01:52:10Z',
  collectingSince: `@wk-${WEEKS}-MO`,
  funnelCollectingSince: `@wk-${WEEKS}-MO`,
  dropoffCollectingSince: '@wk-9-MO',
}, full);
writeCapacity(path.join(OUT, 'full/capacity-daily.json'), buildCapacity({ failedIndexes: [5], seed: 'full' }));

// thin: collection started yesterday, a handful of bookings; one scan only.
const thin = buildInsightsDays({ weeks: 1, volume: 0.8, seed: 'thin', funnelFromWeek: 1, dropoffFromWeek: 1 });
checkInsights(thin);
writeInsights(path.join(OUT, 'thin/usage-insights.json'), {
  updatedAt: '@dayT01:52:10Z',
  collectingSince: '@day-1',
  funnelCollectingSince: '@day-1',
  dropoffCollectingSince: '@day-1',
}, thin);
writeCapacity(path.join(OUT, 'thin/capacity-daily.json'), buildCapacity({ failedIndexes: [], seed: 'thin', keepLast: 1 }));

// fail: the newest scan failed as a whole (fallback to the partial scan before it) and the
// insights rollup for yesterday is still pending (e2eSeed.lastDay = two days ago, E3).
const fail = buildInsightsDays({ weeks: WEEKS, volume: 1, seed: 'full', funnelFromWeek: WEEKS, dropoffFromWeek: 9 });
checkInsights(fail);
writeInsights(path.join(OUT, 'fail/usage-insights.json'), {
  updatedAt: '@day-1T01:52:10Z',
  collectingSince: `@wk-${WEEKS}-MO`,
  funnelCollectingSince: `@wk-${WEEKS}-MO`,
  dropoffCollectingSince: '@wk-9-MO',
}, fail, {
  e2eSeed: { lastDay: '@day-2', note: 'rollup for yesterday pending (E3) — read by StatisticsE2eSeedLoader only' },
});
writeCapacity(path.join(OUT, 'fail/capacity-daily.json'), buildCapacity({ failedIndexes: [11], seed: 'full' }));

for (const set of ['full', 'thin', 'fail']) {
  for (const f of ['usage-insights.json', 'capacity-daily.json']) {
    const p = path.join(OUT, set, f);
    JSON.parse(fs.readFileSync(p, 'utf8'));
    console.log(set, f, fs.statSync(p).size, 'bytes');
  }
}
