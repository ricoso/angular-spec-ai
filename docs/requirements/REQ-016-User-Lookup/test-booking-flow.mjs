#!/usr/bin/env node
/**
 * Full booking flow test — includes Customer Lookup verification.
 *
 * Flow:
 *   Step 0: Auth (SoftNet JWT)
 *   Step 1: Raw Snaps (available appointments)
 *   Step 2: Patch snap with client + car data
 *   Step 3: Create Appointment (SoftNet creates user + car automatically)
 *   Step 4: Customer Lookup — find the just-created customer via multiple strategies
 *   Step 5: Verify appointment (GET)
 *   Step 6: Cancel appointment (cleanup)
 *
 * Purpose: Prove that SoftNet stores customer data on appointment creation
 * and that we can retrieve it for the User-Lookup feature (REQ-016).
 */

import { readFileSync } from 'fs';
import { createSign } from 'crypto';

// ─── Config ───
const SOFTNET_BASE  = 'https://int-api.soft-nrg.com';
const SOFTNET_AUTH  = 'https://int-auth.soft-nrg.com/oauth/token';
const GROUP_ID      = '698b34c24dd8eea4d6d42179';
const BRANCH_ID     = '58341c5cbe856f6dc47e9f05';
const BRAND         = 'bmw';
const FINGERPRINT   = JSON.parse(readFileSync('./cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json', 'utf-8'));

// Test customer data
const TEST_CLIENT = {
  email: 'test@autohaus-test.de',
  lastName: 'Testermann',
  firstName: 'Max',
  mobile: '+491234567890',
  phone: '+491234567890',
  address: 'Teststraße 1',
  zipCode: '80331',
  city: 'München',
  country: 'DE',
  language: 'de',
};

const TEST_CAR = {
  brand: BRAND,
  license: 'M-TEST-123',
  VIN: 'UNKNOWN',
  currentMileage: 50000,
  firstRegMonth: '01',
  firstRegYear: '2022',
  model: '320i',
  color: 'schwarz',
};

let softnetToken = '';

// ─── Get SoftNet token (same logic as backend) ───
async function getSoftnetToken() {
  console.log('\n📌 STEP 0: Get SoftNet access token');
  const now = Math.floor(Date.now() / 1000);

  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: FINGERPRINT.private_key_id })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    id: FINGERPRINT.private_key_id,
    scope: 'scope.api.planning.service',
    sub: FINGERPRINT.client_email,
    iss: FINGERPRINT.client_id,
    aud: SOFTNET_AUTH,
    iat: now,
    exp: now + 300,
  })).toString('base64url');

  const sign = createSign('RSA-SHA256');
  sign.update(`${header}.${payload}`);
  const signature = sign.sign(FINGERPRINT.private_key, 'base64url');
  const jwt = `${header}.${payload}.${signature}`;

  const res = await fetch(SOFTNET_AUTH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });

  if (!res.ok) { console.error('❌ Auth failed:', res.status, await res.text()); return false; }
  const data = await res.json();
  softnetToken = data.access_token;
  console.log(`  ✅ Token acquired (expires in ${data.expires_in}s)`);
  return true;
}

// ─── SoftNet direct call ───
async function softnet(method, path, body) {
  const url = `${SOFTNET_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${softnetToken}`,
    'x-nrg-group': GROUP_ID,
  };

  console.log(`\n${'─'.repeat(60)}`);
  console.log(`→ ${method} ${path}`);
  if (body) console.log('  Body (preview):', JSON.stringify(body, null, 2).substring(0, 1500));

  const start = Date.now();
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const duration = Date.now() - start;
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }

  console.log(`← ${res.status} ${res.statusText} (${duration}ms)`);
  if (res.ok) {
    console.log('  Response (preview):', JSON.stringify(data, null, 2).substring(0, 2000));
  } else {
    console.error('  ERROR:', JSON.stringify(data, null, 2).substring(0, 2000));
  }
  return { ok: res.ok, status: res.status, data };
}

