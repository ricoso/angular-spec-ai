import { expect, type Locator, type Page, test } from '@playwright/test';

/**
 * REQ-038: status page tabs „Statistik | Bugs" with the statistics dashboard (§13.5, TC-41–TC-44).
 *
 * What only an E2E run can prove: both tabs are real documents behind the real security chain,
 * navigation works without any script (links, aria-current, back button, reload), unknown
 * sub-paths never fall through to the Angular shell, and the dashboard stays usable in all three
 * viewports (no horizontal page scrolling, 44 px tab links, visible keyboard focus, labelled
 * scroll regions, a value in every heat cell).
 *
 * Data: scripts/test-local.sh and playwright-backend.config.ts start the backend with the
 * Spring profile `e2e-seed`, all REQ-038 switches on and both crons off. StatisticsE2eSeedLoader
 * then loads playwright/fixtures/req038/<E2E_SEED_SET>/ (default `full`) into the stores — the
 * dates are relative tokens, so the assertions below never depend on a calendar day.
 * REQ-037's usage statistics stay empty on purpose (REQ-037-usage-statistics.spec.ts runs in the
 * same suite and expects two empty periods).
 *
 * Credentials: REPORT_ADMIN_PASSWORD=e2e-local-secret, as in REQ-037.
 */

const ADMIN_USER = process.env.REPORT_ADMIN_USER ?? 'admin';
const ADMIN_PASSWORD = process.env.REPORT_ADMIN_PASSWORD ?? 'e2e-local-secret';
const SEED_SET = process.env.E2E_SEED_SET ?? 'full';

const REPORT_URL = '/internal/report';
const STATISTICS_URL = '/internal/report/statistics';
const BUGS_URL = '/internal/report/bugs';
const INSIGHTS_JSON_URL = '/internal/report/insights.json';
const CAPACITY_JSON_URL = '/internal/report/capacity.json';
const UNKNOWN_PATHS = ['/internal/report/xyz', '/internal/report/a/b'];

/** S1–S7 in the order of §11.2 (S5 is the REQ-037 section); S0 is hidden since REQ-043. */
const STATISTICS_SECTIONS = ['cap', 'rk', 'sg', 'tm', 'us', 'fn', 'st-limits'];

/** Sections filled by the e2e-seed profile (S1–S4, S6; S0 hidden since REQ-043). */
const SEEDED_SECTIONS = ['cap', 'rk', 'sg', 'tm', 'fn'];

/** Sections of the bugs tab — never on the statistics tab (BR-3). */
const BUGS_SELECTOR = 'section.ai, section.ov, section.err, section.nr, .ai, .err';

const TITLE_STATISTICS = 'Statusseite – Statistik';
const TITLE_BUGS = 'Statusseite – Bugs';
const TITLE_NOT_FOUND = 'Statusseite – Seite nicht gefunden';
const HISTORY_UNAVAILABLE = 'Historie derzeit nicht verfügbar';

/** 2.75em at 16 px — WCAG 2.5.5 touch target; half a pixel absorbs sub-pixel rounding. */
const MIN_TARGET_PX = 44;
const SUBPIXEL_TOLERANCE_PX = 0.5;

/** Cell states that must never carry a value (BR-9, AC-20). */
const HIDDEN_STATES = new Set(['SUPPRESSED', 'WITHHELD', 'TOO_FEW', 'NO_DATA']);

