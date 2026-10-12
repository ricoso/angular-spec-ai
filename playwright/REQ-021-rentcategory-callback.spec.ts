import { expect, test } from '@playwright/test';

// REQ-024: Reaktiviert — Selektoren auf das aktuelle Switch-Card + Pill-Group-Markup
// (notes-extras-form.component.html) aktualisiert. Der alte mat-select/mat-option
// Mobility-Dropdown existiert nicht mehr; Mietwagen-Kategorien werden ueber einen
// Toggle-Switch (#rentSwitch) + Radio-Pill-Group angezeigt.

import { setLanguage } from './helpers/app.helpers';
import {
  acceptPrivacyConsent,
  clickAppointmentContinue,
  clickCarinformationContinue,
  clickNotesContinue,
  fillCustomerForm,
  fillVehicleForm,
  goToNotesPage,
  selectAppointmentCard,
} from './helpers/booking.helpers';

/**
 * REQ-021 Nachtrag (i/j/k/l):
 *  - Mobility-Dropdown ersetzt durch dynamische Rentcategories (Switch + Pill-Group)
 *  - Callback-Auswahl wird als `callbackRequested: boolean` im Booking-Request gesendet
 *  - `mobilityOption` nicht mehr im Appointment-Payload
 */

test.describe('REQ-021: Rentcategory + Callback Flag', () => {
  test('TC-1: Notes-Seite zeigt nur EINEN Mietwagen-Switch (keine Doppelung mehr)', async ({ page }) => {
    await setLanguage(page, 'de');
    await goToNotesPage(page);

    const rentSwitch = page.locator('#rentSwitch');
    await expect(rentSwitch).toHaveCount(1);
    await expect(rentSwitch).toBeVisible();
  });

  test('TC-2: Mietwagen-Switch aktiviert Pill-Group mit dynamisch geladenen Kategorien', async ({ page }) => {
    await setLanguage(page, 'de');
    await goToNotesPage(page);

    // "Keine" (aus) ist der Default-Zustand — der Switch ist zunaechst nicht aktiv
    // und die Pill-Group mit Kategorien ist nicht sichtbar.
    const rentSwitch = page.locator('#rentSwitch');
    await expect(rentSwitch).not.toBeChecked();
    await expect(page.locator('.notes-extras__switch-card.is-on .pill-group')).toHaveCount(0);

    // Aktivieren -> mindestens eine Kategorie aus SoftNet geladen.
    // REQ-039: Schalter als visuell verstecktes Input + Label (Mockup) — Klick auf das Label wie ein Nutzer.
    await page.locator('label[for="rentSwitch"]').click();
    await expect(rentSwitch).toBeChecked();
    const categoryLabels = page.locator('.notes-extras__switch-card.is-on .pill-group label');
    await expect(categoryLabels.first()).toBeVisible({ timeout: 10_000 });
    const count = await categoryLabels.count();
    expect(count).toBeGreaterThan(0);

    // Erste Kategorie ist automatisch vorausgewaehlt (onRentSwitchChange)
    const firstRadio = page.locator('.notes-extras__switch-card.is-on .pill-group input[type="radio"]').first();
    await expect(firstRadio).toBeChecked();
  });

  test('TC-3: Callback=Ja wird als callbackRequested:true im Booking-Request gesendet (kein mobilityOption)', async ({ page }) => {
    await setLanguage(page, 'de');

    let sentBody: Record<string, unknown> | null = null;
    await page.route('**/api/v1/appointments', async (route) => {
      const req = route.request();
      if (req.method() === 'POST') {
        sentBody = req.postDataJSON() as Record<string, unknown>;
      }
      await route.continue();
    });

    await goToNotesPage(page);

    // Rueckruf-Switch aktivieren
    await page.locator('label[for="callbackSwitch"]').click();
    await expect(page.locator('#callbackSwitch')).toBeChecked();

    await clickNotesContinue(page);
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10_000 });
    await selectAppointmentCard(page, 0);
    await clickAppointmentContinue(page);

    await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10_000 });
    await fillCustomerForm(page);
    await fillVehicleForm(page);
    await acceptPrivacyConsent(page);
    await clickCarinformationContinue(page);

    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
    const submitButton = page.locator('.booking-overview__submit-button');
    await expect(submitButton).toBeVisible();
    await submitButton.click();

    await expect.poll(() => sentBody, { timeout: 10_000 }).not.toBeNull();
    const body = sentBody as unknown as Record<string, unknown>;
    expect(body).toHaveProperty('callbackRequested');
    expect(body.callbackRequested).toBe(true);
    // mobilityOption darf nicht mehr im Payload auftauchen
    expect(body).not.toHaveProperty('mobilityOption');
  });
});
