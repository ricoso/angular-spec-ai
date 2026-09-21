import { expect, test } from '@playwright/test';

/**
 * REQ-037: anonymous usage statistics on the internal status page.
 *
 * What only an E2E run can prove (the MockMvc tests in ReportControllerTest cannot):
 * the page renders in a real browser through the real security chain, the statistics
 * sit above the error report, and the section stays readable on a phone without
 * horizontal page scrolling (AC-17).
 *
 * Credentials: scripts/test-local.sh and playwright-backend.config.ts both start the
 * backend with REPORT_ADMIN_PASSWORD=e2e-local-secret. Without a fixed value the
 * backend generates a random UUID on every start and no spec could authenticate.
 */

const ADMIN_USER = process.env['REPORT_ADMIN_USER'] ?? 'admin';
const ADMIN_PASSWORD = process.env['REPORT_ADMIN_PASSWORD'] ?? 'e2e-local-secret';

const REPORT_URL = '/internal/report';
const USAGE_JSON_URL = '/internal/report/usage.json';

test.describe('REQ-037: usage statistics — access protection', () => {
  test('AC-16 / E5: status page requires Basic Auth', async ({ request }) => {
    const response = await request.get(REPORT_URL, { maxRedirects: 0 });

    expect(response.status()).toBe(401);
    // §12.2: the realm changed from Spring's default "Realm" to "internal".
    expect(response.headers()['www-authenticate']).toContain('realm="internal"');
  });

  test('AC-16: usage.json requires Basic Auth', async ({ request }) => {
    const response = await request.get(USAGE_JSON_URL, { maxRedirects: 0 });

    expect(response.status()).toBe(401);
  });

  test('AC-20 / TC-22: the internal chain sends the Content-Security-Policy', async ({ request }) => {
    const response = await request.get(REPORT_URL, {
      headers: { Authorization: basicAuthHeader() },
    });

    expect(response.status()).toBe(200);
    const csp = response.headers()['content-security-policy'];
    expect(csp).toBeDefined();
    // No script source at all — the page is server-side HTML with inline CSS only (AC-3).
    expect(csp).toContain("default-src 'none'");
    expect(csp).toContain("style-src 'self' 'unsafe-inline'");
    expect(csp).toContain("frame-ancestors 'none'");
  });
});

test.describe('REQ-037: usage statistics — rendering', () => {
  test.use({ httpCredentials: { username: ADMIN_USER, password: ADMIN_PASSWORD } });

  test('AC-2 / AC-3: both periods render above the error report', async ({ page }) => {
    await page.goto(REPORT_URL);

    const section = page.locator('section.us');
    await expect(section).toBeVisible();
    await expect(section.locator('h2')).toContainText('Nutzungsstatistik');

    // AC-3: exactly two blocks, one per period, stacked — no toggle.
    await expect(section.locator('.us-block')).toHaveCount(2);
    await expect(section.locator('.us-block').first()).toContainText('Aktueller Monat');
    await expect(section.locator('.us-block').nth(1)).toContainText('Jahresübersicht');

    // AC-2: the statistics must come BEFORE the AI summary and the error list.
    const order = await page.evaluate(() => {
      const nodes = Array.from(document.querySelectorAll('section'));
      return nodes.map(n => n.className);
    });
    const usIndex = order.indexOf('us');
    const aiIndex = order.indexOf('ai');
    const errIndex = order.indexOf('err');
    expect(usIndex).toBeGreaterThanOrEqual(0);
    if (aiIndex >= 0) {
      expect(usIndex).toBeLessThan(aiIndex);
    }
    if (errIndex >= 0) {
      expect(usIndex).toBeLessThan(errIndex);
    }
  });

  test('AC-3: the page carries no JavaScript at all', async ({ page }) => {
    await page.goto(REPORT_URL);

    expect(await page.locator('script').count()).toBe(0);
  });

  test('AC-12: a period without data shows the empty state, not tiles full of zeros', async ({ page }) => {
    await page.goto(REPORT_URL);

    // An E2E run has no Azure behind it, so both periods are empty (hasData == false).
    // Per AC-12 the renderer then replaces the funnel tiles with `.us-empty` instead of
    // printing zeros — asserted here for both blocks.
    await expect(page.locator('.us-empty')).toHaveCount(2);
    await expect(page.locator('.us-empty').first()).toContainText('Keine Daten');

    // Consequently the tiles, rates and the monthly table are absent in this state.
    // Their populated form cannot be produced without Log Analytics, so it is covered by
    // UsageSectionRendererTest (rendersEveryClassNameOfTheContractTable,
    // yearBlockRendersTwelveMonthRowsWithGapsAndRunningMonth) rather than here.
    await expect(page.locator('table.us-months')).toHaveCount(0);
  });

  test('E1 / E2 / AC-11: without Azure the page still loads and says so', async ({ page }) => {
    // Locally there is neither Log Analytics nor a storage account, so the page must
    // degrade to the documented hints instead of a 5xx or an empty page (§18).
    const response = await page.goto(REPORT_URL);

    expect(response?.status()).toBe(200);
    await expect(page.locator('section.us')).toBeVisible();
    await expect(page.locator('.us-warn').first()).toBeVisible();
  });

  test('AC-12 / AC-13: empty periods show a dash, never a zero or NaN', async ({ page }) => {
    await page.goto(REPORT_URL);

    const sectionText = await page.locator('section.us').innerText();
    expect(sectionText).not.toContain('NaN');
    expect(sectionText).not.toContain('Infinity');
    // No data locally → the dash must be used somewhere in the section.
    expect(sectionText).toContain('—');
  });

  test('AC-17: no horizontal page scrolling (all three viewports)', async ({ page }) => {
    await page.goto(REPORT_URL);
    await expect(page.locator('section.us')).toBeVisible();

    const overflow = await page.evaluate(() =>
      document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
    // A couple of pixels of rounding are tolerable; a scrollbar's worth is not.
    expect(overflow).toBeLessThanOrEqual(2);
  });

  test('AC-16: usage.json returns the same aggregate as JSON', async ({ page }) => {
    const response = await page.request.get(USAGE_JSON_URL, {
      headers: { Authorization: basicAuthHeader() },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('month');
    expect(body).toHaveProperty('year');
    // AC-14: only counters — the payload must never carry identifiers.
    const raw = JSON.stringify(body);
    expect(raw).not.toContain('sessionId');
    expect(raw).not.toContain('userAgent');
  });
});

test.describe('REQ-037: no regression on the public app', () => {
  test('the booking app still loads with the extended logging filter', async ({ page }) => {
    // The LoggingFilter now reads two extra headers on every request (AC-1).
    // This guards against that change breaking the customer-facing path.
    const response = await page.goto('/home/location');

    expect(response?.status()).toBe(200);
    await expect(page.locator('app-root')).toBeAttached();
  });

  test('the health endpoint stays healthy', async ({ request }) => {
    const response = await request.get('/api/v1/health');

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.data.status).toBe('healthy');
  });
});

function basicAuthHeader(): string {
  return `Basic ${Buffer.from(`${ADMIN_USER}:${ADMIN_PASSWORD}`).toString('base64')}`;
}
