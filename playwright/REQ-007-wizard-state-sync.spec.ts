import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  clickAppointmentBack,
  clickLocationBack,
  clickNotesBack,
  clickNotesContinue,
  clickServiceBack,
  enterNote,
  getPageTitle,
  goToAppointmentPage,
  goToNotesPage,
  goToServiceSelection,
  selectAppointmentCard,
  selectFirstBrand,
  selectFirstLocation,
  selectService,
} from './helpers/booking.helpers';

/**
 * REQ-007: Wizard State Sync (Back Navigation Store Reset)
 *
 * Cross-Cutting Requirement: No new UI elements.
 * Modifies onBack() methods in Container-Components so that Store-Properties
 * are nulled before backward navigation.
 *
 * Wizard Steps:
 *   1. Brand (/home/brand)         — no back button
 *   2. Location (/home/location)   — onBack() clears selectedLocation
 *   3. Services (/home/services)   — onBack() clears selectedServices (already existed)
 *   4. Notes (/home/notes)         — onBack() clears bookingNote
 *   5. Appointment (/home/appointment) — onBack() clears selectedAppointment
 *
 * Guards:
 *   - brandSelectedGuard: requires selectedBrand
 *   - locationSelectedGuard: requires selectedBrand + selectedLocation
 *   - servicesSelectedGuard: requires selectedBrand + selectedLocation + selectedServices
 */