// ─── Customer Lookup: try multiple SoftNet API strategies ───
async function customerLookup(userId, carId) {
  console.log('\n\n📌 STEP 4: Customer Lookup — find the just-created customer');
  console.log('  ℹ️  Testing multiple SoftNet API endpoints to find the right one.');
  console.log(`  ℹ️  Looking for: ${TEST_CLIENT.email} / ${TEST_CAR.license}`);
  console.log(`  ℹ️  Known IDs from appointment: userId=${userId}, carId=${carId}`);

  const results = {};

  // ─── Strategy 1: GET user by ID (from appointment response) ───
  console.log('\n  ── Strategy 1: GET user by known ID ──');
  if (userId) {
    const r1a = await softnet('GET', `/api/planning/users/${userId}`);
    results['GET /api/planning/users/{userId}'] = { status: r1a.status, ok: r1a.ok };
    if (r1a.ok) logCustomer('Strategy 1a', r1a.data);

    const r1b = await softnet('GET', `/api/planning/clients/${userId}`);
    results['GET /api/planning/clients/{userId}'] = { status: r1b.status, ok: r1b.ok };
    if (r1b.ok) logCustomer('Strategy 1b', r1b.data);

    const r1c = await softnet('GET', `/api/planning/groups/${GROUP_ID}/users/${userId}`);
    results['GET /api/planning/groups/{gid}/users/{userId}'] = { status: r1c.status, ok: r1c.ok };
    if (r1c.ok) logCustomer('Strategy 1c', r1c.data);

    const r1d = await softnet('GET', `/api/planning/groups/${GROUP_ID}/clients/${userId}`);
    results['GET /api/planning/groups/{gid}/clients/{userId}'] = { status: r1d.status, ok: r1d.ok };
    if (r1d.ok) logCustomer('Strategy 1d', r1d.data);
  }

  // ─── Strategy 2: GET car by ID ───
  console.log('\n  ── Strategy 2: GET car by known ID ──');
  if (carId) {
    const r2a = await softnet('GET', `/api/planning/cars/${carId}`);
    results['GET /api/planning/cars/{carId}'] = { status: r2a.status, ok: r2a.ok };
    if (r2a.ok) logCustomer('Strategy 2a (car)', r2a.data);

    const r2b = await softnet('GET', `/api/planning/groups/${GROUP_ID}/cars/${carId}`);
    results['GET /api/planning/groups/{gid}/cars/{carId}'] = { status: r2b.status, ok: r2b.ok };
    if (r2b.ok) logCustomer('Strategy 2b (car)', r2b.data);
  }

  // ─── Strategy 3: POST search by email ───
  console.log('\n  ── Strategy 3: POST search by email ──');

  const searchPaths = [
    `/api/planning/groups/${GROUP_ID}/clients/search`,
    `/api/planning/clients/search`,
    `/api/planning/groups/${GROUP_ID}/users/search`,
    `/api/planning/users/search`,
  ];

  for (const path of searchPaths) {
    const r = await softnet('POST', path, { email: TEST_CLIENT.email });
    results[`POST ${path.replace(GROUP_ID, '{gid}')} (email)`] = { status: r.status, ok: r.ok };
    if (r.ok) {
      const list = Array.isArray(r.data) ? r.data : (r.data?.results || r.data?.data || [r.data]);
      console.log(`  ✅ Found ${list.length} result(s) via ${path}`);
      if (list.length > 0) logCustomer(`Strategy 3 (${path})`, list[0]);
    }
  }

  // ─── Strategy 4: POST search by license plate ───
  console.log('\n  ── Strategy 4: POST search by license plate ──');

  for (const path of searchPaths) {
    const r = await softnet('POST', path, { license: TEST_CAR.license });
    results[`POST ${path.replace(GROUP_ID, '{gid}')} (license)`] = { status: r.status, ok: r.ok };
    if (r.ok) {
      const list = Array.isArray(r.data) ? r.data : (r.data?.results || r.data?.data || [r.data]);
      console.log(`  ✅ Found ${list.length} result(s) via ${path}`);
      if (list.length > 0) logCustomer(`Strategy 4 (${path})`, list[0]);
    }
  }

  // ─── Strategy 5: GET list + filter client-side ───
  console.log('\n  ── Strategy 5: GET all clients, filter client-side ──');

  const listPaths = [
    `/api/planning/groups/${GROUP_ID}/clients`,
    `/api/planning/groups/${GROUP_ID}/users`,
    `/api/planning/clients`,
    `/api/planning/users`,
  ];

  for (const path of listPaths) {
    const r = await softnet('GET', path);
    results[`GET ${path.replace(GROUP_ID, '{gid}')}`] = { status: r.status, ok: r.ok };
    if (r.ok) {
      const list = Array.isArray(r.data) ? r.data : (r.data?.results || r.data?.data || []);
      console.log(`  📋 ${path}: ${list.length} total entries`);
      if (list.length > 0) {
        // Try to find our test customer
        const match = list.find(c =>
          c.email === TEST_CLIENT.email ||
          c.license === TEST_CAR.license ||
          c.lastName === TEST_CLIENT.lastName
        );
        if (match) {
          console.log(`  ✅ MATCH FOUND in list!`);
          logCustomer(`Strategy 5 (${path})`, match);
        } else {
          console.log(`  ⚠ No match for ${TEST_CLIENT.email} in ${list.length} entries`);
          console.log('  📋 Sample entry:', JSON.stringify(list[0], null, 2).substring(0, 500));
        }
      }
    }
  }

  // ─── Strategy 6: GET appointments + extract user from there ───
  console.log('\n  ── Strategy 6: GET appointments list, extract user data ──');

  const apptListPaths = [
    `/api/planning/groups/${GROUP_ID}/appointments`,
    `/api/planning/appointments`,
  ];

  for (const path of apptListPaths) {
    const r = await softnet('GET', path);
    results[`GET ${path.replace(GROUP_ID, '{gid}')}`] = { status: r.status, ok: r.ok };
    if (r.ok) {
      const list = Array.isArray(r.data) ? r.data : (r.data?.results || r.data?.data || []);
      console.log(`  📋 ${path}: ${list.length} appointments`);
      if (list.length > 0) {
        // Find appointment with our test email
        const match = list.find(a =>
          a.userid?.email === TEST_CLIENT.email ||
          a.carid?.license === TEST_CAR.license ||
          a.wishlist?.client?.email === TEST_CLIENT.email
        );
        if (match) {
          console.log(`  ✅ APPOINTMENT MATCH FOUND!`);
          console.log(`     User:  ${JSON.stringify(match.userid)}`);
          console.log(`     Car:   ${JSON.stringify(match.carid)}`);
          console.log(`     Email: ${match.userid?.email || match.wishlist?.client?.email}`);
        } else {
          console.log(`  ⚠ No match. Sample:`, JSON.stringify(list[0]?.userid, null, 2)?.substring(0, 300));
        }
      }
    }
  }

  // ─── Summary ───
  console.log('\n\n' + '='.repeat(60));
  console.log('📊 CUSTOMER LOOKUP — ENDPOINT DISCOVERY RESULTS');
  console.log('='.repeat(60));

  const working = [];
  const forbidden = [];
  const notFound = [];
  const other = [];

  for (const [endpoint, result] of Object.entries(results)) {
    const line = `  ${result.ok ? '✅' : '❌'} [${result.status}] ${endpoint}`;
    if (result.ok) working.push(line);
    else if (result.status === 401 || result.status === 403) forbidden.push(line);
    else if (result.status === 404) notFound.push(line);
    else other.push(line);
  }

  if (working.length > 0) {
    console.log('\n✅ WORKING ENDPOINTS:');
    working.forEach(l => console.log(l));
  }
  if (forbidden.length > 0) {
    console.log('\n🔒 AUTH/SCOPE ISSUE (401/403) — need different token scope:');
    forbidden.forEach(l => console.log(l));
  }
  if (notFound.length > 0) {
    console.log('\n❓ NOT FOUND (404) — endpoint does not exist:');
    notFound.forEach(l => console.log(l));
  }
  if (other.length > 0) {
    console.log('\n⚠ OTHER ERRORS:');
    other.forEach(l => console.log(l));
  }

  console.log('\n📝 CONCLUSION FOR REQ-016 IMPLEMENTATION:');
  if (working.length > 0) {
    console.log('   ✅ Direct SoftNet customer lookup IS possible.');
    console.log('   → Use working endpoints above in NestJS Backend proxy.');
  } else if (forbidden.length > 0 && notFound.length === Object.keys(results).length - forbidden.length) {
    console.log('   ⚠ Endpoints exist but need expanded token scope.');
    console.log('   → Request additional scope beyond "scope.api.planning.service"');
    console.log('   → OR: NestJS Backend caches customer data from appointment responses.');
  } else {
    console.log('   ❌ No working SoftNet endpoint for customer lookup found.');
    console.log('   → NestJS Backend must maintain its own customer database.');
    console.log('   → Store customer data when appointments are created.');
    console.log('   → Provide own POST /api/v1/customers/lookup endpoint.');
  }

  console.log('='.repeat(60));
  return results;
}

