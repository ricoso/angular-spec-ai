/**
 * REQ-038 Screenshot Script — status page tabs „Statistik | Bugs" (statistics dashboard).
 *
 * The statistics stores are filled only once, when the backend starts in the Spring profile
 * `e2e-seed` (StatisticsE2eSeedLoader, fixtures in playwright/fixtures/req038/<set>/). `refresh`
 * reloads nothing (BR-16), so every seed set needs its own backend start. By default this script
 * therefore restarts the backend per set via `scripts/test-local.sh serve` with E2E_SEED_SET=<set>
 * (fresh frontend build + fresh backend, see CLAUDE.md „Stale-Bundle") and stops it at the end.
 *
 * Output (docs/requirements/REQ-038-Statistik-Dashboard/screenshots/):
 *   full  → e2e-responsive-{desktop,tablet,mobile}.png   statistics tab, filled dashboard
 *           e2e-bugs-{desktop,tablet,mobile}.png         bugs tab (identical for every set — the
 *                                                        fixtures only feed the statistics stores)
 *           e2e-not-found-desktop.png                    404 page of the catch-all mapping
 *   thin  → e2e-thin-{desktop,tablet,mobile}.png         collection since yesterday, tiny numbers
 *   fail  → e2e-fail-{desktop,tablet,mobile}.png         latest scan failed + rollup pending
 *
 * Usage:
 *   node playwright/take-screenshots-req038.js                  # all sets: full, thin, fail
 *   node playwright/take-screenshots-req038.js fail             # selected sets only
 *   node playwright/take-screenshots-req038.js --no-restart     # shoot the backend that is running
 *                                                               # (its set = E2E_SEED_SET, default full)
 *   node playwright/take-screenshots-req038.js --keep-running   # leave the last backend running
 *
 * Env:
 *   SCREENSHOT_BASE_URL      default http://localhost:3000
 *   REPORT_ADMIN_USER        default admin
 *   REPORT_ADMIN_PASSWORD    default e2e-local-secret (set by scripts/test-local.sh)
 */

const { chromium } = require('playwright');
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const BASE_URL = process.env.SCREENSHOT_BASE_URL || 'http://localhost:3000';
const REPORT_URL = `${BASE_URL}/internal/report`;
const BUGS_URL = `${BASE_URL}/internal/report/bugs`;
const NOT_FOUND_URL = `${BASE_URL}/internal/report/xyz`;
const CAPACITY_JSON_URL = `${BASE_URL}/internal/report/capacity.json`;
const USERNAME = process.env.REPORT_ADMIN_USER || 'admin';
const PASSWORD = process.env.REPORT_ADMIN_PASSWORD || 'e2e-local-secret';

const OUT_DIR = path.resolve(ROOT, 'docs', 'requirements', 'REQ-038-Statistik-Dashboard', 'screenshots');

const SEED_SETS = ['full', 'thin', 'fail'];
const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 720 },
  { name: 'tablet',  width: 768,  height: 1024 },
  { name: 'mobile',  width: 375,  height: 667 },
];

/** Statistics-tab file name per set; `full` keeps the REQ-wide name e2e-responsive-*. */
const STATISTICS_PREFIX = { full: 'e2e-responsive', thin: 'e2e-thin', fail: 'e2e-fail' };

const SEED_WAIT_MS = 60_000;

const args = process.argv.slice(2);
const noRestart = args.includes('--no-restart');
const keepRunning = args.includes('--keep-running');
const requestedSets = args.filter((a) => !a.startsWith('--'));
const unknown = requestedSets.filter((s) => !SEED_SETS.includes(s));
if (unknown.length) {
  console.error(`Unknown seed set(s): ${unknown.join(', ')} — expected ${SEED_SETS.join(' | ')}`);
  process.exit(2);
}
const sets = noRestart
  ? [process.env.E2E_SEED_SET || 'full']
  : (requestedSets.length ? requestedSets : SEED_SETS);

const authHeader = `Basic ${Buffer.from(`${USERNAME}:${PASSWORD}`).toString('base64')}`;

/** Starts a fresh backend for `set` (kills :3000, builds the frontend, waits for health). */
function restartBackend(set) {
  console.log(`\n▶ Backend neu starten mit E2E_SEED_SET=${set} …`);
  const result = spawnSync('bash', [path.join(ROOT, 'scripts', 'test-local.sh'), 'serve'], {
    cwd: ROOT,
    env: { ...process.env, E2E_SEED_SET: set },
    stdio: 'inherit',
  });
  if (result.status !== 0) {
    throw new Error(`scripts/test-local.sh serve failed for set ${set} (exit ${result.status})`);
  }
}

