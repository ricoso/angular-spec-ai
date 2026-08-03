import { expect, test } from '@playwright/test';

// REQ-024: NOCH SKIPPED — Reaktivierung teilweise abgeschlossen (Stand: siehe unten),
// eine weitere, unabhaengige Baustelle blockiert den Rest. NICHT einfach .skip entfernen,
// ohne die unten beschriebene Service-Katalog-Diskrepanz zu klaeren.
//
// Bereits behoben (verifiziert per chromium-desktop-Lauf, 17/35 Tests gruen):
// - REQ-013-Wizard-Reihenfolgewechsel: Standort (1) -> Marke (2) -> Services (3) -> Hinweise
//   -> Termin -> (Werkstattkalender) -> Fahrzeugdaten -> Buchungsuebersicht (vorher: Marke -> Standort).
//   Alle Routen-Erwartungen/Titel/Klick-Reihenfolgen anhand der aktuellen Guards
//   (booking.routes.ts, *-guard.ts) und Container-onBack()-Zielrouten aktualisiert.
// - REQ-001-Header wurde aus der App-Shell entfernt (app.component.html: "Header entfernt
//   (auf Wunsch)") — alle header[role="banner"]/.header__company-name-Checks durch
//   .cart-fab (FloatingOverlayComponent) ersetzt, "Header persistence" umbenannt.
// - Zwei kaputte Helper in booking.helpers.ts gefixt (betrafen nur bereits deaktivierte
//   Specs, kein Regressionsrisiko): getLocationButtonTexts/selectFirstLocation zeigten auf
//   ".location-card__name" (existiert nicht, real: ".location-card__address"); getServiceCardTitles/
//   selectService/getServiceCard zeigten auf ".service-card__title" (existiert nicht, real:
//   ".service-card-mini__title").
//
// NOCH OFFEN — Service-Katalog hat sich inhaltlich weiterentwickelt:
// - 8 statt 7 Service-Kategorien (neu: "Hol & Bringservice", hartcodiert/nicht uebersetzt,
//   siehe service-selection-container.component.ts:116)
// - Umbenennungen: "Wechsel Bremsflüssigkeit" -> "Bremsflüssigkeitswechsel",
//   "Karosserie / Frontscheibe wechseln" -> "Karosserie / Frontscheibenwechsel"
// - Unklar ob die EN-Kategorienamen (Inspection, MOT, ...) noch uebersetzt werden oder
//   die Kategorienamen jetzt durchgehend direkt von SoftNet/hartcodiert kommen (deutsch)
// Betrifft: Happy-Path-Tests, i18n-EN-Test, u.a. — siehe Testnamen in der Fehlerliste
// des letzten Laufs. Muss geklaert werden, bevor der Rest der Datei reaktiviert werden kann.

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  clickAppointmentBack,
  clickBookingOverviewBack,
  clickBrandBack,
  clickCarinformationBack,
  clickNotesBack,
  clickNotesContinue,
  clickServiceBack,
  clickWorkshopCalendarBack,
  confirmServiceWithOptions,
  enterNote,
  getAppointmentCardCount,
  getBrandButtonTexts,
  getCalendarLink,
  getCharCounter,
  getLocationButtonTexts,
  getPageTitle,
  getServiceCardTitles,
  getTimeDayHeadings,
  goToAppointmentPage,
  goToBookingOverviewPage,
  goToCarinformationPage,
  goToLocationSelection,
  goToNotesPage,
  goToServiceSelection,
  goToWorkshopCalendarPage,
  isAppointmentCardSelected,
  isTimeSlotSelected,
  openDatePickerAndSelectToday,
  selectAppointmentCard,
  selectFirstBrand,
  selectFirstLocation,
  selectLocation,
  selectService,
  selectTimeSlot,
} from './helpers/booking.helpers';

/**
 * COMPLETE BOOKING WORKFLOW
 *
 * End-to-End flow through all implemented wizard steps:
 *   REQ-001 (Header) -> REQ-003 (Location Selection) -> REQ-002 (Brand Selection)
 *   -> REQ-011 (Extended Service Selection, 7 Services) -> REQ-005 (Notes)
 *   -> REQ-006 (Appointment Selection) -> REQ-008 (Workshop Calendar)
 *   -> REQ-009 (Car Information) -> REQ-010 (Booking Overview)
 *
 * Tests the full user journey including:
 * - FloatingOverlay (Cart + Accessibility FABs) always visible (REQ-001 header was removed)
 * - Location selection -> Brand selection -> Service selection (7 services with checkboxes)
 *   -> Notes -> Appointment -> Workshop Calendar -> Car Information -> Booking Overview
 * - Language switching during the flow
 * - Alternative flows (back navigation, location/brand change)
 * - Guards and redirects
 */