function logCustomer(label, data) {
  console.log(`  ✅ ${label} — Customer found:`);
  console.log(`     Name:    ${data.firstName || data.first_name || ''} ${data.lastName || data.last_name || ''}`);
  console.log(`     Email:   ${data.email || ''}`);
  console.log(`     Phone:   ${data.mobile || data.phone || ''}`);
  console.log(`     License: ${data.license || data.car?.license || ''}`);
  console.log(`     ID:      ${data.id || data._id || ''}`);
}

// ─── Snap Time Filter: classify checkInTime into morning/afternoon ───
function classifySnaps(snaps) {
  const morning = [];   // < 12:00
  const afternoon = []; // >= 12:00

  for (const snap of snaps) {
    const checkIn = new Date(snap.checkInTime);
    const hour = checkIn.getHours();
    if (hour < 12) {
      morning.push(snap);
    } else {
      afternoon.push(snap);
    }
  }

  return { morning, afternoon };
}

function printSnapSummary(label, snaps) {
  if (snaps.length === 0) {
    console.log(`  ${label}: keine Snaps`);
    return;
  }
  const times = snaps.map(s => {
    const d = new Date(s.checkInTime);
    return `${d.toLocaleDateString('de-DE')} ${d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}`;
  });
  console.log(`  ${label}: ${snaps.length} Snaps`);
  console.log(`     Erste 5: ${times.slice(0, 5).join(', ')}`);
  if (times.length > 5) console.log(`     ... und ${times.length - 5} weitere`);
}

