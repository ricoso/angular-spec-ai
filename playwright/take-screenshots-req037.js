/**
 * REQ-037 Screenshot Script — Nutzungsstatistik on the internal status page.
 *
 * The status page (/internal/report) is protected by HTTP Basic Auth, so unlike
 * playwright/take-screenshots.js (which drives the public SPA) this script needs
 * httpCredentials on the browser context.
 *
 * Usage:
 *   npm run test:local:serve          # build frontend + start fresh backend on :3000
 *   node playwright/take-screenshots-req037.js
 *
 * Env:
 *   SCREENSHOT_BASE_URL      default http://localhost:3000
 *   REPORT_ADMIN_USER        default admin
 *   REPORT_ADMIN_PASSWORD    default e2e-local-secret (set by scripts/test-local.sh)
 */

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE_URL = process.env.SCREENSHOT_BASE_URL || 'http://localhost:3000';
const REPORT_URL = `${BASE_URL}/internal/report`;
const USERNAME = process.env.REPORT_ADMIN_USER || 'admin';
const PASSWORD = process.env.REPORT_ADMIN_PASSWORD || 'e2e-local-secret';

const OUT_DIR = path.resolve(
  __dirname, '..', 'docs', 'requirements', 'REQ-037-Nutzungsstatistik', 'screenshots'
);

const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 720 },
  { name: 'tablet',  width: 768,  height: 1024 },
  { name: 'mobile',  width: 375,  height: 667 },
];

(async () => {
  // Fail fast if the backend is not up — a screenshot of an error page is worse
  // than no screenshot at all.
  const probe = await fetch(`${BASE_URL}/api/v1/health`).catch(() => null);
  if (!probe || !probe.ok) {
    console.error(`Backend not reachable at ${BASE_URL}. Start with: npm run test:local:serve`);
    process.exit(1);
  }

  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

  const browser = await chromium.launch();
  let total = 0;

  for (const vp of VIEWPORTS) {
    const ctx = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      httpCredentials: { username: USERNAME, password: PASSWORD },
    });
    const page = await ctx.newPage();

    const res = await page.goto(REPORT_URL, { waitUntil: 'networkidle' });
    if (!res || res.status() !== 200) {
      console.error(`  ✗ ${vp.name}: HTTP ${res ? res.status() : 'no response'} on ${REPORT_URL}`);
      await ctx.close();
      await browser.close();
      process.exit(1);
    }

    // The usage section is server-rendered; wait for it so we never shoot a
    // half-painted page. It is absent when USAGE_STATS_ENABLED=false (AC-22).
    const usage = page.locator('section.us');
    if (await usage.count()) {
      await usage.first().waitFor({ state: 'visible', timeout: 5000 });
    } else {
      console.warn(`  ! ${vp.name}: no section.us — is USAGE_STATS_ENABLED=false?`);
    }
    await page.waitForTimeout(300);

    const file = path.join(OUT_DIR, `e2e-responsive-${vp.name}.png`);
    await page.screenshot({ path: file, fullPage: true });
    const { size } = fs.statSync(file);
    console.log(`  ✅ ${path.basename(file)} (${vp.width}x${vp.height}, ${size} bytes)`);
    total++;

    await ctx.close();
  }

  await browser.close();
  console.log(`\nDone — ${total} screenshots saved to ${OUT_DIR}`);
})();