test.describe('REQ-038: status page tabs — navigation (TC-41)', () => {
  test.use({ httpCredentials: { username: ADMIN_USER, password: ADMIN_PASSWORD } });

  test('TC-41 precondition: the seed data is loaded, not the „history unavailable" fallback', async ({ page }) => {
    test.skip(SEED_SET !== 'full', 'expects the full seed set (E2E_SEED_SET=full)');
    await page.goto(REPORT_URL);

    // Only the REQ-038 sections — S5 (REQ-037) legitimately says so locally, there is no blob.
    for (const section of SEEDED_SECTIONS) {
      await expect(page.locator(`section.${section}`).first()).not.toContainText(HISTORY_UNAVAILABLE);
    }
    await expect(page.locator('section.cap table.cap-table')).toBeVisible();
    await expect(page.locator('section.rk table.rk-table')).toBeVisible();
    await expect(page.locator('section.sg table.sg-matrix')).toBeVisible();
    await expect(page.locator('section.tm table.st-heat').first()).toBeVisible();
    await expect(page.locator('section.fn ol.fn-steps').first()).toBeVisible();
    await expect(page.locator('section.fn-dropoff')).toBeVisible();
  });

  test('TC-41: statistics is the default tab, the Bugs link switches, back and reload keep the tab', async ({ page }) => {
    await page.goto(REPORT_URL);
    await expectStatisticsTab(page);

    await tabLink(page, 'Bugs').click();
    await expect(page).toHaveURL(/\/internal\/report\/bugs$/);
    await expectBugsTab(page);

    await page.goBack();
    await expect(page).toHaveURL(/\/internal\/report\/?$/);
    await expectStatisticsTab(page);

    await page.goto(BUGS_URL);
    await page.reload();
    await expect(page).toHaveURL(/\/internal\/report\/bugs$/);
    await expectBugsTab(page);
  });

  test('TC-41: the Statistik link opens /internal/report/statistics with the same sections', async ({ page }) => {
    await page.goto(BUGS_URL);

    await tabLink(page, 'Statistik').click();

    await expect(page).toHaveURL(/\/internal\/report\/statistics$/);
    await expectStatisticsTab(page);
    // BR-2: plain links, no ARIA tab widget and no form.
    await expect(page.locator('[role^="tab"]')).toHaveCount(0);
    await expect(page.locator('form')).toHaveCount(0);
  });

  test('TC-42: each tab has its own title and exactly one h1', async ({ page }) => {
    await page.goto(REPORT_URL);
    await expect(page).toHaveTitle(TITLE_STATISTICS);
    await expect(page.locator('h1')).toHaveCount(1);
    await expect(page.locator('h1')).toHaveText('Statusseite');

    await page.goto(STATISTICS_URL);
    await expect(page).toHaveTitle(TITLE_STATISTICS);

    await page.goto(BUGS_URL);
    await expect(page).toHaveTitle(TITLE_BUGS);
    await expect(page.locator('h1')).toHaveCount(1);
  });
});

test.describe('REQ-038: unknown sub-paths (TC-42, E7, E8)', () => {
  for (const path of UNKNOWN_PATHS) {
    test(`TC-42: ${path} answers 404 text/html without the Angular shell`, async ({ request }) => {
      const response = await request.get(path, {
        headers: { Authorization: basicAuthHeader() },
        maxRedirects: 0,
      });

      expect(response.status()).toBe(404);
      expect(response.headers()['content-type']).toContain('text/html');
      const body = await response.text();
      expect(body).not.toContain('app-root');
      expect(body).toContain(`<title>${TITLE_NOT_FOUND}</title>`);
      expect(body).toContain('Seite nicht gefunden');
      // §11.1: links to the existing tabs, but the requested path is never echoed.
      expect(body).toContain(`href="${BUGS_URL}"`);
      expect(body).not.toContain(path);
    });

    test(`E8: ${path} without credentials answers 401, not 404`, async ({ request }) => {
      const response = await request.get(path, { maxRedirects: 0 });

      expect(response.status()).toBe(401);
      expect(response.headers()['www-authenticate']).toContain('realm="internal"');
    });
  }
});

