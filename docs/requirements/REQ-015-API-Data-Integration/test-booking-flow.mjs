#!/usr/bin/env node
/**
 * Full booking flow test — uses backend for auth, then calls SoftNet
 * directly to test correct appointment format (send back whole snap).
 *
 * Steps: backend token → SoftNet raw snaps → patch snap → create appointment → GET verify
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
    console.log('  Response (preview):', JSON.stringify(data, null, 2).substring(0, 1500));
  } else {
    console.error('  ERROR:', JSON.stringify(data, null, 2).substring(0, 2000));
  }
  return { ok: res.ok, status: res.status, data };
}

async function run() {
  console.log('🚀 Booking Flow — Direct SoftNet Test');
  console.log(`   Time: ${new Date().toISOString()}`);

  // ─── Step 0: Auth ───
  if (!await getSoftnetToken()) return;

  // ─── Step 0b: Customer Lookup by Email + License Plate ───
  console.log('\n\n📌 STEP 0b: Customer Lookup (Email + License Plate)');

  const LOOKUP_EMAIL = 'test@autohaus-test.de';
  const LOOKUP_LICENSE = 'M-TEST-123';

  // Search by email
  console.log(`\n  🔍 Searching by email: ${LOOKUP_EMAIL}`);
  const emailLookup = await softnet('POST', `/api/planning/groups/${GROUP_ID}/clients/search`, {
    email: LOOKUP_EMAIL,
  });
  if (emailLookup.ok) {
    const clients = Array.isArray(emailLookup.data) ? emailLookup.data : (emailLookup.data?.results || []);
    console.log(`  ✅ Email lookup: ${clients.length} result(s)`);
    if (clients.length > 0) {
      console.log('  📋 First client:', JSON.stringify(clients[0], null, 2).substring(0, 1000));
    }
  } else {
    console.log(`  ⚠ Email lookup returned ${emailLookup.status} — endpoint may not exist yet`);
  }

  // Search by license plate
  console.log(`\n  🔍 Searching by license plate: ${LOOKUP_LICENSE}`);
  const licenseLookup = await softnet('POST', `/api/planning/groups/${GROUP_ID}/clients/search`, {
    license: LOOKUP_LICENSE,
  });
  if (licenseLookup.ok) {
    const clients = Array.isArray(licenseLookup.data) ? licenseLookup.data : (licenseLookup.data?.results || []);
    console.log(`  ✅ License lookup: ${clients.length} result(s)`);
    if (clients.length > 0) {
      console.log('  📋 First client:', JSON.stringify(clients[0], null, 2).substring(0, 1000));
    }
  } else {
    console.log(`  ⚠ License lookup returned ${licenseLookup.status} — endpoint may not exist yet`);
  }

  // Combined search (both fields)
  console.log(`\n  🔍 Combined search: email=${LOOKUP_EMAIL} + license=${LOOKUP_LICENSE}`);
  const combinedLookup = await softnet('POST', `/api/planning/groups/${GROUP_ID}/clients/search`, {
    email: LOOKUP_EMAIL,
    license: LOOKUP_LICENSE,
  });
  if (combinedLookup.ok) {
    const clients = Array.isArray(combinedLookup.data) ? combinedLookup.data : (combinedLookup.data?.results || []);
    console.log(`  ✅ Combined lookup: ${clients.length} result(s)`);
    if (clients.length > 0) {
      const c = clients[0];
      console.log('  📋 Customer found:');
      console.log(`     Name:    ${c.firstName || ''} ${c.lastName || ''}`);
      console.log(`     Email:   ${c.email || ''}`);
      console.log(`     Phone:   ${c.mobile || c.phone || ''}`);
      console.log(`     License: ${c.license || c.car?.license || ''}`);
      console.log(`     Address: ${c.address || ''}, ${c.zipCode || ''} ${c.city || ''}`);
    }
  } else {
    console.log(`  ⚠ Combined lookup returned ${combinedLookup.status} — endpoint may not exist yet`);
  }

  console.log('\n  ℹ️  Note: If all lookups return 404/405, the client search endpoint');
  console.log('     needs to be implemented in REQ-014 (NestJS Backend).');

  // ─── Step 1: Raw Snaps ───
  console.log('\n\n📌 STEP 1: Get Raw Snaps');
  const snapsRes = await softnet('POST', '/api/planning/snaps', {
    groupid: GROUP_ID,
    brand: BRAND,
    car: { brand: BRAND },
    operations: ['698b34c24dd8eea4d6d4219e'],
    preferredBranch: BRANCH_ID,
    checkInDatePreference: 'asSoonAsPossible',
  });
  if (!snapsRes.ok) { console.error('❌ Snaps failed'); return; }

  const rawSnaps = Array.isArray(snapsRes.data) ? snapsRes.data : [];
  console.log(`  ✅ ${rawSnaps.length} raw snaps`);
  if (rawSnaps.length === 0) { console.error('❌ No snaps'); return; }

  const snap = rawSnaps[0];
  console.log('\n  📋 Raw snap keys:', Object.keys(snap).join(', '));
  console.log('  📋 Full snap:', JSON.stringify(snap, null, 2).substring(0, 2000));

  // ─── Step 2: Patch with client data ───
  console.log('\n\n📌 STEP 2: Patch snap with client data');
  if (!snap.wishlist) snap.wishlist = {};
  snap.wishlist.client = {
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
  snap.wishlist.car = {
    ...(snap.wishlist.car || {}),
    brand: BRAND,
    license: 'M-TEST-123',
    VIN: 'UNKNOWN',
    currentMileage: 50000,
    firstRegMonth: '01',
    firstRegYear: '2022',
    model: '320i',
    color: 'schwarz',
  };
  console.log('  ✅ Client + Car patched');

  // ─── Step 3: Create Appointment ───
  console.log('\n\n📌 STEP 3: Create Appointment (send entire raw snap)');
  const appointmentRes = await softnet('POST', '/api/planning/appointments', snap);

  if (appointmentRes.ok) {
    const a = appointmentRes.data;
    console.log('\n🎉 BOOKING SUCCESSFUL!');
    console.log(`   ID:       ${a.id || a._id}`);
    console.log(`   Status:   ${a.status}`);
    console.log(`   Check-in: ${a.checkInTime}`);
    console.log(`   Branch:   ${a.branch?.name || ''}`);

    // ─── Step 4: Verify GET ───
    const apptId = a.id || a._id;
    console.log('\n\n📌 STEP 4: Verify (GET)');
    const verifyRes = await softnet('GET', `/api/planning/appointments/${apptId}`);
    if (verifyRes.ok) {
      console.log('\n✅ VERIFIED — Appointment exists in SoftNet!');
    } else {
      console.error('\n❌ Verification failed');
    }

    // ─── Step 5: Cancel ───
    console.log('\n\n📌 STEP 5: Cancel (cleanup)');
    const cancelRes = await softnet('PUT', `/api/planning/appointments/${apptId}/cancel`);
    console.log(cancelRes.ok ? '  ✅ Cancelled' : '  ⚠ Could not cancel');
  } else {
    console.error('\n❌ BOOKING FAILED');
  }

  console.log('\n' + '='.repeat(60));
  console.log('Done.');
}

run().catch(err => { console.error('Fatal:', err); process.exit(1); });
