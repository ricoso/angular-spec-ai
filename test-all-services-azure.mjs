#!/usr/bin/env node
/**
 * Smoke-Test: POST /api/v1/snaps fuer jede Branch x jeden Service.
 * Zielt auf die deployte Azure Container App.
 * Schlaegt an, wenn irgendeine Kombination einen 500er wirft (z. B. VariantNotFoundException).
 */

import { readFileSync } from 'fs';

const BASE   = process.env.BASE_URL || 'https://gs-terminbuchungs-app.greensand-67f75bb2.westeurope.azurecontainerapps.io';
const SETUP  = JSON.parse(readFileSync('./server/config/setup.json', 'utf-8'));
const BRANCHES = SETUP.branches;

const SERVICES = ['inspection', 'tuv', 'brake-fluid', 'tire-change', 'actions-checks', 'repair', 'bodywork'];

// Rate-Limit-Schutz: SoftNet sperrt Service-Accounts bei zu vielen Requests.
// 800 ms zwischen Calls → ~2.5 min fuer 189 Calls — konservativ, aber sicher.
const DELAY_MS = 800;
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

const BRAND_BY_NAME_HINT = (name) => {
  const n = name.toLowerCase();
  if (n.includes('audi')) return 'audi';
  if (n.includes('skoda')) return 'skoda';
  if (n.includes('seat') || n.includes('cupra')) return 'seat';
  if (n.includes('vw') || n.includes('volkswagen')) return 'volkswagen';
  return 'volkswagen';
};

const COMMON_HEADERS = { 'Origin': BASE };

async function getSessionToken() {
  const res = await fetch(`${BASE}/api/v1/session/token`, { headers: COMMON_HEADERS });
  if (!res.ok) throw new Error(`session/token ${res.status}`);
  const data = await res.json();
  return data.data?.token || data.token;
}

function isoPlusDays(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

async function testSnaps(token, branch, serviceId) {
  const variant = serviceId === 'tire-change' ? 'tireChange' : 'default';
  const body = {
    branchId: branch.branchId,
    jobTypeIds: [serviceId],
    fromDate: isoPlusDays(0),
    toDate: isoPlusDays(14),
    mode: 'next-available',
    brandName: BRAND_BY_NAME_HINT(branch.name),
    variant,
  };
  const res = await fetch(`${BASE}/api/v1/snaps`, {
    method: 'POST',
    headers: { ...COMMON_HEADERS, 'Content-Type': 'application/json', 'x-session-token': token },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: text.substring(0, 300) };
}

async function run() {
  console.log(`🎯 Ziel: ${BASE}`);
  console.log(`📋 ${BRANCHES.length} Branches x ${SERVICES.length} Services = ${BRANCHES.length * SERVICES.length} Tests\n`);

  const token = await getSessionToken();
  console.log(`✅ Session-Token ok\n`);

  const failures = [];
  let okCount = 0;

  for (const branch of BRANCHES) {
    const results = [];
    for (const svc of SERVICES) {
      await sleep(DELAY_MS);
      const r = await testSnaps(token, branch, svc);
      if (r.status >= 500) {
        failures.push({ branch: branch.name, branchId: branch.branchId, service: svc, ...r });
        results.push(`❌${svc}(${r.status})`);
      } else if (r.status >= 400) {
        results.push(`⚠️${svc}(${r.status})`);
      } else {
        okCount++;
        results.push(`✅${svc}`);
      }
    }
    console.log(`[${branch.name.padEnd(45)}] ${results.join(' ')}`);
  }

  console.log(`\n📊 Ergebnis: ${okCount} ok, ${failures.length} 5xx-Failures\n`);
  if (failures.length > 0) {
    console.log('❌ Failures:');
    for (const f of failures) {
      console.log(`  ${f.branch} / ${f.service}: ${f.status}`);
      console.log(`    ${f.body}`);
    }
    process.exit(1);
  }
  process.exit(0);
}

run().catch(err => { console.error(err); process.exit(1); });