test.describe('REQ-038: accessibility and layout (TC-43)', () => {
  test.use({ httpCredentials: { username: ADMIN_USER, password: ADMIN_PASSWORD } });

  for (const [label, url] of [['statistics', REPORT_URL], ['bugs', BUGS_URL]] as const) {
    test(`TC-43 (${label}): no script, no horizontal page scrolling, 44 px tab links`, async ({ page }) => {
      await page.goto(url);

      expect(await page.locator('script').count()).toBe(0);
      const overflow = await page.evaluate(() =>
        document.documentElement.scrollWidth - document.documentElement.clientWidth
      );
      expect(overflow).toBeLessThanOrEqual(2);

      const links = page.locator('nav.tabs a');
      await expect(links).toHaveCount(2);
      for (const link of await links.all()) {
        const box = await link.boundingBox();
        expect(box?.height ?? 0).toBeGreaterThanOrEqual(MIN_TARGET_PX - SUBPIXEL_TOLERANCE_PX);
      }
    });

    test(`TC-43 (${label}): a tab link focused by keyboard shows a visible focus ring`, async ({ page }) => {
      await page.goto(url);
      const link = page.locator('nav.tabs a').first();

      expect(await focusWithKeyboard(page, link), 'tab link reachable with the keyboard').toBe(true);

      const ring = await link.evaluate((element) => {
        const style = getComputedStyle(element);
        return {
          focusVisible: element.matches(':focus-visible'),
          outlineStyle: style.outlineStyle,
          outlineWidth: Number.parseFloat(style.outlineWidth) || 0,
          boxShadow: style.boxShadow,
        };
      });
      expect(ring.focusVisible).toBe(true);
      const hasOutline = ring.outlineStyle !== 'none' && ring.outlineWidth > 0;
      expect(hasOutline || ring.boxShadow !== 'none').toBe(true);
    });
  }

  test('TC-43: every scroll container is a labelled, focusable region', async ({ page }) => {
    await page.goto(REPORT_URL);
    const regions = page.locator('.st-scroll');

    expect(await regions.count()).toBeGreaterThan(0);
    const invalid = await regions.evaluateAll((elements) =>
      elements
        .filter((element) =>
          element.getAttribute('role') !== 'region'
          || element.getAttribute('tabindex') !== '0'
          || (element.getAttribute('aria-label') ?? '').trim() === '')
        .map((element) => element.outerHTML.slice(0, 120))
    );
    expect(invalid).toEqual([]);
  });

  test('TC-43 / BR-23: every heat-map and trend cell carries its value as text', async ({ page }) => {
    test.skip(SEED_SET === 'thin', 'the thin set has no coloured heat cells');
    await page.goto(REPORT_URL);

    expect(await page.locator('td[class*="st-heat-"]').count()).toBeGreaterThan(0);
    const cells = page.locator('table.st-heat td, table.sg-matrix td, td[class*="st-heat-"], td.cap-day');
    expect(await cells.count()).toBeGreaterThan(0);
    const empty = await cells.evaluateAll((elements) =>
      elements.filter((element) => element.textContent.trim() === '').length
    );
    expect(empty).toBe(0);
  });
});

test.describe('REQ-038: JSON endpoints (TC-44)', () => {
  for (const url of [INSIGHTS_JSON_URL, CAPACITY_JSON_URL]) {
    test(`TC-44: ${url} answers 401 without and 200 with Basic Auth`, async ({ request }) => {
      const anonymous = await request.get(url, { maxRedirects: 0 });
      expect(anonymous.status()).toBe(401);
      expect(anonymous.headers()['www-authenticate']).toContain('realm="internal"');

      const response = await request.get(url, { headers: { Authorization: basicAuthHeader() } });
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('application/json');
      const raw = await response.text();
      // BR-24 / §9: no identifiers, no service advisor, never a value behind a hidden cell.
      expect(raw).not.toContain('sessionId');
      expect(raw).not.toContain('checkInResource');
      expect(hiddenCellsWithValues(JSON.parse(raw))).toEqual([]);
    });
  }

  test('TC-44: insights.json carries the seeded view with bookings, funnel and briefing', async ({ request }) => {
    test.skip(SEED_SET !== 'full', 'expects the full seed set (E2E_SEED_SET=full)');
    const response = await request.get(INSIGHTS_JSON_URL, { headers: { Authorization: basicAuthHeader() } });
    const body = (await response.json()) as InsightsJson;

    expect(body.bookingsEnabled).toBe(true);
    expect(body.funnelEnabled).toBe(true);
    expect(body.ranking?.length ?? 0).toBeGreaterThan(0);
    expect(body.funnel).not.toBeNull();
    expect(body.briefing?.impulses.length ?? 0).toBeGreaterThan(0);
    // BR-9 Nr. 5: a bar never reveals a hidden booking count.
    for (const row of [...(body.ranking ?? []), ...(body.belowThreshold ?? [])]) {
      if (row.bookings.state !== 'VALUE') {
        expect(row.barWidth).toBe(0);
      }
    }
  });

  test('TC-44: capacity.json carries the seeded scan with the 10-day trend', async ({ request }) => {
    test.skip(SEED_SET !== 'full', 'expects the full seed set (E2E_SEED_SET=full)');
    const response = await request.get(CAPACITY_JSON_URL, { headers: { Authorization: basicAuthHeader() } });
    const body = (await response.json()) as CapacityJson;

    expect(body.scanDay).not.toBeNull();
    expect(body.last10ScanDays.length).toBeGreaterThan(0);
    expect(body.rows.length).toBeGreaterThan(0);
    for (const row of body.rows) {
      expect(row.last10.length).toBe(body.last10ScanDays.length);
    }
  });
});

