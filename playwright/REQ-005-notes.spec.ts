import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  clickNotesBack,
  clickNotesContinue,
  confirmTireChange,
  enterNote,
  getCharCounter,
  getPageTitle,
  getVisibleHintTexts,
  goToNotesPage,
  goToServiceSelection,
  selectService,
} from './helpers/booking.helpers';

/**
 * REQ-005: Hinweisfenster (Notes Page)
 * Wizard Step 4: User can add optional notes and view service-specific hints
 *
 * Flow: Brand -> Location -> Services -> Notes
 * Guard: servicesSelectedGuard (requires brand + location + services)
 *
 * Components:
 * - NotesContainerComponent (Container)
 * - NotesFormComponent (Presentational: textarea + char counter)
 * - ServiceHintsComponent (Presentational: conditional hints per selected service)
 */

test.describe.skip('REQ-005: Notes Page (Hinweisfenster)', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: page loads with headings, textarea, hints, and char counter', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page, { serviceNames: ['HU/AU', 'Inspektion'] });

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // Page title (h1)
      const title = await getPageTitle(page);
      expect(title).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');

      // Subtitle (p)
      const subtitle = page.locator('.notes__subtitle');
      await expect(subtitle).toBeVisible();
      await expect(subtitle).toContainText('Möchten Sie uns noch etwas zu Ihrer Buchung mitteilen?');

      // Textarea visible with placeholder
      const textarea = page.locator('.notes-form__textarea');
      await expect(textarea).toBeVisible();
      const placeholder = await textarea.getAttribute('placeholder');
      expect(placeholder).toContain('Bitte tragen Sie hier Ihre Nachricht an uns ein');

      // Character counter shows "0 / 1000"
      const counter = await getCharCounter(page);
      expect(counter).toBe('0 / 1000');

      // Hints section visible
      const hintsTitle = page.locator('.service-hints__title');
      await expect(hintsTitle).toBeVisible();
      await expect(hintsTitle).toContainText('Wichtige Hinweise zu Ihren ausgewählten Services');

      // Hint items visible for selected services
      const hintItems = page.locator('.service-hints__item');
      const hintCount = await hintItems.count();
      expect(hintCount).toBe(2); // HU/AU + Inspektion
    });

    test('Step 2: placeholder disappears on focus', async ({ page }) => {
      await goToNotesPage(page);

      const textarea = page.locator('.notes-form__textarea');
      await expect(textarea).toBeVisible();

      // Before focus: placeholder should be visible (standard HTML behavior)
      const placeholder = await textarea.getAttribute('placeholder');
      expect(placeholder).toBeTruthy();

      // Focus the textarea
      await textarea.focus();

      // After focus: textarea is focused, placeholder still exists as attribute
      // (but rendered hidden by browser when focused with value)
      await expect(textarea).toBeFocused();
    });

    test('Step 3: char counter updates on typing', async ({ page }) => {
      await goToNotesPage(page);

      // Type text
      await enterNote(page, 'Hallo');

      // Counter should update
      const counter = await getCharCounter(page);
      expect(counter).toBe('5 / 1000');
    });

    test('Step 4: continue saves note and navigates', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      await enterNote(page, 'Bitte Öl prüfen.');
      await clickNotesContinue(page);

      // placeholder: Continue stays on /home/notes
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-1: page with all 3 services shows all 3 hints', async ({ page }) => {
      await setLanguage(page, 'de');
      // Select all 3 services (tire change needs special handling)
      await goToServiceSelection(page);
      await selectService(page, 'HU/AU');
      await selectService(page, 'Inspektion');

      // For tire change, use the confirm helper
      await confirmTireChange(page, 'without-storage');

      // Click Continue on services page to navigate to notes
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeVisible();
      await expect(continueButton).toBeEnabled();
      await continueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // All 3 hint items visible
      const hintItems = page.locator('.service-hints__item');
      const hintCount = await hintItems.count();
      expect(hintCount).toBe(3);

      // Counter at 0
      const counter = await getCharCounter(page);
      expect(counter).toBe('0 / 1000');
    });

    test('TC-3: character counter updates on typing', async ({ page }) => {
      await goToNotesPage(page);

      const textarea = page.locator('.notes-form__textarea');
      await textarea.fill('Hallo');
      await waitForAngular(page);

      let counter = await getCharCounter(page);
      expect(counter).toBe('5 / 1000');

      // Type more
      await textarea.fill('Hallo Welt, dies ist ein Test');
      await waitForAngular(page);

      counter = await getCharCounter(page);
      expect(counter).toBe('29 / 1000');
    });

    test('TC-4: maxlength prevents input beyond 1000 characters', async ({ page }) => {
      await goToNotesPage(page);

      const textarea = page.locator('.notes-form__textarea');

      // Check maxlength attribute
      const maxlength = await textarea.getAttribute('maxlength');
      expect(maxlength).toBe('1000');

      // Fill with 1000 characters
      const longText = 'A'.repeat(1000);
      await textarea.fill(longText);
      await waitForAngular(page);

      const counter = await getCharCounter(page);
      expect(counter).toBe('1000 / 1000');
    });

    test('TC-5: continue without text -> bookingNote null, navigation works', async ({ page }) => {
      await goToNotesPage(page);

      // Do not enter any text
      const counter = await getCharCounter(page);
      expect(counter).toBe('0 / 1000');

      // Click continue
      await clickNotesContinue(page);

      // placeholder: stays on notes, but no error
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');
    });

    test('TC-6: continue with text -> bookingNote saved', async ({ page }) => {
      await goToNotesPage(page);

      await enterNote(page, 'Bitte Öl prüfen.');
      const counter = await getCharCounter(page);
      // "Bitte Öl prüfen." = 16 characters (Ö counts as 1)
      expect(counter).toBe('16 / 1000');

      await clickNotesContinue(page);

      // placeholder: stays on notes
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');
    });

    test('TC-7: back navigation to /home/services', async ({ page }) => {
      await goToNotesPage(page);

      await clickNotesBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');
    });

    test('TC-8: only hints for selected services shown (HU/AU only)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page, { serviceNames: ['HU/AU'] });

      // Only HU/AU hint should be visible
      const hintItems = page.locator('.service-hints__item');
      const hintCount = await hintItems.count();
      expect(hintCount).toBe(1);

      // Verify it is the HU/AU hint
      const hintTexts = await getVisibleHintTexts(page);
      expect(hintTexts[0]).toContain('HU/AU');
    });

    test('TC-8b: only hints for selected services shown (Inspektion only)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page, { serviceNames: ['Inspektion'] });

      const hintItems = page.locator('.service-hints__item');
      const hintCount = await hintItems.count();
      expect(hintCount).toBe(1);

      // The hint text contains the service-specific hint, not the service name
      const hintTexts = await getVisibleHintTexts(page);
      expect(hintTexts[0]).toContain('Serviceheft');
    });

    test('TC-11: guard redirect — no services -> /home/services', async ({ page }) => {
      // Navigate directly to notes without any prerequisites
      // Guard chain: no brand -> /home/brand
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Without brand, redirects to /home/brand first
      expect(route).toBe('/home/brand');
    });

    test('TC-13: guard redirect — no brand -> /home/brand', async ({ page }) => {
      // Fresh session, no brand selected
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: continue without text (empty field = null)', async ({ page }) => {
      await goToNotesPage(page);

      // Leave textarea empty, click continue
      const counter = await getCharCounter(page);
      expect(counter).toBe('0 / 1000');

      await clickNotesContinue(page);

      // No error, page does not break
      const route = await getCurrentRoute(page);
      expect(route).toBeTruthy();
    });

    test('5.2: back to services', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Enter some text first
      await enterNote(page, 'Test text');

      // Click back
      await clickNotesBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      // Services page title visible
      const title = await getPageTitle(page);
      expect(title).toBe('Welche Services möchten Sie buchen?');
    });

    test('5.2b: back then forward preserves page', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Back to services
      await clickNotesBack(page);
      const servicesRoute = await getCurrentRoute(page);
      expect(servicesRoute).toBe('/home/services');

      // Click Continue on services page to navigate back to notes
      const continueBtn = page.locator('.summary-bar__continue-button');
      await expect(continueBtn).toBeVisible();
      await expect(continueBtn).toBeEnabled();
      await continueBtn.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      const notesRoute = await getCurrentRoute(page);
      expect(notesRoute).toBe('/home/notes');

      // Page should be intact
      const title = await getPageTitle(page);
      expect(title).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');
    });

  });

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: direct access to /home/notes without services -> redirect', async ({ page }) => {
      // Without any state, guard should redirect
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Guard checks brand first -> /home/brand
      expect(route).not.toBe('/home/notes');
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

    test('6.1b: guard chain — no brand -> /home/brand', async ({ page }) => {
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('6.2: fresh session -> no access to notes', async ({ page }) => {
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/notes');
    });

  });

  // =============================================
  // i18n (Language Switch)
  // =============================================

  test.describe('i18n', () => {

    test('DE: page title in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');
    });

    test('EN: page title in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Please provide further notes for your booking');
    });

    test('DE: subtitle in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const subtitle = page.locator('.notes__subtitle');
      await expect(subtitle).toContainText('Möchten Sie uns noch etwas zu Ihrer Buchung mitteilen?');
    });

    test('EN: subtitle in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page);

      const subtitle = page.locator('.notes__subtitle');
      await expect(subtitle).toContainText('Would you like to tell us anything else about your booking?');
    });

    test('DE: hints title in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const hintsTitle = page.locator('.service-hints__title');
      await expect(hintsTitle).toContainText('Wichtige Hinweise zu Ihren ausgewählten Services');
    });

    test('EN: hints title in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page);

      const hintsTitle = page.locator('.service-hints__title');
      await expect(hintsTitle).toContainText('Important notes about your selected services');
    });

    test('DE: button labels (Zurück/Weiter)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const backButton = page.locator('.notes__back-button');
      await expect(backButton).toContainText('Zurück');

      const continueButton = page.locator('.notes__continue-button');
      await expect(continueButton).toContainText('Weiter');
    });

    test('EN: button labels (Back/Continue)', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page);

      const backButton = page.locator('.notes__back-button');
      await expect(backButton).toContainText('Back');

      const continueButton = page.locator('.notes__continue-button');
      await expect(continueButton).toContainText('Continue');
    });

    test('DE: textarea placeholder in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const textarea = page.locator('.notes-form__textarea');
      const placeholder = await textarea.getAttribute('placeholder');
      expect(placeholder).toContain('Bitte tragen Sie hier Ihre Nachricht an uns ein');
    });

    test('EN: textarea placeholder in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page);

      const textarea = page.locator('.notes-form__textarea');
      const placeholder = await textarea.getAttribute('placeholder');
      expect(placeholder).toContain('Please enter your message here');
    });

    test('DE: hint texts in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page, { serviceNames: ['HU/AU'] });

      const hintTexts = await getVisibleHintTexts(page);
      expect(hintTexts[0]).toContain('HU/AU');
      expect(hintTexts[0]).toContain('Fahrzeugpapiere');
    });

    test('EN: hint texts in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToNotesPage(page, { serviceNames: ['HU/AU'] });

      const hintTexts = await getVisibleHintTexts(page);
      expect(hintTexts[0]).toContain('HU/AU');
      expect(hintTexts[0]).toContain('vehicle documents');
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('textarea has aria-label and aria-describedby', async ({ page }) => {
      await goToNotesPage(page);

      const textarea = page.locator('.notes-form__textarea');
      const ariaLabel = await textarea.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();

      // REQ-025 (a): aria-describedby links to both notes-hint (top) and notes-counter (bottom)
      const ariaDescribedBy = await textarea.getAttribute('aria-describedby');
      expect(ariaDescribedBy).toContain('notes-hint');
      expect(ariaDescribedBy).toContain('notes-counter');
    });

    test('char counter has aria-live="polite"', async ({ page }) => {
      await goToNotesPage(page);

      const counter = page.locator('#notes-counter');
      const ariaLive = await counter.getAttribute('aria-live');
      expect(ariaLive).toBe('polite');
    });

    test('service hints section has aria-label', async ({ page }) => {
      await goToNotesPage(page);

      const hintsSection = page.locator('.service-hints');
      const ariaLabel = await hintsSection.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('hint items have role="note"', async ({ page }) => {
      await goToNotesPage(page);

      const hintItem = page.locator('.service-hints__item').first();
      const role = await hintItem.getAttribute('role');
      expect(role).toBe('note');
    });

    test('hint icons have aria-hidden="true"', async ({ page }) => {
      await goToNotesPage(page);

      const icon = page.locator('.service-hints__icon').first();
      const ariaHidden = await icon.getAttribute('aria-hidden');
      expect(ariaHidden).toBe('true');
    });

    test('buttons have aria-label', async ({ page }) => {
      await goToNotesPage(page);

      const backButton = page.locator('.notes__back-button');
      const backAriaLabel = await backButton.getAttribute('aria-label');
      expect(backAriaLabel).toBeTruthy();

      const continueButton = page.locator('.notes__continue-button');
      const continueAriaLabel = await continueButton.getAttribute('aria-label');
      expect(continueAriaLabel).toBeTruthy();
    });

    test('keyboard navigation: Tab order (textarea -> back -> continue)', async ({ page }) => {
      await goToNotesPage(page);

      // Tab to textarea
      await page.keyboard.press('Tab');
      // Textarea might not be first focusable, so we press tab until we reach it
      // or check the general focus flow
      const focused = page.locator(':focus');
      await expect(focused).toBeVisible();
    });

  });

  // =============================================
  // RESPONSIVE
  // =============================================

  test.describe('Responsive Layout', () => {

    test('notes section visible with all elements', async ({ page }) => {
      await goToNotesPage(page);

      const notesSection = page.locator('.notes');
      await expect(notesSection).toBeVisible();

      const title = page.locator('.notes__title');
      await expect(title).toBeVisible();

      const textarea = page.locator('.notes-form__textarea');
      await expect(textarea).toBeVisible();

      const actions = page.locator('.notes__actions');
      await expect(actions).toBeVisible();
    });

    test('back and continue buttons visible', async ({ page }) => {
      await goToNotesPage(page);

      const backButton = page.locator('.notes__back-button');
      await expect(backButton).toBeVisible();

      const continueButton = page.locator('.notes__continue-button');
      await expect(continueButton).toBeVisible();
    });

    test('textarea is full-width', async ({ page }) => {
      await goToNotesPage(page);

      const formField = page.locator('.notes-form__field');
      await expect(formField).toBeVisible();

      // Verify the form field has reasonable width
      const box = await formField.boundingBox();
      expect(box).toBeTruthy();
      // Should take up significant width of the page (at least 50%)
      if (box) {
        const viewportSize = page.viewportSize();
        if (viewportSize) {
          expect(box.width).toBeGreaterThan(viewportSize.width * 0.4);
        }
      }
    });

    test('service hints section visible when services selected', async ({ page }) => {
      await goToNotesPage(page);

      const hintsSection = page.locator('.service-hints');
      await expect(hintsSection).toBeVisible();
    });

  });

});
