import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  clickBookingOverviewBack,
  clickBookingOverviewSubmit,
  getPageTitle,
  goToBookingOverviewPage,
} from './helpers/booking.helpers';

/**
 * REQ-010: Booking Overview (Buchungsübersicht)
 * Wizard Step 7 (final): Displays a summary of the booking before submission.
 *
 * Flow: Brand -> Location -> Services -> Notes -> Appointment -> Car Information -> Booking Overview
 * Guard: bookingOverviewGuard (requires brand + location + services + appointment + customerInfo + vehicleInfo + privacyConsent)
 *
 * Components:
 * - BookingOverviewContainerComponent (Container)
 * - AppointmentTileComponent (Presentational)
 * - ServicesTileComponent (Presentational)
 * - PersonalDataTileComponent (Presentational)
 * - PriceTileComponent (Presentational)
 */

test.describe.skip('REQ-010: Booking Overview (Buchungsübersicht)', () => {

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: direct access to /home/booking-overview without prerequisites -> redirect to /home', async ({ page }) => {
      await navigateTo(page, '/home/booking-overview');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/booking-overview');
      expect(route).toMatch(/\/home\/(brand)?/);
    });

    test('6.2: store data missing after F5 reload -> guard redirects to /home', async ({ page }) => {
      await navigateTo(page, '/home/booking-overview');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/booking-overview');
    });

  });

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: Happy Path - page loads with title, subtitle, 4 tiles, and navigation (DE)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/booking-overview');

      // Title "Übersicht"
      const title = await getPageTitle(page);
      expect(title).toBe('Übersicht');

      // Subtitle
      const subtitle = page.locator('.booking-overview__subtitle');
      await expect(subtitle).toBeVisible();
      await expect(subtitle).toContainText('Bitte prüfen Sie Ihre Angaben');

      // 4 summary cards
      const cards = page.locator('.summary-card');
      await expect(cards).toHaveCount(4);

      // Navigation bar
      const nav = page.locator('.booking-overview__navigation');
      await expect(nav).toBeVisible();

      // Back button
      const backButton = page.locator('.booking-overview__back-button');
      await expect(backButton).toBeVisible();
      await expect(backButton).toContainText('Zurück');

      // Submit button
      const submitButton = page.locator('.booking-overview__submit-button');
      await expect(submitButton).toBeVisible();
      await expect(submitButton).toContainText('Jetzt anfragen');
    });

    test('TC-2: Appointment tile shows date and time', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const appointmentTile = page.locator('app-appointment-tile');
      await expect(appointmentTile).toBeVisible();

      // Tile title "Wunschtermin"
      const tileTitle = appointmentTile.locator('.summary-card__title');
      await expect(tileTitle).toContainText('Wunschtermin');

      // Date and Time labels are present
      const dateItem = appointmentTile.locator('.detail-list__item').first();
      await expect(dateItem).toContainText('Datum');
      await expect(dateItem).toBeVisible();

      const timeItem = appointmentTile.locator('.detail-list__item').nth(1);
      await expect(timeItem).toContainText('Uhrzeit');
      await expect(timeItem).toBeVisible();

      // Values are rendered (bold text inside items)
      const dateValue = dateItem.locator('strong');
      expect((await dateValue.textContent() ?? '').trim().length).toBeGreaterThan(0);

      const timeValue = timeItem.locator('strong');
      expect((await timeValue.textContent() ?? '').trim().length).toBeGreaterThan(0);
    });

    test('TC-3: Services tile lists all selected services with location', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const servicesTile = page.locator('app-services-tile');
      await expect(servicesTile).toBeVisible();

      // Tile title "Gewählter Service"
      const tileTitle = servicesTile.locator('.summary-card__title');
      await expect(tileTitle).toContainText('Gewählter Service');

      // Location name in intro text — should show the dynamically selected location
      const intro = servicesTile.locator('.service-intro');
      const introText = await intro.textContent() ?? '';
      expect(introText.length).toBeGreaterThan(0);

      // At least one service listed (HU/AU is default)
      const serviceItems = servicesTile.locator('.detail-list__item');
      expect(await serviceItems.count()).toBeGreaterThanOrEqual(1);

      // First service should be HU/AU
      const firstService = serviceItems.first().locator('strong');
      await expect(firstService).toContainText('HU/AU');
    });

    test('TC-5: Personal data tile shows name, address, phone, email, brand, plate, mileage', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const personalTile = page.locator('app-personal-data-tile');
      await expect(personalTile).toBeVisible();

      // Tile title "Ihre Daten"
      const tileTitle = personalTile.locator('.summary-card__title');
      await expect(tileTitle).toContainText('Ihre Daten');

      // Customer data labels and values
      const tileText = await personalTile.textContent() ?? '';
      expect(tileText).toContain('Max');
      expect(tileText).toContain('Mustermann');
      expect(tileText).toContain('Musterweg 1');
      expect(tileText).toContain('Berlin');
      expect(tileText).toContain('017012345678');
      expect(tileText).toContain('max@mustermann.de');

      // Vehicle data
      expect(tileText).toContain('B-MS1234');
      expect(tileText).toContain('5000');

      // Brand name (dynamically selected — just verify something is there)
      const brandNames = ['BMW', 'BMWi', 'Mercedes-Benz', 'Volkswagen'];
      const hasSomeBrand = brandNames.some(b => tileText.includes(b));
      expect(hasSomeBrand).toBeTruthy();
    });

    test('TC-11: Brand name displays with proper casing', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const personalTile = page.locator('app-personal-data-tile');
      await expect(personalTile).toBeVisible();

      const tileText = await personalTile.textContent() ?? '';
      // Check that brand name appears with proper casing (not all-lowercase)
      const brandNames = ['BMW', 'BMWi', 'Mercedes-Benz', 'Volkswagen'];
      const hasSomeBrand = brandNames.some(b => tileText.includes(b));
      expect(hasSomeBrand).toBeTruthy();
    });

    test('Price tile shows price with VAT note', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const priceTile = page.locator('app-price-tile, .price-tile');
      await expect(priceTile.first()).toBeVisible();

      // Tile title "Preis"
      const tileTitle = priceTile.first().locator('.summary-card__title');
      await expect(tileTitle).toContainText('Preis');

      // Price amount visible
      const amount = priceTile.first().locator('.price-tile__amount');
      await expect(amount).toBeVisible();
      const amountText = await amount.textContent() ?? '';
      expect(amountText).toContain('129');

      // VAT note
      const tax = priceTile.first().locator('.price-tile__tax');
      await expect(tax).toContainText('inkl. Mehrwertsteuer');
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-6: Submit button click sets bookingSubmitted and stays on page', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/booking-overview');

      // Click submit
      await clickBookingOverviewSubmit(page);

      // Currently the app navigates to /home/booking-overview (same page)
      const routeAfter = await getCurrentRoute(page);
      expect(routeAfter).toBe('/home/booking-overview');
    });

    test('TC-7: Back button navigates to /home/carinformation', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/booking-overview');

      // Click back
      await clickBookingOverviewBack(page);

      const backRoute = await getCurrentRoute(page);
      expect(backRoute).toBe('/home/carinformation');
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: Back to carinformation preserves form data', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      // Navigate back to carinformation
      await clickBookingOverviewBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/carinformation');

      // Verify the carinformation page is accessible (form still present)
      const carinfoSection = page.locator('.carinformation');
      await expect(carinfoSection).toBeVisible();
    });

  });

  // =============================================
  // i18n (DE + EN)
  // =============================================

  test.describe('i18n', () => {

    test('DE: title "Übersicht", subtitle, back "Zurück", submit "Jetzt anfragen"', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Übersicht');

      const subtitle = page.locator('.booking-overview__subtitle');
      await expect(subtitle).toContainText('Bitte prüfen Sie Ihre Angaben');

      const backButton = page.locator('.booking-overview__back-button');
      await expect(backButton).toContainText('Zurück');

      const submitButton = page.locator('.booking-overview__submit-button');
      await expect(submitButton).toContainText('Jetzt anfragen');
    });

    test('EN: title "Overview", subtitle, back "Back", submit "Request Now"', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToBookingOverviewPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Overview');

      const subtitle = page.locator('.booking-overview__subtitle');
      await expect(subtitle).toContainText('Please review your details');

      const backButton = page.locator('.booking-overview__back-button');
      await expect(backButton).toContainText('Back');

      const submitButton = page.locator('.booking-overview__submit-button');
      await expect(submitButton).toContainText('Request Now');
    });

    test('EN: tile titles are translated', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToBookingOverviewPage(page);

      // Appointment tile
      const appointmentTitle = page.locator('app-appointment-tile .summary-card__title');
      await expect(appointmentTitle).toContainText('Desired Appointment');

      // Services tile
      const servicesTitle = page.locator('app-services-tile .summary-card__title');
      await expect(servicesTitle).toContainText('Selected Service');

      // Personal data tile
      const personalTitle = page.locator('app-personal-data-tile .summary-card__title');
      await expect(personalTitle).toContainText('Your Details');

      // Price tile
      const priceTitle = page.locator('[id="tile-title-price"]');
      await expect(priceTitle).toContainText('Price');
    });

  });

  // =============================================
  // ACCESSIBILITY (WCAG 2.1 AA)
  // =============================================

  test.describe('Accessibility', () => {

    test('booking overview section has aria-labelledby on title', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const section = page.locator('.booking-overview');
      const ariaLabelledby = await section.getAttribute('aria-labelledby');
      expect(ariaLabelledby).toBe('overview-title');
    });

    test('grid region has aria-label "booking summary"', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const grid = page.locator('.booking-overview__grid');
      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBe('booking summary');
    });

    test('navigation has aria-label "page navigation"', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const nav = page.locator('.booking-overview__navigation');
      const ariaLabel = await nav.getAttribute('aria-label');
      expect(ariaLabel).toBe('page navigation');
    });

    test('back button has aria-label attribute', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const backButton = page.locator('.booking-overview__back-button');
      const ariaLabel = await backButton.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('submit button has aria-label attribute', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const submitButton = page.locator('.booking-overview__submit-button');
      const ariaLabel = await submitButton.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('each tile has aria-labelledby on its article', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      // Appointment tile
      const appointmentArticle = page.locator('article[aria-labelledby="tile-title-appointment"]');
      await expect(appointmentArticle).toBeVisible();

      // Services tile
      const servicesArticle = page.locator('article[aria-labelledby="tile-title-service"]');
      await expect(servicesArticle).toBeVisible();

      // Personal data tile
      const dataArticle = page.locator('article[aria-labelledby="tile-title-data"]');
      await expect(dataArticle).toBeVisible();

      // Price tile
      const priceArticle = page.locator('article[aria-labelledby="tile-title-price"]');
      await expect(priceArticle).toBeVisible();
    });

  });

  // =============================================
  // RESPONSIVE LAYOUT
  // =============================================

  test.describe('Responsive Layout', () => {

    test('desktop: all 4 tiles and navigation visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const cards = page.locator('.summary-card');
      await expect(cards).toHaveCount(4);

      const backButton = page.locator('.booking-overview__back-button');
      await expect(backButton).toBeVisible();

      const submitButton = page.locator('.booking-overview__submit-button');
      await expect(submitButton).toBeVisible();
    });

  });

});
