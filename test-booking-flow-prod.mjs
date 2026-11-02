#!/usr/bin/env node
/**
 * PROD Booking Flow Test — Frau Voß (Wuppertal), Audi Mettmann, Inspektion
 *
 * WICHTIG: Zielt auf das Prod-System. Einmalig zum Verifizieren + direkt stornieren.
 * Nicht in Dauerschleife laufen lassen, sonst sperrt SoftNet den Service-Account.
 */

import { readFileSync } from 'fs';
import { createSign } from 'crypto';

// ─── Prod Config ───
const SOFTNET_BASE  = 'https://api.soft-nrg.com';
const SOFTNET_AUTH  = 'https://auth.soft-nrg.com/oauth/token';
const GROUP_ID      = '6908807454f4bb2b69b03fde';        // Prod Group
const BRANCH_ID     = '68f606798d2bb4389ed1af51';        // Audi Mettmann
const BRAND         = 'audi';
const OPERATION_ID  = '6908807454f4bb2b69b03ffc';        // Inspektion
const FINGERPRINT   = JSON.parse(readFileSync('./cognigy.net_fingerprint-e6ced285a2bfce274426c261cca6edcd.json', 'utf-8'));

let softnetToken = '';

async function getSoftnetToken() {
  console.log('\n📌 STEP 0: Get SoftNet access token (PROD)');
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
  console.log(`  ✅ Token (expires ${data.expires_in}s)`);
  return true;
}

async function softnet(method, path, body) {
  const url = `${SOFTNET_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${softnetToken}`,
    'x-nrg-group': GROUP_ID,
  };
  console.log(`\n→ ${method} ${path}`);
  const start = Date.now();
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const duration = Date.now() - start;
  const text = await res.text();
  let data; try { data = JSON.parse(text); } catch { data = text; }
  console.log(`← ${res.status} ${res.statusText} (${duration}ms)`);
  if (!res.ok) console.error('  ERROR:', JSON.stringify(data, null, 2).substring(0, 1500));
  return { ok: res.ok, status: res.status, data };
}

async function run() {
  console.log('🚀 PROD Booking Flow — Frau Voß (Wuppertal) @ Audi Mettmann');
  console.log(`   Time: ${new Date().toISOString()}`);

  if (!await getSoftnetToken()) return;

  // ─── Step 1: Snaps ───
  console.log('\n📌 STEP 1: Get Snaps');
  const snapsRes = await softnet('POST', '/api/planning/snaps', {
    groupid: GROUP_ID,
    brand: BRAND,
    car: { brand: BRAND },
    operations: [OPERATION_ID],
    preferredBranch: BRANCH_ID,
    checkInDatePreference: 'asSoonAsPossible',
  });
  if (!snapsRes.ok) { console.error('❌ Snaps failed — STOP'); return; }
  const rawSnaps = Array.isArray(snapsRes.data) ? snapsRes.data : [];
  if (rawSnaps.length === 0) { console.error('❌ No snaps returned — STOP'); return; }
  console.log(`  ✅ ${rawSnaps.length} snaps — erster: ${rawSnaps[0].checkInTime || '(kein checkInTime)'}`);

  const snap = rawSnaps[0];

  // ─── Step 2: Patch with Frau Voß ───
  console.log('\n📌 STEP 2: Patch snap — Frau Voß (Wuppertal)');
  if (!snap.wishlist) snap.wishlist = {};
  snap.wishlist.client = {
    email: 'test-voss@autohaus-test.de',
    lastName: 'Voß',
    firstName: 'Petra',
    mobile: '+492021234567',
    phone: '+492021234567',
    address: 'Wuppertaler Str. 12',
    zipCode: '42103',
    city: 'Wuppertal',
    country: 'DE',
    language: 'de',
  };
  snap.wishlist.car = {
    ...(snap.wishlist.car || {}),
    brand: BRAND,
    license: 'W-TEST-99',
    VIN: 'UNKNOWN',
    currentMileage: 45000,
    firstRegMonth: '03',
    firstRegYear: '2023',
    model: 'A4 Avant',
    color: 'schwarz',
  };
  console.log('  ✅ Patched');

  // ─── Step 3: Create appointment ───
  console.log('\n📌 STEP 3: Create appointment');
  const apptRes = await softnet('POST', '/api/planning/appointments', snap);
  if (!apptRes.ok) { console.error('❌ BOOKING FAILED'); return; }

  const a = apptRes.data;
  const apptId = a.id || a._id;
  console.log(`\n🎉 BOOKING OK!`);
  console.log(`   ID:       ${apptId}`);
  console.log(`   Status:   ${a.status}`);
  console.log(`   Check-in: ${a.checkInTime}`);
  console.log(`   Branch:   ${a.branch?.name || ''}`);

  // ─── Step 4: Cancel immediately (cleanup) ───
  console.log('\n📌 STEP 4: Cancel (cleanup)');
  const cancelRes = await softnet('PUT', `/api/planning/appointments/${apptId}/cancel`);
  console.log(cancelRes.ok ? '  ✅ Cancelled' : '  ⚠ Cancel failed — bitte in SoftNet manuell stornieren');

  console.log('\n' + '='.repeat(60));
  console.log('Done.');
}

run().catch(err => { console.error('Fatal:', err); process.exit(1); });
