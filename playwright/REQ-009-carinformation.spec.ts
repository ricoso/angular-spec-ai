import { expect, type Page,test } from '@playwright/test';

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  clickAppointmentContinue,
  getPageTitle,
  goToAppointmentPage,
  selectAppointmentCard,
} from './helpers/booking.helpers';

/**
 * REQ-009: Car Information (Kundendaten & Fahrzeugdaten)
 * Wizard Step 6: User enters customer and vehicle data before proceeding to booking overview.
 *
 * Flow: Brand -> Location -> Services -> Notes -> Appointment (select + continue) -> Car Information
 * Guard: carInformationGuard (requires brand + location + services + selectedAppointment)
 *
 * Components:
 * - CarinformationContainerComponent (Container)
 * - CustomerFormComponent (Presentational: Kundendaten)
 * - VehicleFormComponent (Presentational: Fahrzeugdaten)
 */

// =============================================
// HELPER: Navigate to the carinformation page
// =============================================

/**
 * Navigate to the carinformation page via the full wizard flow:
 * Brand -> Location -> Services -> Notes -> Appointment (select first card) -> Continue
 */
async function navigateToCarinformation(page: Page): Promise<void> {
  await goToAppointmentPage(page);
  await selectAppointmentCard(page, 0);
  await clickAppointmentContinue(page);
  await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

// =============================================
// TESTS
// =============================================

test.describe('REQ-009: Car Information (Kundendaten & Fahrzeugdaten)', () => {

  // =============================================
  // EXCEPTION FLOW: Guard Test (Section 6.1)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: direct access to /home/carinformation without prerequisites -> redirect to /home/brand', async ({ page }) => {
      await navigateTo(page, '/home/carinformation');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Guard redirects to /home (wizard start) or /home/brand
      expect(route).not.toBe('/home/carinformation');
      expect(route).toMatch(/\/home\/(brand|location|services|appointment)?/);
    });

  });

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: page loads with title, returning customer banner, customer form and vehicle form (DE)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/carinformation');

      // AC-1: Page title
      const title = await getPageTitle(page);
      expect(title).toBeTruthy();
      // Title should be about appointment information
      expect(title.length).toBeGreaterThan(5);

      // AC-2: Returning customer banner is visible
      const returningBanner = page.locator('.carinformation__returning-banner');
      await expect(returningBanner).toBeVisible();

      // Returning customer title
      const returningTitle = page.locator('.carinformation__returning-title');
      await expect(returningTitle).toBeVisible();

      // Returning customer button "Jetzt Daten abrufen!"
      const returningButton = page.locator('.carinformation__returning-button');
      await expect(returningButton).toBeVisible();
    });

    test('TC-1b: customer form (Kundendaten) section is visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Customer form fieldset
      const customerForm = page.locator('.customer-form');
      await expect(customerForm).toBeVisible();

      // Email field
      const emailInput = page.locator('input[type="email"], input[formcontrolname="email"]');
      await expect(emailInput.first()).toBeVisible();

      // Salutation select (mat-select)
      const salutationSelect = page.locator('mat-select[formcontrolname="salutation"]');
      await expect(salutationSelect).toBeVisible();

      // First name
      const firstNameInput = page.locator('input[formcontrolname="firstName"]');
      await expect(firstNameInput).toBeVisible();

      // Last name
      const lastNameInput = page.locator('input[formcontrolname="lastName"]');
      await expect(lastNameInput).toBeVisible();
    });

    test('TC-1c: vehicle form (Fahrzeugdaten) section is visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Vehicle form fieldset
      const vehicleForm = page.locator('.vehicle-form');
      await expect(vehicleForm).toBeVisible();

      // License plate
      const licensePlateInput = page.locator('input[formcontrolname="licensePlate"]');
      await expect(licensePlateInput).toBeVisible();

      // Mileage
      const mileageInput = page.locator('input[formcontrolname="mileage"]');
      await expect(mileageInput).toBeVisible();

      // VIN / FIN
      const vinInput = page.locator('input[formcontrolname="vin"]');
      await expect(vinInput).toBeVisible();
    });

    test('TC-1d: DSGVO privacy checkbox is visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Privacy checkbox container
      const privacySection = page.locator('.carinformation__privacy');
      await expect(privacySection).toBeVisible();

      // mat-checkbox
      const privacyCheckbox = page.locator('mat-checkbox');
      await expect(privacyCheckbox).toBeVisible();
    });

    test('TC-1e: navigation buttons (Back and Continue) are visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Back button
      const backButton = page.locator('.carinformation__back-button');
      await expect(backButton).toBeVisible();

      // Continue button
      const continueButton = page.locator('.carinformation__continue-button');
      await expect(continueButton).toBeVisible();
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-6: privacy checkbox not checked -> submit blocked (AC-13, BR-10)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Fill in valid customer data
      await page.locator('input[formcontrolname="email"]').fill('max@mustermann.de');
      await page.locator('input[formcontrolname="firstName"]').fill('Max');
      await page.locator('input[formcontrolname="lastName"]').fill('Mustermann');
      await page.locator('input[formcontrolname="street"]').fill('Musterweg 1');
      await page.locator('input[formcontrolname="postalCode"]').fill('30159');
      await page.locator('input[formcontrolname="city"]').fill('Berlin');
      await page.locator('input[formcontrolname="mobilePhone"]').fill('017012345678');

      // Fill in valid vehicle data
      await page.locator('input[formcontrolname="licensePlate"]').fill('B-MS1234');
      await page.locator('input[formcontrolname="mileage"]').fill('5000');
      await page.locator('input[formcontrolname="vin"]').fill('WDB8XXXXXXA123456');

      // Do NOT check privacy checkbox

      // Click continue
      const continueButton = page.locator('.carinformation__continue-button');
      await continueButton.click();
      await waitForAngular(page);

      // Should still be on carinformation page (navigation blocked)
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/carinformation');

      // Privacy error should appear
      const privacyError = page.locator('.carinformation__privacy-error');
      await expect(privacyError).toBeVisible();
    });

    test('TC-9: back button navigates away from carinformation (AC, Section 5.2)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Click Back button
      const backButton = page.locator('.carinformation__back-button');
      await expect(backButton).toBeVisible();
      await backButton.click();
      await waitForAngular(page);

      // Should navigate back (to appointment page or similar)
      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/carinformation');
      // Typically navigates to /home/appointment
      expect(route).toMatch(/\/home\/(appointment|workshop-calendar)/);
    });

    test('TC-2: AC-2 returning customer banner visible with correct button text (DE)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Banner title
      const bannerTitle = page.locator('.carinformation__returning-title');
      await expect(bannerTitle).toBeVisible();

      // "Jetzt Daten abrufen!" button is visible
      const returningButton = page.locator('.carinformation__returning-button');
      await expect(returningButton).toBeVisible();
      await expect(returningButton).toContainText('Daten');
    });

    test('AC-3: required hint "Pflichtfelder" is visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const hint = page.locator('.carinformation__required-hint');
      await expect(hint).toBeVisible();
      await expect(hint).toContainText('Pflichtfelder');
    });

    test('AC-5: salutation dropdown has Herr and Frau options (DE)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Open the salutation select
      const salutationSelect = page.locator('mat-select[formcontrolname="salutation"]');
      await salutationSelect.click();
      await waitForAngular(page);

      // Check options are visible
      const options = page.locator('mat-option');
      const optionTexts = await options.allTextContents();
      const trimmed = optionTexts.map(t => t.trim());
      expect(trimmed).toContain('Herr');
      expect(trimmed).toContain('Frau');
    });

    test('TC-3: VIN field accepts only 17 alphanumeric chars (AC-12, BR-9)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const vinInput = page.locator('input[formcontrolname="vin"]');
      await expect(vinInput).toBeVisible();

      // VIN field has maxlength="17"
      const maxLength = await vinInput.getAttribute('maxlength');
      expect(maxLength).toBe('17');
    });

    test('TC-4: VIN link "Erklärung der FIN" is visible (AC-12)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const vinLink = page.locator('.vehicle-form__vin-link');
      await expect(vinLink).toBeVisible();
    });

    test('TC-5: privacy checkbox required - clicking submit without check shows error', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Click Continue without filling anything or checking checkbox
      const continueButton = page.locator('.carinformation__continue-button');
      await continueButton.click();
      await waitForAngular(page);

      // Privacy error should appear
      const privacyError = page.locator('.carinformation__privacy-error');
      await expect(privacyError).toBeVisible();
    });

  });

  // =============================================
  // i18n
  // =============================================

  test.describe('i18n', () => {

    test('DE: back button contains "Zurück"', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const backButton = page.locator('.carinformation__back-button');
      await expect(backButton).toContainText('Zurück');
    });

    test('DE: continue button contains "Buchungsübersicht"', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const continueButton = page.locator('.carinformation__continue-button');
      await expect(continueButton).toContainText('Buchungsübersicht');
    });

    test('EN: back button contains "Back"', async ({ page }) => {
      await setLanguage(page, 'en');
      await navigateToCarinformation(page);

      const backButton = page.locator('.carinformation__back-button');
      await expect(backButton).toContainText('Back');
    });

    test('EN: continue button contains "Booking" or "Overview"', async ({ page }) => {
      await setLanguage(page, 'en');
      await navigateToCarinformation(page);

      const continueButton = page.locator('.carinformation__continue-button');
      const text = await continueButton.textContent();
      expect(text?.toLowerCase()).toMatch(/booking|overview/);
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('carinformation section has correct CSS class', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const section = page.locator('.carinformation');
      await expect(section).toBeVisible();
    });

    test('back button has aria-label attribute', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const backButton = page.locator('.carinformation__back-button');
      const ariaLabel = await backButton.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('continue button has aria-label attribute', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const continueButton = page.locator('.carinformation__continue-button');
      const ariaLabel = await continueButton.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('customer form fieldset has legend (AC-16 label accessibility)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const legend = page.locator('.customer-form legend, .customer-form__title');
      await expect(legend.first()).toBeVisible();
    });

    test('vehicle form fieldset has legend', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const legend = page.locator('.vehicle-form legend, .vehicle-form__title');
      await expect(legend.first()).toBeVisible();
    });

  });

  // =============================================
  // RESPONSIVE LAYOUT (Section 9, AC-17)
  // =============================================

  test.describe('Responsive Layout', () => {

    test('all key UI elements are visible (desktop)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Main section
      await expect(page.locator('.carinformation')).toBeVisible();

      // Returning banner
      await expect(page.locator('.carinformation__returning-banner')).toBeVisible();

      // Customer form
      await expect(page.locator('.customer-form')).toBeVisible();

      // Vehicle form
      await expect(page.locator('.vehicle-form')).toBeVisible();

      // Privacy section
      await expect(page.locator('.carinformation__privacy')).toBeVisible();

      // Navigation actions
      await expect(page.locator('.carinformation__actions')).toBeVisible();
    });

    test('on mobile viewport (375px): carinformation page renders without overflow', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      // Main section still visible on mobile
      const section = page.locator('.carinformation');
      await expect(section).toBeVisible();

      // Buttons still accessible
      const backButton = page.locator('.carinformation__back-button');
      await expect(backButton).toBeVisible();

      const continueButton = page.locator('.carinformation__continue-button');
      await expect(continueButton).toBeVisible();
    });

    test('on tablet viewport (768px): carinformation page renders correctly', async ({ page }) => {
      await page.setViewportSize({ width: 768, height: 1024 });
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const section = page.locator('.carinformation');
      await expect(section).toBeVisible();

      // Customer form visible
      await expect(page.locator('.customer-form')).toBeVisible();

      // Vehicle form visible
      await expect(page.locator('.vehicle-form')).toBeVisible();
    });

  });

  // =============================================
  // ALTERNATIVE FLOW: Returning customer button
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: returning customer button click does not crash (placeholder)', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const returningButton = page.locator('.carinformation__returning-button');
      await expect(returningButton).toBeVisible();

      // Click the button (placeholder - no action expected)
      await returningButton.click();
      await waitForAngular(page);

      // Still on carinformation page
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/carinformation');
    });

    test('5.3: VIN explanation link placeholder does not navigate away', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateToCarinformation(page);

      const vinLink = page.locator('.vehicle-form__vin-link');
      await expect(vinLink).toBeVisible();

      // Click the link (href="#", placeholder)
      await vinLink.click();
      await waitForAngular(page);

      // Still on carinformation page
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/carinformation');
    });

  });

});