test.describe.skip('Complete Booking Workflow', () => {

  // =============================================
  // HAPPY PATH — Full Flow
  // =============================================

  test.describe('Happy Path', () => {

    test('complete flow: Start -> Location -> Brand -> Services (7 cards) -> Notes -> Appointment', async ({ page }) => {
      await setLanguage(page, 'de');

      // Step 1: Navigate to app root -> should redirect to /home/location (REQ-013: Step 1)
      await navigateTo(page, '/');
      await waitForAngular(page);
      const startRoute = await getCurrentRoute(page);
      expect(startRoute).toBe('/home/location');

      // Step 2: Verify REQ-001 header is visible
      // Header (REQ-001) was removed from the app shell — Cart/A11y now live in
      // FloatingOverlayComponent (app.component.html). Use the cart FAB as the
      // persistent-chrome proxy instead of the old header[role="banner"].
      const header = page.locator('.cart-fab');
      await expect(header).toBeVisible();

      // Step 3: REQ-003 — Select first available location
      const title = await getPageTitle(page);
      expect(title).toBe('Buchen Sie Ihren Werkstatt-Termin online!');

      const locations = await getLocationButtonTexts(page);
      expect(locations.length).toBeGreaterThanOrEqual(1);

      await selectFirstLocation(page);
      await waitForAngular(page);

      // Step 4: REQ-002 — Verify brand page loaded
      const brandRoute = await getCurrentRoute(page);
      expect(brandRoute).toBe('/home/brand');

      const brandTitle = await getPageTitle(page);
      expect(brandTitle).toBe('Welche Fahrzeugmarke fahren Sie?');

      const brands = await getBrandButtonTexts(page);
      expect(brands.length).toBeGreaterThanOrEqual(1);

      // Step 5: Header still visible on brand page
      await expect(header).toBeVisible();

      // Step 6: Select first available brand
      await selectFirstBrand(page);

      // Step 7: REQ-011 — Verify services page loaded with 7 cards
      const servicesRoute = await getCurrentRoute(page);
      expect(servicesRoute).toBe('/home/services');

      const servicesTitle = await getPageTitle(page);
      expect(servicesTitle).toBe('Welche Services möchten Sie buchen?');

      // Step 8: Verify 7 service cards
      const serviceCards = await getServiceCardTitles(page);
      expect(serviceCards).toHaveLength(7);
      expect(serviceCards).toEqual(
        expect.arrayContaining([
          'Inspektion', 'TÜV', 'Wechsel Bremsflüssigkeit', 'Räderwechsel',
          'Aktionen / Checks', 'Reparatur / Beanstandung', 'Karosserie / Frontscheibe wechseln'
        ])
      );

      // Step 9: Select brake-fluid service (direct toggle, no options)
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      const brakeFluidCard = page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' });
      await expect(brakeFluidCard).toHaveClass(/service-card--selected/);

      // Step 10: Header still visible on services page
      await expect(header).toBeVisible();

      // Step 11: REQ-005 — Click Continue on services page to go to notes
      const servicesContinueButton = page.locator('.summary-bar__continue-button');
      await expect(servicesContinueButton).toBeVisible();
      await expect(servicesContinueButton).toBeEnabled();
      await servicesContinueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      const notesRoute = await getCurrentRoute(page);
      expect(notesRoute).toBe('/home/notes');

      const notesTitle = await getPageTitle(page);
      expect(notesTitle).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');

      // Step 12: Verify notes form elements
      const textarea = page.locator('.notes-form__textarea');
      await expect(textarea).toBeVisible();

      const counter = await getCharCounter(page);
      expect(counter).toBe('0 / 1000');

      // Step 13: Verify hints section
      const hintsTitle = page.locator('.service-hints__title');
      await expect(hintsTitle).toBeVisible();

      // Step 14: Enter a note
      await enterNote(page, 'Bitte Öl prüfen.');
      const updatedCounter = await getCharCounter(page);
      expect(updatedCounter).toBe('16 / 1000');

      // Step 15: Click continue on notes -> navigate to appointment
      await clickNotesContinue(page);
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Step 16: REQ-006 — Verify appointment page loaded
      const appointmentRoute = await getCurrentRoute(page);
      expect(appointmentRoute).toBe('/home/appointment');

      const appointmentTitle = await getPageTitle(page);
      expect(appointmentTitle).toBe('Wählen Sie den für Sie passenden Tag und Uhrzeit aus');

      // Step 17: Verify 4 appointment cards
      const appointmentCardCount = await getAppointmentCardCount(page);
      expect(appointmentCardCount).toBe(4);

      // Step 18: Select first appointment
      await selectAppointmentCard(page, 0);
      expect(await isAppointmentCardSelected(page, 0)).toBe(true);

      // Step 19: Header still visible on appointment page
      await expect(header).toBeVisible();

      // Step 20: REQ-008 — Click calendar link to navigate to workshop calendar
      const calendarLink = getCalendarLink(page);
      await expect(calendarLink).toBeVisible();
      await calendarLink.click();
      await page.locator('.workshop-calendar').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      const workshopRoute = await getCurrentRoute(page);
      expect(workshopRoute).toBe('/home/workshop-calendar');

      const workshopTitle = await getPageTitle(page);
      expect(workshopTitle).toBe('Hier sehen Sie weitere freie Termine in unserem Werkstattkalender');

      // Step 21: Select date via datepicker
      await openDatePickerAndSelectToday(page);

      // Step 22: Verify 3 days with time slots
      const dayHeadings = await getTimeDayHeadings(page);
      expect(dayHeadings).toHaveLength(3);

      // Step 23: Select a time slot
      await selectTimeSlot(page, '09:00 Uhr');
      expect(await isTimeSlotSelected(page, '09:00 Uhr')).toBe(true);

      // Step 24: Header still visible on workshop calendar page
      await expect(header).toBeVisible();
    });

    test('complete flow: second location with brands', async ({ page }) => {
      await navigateTo(page, '/');
      await waitForAngular(page);

      const locations = await getLocationButtonTexts(page);
      if (locations.length < 2) {
        return; // Only one location — skip
      }

      await selectLocation(page, locations[1]);

      const brands = await getBrandButtonTexts(page);
      expect(brands.length).toBeGreaterThanOrEqual(1);

      await selectFirstBrand(page);
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBeTruthy();
    });

    test('complete flow: all locations show at least one brand', async ({ page }) => {
      await navigateTo(page, '/');
      await waitForAngular(page);
      const locations = await getLocationButtonTexts(page);
      expect(locations.length).toBeGreaterThanOrEqual(1);

      for (const location of locations) {
        await goToLocationSelection(page);
        await selectLocation(page, location);

        const brands = await getBrandButtonTexts(page);
        expect(brands.length).toBeGreaterThanOrEqual(1);
      }
    });

    test('complete flow with service with options: Inspektion -> Notes', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Confirm Inspektion with checkboxes
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme', 'Ölwechsel-Service']);

      const inspectionCard = page.locator('.service-card', { hasText: 'Inspektion' });
      await expect(inspectionCard).toHaveClass(/service-card--selected/);

      // Continue to notes
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();
      await continueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      expect(await getCurrentRoute(page)).toBe('/home/notes');
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS
  // =============================================

  test.describe('Alternative Flows', () => {

    test('flow: Location -> Brand -> Back to Location -> Different Location -> Brand', async ({ page }) => {
      await navigateTo(page, '/');
      await selectFirstLocation(page);

      let brands = await getBrandButtonTexts(page);
      expect(brands.length).toBeGreaterThanOrEqual(1);

      await clickBrandBack(page);
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      await selectFirstLocation(page);

      brands = await getBrandButtonTexts(page);
      expect(brands.length).toBeGreaterThanOrEqual(1);
    });

    test('flow: Location -> Brand -> Select -> Back to Location -> Location -> Different Brand', async ({ page }) => {
      await navigateTo(page, '/');
      await selectFirstLocation(page);
      await selectFirstBrand(page);
      await waitForAngular(page);

      await clickServiceBack(page);
      await clickBrandBack(page);
      await selectFirstLocation(page);

      const brands = await getBrandButtonTexts(page);
      expect(brands.length).toBeGreaterThanOrEqual(1);
      await selectFirstBrand(page);
      await waitForAngular(page);
    });

    test('flow: Multiple location switches preserve page structure', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateTo(page, '/');
      const locations = await getLocationButtonTexts(page);

      for (const location of locations) {
        await goToLocationSelection(page);
        await selectLocation(page, location);

        const title = await getPageTitle(page);
        expect(title).toBe('Welche Fahrzeugmarke fahren Sie?');

        const subtitle = page.locator('p').first();
        await expect(subtitle).toContainText('Bitte wählen Sie die gewünschte Marke aus');

        const brandGrid = page.locator('[role="group"]');
        await expect(brandGrid).toBeVisible();
      }
    });

    test('flow: Services -> Back to Brand', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const servicesRoute = await getCurrentRoute(page);
      expect(servicesRoute).toBe('/home/services');

      // Click back button
      const backButton = page.locator('.summary-bar__back-button');
      await backButton.click();
      await waitForAngular(page);

      const brandRoute = await getCurrentRoute(page);
      expect(brandRoute).toBe('/home/brand');

      const brandTitle = await getPageTitle(page);
      expect(brandTitle).toBe('Welche Fahrzeugmarke fahren Sie?');
    });

    test('flow: Notes -> Back to Services', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToNotesPage(page);

      const notesRoute = await getCurrentRoute(page);
      expect(notesRoute).toBe('/home/notes');

      // Click back button on notes page
      await clickNotesBack(page);

      const servicesRoute = await getCurrentRoute(page);
      expect(servicesRoute).toBe('/home/services');

      const servicesTitle = await getPageTitle(page);
      expect(servicesTitle).toBe('Welche Services möchten Sie buchen?');
    });

    test('flow: Appointment -> Back to Notes', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      const appointmentRoute = await getCurrentRoute(page);
      expect(appointmentRoute).toBe('/home/appointment');

      // Click back button on appointment page
      await clickAppointmentBack(page);

      const notesRoute = await getCurrentRoute(page);
      expect(notesRoute).toBe('/home/notes');

      const notesTitle = await getPageTitle(page);
      expect(notesTitle).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');
    });

    test('flow: Workshop Calendar -> Back to Appointment', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      const workshopRoute = await getCurrentRoute(page);
      expect(workshopRoute).toBe('/home/workshop-calendar');

      // Click back button on workshop calendar page
      await clickWorkshopCalendarBack(page);

      const appointmentRoute = await getCurrentRoute(page);
      expect(appointmentRoute).toBe('/home/appointment');

      const appointmentTitle = await getPageTitle(page);
      expect(appointmentTitle).toBe('Wählen Sie den für Sie passenden Tag und Uhrzeit aus');
    });

  });

  // =============================================
  // EXCEPTION FLOWS & GUARDS
  // =============================================

  test.describe('Exception Flows & Guards', () => {

    test('direct access to /home/brand without location -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/home/brand');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('unknown route -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/some/unknown/path');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toMatch(/\/home/);
    });

    test('direct access to /home -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/home');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('direct access to /home/services without location/brand -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/home/services');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // brandSelectedGuard checks location first
      expect(route).toBe('/home/location');
    });

    test('direct access to /home/notes without prerequisites -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/home/notes');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // servicesSelectedGuard checks location first
      expect(route).toBe('/home/location');
    });

    test('direct access to /home/appointment without prerequisites -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/home/appointment');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('direct access to /home/workshop-calendar without prerequisites -> redirect to /home/location', async ({ page }) => {
      await navigateTo(page, '/home/workshop-calendar');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

  });

  // =============================================
  // i18n — Language through complete flow
  // =============================================

  test.describe('i18n — Language through flow', () => {

    test('EN: complete flow with English language including services (7 cards), notes, appointment, and workshop calendar', async ({ page }) => {
      await setLanguage(page, 'en');
      await navigateTo(page, '/home/location');

      const locationTitle = await getPageTitle(page);
      expect(locationTitle).toBe('Book your workshop appointment online!');

      await selectFirstLocation(page);

      const brandTitle = await getPageTitle(page);
      expect(brandTitle).toBe('Which vehicle brand do you drive?');

      const brands = await getBrandButtonTexts(page);
      expect(brands.length).toBeGreaterThanOrEqual(1);

      await selectFirstBrand(page);

      const servicesTitle = await getPageTitle(page);
      expect(servicesTitle).toBe('Which services would you like to book?');

      const cardTitles = await getServiceCardTitles(page);
      expect(cardTitles).toHaveLength(7);
      expect(cardTitles).toEqual(
        expect.arrayContaining([
          'Inspection', 'MOT', 'Brake Fluid Change', 'Tire Change',
          'Actions / Checks', 'Repair / Complaint', 'Bodywork / Windshield Replacement'
        ])
      );

      // Select brake fluid (no options, direct toggle) and click Continue to navigate to notes
      await selectService(page, 'Brake Fluid Change');
      const enContinueButton = page.locator('.summary-bar__continue-button');
      await expect(enContinueButton).toBeVisible();
      await expect(enContinueButton).toBeEnabled();
      await enContinueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      const notesTitle = await getPageTitle(page);
      expect(notesTitle).toBe('Please provide further notes for your booking');

      const backButton = page.locator('.notes__back-button');
      await expect(backButton).toContainText('Back');

      const continueButton = page.locator('.notes__continue-button');
      await expect(continueButton).toContainText('Continue');

      // Navigate through to appointment and workshop calendar
      await continueButton.click();
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      const appointmentTitle = await getPageTitle(page);
      expect(appointmentTitle).toBe('Select your preferred day and time');

      // Click calendar link to go to workshop calendar
      const calendarLink = getCalendarLink(page);
      await calendarLink.click();
      await page.locator('.workshop-calendar').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // REQ-008: English title
      const workshopTitle = await getPageTitle(page);
      expect(workshopTitle).toBe('Here you can see further available appointments in our workshop calendar');

      // English Back/Continue buttons
      const workshopBack = page.locator('.wizard-nav__back-button');
      await expect(workshopBack).toContainText('Back');

      const workshopContinue = page.locator('.wizard-nav__continue-button');
      await expect(workshopContinue).toContainText('Continue');
    });

    test('language switch mid-flow: DE location -> EN brand', async ({ page }) => {
      // Start in DE
      await setLanguage(page, 'de');
      await navigateTo(page, '/home/location');
      const locationTitle = await getPageTitle(page);
      expect(locationTitle).toBe('Buchen Sie Ihren Werkstatt-Termin online!');

      // Select location
      await selectFirstLocation(page);

      // Switch to EN on brand page
      await setLanguage(page, 'en');
      // After reload, wizard state (in-memory only) is lost -> guard sends us back
      // to /home/location — re-select to reach the brand page again.
      await selectFirstLocation(page);

      const brandTitle = await getPageTitle(page);
      expect(brandTitle).toBe('Which vehicle brand do you drive?');

      // Header should also be in EN now
      const a11yButton = page.locator('.a11y-fab');
      const ariaLabel = await a11yButton.getAttribute('aria-label');
      expect(ariaLabel).toBe('Accessibility Settings');
    });

    test('language persists after navigation', async ({ page }) => {
      await setLanguage(page, 'en');
      await navigateTo(page, '/home/location');

      await selectFirstLocation(page);

      const brandTitle = await getPageTitle(page);
      expect(brandTitle).toBe('Which vehicle brand do you drive?');

      await clickBrandBack(page);

      const locationTitle = await getPageTitle(page);
      expect(locationTitle).toBe('Book your workshop appointment online!');
    });

  });

  // =============================================
  // HEADER PERSISTENCE ACROSS PAGES
  // =============================================

  test.describe('FloatingOverlay persistence (REQ-001 header removed)', () => {

    test('cart FAB stays visible through location -> brand -> services -> notes -> appointment -> workshop calendar flow', async ({ page }) => {
      await setLanguage(page, 'de');
      // Header (REQ-001) was removed from the app shell — Cart/A11y now live in
      // FloatingOverlayComponent (app.component.html). Use the cart FAB as the
      // persistent-chrome proxy instead of the old header[role="banner"].
      const header = page.locator('.cart-fab');

      await navigateTo(page, '/home/location');
      await expect(header).toBeVisible();

      await selectFirstLocation(page);
      await expect(header).toBeVisible();

      await selectFirstBrand(page);
      await waitForAngular(page);

      // Header visible on services page
      await expect(header).toBeVisible();

      // Select brake fluid (no options) and click Continue to go to notes
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      const headerContinueButton = page.locator('.summary-bar__continue-button');
      await expect(headerContinueButton).toBeVisible();
      await expect(headerContinueButton).toBeEnabled();
      await headerContinueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Header visible on notes page
      await expect(header).toBeVisible();

      // Click Continue on notes to go to appointment
      const notesContinueButton = page.locator('.notes__continue-button');
      await notesContinueButton.click();
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Header visible on appointment page
      await expect(header).toBeVisible();

      // Click calendar link to go to workshop calendar
      const calendarLink = getCalendarLink(page);
      await calendarLink.click();
      await page.locator('.workshop-calendar').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Header visible on workshop calendar page
      await expect(header).toBeVisible();
    });

    test('accessibility settings persist across page navigation', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateTo(page, '/home/location');

      // Open accessibility menu and change font size
      const a11yButton = page.locator('.a11y-fab');
      await a11yButton.click();
      await waitForAngular(page);

      // Click "Large" radio label (3rd option)
      const radioLabels = page.locator('.accessibility-menu__radio label');
      await radioLabels.nth(2).click();
      await waitForAngular(page);

      // Check localStorage
      const settings = await page.evaluate(() => localStorage.getItem('accessibility-settings'));
      expect(settings).toBeTruthy();
    });

    test('cart icon visible on all pages including services and notes', async ({ page }) => {
      await setLanguage(page, 'de');
      const cartButton = page.locator('.cart-fab');

      await navigateTo(page, '/home/location');
      await expect(cartButton).toBeVisible();

      await selectFirstLocation(page);
      await expect(cartButton).toBeVisible();

      await selectFirstBrand(page);
      await waitForAngular(page);
      await expect(cartButton).toBeVisible();

      // Select brake fluid and click Continue to navigate to notes
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      const cartContinueButton = page.locator('.summary-bar__continue-button');
      await expect(cartContinueButton).toBeVisible();
      await expect(cartContinueButton).toBeEnabled();
      await cartContinueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);
      await expect(cartButton).toBeVisible();
    });

  });

  // =============================================
  // REQ-007: WIZARD STATE SYNC — Back Navigation with Store Reset
  // =============================================

  test.describe('REQ-007: Wizard State Sync — Back Navigation', () => {

    test('complete backward navigation: Appointment -> Notes -> Services -> Brand -> Location', async ({ page }) => {
      await setLanguage(page, 'de');

      // Walk the full wizard forward
      await goToAppointmentPage(page);
      await selectAppointmentCard(page, 0);

      // Now navigate all the way back
      // Step 1: Appointment -> Notes (clears selectedAppointment)
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      // Step 2: Notes -> Services (clears bookingNote)
      await clickNotesBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/services');

      // Step 3: Services -> Brand (clears selectedServices)
      await clickServiceBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/brand');

      // Step 4: Brand -> Location (clears selectedBrand)
      await clickBrandBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      // Verify location page renders
      const title = await getPageTitle(page);
      expect(title).toBe('Buchen Sie Ihren Werkstatt-Termin online!');
    });

    test('guard redirect after complete backward navigation: /home/appointment -> redirected', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Navigate all the way back
      await clickAppointmentBack(page);
      await clickNotesBack(page);
      await clickServiceBack(page);
      await clickBrandBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/location');

      // Direct access to /home/appointment should redirect
      await navigateTo(page, '/home/appointment');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/appointment');
      expect(route).toBe('/home/location');
    });

    test('partial back then forward: appointment reset requires re-selection', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select appointment
      await selectAppointmentCard(page, 0);
      expect(await isAppointmentCardSelected(page, 0)).toBe(true);

      // Back to notes (clears selectedAppointment)
      await clickAppointmentBack(page);
      expect(await getCurrentRoute(page)).toBe('/home/notes');

      // Forward from notes back to appointment
      await clickNotesContinue(page);
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // No appointment should be pre-selected
      const selectedCards = page.locator('.appointment-card--selected');
      expect(await selectedCards.count()).toBe(0);
    });

    test('cart badge resets when services are cleared via back navigation', async ({ page }) => {
      await setLanguage(page, 'de');
      // Select 2 services: Inspektion (with options) and brake-fluid (toggle)
      await goToServiceSelection(page);
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme']);
      await selectService(page, 'Wechsel Bremsflüssigkeit');

      const badge = page.locator('.cart-fab .badge');
      await expect(badge).toHaveText('2');

      // Continue to notes
      const continueButton = page.locator('.summary-bar__continue-button');
      await continueButton.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Back from notes (clears bookingNote, badge stays 2)
      await clickNotesBack(page);
      await expect(badge).toHaveText('2');

      // Back from services (clears selectedServices, badge disappears)
      await clickServiceBack(page);
      await expect(badge).toBeHidden();
    });

    test('cart FAB stays visible during backward navigation including workshop calendar', async ({ page }) => {
      await setLanguage(page, 'de');
      // Header (REQ-001) was removed from the app shell — Cart/A11y now live in
      // FloatingOverlayComponent (app.component.html). Use the cart FAB as the
      // persistent-chrome proxy instead of the old header[role="banner"].
      const header = page.locator('.cart-fab');

      await goToWorkshopCalendarPage(page);
      await expect(header).toBeVisible();

      // Workshop Calendar -> Appointment
      await clickWorkshopCalendarBack(page);
      await expect(header).toBeVisible();

      await clickAppointmentBack(page);
      await expect(header).toBeVisible();

      await clickNotesBack(page);
      await expect(header).toBeVisible();

      await clickServiceBack(page);
      await expect(header).toBeVisible();

      await clickBrandBack(page);
      await expect(header).toBeVisible();
    });

  });

  // =============================================
  // REQ-010: BOOKING OVERVIEW — Complete Flow Extension
  // =============================================

  test.describe('REQ-010: Booking Overview Flow', () => {

    test('complete flow: Location -> ... -> Car Information -> Booking Overview (Happy Path)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/booking-overview');

      // Title
      const title = await getPageTitle(page);
      expect(title).toBe('Übersicht');

      // 4 summary tiles
      const cards = page.locator('.summary-card');
      await expect(cards).toHaveCount(4);

      // Appointment tile
      const appointmentTile = page.locator('app-appointment-tile');
      await expect(appointmentTile).toBeVisible();

      // Services tile with location — check it has some text content
      const servicesTile = page.locator('app-services-tile');
      await expect(servicesTile).toBeVisible();
      const servicesTileText = await servicesTile.textContent() ?? '';
      expect(servicesTileText.length).toBeGreaterThan(0);

      // Personal data tile
      const personalTile = page.locator('app-personal-data-tile');
      await expect(personalTile).toBeVisible();
      await expect(personalTile).toContainText('Max');
      await expect(personalTile).toContainText('Mustermann');

      // Price tile
      const priceTile = page.locator('.price-tile');
      await expect(priceTile.first()).toBeVisible();

      // Header still visible
      // Header (REQ-001) was removed from the app shell — Cart/A11y now live in
      // FloatingOverlayComponent (app.component.html). Use the cart FAB as the
      // persistent-chrome proxy instead of the old header[role="banner"].
      const header = page.locator('.cart-fab');
      await expect(header).toBeVisible();
    });

    test('flow: Booking Overview -> Back to Car Information', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      expect(await getCurrentRoute(page)).toBe('/home/booking-overview');

      await clickBookingOverviewBack(page);

      expect(await getCurrentRoute(page)).toBe('/home/carinformation');
    });

    test('flow: Car Information -> Back to Appointment', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToCarinformationPage(page);

      expect(await getCurrentRoute(page)).toBe('/home/carinformation');

      await clickCarinformationBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toMatch(/\/home\/(appointment|workshop-calendar)/);
    });

    test('guard: direct /home/booking-overview without prerequisites -> redirect', async ({ page }) => {
      await navigateTo(page, '/home/booking-overview');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/booking-overview');
      expect(route).toMatch(/\/home/);
    });

    test('i18n: EN titles on booking overview page', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToBookingOverviewPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Overview');

      const backButton = page.locator('.booking-overview__back-button');
      await expect(backButton).toContainText('Back');

      const submitButton = page.locator('.booking-overview__submit-button');
      await expect(submitButton).toContainText('Request Now');
    });

    test('cart FAB visible on booking overview page', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBookingOverviewPage(page);

      // Header (REQ-001) was removed from the app shell — Cart/A11y now live in
      // FloatingOverlayComponent (app.component.html). Use the cart FAB as the
      // persistent-chrome proxy instead of the old header[role="banner"].
      const header = page.locator('.cart-fab');
      await expect(header).toBeVisible();

    });

  });

});