function stopBackend() {
  console.log('\n▶ Stoppe Backend auf :3000');
  // Only the listening server: without -sTCP:LISTEN lsof also lists this very script (its
  // keep-alive fetch connections to :3000) and kill -9 would end it. / Nur der lauschende Server.
  spawnSync('bash', ['-c', 'lsof -ti:3000 -sTCP:LISTEN | xargs kill -9 2>/dev/null || true'], { stdio: 'inherit' });
}

/**
 * Health probe with retries / Health-Probe mit Wiederholung. `restartBackend` blocks the event loop
 * (spawnSync), so fetch's keep-alive pool never notices that its socket to the previous backend
 * died; the first request after a restart can hit that dead socket and fail although the new
 * backend is healthy. Without the retry the `finally` below then stopped the fresh backend
 * (exit 137, observed thin → fail). / Erster Versuch nach dem Neustart kann auf einen toten
 * Keep-alive-Socket treffen.
 */
async function probeHealth() {
  for (let attempt = 1; attempt <= 10; attempt++) {
    const res = await fetch(`${BASE_URL}/api/v1/health`).catch(() => null);
    if (res && res.ok) return true;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return false;
}

/**
 * Health answers before the ApplicationReadyEvent listeners ran — wait until the seed landed
 * (the capacity view then names a scan day) so we never shoot the „Historie nicht verfügbar"
 * fallback.
 */
async function waitForSeed() {
  const deadline = Date.now() + SEED_WAIT_MS;
  while (Date.now() < deadline) {
    const res = await fetch(CAPACITY_JSON_URL, { headers: { Authorization: authHeader } }).catch(() => null);
    if (res && res.ok) {
      const body = await res.json().catch(() => null);
      if (body && body.scanDay) return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`seed data not visible at ${CAPACITY_JSON_URL} after ${SEED_WAIT_MS / 1000} s — `
    + 'is the backend running with SPRING_PROFILES_ACTIVE=e2e-seed and CAPACITY_SCAN_ENABLED=true?');
}

async function shoot(browser, vp, url, file, expectedStatus) {
  const ctx = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    httpCredentials: { username: USERNAME, password: PASSWORD },
  });
  const page = await ctx.newPage();
  try {
    const res = await page.goto(url, { waitUntil: 'networkidle' });
    if (!res || res.status() !== expectedStatus) {
      throw new Error(`${vp.name}: HTTP ${res ? res.status() : 'no response'} on ${url} (expected ${expectedStatus})`);
    }
    // Server-rendered, no script — wait for the landmark so we never shoot a half-painted page.
    await page.locator('main#content').waitFor({ state: 'visible', timeout: 5000 });
    await page.waitForTimeout(300);
    const target = path.join(OUT_DIR, file);
    await page.screenshot({ path: target, fullPage: true });
    const { size } = fs.statSync(target);
    console.log(`  ✅ ${file} (${vp.width}x${vp.height}, ${size} bytes)`);
  } finally {
    await ctx.close();
  }
}

(async () => {
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
  let total = 0;
  let exitCode = 0;
  try {
    for (const set of sets) {
      if (!noRestart) restartBackend(set);
      if (!(await probeHealth())) {
        throw new Error(`Backend not reachable at ${BASE_URL}. Start with: npm run test:local:serve`);
      }
      await waitForSeed();
      console.log(`\n📸 Seed-Set ${set}`);

      const browser = await chromium.launch();
      try {
        for (const vp of VIEWPORTS) {
          await shoot(browser, vp, REPORT_URL, `${STATISTICS_PREFIX[set]}-${vp.name}.png`, 200);
          total++;
          if (set === 'full') {
            await shoot(browser, vp, BUGS_URL, `e2e-bugs-${vp.name}.png`, 200);
            total++;
          }
        }
        if (set === 'full') {
          await shoot(browser, VIEWPORTS[0], NOT_FOUND_URL, 'e2e-not-found-desktop.png', 404);
          total++;
        }
      } finally {
        await browser.close();
      }
    }
  } catch (e) {
    console.error(`  ✗ ${e.message}`);
    exitCode = 1;
  } finally {
    // A backend left on the last set would be reused by `npm run e2e:backend`
    // (reuseExistingServer) and break the full-set specs — stop it unless asked otherwise.
    if (!noRestart && !keepRunning) stopBackend();
  }
  console.log(`\nDone — ${total} screenshots saved to ${OUT_DIR}`);
  process.exit(exitCode);
})();