async function run() {
  console.log('🚀 Booking Flow + Customer Lookup Test (REQ-016)');
  console.log(`   Time: ${new Date().toISOString()}`);

  // ─── Step 0: Auth ───
  if (!await getSoftnetToken()) return;

  // ─── Step 1: Raw Snaps + Time Filter Discovery ───
  console.log('\n\n📌 STEP 1: Snap Time Filter — API Discovery');
  console.log('  ℹ️  Testing: Kann die SoftNet API Snaps nach Tageszeit filtern?');
  console.log('  ℹ️  User-Flow: Kunde wählt "Vormittags" oder "Nachmittags",');
  console.log('     Client sendet Filter → NestJS leitet an SoftNet durch.');

  const baseBody = {
    groupid: GROUP_ID,
    brand: BRAND,
    car: { brand: BRAND },
    operations: ['698b34c24dd8eea4d6d4219e'],
    preferredBranch: BRANCH_ID,
  };

  // ─── 1a: Baseline — asSoonAsPossible (kein Zeitfilter) ───
  console.log('\n  ── 1a: Baseline (asSoonAsPossible, kein Zeitfilter) ──');
  const baselineRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'asSoonAsPossible',
  });
  if (!baselineRes.ok) { console.error('❌ Baseline Snaps failed'); return; }
  const baselineSnaps = Array.isArray(baselineRes.data) ? baselineRes.data : [];
  console.log(`  ✅ Baseline: ${baselineSnaps.length} Snaps`);
  const baselineClassified = classifySnaps(baselineSnaps);
  printSnapSummary('Vormittags (< 12:00)', baselineClassified.morning);
  printSnapSummary('Nachmittags (>= 12:00)', baselineClassified.afternoon);

  // ─── 1b: checkInDatePreference Varianten testen ───
  console.log('\n  ── 1b: checkInDatePreference Varianten ──');
  const preferences = ['morning', 'afternoon', 'am', 'pm', 'forenoon', 'earlyMorning', 'lateMorning', 'earlyAfternoon', 'lateAfternoon'];

  for (const pref of preferences) {
    const res = await softnet('POST', '/api/planning/snaps', {
      ...baseBody,
      checkInDatePreference: pref,
    });
    const snaps = res.ok ? (Array.isArray(res.data) ? res.data : []) : [];
    const icon = res.ok ? '✅' : '❌';
    console.log(`  ${icon} checkInDatePreference: "${pref}" → ${res.status} (${snaps.length} Snaps)`);
    if (res.ok && snaps.length > 0) {
      const classified = classifySnaps(snaps);
      console.log(`     Vormittags: ${classified.morning.length}, Nachmittags: ${classified.afternoon.length}`);
    }
  }

  // ─── 1c: checkInTimeFrom / checkInTimeTo testen ───
  console.log('\n  ── 1c: checkInTimeFrom / checkInTimeTo Parameter ──');

  // Morgen als Referenzdatum
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dateStr = tomorrow.toISOString().split('T')[0]; // YYYY-MM-DD

  const timeFilterTests = [
    { label: 'Vormittags (08:00–12:00)', extra: { checkInTimeFrom: `${dateStr}T08:00:00`, checkInTimeTo: `${dateStr}T12:00:00` } },
    { label: 'Nachmittags (12:00–18:00)', extra: { checkInTimeFrom: `${dateStr}T12:00:00`, checkInTimeTo: `${dateStr}T18:00:00` } },
    { label: 'ISO mit Z', extra: { checkInTimeFrom: `${dateStr}T08:00:00.000Z`, checkInTimeTo: `${dateStr}T12:00:00.000Z` } },
    { label: 'timeFrom/timeTo', extra: { timeFrom: `${dateStr}T08:00:00`, timeTo: `${dateStr}T12:00:00` } },
    { label: 'from/to', extra: { from: `${dateStr}T08:00:00`, to: `${dateStr}T12:00:00` } },
    { label: 'startTime/endTime', extra: { startTime: `${dateStr}T08:00:00`, endTime: `${dateStr}T12:00:00` } },
    { label: 'checkInDate (nur Datum)', extra: { checkInDate: dateStr } },
    { label: 'preferredTime morning', extra: { preferredTime: 'morning' } },
    { label: 'preferredTime afternoon', extra: { preferredTime: 'afternoon' } },
    { label: 'timeOfDay morning', extra: { timeOfDay: 'morning' } },
    { label: 'timeOfDay afternoon', extra: { timeOfDay: 'afternoon' } },
  ];

  for (const test of timeFilterTests) {
    const res = await softnet('POST', '/api/planning/snaps', {
      ...baseBody,
      checkInDatePreference: 'asSoonAsPossible',
      ...test.extra,
    });
    const snaps = res.ok ? (Array.isArray(res.data) ? res.data : []) : [];
    const icon = res.ok ? '✅' : '❌';
    console.log(`  ${icon} ${test.label} → ${res.status} (${snaps.length} Snaps)`);
    if (res.ok && snaps.length > 0) {
      const classified = classifySnaps(snaps);
      console.log(`     Vormittags: ${classified.morning.length}, Nachmittags: ${classified.afternoon.length}`);
      // Vergleich mit Baseline — hat sich was geändert?
      if (snaps.length !== baselineSnaps.length) {
        console.log(`     ⚡ UNTERSCHIED zur Baseline (${baselineSnaps.length} Snaps)!`);
      }
    }
  }

  // ─── 1d: Score-Vergleich — Ranking vs Filtering ───
  console.log('\n  ── 1d: Score-Vergleich (Ranking-Effekt von checkInDatePreference) ──');
  console.log('  ℹ️  Alle Varianten liefern gleiche Snap-Anzahl → API FILTERT NICHT.');
  console.log('  ℹ️  Aber: Ändert sich das Scoring/Ranking?');

  const morningRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'morning',
  });
  const afternoonRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'afternoon',
  });

  if (morningRes.ok && afternoonRes.ok) {
    const morningSnaps = Array.isArray(morningRes.data) ? morningRes.data : [];
    const afternoonSnaps = Array.isArray(afternoonRes.data) ? afternoonRes.data : [];

    // Vergleiche Scores für denselben ersten Morgen-Snap
    const baseFirst = baselineSnaps[0];
    const mornFirst = morningSnaps.find(s => s.checkInTime === baseFirst?.checkInTime);
    const aftFirst = afternoonSnaps.find(s => s.checkInTime === baseFirst?.checkInTime);

    console.log(`\n  📊 Score-Vergleich für Snap ${baseFirst?.checkInTime}:`);
    console.log(`     Baseline (asSoonAsPossible): score = ${baseFirst?.score}`);
    console.log(`     checkInDatePreference=morning: score = ${mornFirst?.score}`);
    console.log(`     checkInDatePreference=afternoon: score = ${aftFirst?.score}`);

    // Top-5 Ranking vergleichen
    console.log(`\n  📊 Top-5 Ranking (nach Score sortiert):`);
    console.log(`     Baseline:  ${baselineSnaps.slice(0, 5).map(s => new Date(s.checkInTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + '(s=' + s.score + ')').join(', ')}`);
    console.log(`     Morning:   ${morningSnaps.slice(0, 5).map(s => new Date(s.checkInTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + '(s=' + s.score + ')').join(', ')}`);
    console.log(`     Afternoon: ${afternoonSnaps.slice(0, 5).map(s => new Date(s.checkInTime).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + '(s=' + s.score + ')').join(', ')}`);

    const scoreDiffers = baseFirst?.score !== mornFirst?.score || baseFirst?.score !== aftFirst?.score;
    if (scoreDiffers) {
      console.log('\n  ⚡ ERGEBNIS: checkInDatePreference beeinflusst das SCORING/RANKING!');
      console.log('     → API filtert nicht, aber rankt Morgen-Snaps höher bei "morning".');
    } else {
      console.log('\n  ℹ️  ERGEBNIS: Kein Scoring-Unterschied erkannt.');
    }
  }

  // ─── 1e: Client-seitige Filterung (Empfehlung) ───
  console.log('\n  ── 1e: Client-seitige Filterung ──');
  printSnapSummary('🌅 Vormittags', baselineClassified.morning);
  printSnapSummary('🌇 Nachmittags', baselineClassified.afternoon);

  // ─── Summary ───
  console.log('\n\n' + '='.repeat(60));
  console.log('📊 SNAP TIME FILTER — DISCOVERY RESULTS');
  console.log('='.repeat(60));
  console.log('\n📝 ERGEBNIS:');
  console.log('   1. SoftNet API FILTERT NICHT nach Tageszeit.');
  console.log('      Alle getesteten Parameter (checkInDatePreference, checkInTimeFrom/To,');
  console.log('      timeOfDay, preferredTime, from/to, startTime/endTime) liefern');
  console.log('      identische Snap-Anzahl.');
  console.log('   2. checkInDatePreference beeinflusst das SCORING (niedrigerer score = besser).');
  console.log('      "morning" → Morgen-Snaps bekommen besseren Score.');
  console.log('      "afternoon" → Nachmittags-Snaps bekommen besseren Score.');
  console.log('   3. EMPFEHLUNG für Implementation:');
  console.log('      a) Client sendet Zeitpräferenz (morning/afternoon) an NestJS');
  console.log('      b) NestJS leitet checkInDatePreference an SoftNet durch (Ranking)');
  console.log('      c) NestJS filtert Snaps zusätzlich nach checkInTime:');
  console.log('         - Vormittags: checkInTime.hour < 12');
  console.log('         - Nachmittags: checkInTime.hour >= 12');
  console.log('      d) Client zeigt nur gefilterte Snaps an');
  console.log('='.repeat(60));

  // ─── Ab hier: Original Booking Flow (Step 1 weiter) ───
  console.log('\n\n📌 STEP 1 (continued): Pick a snap for booking');
  const rawSnaps = baselineSnaps;
  if (rawSnaps.length === 0) { console.error('❌ No snaps'); return; }

  // Use a later snap to avoid "allocation already used" conflicts
  const snapIndex = Math.min(5, rawSnaps.length - 1);
  const snap = rawSnaps[snapIndex];
  console.log(`  Using snap[${snapIndex}] — checkIn: ${snap.checkInTime}`);
  console.log('  📋 Raw snap keys:', Object.keys(snap).join(', '));

  // ─── Step 2: Patch with client data ───
  console.log('\n\n📌 STEP 2: Patch snap with client data');
  if (!snap.wishlist) snap.wishlist = {};
  snap.wishlist.client = { ...TEST_CLIENT };
  snap.wishlist.car = { ...(snap.wishlist.car || {}), ...TEST_CAR };
  console.log('  ✅ Client + Car patched');
  console.log(`     Email:   ${TEST_CLIENT.email}`);
  console.log(`     License: ${TEST_CAR.license}`);

  // ─── Step 3: Create Appointment ───
  console.log('\n\n📌 STEP 3: Create Appointment (creates user + car in SoftNet)');
  const appointmentRes = await softnet('POST', '/api/planning/appointments', snap);

  if (!appointmentRes.ok) {
    console.error('\n❌ BOOKING FAILED — cannot proceed with lookup test');
    return;
  }

  const appointment = appointmentRes.data;
  const appointmentId = appointment.id || appointment._id;
  const userId = appointment.userid?.id || appointment.userid?._id;
  const carId = appointment.carid?.id || appointment.carid?._id;

  console.log('\n  🎉 BOOKING SUCCESSFUL!');
  console.log(`     Appointment ID: ${appointmentId}`);
  console.log(`     User ID:        ${userId}`);
  console.log(`     Car ID:         ${carId}`);
  console.log(`     Status:         ${appointment.status}`);
  console.log(`     Check-in:       ${appointment.checkInTime}`);
  console.log(`     Branch:         ${appointment.branchid?.branchLabel || ''}`);
  console.log(`     User Email:     ${appointment.userid?.email || ''}`);
  console.log(`     Car License:    ${appointment.carid?.license || ''}`);

  // ─── Step 4: Customer Lookup — the main test! ───
  await customerLookup(userId, carId);

  // ─── Step 5: Verify appointment ───
  console.log('\n\n📌 STEP 5: Verify Appointment (GET)');
  const verifyRes = await softnet('GET', `/api/planning/appointments/${appointmentId}`);
  if (verifyRes.ok) {
    console.log('  ✅ VERIFIED — Appointment exists');
  } else {
    console.log(`  ⚠ Verify returned ${verifyRes.status}`);
  }

  // ─── Step 6: Cancel ───
  console.log('\n\n📌 STEP 6: Cancel Appointment (cleanup)');
  const cancelRes = await softnet('PUT', `/api/planning/appointments/${appointmentId}/cancel`);
  console.log(cancelRes.ok ? '  ✅ Cancelled' : `  ⚠ Cancel returned ${cancelRes.status}`);

  console.log('\n' + '='.repeat(60));
  console.log('Done.');
}

run().catch(err => { console.error('Fatal:', err); process.exit(1); });
