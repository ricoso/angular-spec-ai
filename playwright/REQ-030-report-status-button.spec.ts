import { expect, test } from '@playwright/test';

import { setLanguage, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-030 / AC-8: the internal report status button is visible on a direct
 * call but hidden for customers viewing the app inside an iframe.
 *
 * LocationSelectionContainerComponent renders the button only `@if (!isEmbedded())`,
 * where isEmbedded = `window.self !== window.top`. So inside an iframe the
 * `.report-status-button` element must not be present in the DOM at all.
 *
 * Note: the "Direct call" case navigates to plain `/home/location` (no
 * branchId/groupId) — a valid branchId+groupId makes the REQ-023 Deep-Link Guard
 * skip location selection entirely and redirect to /home/brand, where this
 * button (only rendered by LocationSelectionContainerComponent) could never be
 * found. The iframe case keeps the deep-link params since it only asserts the
 * button is absent, which holds regardless of which page the guard lands on.
 */

const VALID_BRANCH_ID = '68f606798d2bb4389ed1af51'; // Audi Mettmann
const DEEP_LINK = `/home/location?branchId=${VALID_BRANCH_ID}&groupId=6908807454f4bb2b69b03fde`;

test.describe('REQ-030: report status button (iframe-aware)', () => {
  test('Direct call: report status button is visible', async ({ page }) => {
    await setLanguage(page, 'de');
    await page.goto('/home/location');
    await waitForAngular(page);

    await expect(page.locator('app-location-selection-container')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.report-status-button')).toBeVisible();
  });

  test('Inside iframe: report status button is NOT rendered', async ({ page }) => {
    await setLanguage(page, 'de');
    const baseUrl = (/^https?:\/\/[^/]+/.exec(page.url()))?.[0] ?? 'http://localhost:3000';

    await page.setContent(`
      <html>
        <body style="margin:0">
          <iframe id="app" src="${baseUrl}${DEEP_LINK}"
                  style="width:100%;height:90vh;border:0"></iframe>
        </body>
      </html>
    `);

    const frame = page.frameLocator('#app');
    await expect(frame.locator('app-location-selection-container, app-brand-selection-container').first())
      .toBeVisible({ timeout: 15000 });
    await page.waitForTimeout(500);

    // Button is guarded by @if (!isEmbedded()) → not in the DOM inside an iframe.
    expect(await frame.locator('.report-status-button').count()).toBe(0);
  });
});
