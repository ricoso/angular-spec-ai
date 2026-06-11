import { expect, test } from '@playwright/test';

import { setLanguage, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-023 / AC-4: Form-Error-Reaktivitaet im Kundendaten-Formular.
 *
 * Hintergrund (REQ-021 (z)): xxxErrors-Computeds reagieren jetzt auf
 * `toSignal(merge(form.valueChanges, form.statusChanges))` statt nur auf das
 * Input-Signal `form()`. Dadurch erscheint die Fehlermeldung beim Tippen — ohne Blur.
 *
 * Test: ungueltige Mobilnummer "12345" tippen → Error-Text erscheint sofort.
 *
 * Hinweis: Wir navigieren direkt zur Carinformation-Seite via deep-link;
 * wenn der Flow Voraussetzungen erfordert, wird der Test geskippt.
 */

const VALID_BRANCH_ID = '68f606798d2bb4389ed1af51';
const VALID_GROUP_ID = '6908807454f4bb2b69b03fde';

test.describe('REQ-023: Form-Error-Reaktivitaet', () => {
  test('Mobilnummer "12345" zeigt sofort Fehler beim Tippen', async ({ page }) => {
    await setLanguage(page, 'de');

    // Direkt zur Kundendaten-Seite — falls Guard greift, brechen wir ab.
    await page.goto(`/home/carinformation?branchId=${VALID_BRANCH_ID}&groupId=${VALID_GROUP_ID}`);
    await waitForAngular(page);

    const mobilePhoneInput = page.locator('input[formcontrolname="mobilePhone"]');
    if (await mobilePhoneInput.count() === 0) {
      test.skip(true, 'Carinformation-Seite nicht erreichbar (Guard) — vollstaendiger Flow getrennt getestet');
    }

    // Feld ist sichtbar — touchen + ungueltigen Wert eintippen
    await mobilePhoneInput.click();
    await mobilePhoneInput.fill('12345');
    // Ein Char nachsenden um valueChanges zu triggern (idempotent)
    await mobilePhoneInput.press('End');
    await page.waitForTimeout(150);

    // Pruefe: das umgebende mat-form-field hat invalid-State UND die Error-Meldung ist sichtbar
    const formField = page.locator('mat-form-field', { has: mobilePhoneInput });
    await expect(formField).toHaveClass(/mat-form-field-invalid/);

    const errorText = formField.locator('mat-error');
    await expect(errorText).toBeVisible();
    const text = (await errorText.textContent() ?? '').trim();
    // i18n: DE-Text "Muss mit 0 oder + beginnen" / EN aequivalent
    expect(text.length).toBeGreaterThan(3);
  });

  test('Mobilnummer "+491701234567" zeigt KEINEN Fehler', async ({ page }) => {
    await setLanguage(page, 'de');
    await page.goto(`/home/carinformation?branchId=${VALID_BRANCH_ID}&groupId=${VALID_GROUP_ID}`);
    await waitForAngular(page);

    const mobilePhoneInput = page.locator('input[formcontrolname="mobilePhone"]');
    if (await mobilePhoneInput.count() === 0) {
      test.skip(true, 'Carinformation-Seite nicht erreichbar (Guard)');
    }

    await mobilePhoneInput.click();
    await mobilePhoneInput.fill('+491701234567');
    await page.waitForTimeout(150);

    const formField = page.locator('mat-form-field', { has: mobilePhoneInput });
    await expect(formField).not.toHaveClass(/mat-form-field-invalid/);

    const errorText = formField.locator('mat-error');
    expect(await errorText.count()).toBe(0);
  });
});
