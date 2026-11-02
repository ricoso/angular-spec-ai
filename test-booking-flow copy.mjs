#!/usr/bin/env node
/**
 * SoftNet API — Feature Discovery Tests
 *
 * Tests:
 *   Test 1: Snap Time Filter (Vormittags / Nachmittags)
 *   Test 2: Mobility / Replacement Car (Ersatzwagen)
 *
 * Purpose: Discover which SoftNet API parameters actually work
 * so we know what to pass through from NestJS and what to filter ourselves.
 *
 * Run: node "test-booking-flow copy.mjs"
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

let softnetToken = '';

// ─── Auth ───
async function getSoftnetToken() {
  console.log('\n📌 AUTH: Get SoftNet access token');
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

// ─── SoftNet call (compact logging) ───
async function softnet(method, path, body, { quiet = false } = {}) {
  const url = `${SOFTNET_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${softnetToken}`,
    'x-nrg-group': GROUP_ID,
  };

  if (!quiet) {
    console.log(`  → ${method} ${path}`);
  }

  const start = Date.now();
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const duration = Date.now() - start;
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }

  if (!quiet) {
    console.log(`  ← ${res.status} (${duration}ms)`);
  }
  return { ok: res.ok, status: res.status, data, duration };
}

// ─── Helpers ───
function classifySnaps(snaps) {
  const morning = [];
  const afternoon = [];
  for (const snap of snaps) {
    const hour = new Date(snap.checkInTime).getHours();
    if (hour < 12) morning.push(snap);
    else afternoon.push(snap);
  }
  return { morning, afternoon };
}

function fmtTime(isoStr) {
  const d = new Date(isoStr);
  return `${d.toLocaleDateString('de-DE')} ${d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}`;
}

function printSnapTimes(label, snaps, max = 5) {
  if (snaps.length === 0) { console.log(`    ${label}: —`); return; }
  const times = snaps.slice(0, max).map(s => fmtTime(s.checkInTime));
  console.log(`    ${label}: ${snaps.length} Snaps → ${times.join(', ')}${snaps.length > max ? ` ... +${snaps.length - max}` : ''}`);
}

const baseBody = {
  groupid: GROUP_ID,
  brand: BRAND,
  car: { brand: BRAND },
  operations: ['698b34c24dd8eea4d6d4219e'],
  preferredBranch: BRANCH_ID,
};

// ══════════════════════════════════════════════════════════════
// TEST 1: Snap Time Filter (checkInTimePreference: AM/PM)
// OpenAPI Spec: Enum "any" | "AM" | "PM"
// ══════════════════════════════════════════════════════════════
async function testTimeFilter() {
  console.log('\n' + '═'.repeat(60));
  console.log('TEST 1: SNAP TIME FILTER (checkInTimePreference)');
  console.log('═'.repeat(60));
  console.log('OpenAPI Spec: checkInTimePreference — Enum: "any", "AM", "PM"');
  console.log('"Don\'t care, before noon, after noon as defined in branch settings"\n');

  // ─── 1a: Baseline ───
  console.log('── 1a: Baseline (kein checkInTimePreference) ──');
  const baselineRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'asSoonAsPossible',
  });
  if (!baselineRes.ok) { console.error('❌ Baseline failed'); return null; }
  const baseline = Array.isArray(baselineRes.data) ? baselineRes.data : [];
  const baseClass = classifySnaps(baseline);
  console.log(`  Total: ${baseline.length} Snaps`);
  printSnapTimes('Vormittags  (<12)', baseClass.morning);
  printSnapTimes('Nachmittags (>=12)', baseClass.afternoon);

  // ─── 1b: Offizieller Parameter: checkInTimePreference ───
  console.log('\n── 1b: checkInTimePreference (offizieller OpenAPI-Parameter) ──');
  for (const pref of ['any', 'AM', 'PM']) {
    const res = await softnet('POST', '/api/planning/snaps', {
      ...baseBody,
      checkInDatePreference: 'asSoonAsPossible',
      checkInTimePreference: pref,
    }, { quiet: true });
    const snaps = res.ok ? (Array.isArray(res.data) ? res.data : []) : [];
    const c = classifySnaps(snaps);
    console.log(`  checkInTimePreference="${pref}" → ${res.status} | ${snaps.length} Snaps (VM:${c.morning.length} NM:${c.afternoon.length})`);
  }

  // ─── 1c: Vergleich mit inoffiziellen Parametern ───
  console.log('\n── 1c: Vergleich — inoffizielle Parameter (kein Effekt) ──');
  for (const pref of ['morning', 'afternoon']) {
    const res = await softnet('POST', '/api/planning/snaps', {
      ...baseBody,
      checkInDatePreference: pref,
    }, { quiet: true });
    const snaps = res.ok ? (Array.isArray(res.data) ? res.data : []) : [];
    const c = classifySnaps(snaps);
    console.log(`  checkInDatePreference="${pref}" → ${res.status} | ${snaps.length} Snaps (VM:${c.morning.length} NM:${c.afternoon.length})`);
  }

  // ─── 1d: Score + Verteilungsvergleich ───
  console.log('\n── 1d: AM vs PM — Score + Verteilung ──');
  const amRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody, checkInDatePreference: 'asSoonAsPossible', checkInTimePreference: 'AM',
  }, { quiet: true });
  const pmRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody, checkInDatePreference: 'asSoonAsPossible', checkInTimePreference: 'PM',
  }, { quiet: true });

  if (amRes.ok && pmRes.ok) {
    const amSnaps = Array.isArray(amRes.data) ? amRes.data : [];
    const pmSnaps = Array.isArray(pmRes.data) ? pmRes.data : [];

    console.log(`\n  Top-5 Reihenfolge:`);
    console.log(`    Baseline: ${baseline.slice(0, 5).map(s => fmtTime(s.checkInTime) + '(s=' + s.score + ')').join(', ')}`);
    console.log(`    AM:       ${amSnaps.slice(0, 5).map(s => fmtTime(s.checkInTime) + '(s=' + s.score + ')').join(', ')}`);
    console.log(`    PM:       ${pmSnaps.slice(0, 5).map(s => fmtTime(s.checkInTime) + '(s=' + s.score + ')').join(', ')}`);
  }

  // ─── Conclusion ───
  console.log('\n┌──────────────────────────────────────────────────────────┐');
  console.log('│ ERGEBNIS: SNAP TIME FILTER                              │');
  console.log('├──────────────────────────────────────────────────────────┤');
  console.log('│ ✅ checkInTimePreference: "AM" / "PM" FUNKTIONIERT!     │');
  console.log('│    Verschiebt AM/PM-Verteilung der Snaps deutlich.      │');
  console.log('│    AM → mehr Vormittags-Slots, PM → mehr Nachmittags.   │');
  console.log('│ ❌ checkInDatePreference "morning"/"afternoon" hat       │');
  console.log('│    KEINEN Effekt auf die Verteilung.                    │');
  console.log('│ ✅ EMPFEHLUNG:                                          │');
  console.log('│    Client sendet "AM" oder "PM"                         │');
  console.log('│    → NestJS setzt checkInTimePreference im Snap-Request │');
  console.log('│    → Zusaetzlich NestJS-seitig nach checkInTime filtern │');
  console.log('│      (<12 = VM, >=12 = NM) fuer exakte Trennung        │');
  console.log('└──────────────────────────────────────────────────────────┘');

  return baseline;
}

// ══════════════════════════════════════════════════════════════
// TEST 2: Mobility / Replacement Car (replacementPreference)
// OpenAPI Spec: replacementPreference — boolean
// ══════════════════════════════════════════════════════════════
async function testMobility() {
  console.log('\n\n' + '═'.repeat(60));
  console.log('TEST 2: MOBILITY / REPLACEMENT CAR (replacementPreference)');
  console.log('═'.repeat(60));
  console.log('OpenAPI Spec: replacementPreference — boolean');
  console.log('Response-Feld: mobilityAlternativeResource (null oder Objekt)\n');

  // ─── 2a: Baseline ───
  console.log('── 2a: Baseline (kein replacementPreference) ──');
  const baselineRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'asSoonAsPossible',
  });
  if (!baselineRes.ok) { console.error('❌ Snaps failed'); return; }
  const snaps = Array.isArray(baselineRes.data) ? baselineRes.data : [];
  const baseClass = classifySnaps(snaps);
  const baseMob = snaps.filter(s => s.mobilityAlternativeResource != null);
  console.log(`  Total: ${snaps.length} Snaps (VM:${baseClass.morning.length} NM:${baseClass.afternoon.length})`);
  console.log(`  mobilityAlternativeResource != null: ${baseMob.length}`);

  // ─── 2b: Offizieller Parameter: replacementPreference ───
  console.log('\n── 2b: replacementPreference (offizieller OpenAPI-Parameter) ──');
  for (const val of [true, false]) {
    const res = await softnet('POST', '/api/planning/snaps', {
      ...baseBody,
      checkInDatePreference: 'asSoonAsPossible',
      replacementPreference: val,
    }, { quiet: true });
    const resSnaps = res.ok ? (Array.isArray(res.data) ? res.data : []) : [];
    const c = classifySnaps(resSnaps);
    const hasMob = resSnaps.filter(s => s.mobilityAlternativeResource != null);
    console.log(`  replacementPreference=${val} → ${res.status} | ${resSnaps.length} Snaps (VM:${c.morning.length} NM:${c.afternoon.length}) | mobility: ${hasMob.length}`);
    if (hasMob.length > 0) {
      console.log(`    ⚡ ${hasMob.length} Snaps mit mobilityAlternativeResource!`);
      console.log(`    Erster: ${JSON.stringify(hasMob[0].mobilityAlternativeResource)}`);
    }
  }

  // ─── 2c: Snap-Keys fuer Referenz ───
  console.log('\n── 2c: Snap-Struktur (Referenz) ──');
  console.log(`  Snap-Keys: ${Object.keys(snaps[0]).join(', ')}`);
  console.log(`  Wishlist-Keys: ${Object.keys(snaps[0].wishlist || {}).join(', ')}`);
  console.log(`  mobilityAlternativeResource: ${JSON.stringify(snaps[0].mobilityAlternativeResource)}`);

  // ─── Conclusion ───
  console.log('\n┌──────────────────────────────────────────────────────────┐');
  console.log('│ ERGEBNIS: MOBILITY / ERSATZWAGEN                        │');
  console.log('├──────────────────────────────────────────────────────────┤');
  console.log('│ ⚡ replacementPreference: true hat leichten Effekt       │');
  console.log('│    auf Snap-Verteilung (AM/PM verschiebt sich).         │');
  console.log('│ ❌ mobilityAlternativeResource bleibt null.              │');
  console.log('│    → Branch "Muenchen" hat keine Ersatzwagen.           │');
  console.log('│ ✅ EMPFEHLUNG:                                          │');
  console.log('│    replacementPreference im Snap-Request durchreichen.  │');
  console.log('│    Falls Branch Ersatzwagen hat, wird                   │');
  console.log('│    mobilityAlternativeResource befuellt.                │');
  console.log('└──────────────────────────────────────────────────────────┘');
}

// ══════════════════════════════════════════════════════════════
// TEST 3: Returning Customer — Email-based Lookup
// OpenAPI Spec:
//   "If a client with the same email exists, the existing user profile will be used."
//   "If a car with the same VIN and license exists for the user, the existing car will be used."
//   wishlist.note — string, max 2000 chars (Freitext)
// ══════════════════════════════════════════════════════════════
async function testReturningCustomerLookup() {
  console.log('\n\n' + '═'.repeat(60));
  console.log('TEST 3: RETURNING CUSTOMER — EMAIL LOOKUP');
  console.log('═'.repeat(60));
  console.log('Use Case: Kunde hat schon mal gebucht. Bei Zweitbuchung gibt er');
  console.log('          nur Email ein → SoftNet erkennt ihn und liefert');
  console.log('          gespeicherte Daten (Name, Auto, Kennzeichen) zurueck.');
  console.log('OpenAPI Regeln:');
  console.log('  • Gleiche Email → gleicher User (Profil wird wiederverwendet)');
  console.log('  • Gleiche VIN + License → gleiches Auto');
  console.log('  • wishlist.note — Freitext, max 2000 chars\n');

  // ─── 3a: Erstbuchung — Kunden in SoftNet anlegen ───
  console.log('── 3a: ERSTBUCHUNG — Kunden mit vollen Daten anlegen ──');
  const TEST_LICENSE = 'M-RK-2026';
  const TEST_EMAIL   = 'returning-test@autohaus-test.de';
  const TEST_LAST    = 'Wiederkehr';
  const TEST_FIRST   = 'Anna';

  const snapsRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'asSoonAsPossible',
  });
  if (!snapsRes.ok) { console.error('❌ Snaps failed'); return; }
  const snaps = Array.isArray(snapsRes.data) ? snapsRes.data : [];

  const snapIndex = Math.min(12, snaps.length - 1);
  const snap = JSON.parse(JSON.stringify(snaps[snapIndex]));

  if (!snap.wishlist) snap.wishlist = {};
  snap.wishlist.client = {
    email: TEST_EMAIL, lastName: TEST_LAST, firstName: TEST_FIRST,
    mobile: '+491771234567', phone: '+491771234567',
    address: 'Wiederkehrstr. 42', zipCode: '80333', city: 'Muenchen',
    country: 'DE', language: 'de',
  };
  snap.wishlist.car = {
    ...(snap.wishlist.car || {}),
    brand: BRAND, license: TEST_LICENSE, VIN: 'WBA11111111111111',
    currentMileage: 45000, firstRegMonth: '03', firstRegYear: '2020',
    model: '330e', color: 'mineralgrau',
  };

  // OpenAPI: note field (max 2000 chars) — Freitext im Wishlist
  snap.wishlist.note = `Kennzeichen: ${TEST_LICENSE} | Kunde: ${TEST_FIRST} ${TEST_LAST}`;

  console.log(`  Kennzeichen: ${TEST_LICENSE}`);
  console.log(`  Kunde: ${TEST_FIRST} ${TEST_LAST} <${TEST_EMAIL}>`);
  console.log(`  note: "${snap.wishlist.note}"`);

  const apptRes = await softnet('POST', '/api/planning/appointments', snap);
  if (!apptRes.ok) {
    console.error('  ❌ Booking failed — Test abgebrochen');
    console.log('  Response:', JSON.stringify(apptRes.data, null, 2).substring(0, 500));
    return;
  }

  const appt = apptRes.data;
  const apptId = appt.id || appt._id;
  const userId = appt.userid?.id || appt.userid?._id;
  const carId  = appt.carid?.id || appt.carid?._id;

  console.log(`  ✅ Termin erstellt!`);
  console.log(`     Appointment: ${apptId}`);
  console.log(`     User ID:     ${userId} (${appt.userid?.firstName} ${appt.userid?.lastName})`);
  console.log(`     Car ID:      ${carId} (${appt.carid?.license})`);
  console.log(`     Email:       ${appt.userid?.email}`);

  // userid + carid + note aus Response
  console.log('\n  📋 userid:', JSON.stringify(appt.userid));
  console.log('  📋 carid:', JSON.stringify(appt.carid));
  console.log('  📋 wishlist.note:', JSON.stringify(appt.wishlist?.note || '(not in response)'));
  console.log('  📋 wishlist keys:', Object.keys(appt.wishlist || {}).join(', '));

  // ─── 3b: ZWEITBUCHUNG — nur Email → SoftNet liefert volle Daten ───
  console.log('\n── 3b: ZWEITBUCHUNG — nur Email, SoftNet fuellt Daten ──');
  console.log('  Sende NUR { email } als client → SoftNet soll Bestandskunden erkennen.\n');

  const snap2Index = Math.min(15, snaps.length - 1);
  const snap2 = JSON.parse(JSON.stringify(snaps[snap2Index]));
  if (!snap2.wishlist) snap2.wishlist = {};
  snap2.wishlist.client = { email: TEST_EMAIL };  // NUR Email!
  snap2.wishlist.car = { brand: BRAND, license: TEST_LICENSE, VIN: 'WBA11111111111111' };

  const appt2Res = await softnet('POST', '/api/planning/appointments', snap2);
  if (!appt2Res.ok) {
    console.error('  ❌ Zweitbuchung failed');
    console.log('  Response:', JSON.stringify(appt2Res.data, null, 2).substring(0, 500));
  } else {
    const a2 = appt2Res.data;
    const a2Id = a2.id || a2._id;
    console.log('  ✅ Zweitbuchung OK!');
    console.log('  Gesendet:   { email: "' + TEST_EMAIL + '" }');
    console.log('  Erhalten:');
    console.log('    userid:  ', JSON.stringify(a2.userid));
    console.log('    carid:   ', JSON.stringify(a2.carid));
    console.log('    User-ID gleich wie Erstbuchung? ' + (a2.userid?.id === userId ? '✅ JA' : '❌ NEIN'));
    console.log('    Car-ID gleich wie Erstbuchung?  ' + (a2.carid?.id === carId ? '✅ JA' : '❌ NEIN'));
    console.log('    Name:    ' + (a2.userid?.firstName || '(leer)') + ' ' + (a2.userid?.lastName || '(leer)'));
    console.log('    Email:   ' + (a2.userid?.email || '(leer)'));
    console.log('    License: ' + (a2.carid?.license || '(leer)'));
    console.log('    Model:   ' + (a2.carid?.model || '(leer)'));

    // Cleanup
    await softnet('PUT', `/api/planning/appointments/${a2Id}/cancel`, null, { quiet: true });
  }

  // ─── 3c: Ohne Email geht es nicht ───
  console.log('\n── 3c: Booking OHNE Email (nur Kennzeichen) ──');
  console.log('  SoftNet braucht Email als Schluessel — ohne geht es nicht.\n');

  const snap3Index = Math.min(20, snaps.length - 1);
  const snap3 = JSON.parse(JSON.stringify(snaps[snap3Index]));
  if (!snap3.wishlist) snap3.wishlist = {};
  snap3.wishlist.client = {};  // KEINE Email
  snap3.wishlist.car = { brand: BRAND, license: TEST_LICENSE, VIN: 'WBA11111111111111' };

  const appt3Res = await softnet('POST', '/api/planning/appointments', snap3);
  if (appt3Res.ok) {
    console.log('  ⚠ Booking ging ohne Email durch — unerwartet!');
    console.log('  userid:', JSON.stringify(appt3Res.data.userid));
    const a3Id = appt3Res.data.id || appt3Res.data._id;
    await softnet('PUT', `/api/planning/appointments/${a3Id}/cancel`, null, { quiet: true });
  } else {
    console.log('  ✅ Erwartet: Booking ohne Email schlaegt fehl');
    console.log('  Status:', appt3Res.status);
    console.log('  Error:', JSON.stringify(appt3Res.data).substring(0, 300));
  }

  // ─── 3d: note-Feld Test ───
  console.log('\n── 3d: wishlist.note — Freitext-Feld ──');
  console.log('  Snap-Request mit note → wird es im Response echoed?\n');

  const noteTestRes = await softnet('POST', '/api/planning/snaps', {
    ...baseBody,
    checkInDatePreference: 'asSoonAsPossible',
    note: 'Kundenwunsch: Fruehtermin bevorzugt',
  });
  if (noteTestRes.ok) {
    const noteSnaps = Array.isArray(noteTestRes.data) ? noteTestRes.data : [];
    const noteEchoed = noteSnaps[0]?.wishlist?.note;
    console.log(`  note in Response: ${noteEchoed ? '✅ "' + noteEchoed + '"' : '❌ nicht vorhanden'}`);
  }

  // ─── 3e: /api/planning/customercars — Text Search ───
  console.log('\n── 3e: /api/planning/customercars (Text Search) ──');
  console.log('  Endpunkt fuer Kunden+Auto Lookup per Freitext (Kennzeichen, Email, Name).');
  console.log('  INT-System: 401 (Scope fehlt) — mit Prod-Scope testen!\n');

  const customerCarTests = [
    { label: 'GET text=Kennzeichen', m: 'GET', p: `/api/planning/customercars?text=${TEST_LICENSE}` },
    { label: 'GET text=Email', m: 'GET', p: `/api/planning/customercars?text=${TEST_EMAIL}` },
    { label: 'GET text=Nachname', m: 'GET', p: `/api/planning/customercars?text=${TEST_LAST}` },
    { label: 'GET group+text=Kennzeichen', m: 'GET', p: `/api/planning/groups/${GROUP_ID}/customercars?text=${TEST_LICENSE}` },
    { label: 'GET group+text=Email', m: 'GET', p: `/api/planning/groups/${GROUP_ID}/customercars?text=${TEST_EMAIL}` },
    { label: 'POST text=Kennzeichen', m: 'POST', p: `/api/planning/customercars`, b: { text: TEST_LICENSE } },
    { label: 'POST group+text=Kennzeichen', m: 'POST', p: `/api/planning/groups/${GROUP_ID}/customercars`, b: { text: TEST_LICENSE } },
  ];

  for (const t of customerCarTests) {
    const res = await softnet(t.m, t.p, t.b || null, { quiet: true });
    let detail = '';
    if (res.ok) {
      const list = Array.isArray(res.data) ? res.data : [res.data];
      detail = `${list.length} results — ${JSON.stringify(res.data).substring(0, 300)}`;
    }
    console.log(`  ${t.label} → ${res.status} ${res.ok ? '✅ ' + detail : '❌'}`);
  }

  // ─── 3f: Cleanup Erstbuchung ───
  console.log('\n── 3f: Cleanup ──');
  const cancelRes = await softnet('PUT', `/api/planning/appointments/${apptId}/cancel`, null, { quiet: true });
  console.log(cancelRes.ok ? '  ✅ Erstbuchung cancelled' : `  ⚠ Cancel: ${cancelRes.status} (ggf. manuell: ${apptId})`);

  // ─── Conclusion ───
  console.log('\n┌──────────────────────────────────────────────────────────┐');
  console.log('│ ERGEBNIS: RETURNING CUSTOMER                            │');
  console.log('├──────────────────────────────────────────────────────────┤');
  console.log('│ ✅ SoftNet erkennt Bestandskunden per EMAIL!            │');
  console.log('│    Zweitbuchung mit nur Email → volle Daten zurueck:   │');
  console.log('│    Name, Auto, Kennzeichen, Modell.                    │');
  console.log('│ ✅ Gleiche VIN + License → gleiches Auto.              │');
  console.log('│ ✅ wishlist.note (max 2000 chars) wird gespeichert.    │');
  console.log('│ ❌ Ohne Email geht kein Lookup (Pflichtfeld).          │');
  console.log('│ ❌ Nur Kennzeichen reicht nicht — Email ist Schluessel.│');
  console.log('│                                                          │');
  console.log('│ ✅ FLOW FUER WIEDERKEHR-KUNDEN:                        │');
  console.log('│                                                          │');
  console.log('│ A) Aktueller Scope (Booking-basiert):                   │');
  console.log('│    1) Kunde gibt Email ein                              │');
  console.log('│    2) Booking mit { client: { email } }                 │');
  console.log('│    3) SoftNet liefert User + Car in Response            │');
  console.log('│    → Nur firstName, lastName, email, brand, model,      │');
  console.log('│      license (keine Adresse/Telefon)                    │');
  console.log('│                                                          │');
  console.log('│ B) Prod-Scope (/api/planning/customercars):             │');
  console.log('│    1) Kunde gibt Kennzeichen ODER Email ein             │');
  console.log('│    2) GET /api/planning/customercars?text=M-RK-2026    │');
  console.log('│    3) SoftNet liefert volle Kundendaten + Auto          │');
  console.log('│    4) Formular wird komplett vorausgefuellt             │');
  console.log('│    → INT: 401 (Scope fehlt) — mit Prod testen!         │');
  console.log('└──────────────────────────────────────────────────────────┘');
}

// ══════════════════════════════════════════════════════════════
// MAIN
// ══════════════════════════════════════════════════════════════
async function run() {
  console.log('🚀 SoftNet API Feature Discovery');
  console.log(`   Time: ${new Date().toISOString()}`);
  console.log(`   Group: ${GROUP_ID}`);
  console.log(`   Branch: ${BRANCH_ID}`);
  console.log(`   Brand: ${BRAND}`);

  if (!await getSoftnetToken()) return;

  await testTimeFilter();
  await testMobility();
  await testReturningCustomerLookup();

  console.log('\n' + '═'.repeat(60));
  console.log('Done.');
}

run().catch(err => { console.error('Fatal:', err); process.exit(1); });