// ---- helpers ----

interface CellJson {
  state: string;
}

interface RankingRowJson {
  bookings: CellJson;
  barWidth: number;
}

interface InsightsJson {
  bookingsEnabled: boolean;
  funnelEnabled: boolean;
  ranking: RankingRowJson[] | null;
  belowThreshold: RankingRowJson[] | null;
  funnel: unknown;
  briefing?: { impulses: unknown[] } | null;
}

interface CapacityJson {
  scanDay: string | null;
  last10ScanDays: string[];
  rows: { last10: string[] }[];
}

function tabLink(page: Page, name: string): Locator {
  return page.locator('nav.tabs').getByRole('link', { name, exact: true });
}

async function expectStatisticsTab(page: Page): Promise<void> {
  await expect(page.locator('nav.tabs')).toBeVisible();
  await expect(page.locator('nav.tabs a[aria-current="page"]')).toHaveCount(1);
  await expect(page.locator('nav.tabs a[aria-current="page"]')).toHaveText('Statistik');
  for (const section of STATISTICS_SECTIONS) {
    await expect(page.locator(`section.${section}`).first(), `section.${section}`).toBeVisible();
  }
  await expect(page.locator(BUGS_SELECTOR)).toHaveCount(0);
}

async function expectBugsTab(page: Page): Promise<void> {
  await expect(page.locator('nav.tabs')).toBeVisible();
  await expect(page.locator('nav.tabs a[aria-current="page"]')).toHaveText('Bugs');
  // Locally there is no LLM, so the stored report is missing and the hint (.nr) stands in.
  await expect(page.locator('section.nr, section.ai').first()).toBeVisible();
  await expect(page.locator('section.us, section.rk, section.ib, section.cap')).toHaveCount(0);
}

/**
 * Moves the focus with the keyboard until `target` has it. WebKit on macOS skips links on Tab
 * (Safari's default), so a second pass uses Alt+Tab from a fresh page.
 */
async function focusWithKeyboard(page: Page, target: Locator, maxPresses = 15): Promise<boolean> {
  for (const key of ['Tab', 'Alt+Tab']) {
    if (key !== 'Tab') {
      await page.reload();
    }
    for (let press = 0; press < maxPresses; press++) {
      await page.keyboard.press(key);
      if (await target.evaluate((element) => element === document.activeElement)) {
        return true;
      }
    }
  }
  return false;
}

/** JSON paths of hidden cells that still carry a value, a ratio, a percentage or a heat level. */
function hiddenCellsWithValues(node: unknown, path = '$'): string[] {
  if (Array.isArray(node)) {
    return node.flatMap((item, index) => hiddenCellsWithValues(item, `${path}[${String(index)}]`));
  }
  if (node === null || typeof node !== 'object') {
    return [];
  }
  const record = node as Record<string, unknown>;
  const nested = Object.entries(record).flatMap(([key, value]) => hiddenCellsWithValues(value, `${path}.${key}`));
  return leaksHiddenValue(record) ? [path, ...nested] : nested;
}

/** A `Cell` whose state hides the value but which still carries one (BR-9 Nr. 5). */
function leaksHiddenValue(record: Record<string, unknown>): boolean {
  const hidden = typeof record.state === 'string' && HIDDEN_STATES.has(record.state) && 'heat' in record;
  if (!hidden) {
    return false;
  }
  return present(record.value) || present(record.ratio) || present(record.percent) || record.heat !== 0;
}

function present(value: unknown): boolean {
  return value !== null && value !== undefined;
}

function basicAuthHeader(): string {
  return `Basic ${Buffer.from(`${ADMIN_USER}:${ADMIN_PASSWORD}`).toString('base64')}`;
}
