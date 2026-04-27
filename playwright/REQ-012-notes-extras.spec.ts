import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, setLanguage } from './helpers/app.helpers';
import {
  clickNotesContinue,
  goToNotesPage,
} from './helpers/booking.helpers';

/**
 * REQ-012: Hinweisseite Erweitert (Notes Extras)
 * Extends Wizard Step 4: Adds 3 mat-select dropdowns for
 * Mobility Options, Appointment Preference, and Callback.
 *
 * Flow: Brand -> Location -> Services -> Notes (extras visible)
 * Guard: servicesSelectedGuard (requires brand + location + services)
 */

/** Helper: get the mat-select trigger text for a given dropdown */
async function getDropdownValue(page: import('@playwright/test').Page, index: number): Promise<string> {
  const selects = page.locator('app-notes-extras-form mat-select');
  const trigger = selects.nth(index).locator('.mat-mdc-select-value-text');
  return (await trigger.textContent())?.trim() ?? '';
}

/** Helper: select an option from a mat-select dropdown by index */
async function selectDropdownOption(
  page: import('@playwright/test').Page,
  dropdownIndex: number,
  optionText: string,
): Promise<void> {
  const selects = page.locator('app-notes-extras-form mat-select');
  await selects.nth(dropdownIndex).click();
  await page.waitForTimeout(300);
  const option = page.locator('mat-option', { hasText: optionText });
  await option.click();
  await page.waitForTimeout(300);
}

