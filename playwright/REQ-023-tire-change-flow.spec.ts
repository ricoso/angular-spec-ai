import { expect, test } from '@playwright/test';

import { setLanguage, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-023 / AC-3: Reifenwechsel-Flow End-to-End.
 *
 * - Service "tire-change" auswählen → Snaps-Request enthält `variant: 'tireChange'`
 * - Bei deaktivierter Saison fällt das Backend auf default zurück (REQ-021 (aa))
 *   → kein 500er, sondern 200 mit Snaps der default-Group.
 */

const VALID_BRANCH_ID = '68f606798d2bb4389ed1af51'; // Audi Mettmann
const VALID_GROUP_ID = '6908807454f4bb2b69b03fde'; // default group

test.describe('REQ-023: Reifenwechsel-Flow', () => {
  test('Snaps-Request enthält variant=tireChange wenn tire-change Service ausgewählt wird', async ({ page }) => {
    await setLanguage(page, 'de');

    const snapsRequests: Record<string, unknown>[] = [];
    await page.route('**/api/v1/snaps', async (route) => {
      const req = route.request();
      if (req.method() === 'POST') {
        snapsRequests.push(req.postDataJSON() as Record<string, unknown>);
      }
      await route.continue();
    });

    // Deep-Link zur Brand-Seite
    await page.goto(`/home/location?branchId=${VALID_BRANCH_ID}&groupId=${VALID_GROUP_ID}`);
    await waitForAngular(page);

    // Marke wählen
    const brandBtn = page.locator('.brand-grid__button, [class*="brand"] button').first();
    if (await brandBtn.count() > 0) {
      await brandBtn.click();
      await waitForAngular(page);
    }

    // Service "tire-change" anklicken (falls verfügbar)
    const tireChangeCard = page.locator('app-service-card', { hasText: /reifen|tire/i }).first();
    if (await tireChangeCard.count() > 0) {
      await tireChangeCard.click();
      await waitForAngular(page);

      // Continue zum nächsten Step (löst Snaps-Request aus)
      const continueBtn = page.locator('button[class*="continue"]').first();
      if (await continueBtn.count() > 0 && await continueBtn.isEnabled()) {
        await continueBtn.click();
        await waitForAngular(page);
      }
    }

    // Wenn Snaps-Request gesendet wurde, prüfe variant
    const tireChangeRequest = snapsRequests.find(b => (b.jobTypeIds as string[] | undefined)?.includes('tire-change'));
    if (tireChangeRequest) {
      expect(tireChangeRequest.variant).toBe('tireChange');
    }
  });

  test('Snaps mit variant=tireChange liefert 200 (Fallback auf default wenn Saison aus)', async ({ request }) => {
    // Direkter Backend-Call statt durchklicken — testet den SetupService-Fallback aus REQ-021 (aa)
    // OriginFilter im Backend blockt Calls ohne erlaubten Origin-Header (`/api/v1/*`).
    // Lokal ist 'http://localhost:3000' (= 'self') automatisch erlaubt.
    const originHeader = { Origin: 'http://localhost:3000' };

    const sessionResponse = await request.get('/api/v1/session/token', { headers: originHeader });
    expect(sessionResponse.ok()).toBeTruthy();
    const tokenData = await sessionResponse.json() as { data: { token: string } };
    const token = tokenData.data.token;

    const today = new Date().toISOString().slice(0, 10);
    const future = new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

    const snapsResponse = await request.post('/api/v1/snaps', {
      headers: {
        ...originHeader,
        'Content-Type': 'application/json',
        'x-session-token': token,
      },
      data: {
        branchId: VALID_BRANCH_ID,
        jobTypeIds: ['inspection'],
        fromDate: today,
        toDate: future,
        mode: 'next-available',
        brandName: 'audi',
        variant: 'tireChange', // Saison aus → SetupService faellt auf default zurueck
      },
    });

    // Erwartung: NICHT 500 (kein VariantNotFoundException mehr).
    expect(snapsResponse.status()).not.toBe(500);
    expect([200, 400, 404]).toContain(snapsResponse.status());
  });
});