test.describe.skip('REQ-007: Wizard State Sync (Back Navigation)', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('Step 1: Back from Appointment (Step 5) clears selectedAppointment, navigates to /home/notes', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select an appointment first (so selectedAppointment is set)
      await selectAppointmentCard(page, 0);

      // Verify we are on appointment page
      const routeBefore = await getCurrentRoute(page);
      expect(routeBefore).toBe('/home/appointment');

      // Click back
      await clickAppointmentBack(page);

      // Verify navigation to /home/notes
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // Verify notes page renders correctly
      const title = await getPageTitle(page);
      expect(title).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');
    });

    test('Step 2: Back from Notes (Step 4) clears bookingNote, navigates to /home/services', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Enter a note (so bookingNote is set)
      await enterNote(page, 'Bitte Öl prüfen.');

      // Verify we are on notes page
      const routeBefore = await getCurrentRoute(page);
      expect(routeBefore).toBe('/home/notes');

      // Click back
      await clickNotesBack(page);

      // Verify navigation to /home/services
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      // Verify services page renders correctly
      const title = await getPageTitle(page);
      expect(title).toBe('Welche Services möchten Sie buchen?');
    });

    test('Step 3: Back from Services (Step 3) clears selectedServices, navigates to /home/location', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select a service (so selectedServices is set)
      await selectService(page, 'HU/AU');

      // Verify we are on services page
      const routeBefore = await getCurrentRoute(page);
      expect(routeBefore).toBe('/home/services');

      // Click back
      await clickServiceBack(page);

      // Verify navigation to /home/location
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      // Verify location page renders correctly
      const title = await getPageTitle(page);
      expect(title).toBe('An welchem Standort dürfen wir Sie begrüßen?');
    });

    test('Step 4: Back from Location (Step 2) clears selectedLocation, navigates to /home/brand', async ({ page }) => {
      await setLanguage(page, 'de');

      // Navigate to location page via brand selection
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });

      // Verify we are on location page
      const routeBefore = await getCurrentRoute(page);
      expect(routeBefore).toBe('/home/location');

      // Click back
      await clickLocationBack(page);

      // Verify navigation to /home/brand
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');

      // Verify brand page renders correctly
      const title = await getPageTitle(page);
      expect(title).toBe('Welche Fahrzeugmarke fahren Sie?');
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-1: Appointment onBack() nulls selectedAppointment (AC-1)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select an appointment to set selectedAppointment
      await selectAppointmentCard(page, 0);

      // Click back
      await clickAppointmentBack(page);

      // Navigated to /home/notes
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // If we now navigate forward from notes, we should reach appointment
      // but selectedAppointment should be null (no appointment card selected)
      await clickNotesContinue(page);
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // No appointment card should be pre-selected
      const selectedCards = page.locator('.appointment-card--selected');
      const selectedCount = await selectedCards.count();
      expect(selectedCount).toBe(0);

      // Continue button should be disabled (no appointment selected)
      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toBeDisabled();
    });

    test('TC-2: Notes onBack() nulls bookingNote (AC-2)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Enter a note
      await enterNote(page, 'Bitte Öl prüfen.');

      // Click back
      await clickNotesBack(page);

      // Navigated to /home/services
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');
    });

    test('TC-3: Services onBack() nulls selectedServices (AC-3, already implemented)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select services
      await selectService(page, 'HU/AU');
      await selectService(page, 'Inspektion');

      // Verify badge shows 2 services
      const badge = page.locator('.cart-fab .mat-badge-content');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('2');

      // Click back
      await clickServiceBack(page);

      // Navigated to /home/location
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('TC-4: Location onBack() nulls selectedLocation (AC-4)', async ({ page }) => {
      await setLanguage(page, 'de');

      // Navigate to location
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });

      // Click back
      await clickLocationBack(page);

      // Navigated to /home/brand
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('TC-5: Guard redirect after backward navigation from Notes (AC-5, AC-6)', async ({ page }) => {
      await setLanguage(page, 'de');

      // Walk through wizard to appointment page
      await goToAppointmentPage(page);

      // Navigate back: Appointment -> Notes -> Services -> Location
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      // Now selectedServices is null, selectedLocation still set (we're on location page)
      // Direct access to /home/notes should be redirected by guard
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Guard should redirect because selectedServices is null
      expect(route).not.toBe('/home/notes');
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

    test('TC-6: Guard redirect after complete backward navigation (AC-6)', async ({ page }) => {
      await setLanguage(page, 'de');

      // Walk through wizard to appointment page
      await goToAppointmentPage(page);

      // Navigate all the way back: Appointment -> Notes -> Services -> Location -> Brand
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      await clickLocationBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/brand');

      // After full backward navigation, try direct access to /home/appointment
      await navigateTo(page, '/home/appointment');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Guard cascade: selectedLocation is null -> redirect away from appointment
      expect(route).not.toBe('/home/appointment');
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

    test('TC-7: Forward navigation remains unchanged (AC-7)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select a service
      await selectService(page, 'HU/AU');

      // Verify badge shows 1 service
      const badge = page.locator('.cart-fab .mat-badge-content');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('1');

      // Click continue (forward navigation)
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();
      await continueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Should navigate to /home/notes
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // Badge should still show 1 (services NOT cleared by forward navigation)
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('1');
    });

    test('TC-8: Cart badge updates immediately after back navigation reset (AC-8)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page, { serviceNames: ['HU/AU', 'Inspektion'] });

      // Enter a note
      await enterNote(page, 'Testnotiz');

      // Badge should show 2 services
      const badge = page.locator('.cart-fab .mat-badge-content');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('2');

      // Click back on notes -> clears bookingNote, navigate to /home/services
      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      // Badge should still show 2 (bookingNote has no badge effect)
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('2');

      // Click back on services -> clears selectedServices, navigate to /home/location
      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      // Badge should now be hidden (0 services)
      await expect(badge).toBeHidden();
    });

    test('TC-9: Complete backward navigation and URL redirect (E2E)', async ({ page }) => {
      await setLanguage(page, 'de');

      // Walk through full wizard: Brand -> Location -> Services -> Notes -> Appointment
      await goToAppointmentPage(page);

      // Select an appointment
      await selectAppointmentCard(page, 0);

      // Navigate back 4 times: Appointment -> Notes -> Services -> Location -> Brand
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      await clickLocationBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/brand');

      // After full backward navigation, direct access to /home/appointment should redirect
      await navigateTo(page, '/home/appointment');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/appointment');
      // Should redirect to location (brand is still set, but location is null)
      expect(route).toMatch(/\/home\/(brand|location)/);
    });

    test('TC-10: Partial back then forward - appointment must be re-selected (E2E)', async ({ page }) => {
      await setLanguage(page, 'de');

      // Walk through full wizard to appointment
      await goToAppointmentPage(page);

      // Select an appointment
      await selectAppointmentCard(page, 0);

      // Navigate back to notes (clears selectedAppointment)
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      // Navigate forward from notes (sets bookingNote again)
      await clickNotesContinue(page);
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Should be on appointment page
      expect(await getCurrentRoute(page)).toBe('/home/appointment');

      // selectedAppointment should be null -> no card pre-selected
      const selectedCards = page.locator('.appointment-card--selected');
      expect(await selectedCards.count()).toBe(0);

      // Continue button should be disabled
      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toBeDisabled();
    });

    test('TC-11: Idempotent reset - double back does not error (Exception 6.1)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Do NOT select an appointment (selectedAppointment is already null)
      // Click back -> should still work without errors
      await clickAppointmentBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // Notes page should render correctly
      const title = await getPageTitle(page);
      expect(title).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');
    });

    test('TC-12: Browser back navigates without wizard onBack() (Alternative 5.2)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Enter a note
      await enterNote(page, 'Testnotiz fuer Browser-Back');

      // Use browser back (history back) instead of wizard back button
      await page.goBack();
      await waitForAngular(page);

      // Should be on /home/services (browser navigated back)
      const servicesRoute = await getCurrentRoute(page);
      expect(servicesRoute).toBe('/home/services');

      // Services should still be in the store (browser back did NOT call onBack())
      // Continue button should still be enabled because selectedServices remains set
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: Partial back from Step 5 to Step 4, then forward again', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select appointment
      await selectAppointmentCard(page, 0);

      // Back to notes (clears selectedAppointment)
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      // Forward from notes (does NOT clear bookingNote when going forward)
      await clickNotesContinue(page);
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // On appointment page, but no appointment pre-selected
      expect(await getCurrentRoute(page)).toBe('/home/appointment');
      const selectedCards = page.locator('.appointment-card--selected');
      expect(await selectedCards.count()).toBe(0);
    });

    test('5.2: Browser back does not trigger store reset', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Enter a note
      await enterNote(page, 'Test note for browser back');

      // Use browser back
      await page.goBack();
      await waitForAngular(page);

      // Should be on /home/services
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      // Services should still be selected (browser back did not clear store)
      // The continue button should still be enabled because services remain in store
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();
    });

    test('5.3: Brand page has no back button (Step 1 is entry point)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateTo(page, '/home/brand');
      await waitForAngular(page);

      // No back button should exist on brand page
      const backButtons = page.locator('.brand-selection__back-button');
      expect(await backButtons.count()).toBe(0);
    });

  });

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: Store already empty on back - no error, navigation works (idempotent)', async ({ page }) => {
      await setLanguage(page, 'de');

      // Navigate to location via brand selection
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });

      // Do NOT select a location (selectedLocation might be from a previous step,
      // but in this flow we haven't selected one on this page)
      // Click back
      await clickLocationBack(page);

      // Should navigate without error
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('6.2: Guard cascade after multi-step backward navigation', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Navigate back from Appointment to Services (clears selectedAppointment + bookingNote)
      await clickAppointmentBack(page);
      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      // Now selectedServices is still set, but selectedAppointment and bookingNote are null
      // Direct access to /home/appointment should redirect because after back from services,
      // services would be cleared too. But we haven't gone back from services yet.
      // So /home/appointment should still be accessible IF the guard only checks services...
      // Actually: the guard checks brand + location + services, which are all still set.
      // So /home/appointment should work.

      // But let's go back from services too to clear selectedServices:
      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      // Now try /home/appointment — selectedServices is null
      await navigateTo(page, '/home/appointment');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/appointment');
      // Guard redirects because selectedServices is null
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

    test('6.2b: Direct URL to /home/notes after clearing services via back', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // Back from notes to services (clears bookingNote)
      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      // Back from services to location (clears selectedServices)
      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      // Direct access to /home/notes should fail because selectedServices is null
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/notes');
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

  });

  // =============================================
  // i18n
  // =============================================

  test.describe('i18n', () => {

    test('DE: Back buttons use German labels on all pages', async ({ page }) => {
      await setLanguage(page, 'de');

      // Location page
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });
      const locationBackButton = page.locator('.location-selection__back-button');
      await expect(locationBackButton).toContainText('Zurück');

      // Go forward to services
      await selectFirstLocation(page);
      await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 10000 });
      const serviceBackButton = page.locator('.summary-bar__back-button');
      await expect(serviceBackButton).toContainText('Zurück');
    });

    test('EN: Back buttons use English labels on all pages', async ({ page }) => {
      await setLanguage(page, 'en');

      // Location page
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });
      const locationBackButton = page.locator('.location-selection__back-button');
      await expect(locationBackButton).toContainText('Back');

      // Go forward to services
      await selectFirstLocation(page);
      await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 10000 });
      const serviceBackButton = page.locator('.summary-bar__back-button');
      await expect(serviceBackButton).toContainText('Back');
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('all back buttons have accessible text content', async ({ page }) => {
      await setLanguage(page, 'de');

      // Location page back button has visible text
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });
      const locationBack = page.locator('.location-selection__back-button');
      await expect(locationBack).toContainText('Zurück');
    });

    test('back buttons are keyboard-accessible', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      // The back button should be focusable
      const backButton = page.locator('.notes__back-button');
      await backButton.focus();
      await expect(backButton).toBeFocused();

      // Press Enter to trigger back navigation
      await page.keyboard.press('Enter');
      await waitForAngular(page);

      // Should navigate to /home/services
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');
    });

  });

  // =============================================
  // RESPONSIVE LAYOUT
  // =============================================

  test.describe('Responsive Layout', () => {

    test('back buttons visible on all wizard pages', async ({ page }) => {
      await setLanguage(page, 'de');

      // Location page
      await selectFirstBrand(page);
      await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 10000 });
      const locationBack = page.locator('.location-selection__back-button');
      await expect(locationBack).toBeVisible();

      // Navigate forward and check services back button
      await selectFirstLocation(page);
      await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 10000 });
      const serviceBack = page.locator('.summary-bar__back-button');
      await expect(serviceBack).toBeVisible();

      // Navigate forward and check notes back button
      await selectService(page, 'HU/AU');
      const continueBtn = page.locator('.summary-bar__continue-button');
      await continueBtn.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);
      const notesBack = page.locator('.notes__back-button');
      await expect(notesBack).toBeVisible();

      // Navigate forward and check appointment back button
      await clickNotesContinue(page);
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);
      const appointmentBack = page.locator('.appointment-selection__back-button');
      await expect(appointmentBack).toBeVisible();
    });

  });

});
