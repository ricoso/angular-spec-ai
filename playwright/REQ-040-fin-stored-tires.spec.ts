import { expect, test } from '@playwright/test';

import { ensureGermanLanguage } from './helpers/app.helpers';
import {
  acceptPrivacyConsent,
  clickCarinformationContinue,
  fillCustomerForm,
  fillVehicleForm,
  goToCarinformationPage,
  goToCarinformationWithStoredTires
} from './helpers/booking.helpers';

/**
 * REQ-040: Die FIN wird nur beim Service „Räderwechsel – meine Räder sind eingelagert"
 * abgefragt — und ist dann Pflicht (17-stellig, alphanumerisch). Bei allen anderen Buchungen
 * erscheint das Feld nicht. Läuft gegen das echte Backend (SoftNet INT); kein finaler Submit,
 * daher keine echte Buchung (Stopp auf der Übersicht).
 */
test.describe('REQ-040 FIN bei eingelagerten Rädern — bedingt + Pflicht', () => {
  test.beforeEach(async ({ page }) => {
    // Voller Wizard-Durchlauf gegen SoftNet (Service → Termin → Kontakt) braucht mehr als 30 s.
    test.setTimeout(120_000);
    await page.goto('/home/location');
    await ensureGermanLanguage(page);
  });

  test('Standardservice (Inspektion) zeigt kein FIN-Feld', async ({ page }) => {
    await goToCarinformationPage(page);
    await expect(page.locator('#vehicle-vin')).toHaveCount(0);
    await expect(page.locator('input[formcontrolname="vin"]')).toHaveCount(0);
  });

  test('eingelagerte Räder: FIN-Feld sichtbar + Pflicht, leerer Submit blockiert', async ({ page }) => {
    await goToCarinformationWithStoredTires(page);

    const vin = page.locator('#vehicle-vin');
    await expect(vin).toBeVisible();
    await expect(vin).toHaveAttribute('aria-required', 'true');

    // Pflicht: Kennzeichen + km gefüllt, FIN leer → Submit blockiert, Fehler erscheint.
    await fillCustomerForm(page);
    await fillVehicleForm(page);
    await acceptPrivacyConsent(page);
    await clickCarinformationContinue(page);

    await expect(page.locator('#vehicle-vin-error')).toBeVisible();
    await expect(page).toHaveURL(/\/home\/carinformation/);
  });

  test('eingelagerte Räder: gültige FIN führt zur Übersicht', async ({ page }) => {
    await goToCarinformationWithStoredTires(page);

    await fillCustomerForm(page);
    await fillVehicleForm(page, { vin: 'wvwzzz1jz3w386752' });
    await acceptPrivacyConsent(page);
    await clickCarinformationContinue(page);

    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
    await expect(page.locator('.booking-overview')).toContainText('WVWZZZ1JZ3W386752');
  });
});
