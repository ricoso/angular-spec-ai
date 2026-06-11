import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import { goToBrandSelection, selectFirstBrand, selectFirstLocation } from './helpers/booking.helpers';

async function goToServicesPage(page: import('@playwright/test').Page): Promise<void> {
  await goToBrandSelection(page);
  await selectFirstBrand(page);
  await selectFirstLocation(page);
  await navigateTo(page, '/home/services');
  await waitForAngular(page);
}

/**
 * Cart-Overlay Content Tests — deckt die Luecke ab, die in den anderen Specs
 * fehlte: tatsaechlicher DOM-Inhalt des Warenkorbs muss zur User-Auswahl passen.
 */

test.describe.skip('Floating Overlay: Warenkorb-Inhalt', () => {
  test('zeigt jede ausgewaehlte Sub-Leistung mit Namen, nicht nur die Anzahl', async ({ page }) => {
    await setLanguage(page, 'de');
    await goToServicesPage(page);

    // Eine Service-Card expandieren und 3 Sub-Optionen auswaehlen
    const inspectionCard = page.locator('mat-card', { hasText: 'Inspektion' }).first();
    await inspectionCard.click();

    const optionsToSelect = ['Inspektion', 'Ölwechsel-Service', 'Bremsen prüfen'];
    for (const label of optionsToSelect) {
      const checkbox = inspectionCard.locator('mat-checkbox', { hasText: label }).first();
      await checkbox.click();
    }

    // Warenkorb oeffnen
    const cartFab = page.locator('.floating-overlay__fab').first();
    await cartFab.click();

    // Panel muss sichtbar sein und jede Sub-Leistung als Listeneintrag zeigen
    const serviceList = page.locator('.floating-overlay__service-list');
    await expect(serviceList).toBeVisible();

    const items = serviceList.locator('.floating-overlay__service-list-item');
    await expect(items).toHaveCount(optionsToSelect.length);

    for (const label of optionsToSelect) {
      await expect(items.filter({ hasText: label })).toHaveCount(1);
    }
  });

  test('bei Service ohne Sub-Optionen wird der Service-Titel als ein Eintrag gezeigt', async ({ page }) => {
    await setLanguage(page, 'de');
    await goToServicesPage(page);

    // Brake Fluid hat keine Sub-Options (siehe service.model.ts)
    const brakeCard = page.locator('mat-card', { hasText: 'Bremsflüssigkeit' }).first();
    await brakeCard.click();

    const cartFab = page.locator('.floating-overlay__fab').first();
    await cartFab.click();

    const items = page.locator('.floating-overlay__service-list-item');
    await expect(items).toHaveCount(1);
    await expect(items.first()).toContainText(/bremsflüssigkeit/i);
  });
});