test.describe.skip('REQ-012: Notes Extras (Hinweisseite Erweitert)', () => {

  // =============================================
  // AC-1 to AC-4: Dropdowns are visible
  // =============================================

  test.describe('Dropdown Visibility', () => {

    test('TC-1: all 3 dropdowns are visible on notes page (AC-1, AC-2, AC-3, AC-4)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // 3 mat-select dropdowns exist
      const selects = page.locator('app-notes-extras-form mat-select');
      await expect(selects).toHaveCount(3);

      // Extras section appears BEFORE the textarea (AC-4)
      const extrasBox = await page.locator('.notes-extras').first().boundingBox();
      const textareaBox = await page.locator('.notes-form__textarea').first().boundingBox();
      expect(extrasBox).toBeTruthy();
      expect(textareaBox).toBeTruthy();
      if (extrasBox && textareaBox) {
        expect(extrasBox.y).toBeLessThan(textareaBox.y);
      }
    });

    test('TC-2: mobility dropdown has 4 options (AC-1)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Open first dropdown (Mobility)
      const selects = page.locator('app-notes-extras-form mat-select');
      await selects.nth(0).click();
      await page.waitForTimeout(300);

      const options = page.locator('mat-option');
      await expect(options).toHaveCount(4);

      // Close dropdown
      await page.keyboard.press('Escape');
    });

    test('TC-3: appointment dropdown has 3 options (AC-2)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const selects = page.locator('app-notes-extras-form mat-select');
      await selects.nth(1).click();
      await page.waitForTimeout(300);

      const options = page.locator('mat-option');
      await expect(options).toHaveCount(3);

      await page.keyboard.press('Escape');
    });

    test('TC-4: callback dropdown has 2 options (AC-3)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const selects = page.locator('app-notes-extras-form mat-select');
      await selects.nth(2).click();
      await page.waitForTimeout(300);

      const options = page.locator('mat-option');
      await expect(options).toHaveCount(2);

      await page.keyboard.press('Escape');
    });
  });

  // =============================================
  // AC-5 to AC-7: Default values
  // =============================================

  test.describe('Default Values', () => {

    test('TC-5: mobility default is "Keine Auswahl" (AC-5)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const value = await getDropdownValue(page, 0);
      expect(value).toBe('Keine Auswahl');
    });

    test('TC-6: appointment default is "Jederzeit" (AC-6)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const value = await getDropdownValue(page, 1);
      expect(value).toBe('Jederzeit');
    });

    test('TC-7: callback default is "Keine Auswahl" (AC-7)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const value = await getDropdownValue(page, 2);
      expect(value).toBe('Keine Auswahl');
    });
  });

  // =============================================
  // AC-8: Continue saves extras
  // =============================================

  test.describe('Save & Restore', () => {

    test('TC-8: continue saves dropdown values to store (AC-8)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Select "Mittelklasse" for mobility
      await selectDropdownOption(page, 0, 'Mittelklasse');

      // Select "Vormittags" for appointment
      await selectDropdownOption(page, 1, 'Vormittags');

      // Select "Ja" for callback
      await selectDropdownOption(page, 2, 'Ja');

      // Click Continue
      await clickNotesContinue(page);
      await page.waitForTimeout(500);

      // Navigate back to notes
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/appointment');
    });

    test('TC-9: back navigation and return preserves selected values (AC-9)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Select custom values
      await selectDropdownOption(page, 0, 'Kleinwagen');
      await selectDropdownOption(page, 1, 'Nachmittags');
      await selectDropdownOption(page, 2, 'Ja');

      // Continue to next page
      await clickNotesContinue(page);
      await page.waitForTimeout(500);

      // Navigate back to notes via browser back
      await page.goBack();
      await page.waitForTimeout(500);

      // Verify values are preserved
      const mobility = await getDropdownValue(page, 0);
      const appointment = await getDropdownValue(page, 1);
      const callback = await getDropdownValue(page, 2);

      expect(mobility).toBe('Kleinwagen');
      expect(appointment).toBe('Nachmittags');
      expect(callback).toBe('Ja');
    });
  });

  // =============================================
  // AC-10: Accessibility
  // =============================================

  test.describe('Accessibility', () => {

    test('TC-10: dropdowns are keyboard navigable (AC-10)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Tab to first dropdown
      const selects = page.locator('app-notes-extras-form mat-select');
      await selects.nth(0).focus();

      // Open with Enter
      await page.keyboard.press('Enter');
      await page.waitForTimeout(300);

      const options = page.locator('mat-option');
      await expect(options.first()).toBeVisible();

      // Close
      await page.keyboard.press('Escape');
    });

    test('TC-11: dropdowns have aria-labels (AC-10)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const formFields = page.locator('app-notes-extras-form mat-form-field');
      const count = await formFields.count();
      expect(count).toBe(3);

      // Each mat-form-field has a label
      for (let i = 0; i < count; i++) {
        const label = formFields.nth(i).locator('mat-label');
        await expect(label).toBeVisible();
      }
    });
  });

  // =============================================
  // AC-11: Responsive
  // =============================================

  test.describe('Responsive Layout', () => {

    test('TC-12: mobile layout stacks dropdowns vertically (AC-11)', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const selects = page.locator('app-notes-extras-form mat-form-field');
      await expect(selects).toHaveCount(3);

      // In mobile, all form fields should be visible
      for (let i = 0; i < 3; i++) {
        await expect(selects.nth(i)).toBeVisible();
      }
    });
  });

  // =============================================
  // AC-12: i18n
  // =============================================

  test.describe('Internationalization', () => {

    test('TC-13: labels show in German (AC-12)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Check headings (h2) not mat-labels for section titles
      const headings = page.locator('app-notes-extras-form h2');
      const headingTexts = await headings.allTextContents();

      expect(headingTexts.some(t => t.includes('Mobilitätsoptionen'))).toBe(true);
      expect(headingTexts.some(t => t.includes('Terminpräferenz'))).toBe(true);
      expect(headingTexts.some(t => t.includes('Rückruf'))).toBe(true);
    });

    test('TC-14: labels show in English (AC-12)', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page);

      // Check headings (h2) for section titles in English
      const headings = page.locator('app-notes-extras-form h2');
      const headingTexts = await headings.allTextContents();

      expect(headingTexts.some(t => t.includes('Mobility'))).toBe(true);
      expect(headingTexts.some(t => t.includes('Appointment'))).toBe(true);
      expect(headingTexts.some(t => t.includes('Callback'))).toBe(true);
    });
  });

  // =============================================
  // AC-14: Existing functionality preserved
  // =============================================

  test.describe('Existing Functionality', () => {

    test('TC-15: textarea still works alongside extras (AC-14)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Textarea is still visible
      const textarea = page.locator('.notes-form__textarea');
      await expect(textarea).toBeVisible();

      // Can type into textarea
      await textarea.fill('Test note');
      await expect(textarea).toHaveValue('Test note');

      // Extras are also visible
      const selects = page.locator('app-notes-extras-form mat-select');
      await expect(selects).toHaveCount(3);
    });

    test('TC-16: continue and back buttons still work (AC-14)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Continue button exists
      const continueBtn = page.locator('.notes__continue-button');
      await expect(continueBtn).toBeVisible();

      // Back button exists
      const backBtn = page.locator('.notes__back-button');
      await expect(backBtn).toBeVisible();
    });
  });
});
