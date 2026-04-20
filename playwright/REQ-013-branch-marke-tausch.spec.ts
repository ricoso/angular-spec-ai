import { expect, test } from '@playwright/test';

import { getCurrentRoute, navigateTo, saveScreenshot, setLanguage, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-013: Branch-Marke-Tausch (Location-Brand Swap)
 *
 * Tests the new wizard order: Location (Step 1) → Brand (Step 2) → Services → ...
 * Previously: Brand → Location → Services
 *
 * Key change: NO "Continue" buttons on Location or Brand pages.
 * Clicking a card auto-navigates to the next step.
 *
 * Test Cases from requirement.md Section 13:
 * - TC-1:  Wizard entry is location selection (AC-1, AC-11)
 * - TC-2:  Select branch auto-navigates to brands (AC-2, AC-3, AC-18)
 * - TC-3:  Brands filtered by branch — multi-brand branch (AC-4)
 * - TC-4:  Brands filtered by branch — single brand (AC-4)
 * - TC-5:  Back button on brand selection (AC-5)
 * - TC-6:  No back/continue button on location selection (AC-6, AC-18)
 * - TC-7:  Guard — /home/brand without branch (AC-8, 6.3)
 * - TC-8:  Guard — /home/services without brand (AC-9, 6.4)
 * - TC-9:  Store order — branch then brand (AC-7)
 * - TC-10: Branch change resets brand (AC-10)
 * - TC-11: Wizard breadcrumb visible (AC-12)
 * - TC-12: Info banner on brand page (AC-16)
 * - TC-14: Auto-navigation location (AC-18)
 * - TC-15: Auto-navigation brand (AC-19)
 * - TC-16: No VIN field on carinformation (AC-22)
 * - TC-17: Notes & Options tile instead of price (AC-23)
 * - TC-18: Breadcrumb reset + data retention (AC-24)
 * - TC-19: Customer data persists through navigation (AC-24b)
 */

// =============================================
// HELPERS for REQ-013 (card-based auto-navigation)
// =============================================

async function goToLocationPage(page: import('@playwright/test').Page): Promise<void> {
  await navigateTo(page, '/home/location');
  await page.locator('.location-selection').waitFor({ state: 'visible', timeout: 15000 });
  // Wait for branch cards to load (async fetch from branch-config.json)
  await page.locator('.location-card').first().waitFor({ state: 'visible', timeout: 15000 });
  await waitForAngular(page);
}

/** Click a branch card — auto-navigates to /home/brand */
async function selectBranchByName(page: import('@playwright/test').Page, branchName: string): Promise<void> {
  const card = page.locator('.location-card').filter({
    has: page.locator('.location-card__name', { hasText: branchName })
  });
  await expect(card).toBeVisible({ timeout: 10000 });
  await card.click();
  await waitForAngular(page);
  // Auto-navigation: wait for brand page to appear
  await page.locator('.brand-selection').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

/** Click a brand card — auto-navigates to /home/services */
async function selectBrandByName(page: import('@playwright/test').Page, brandName: string): Promise<void> {
  const card = page.locator('.brand-card').filter({
    has: page.locator('.brand-card__name', { hasText: new RegExp(`^${brandName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`) })
  });
  await expect(card).toBeVisible({ timeout: 10000 });
  await card.click();
  await waitForAngular(page);
  // Auto-navigation: wait for services page to appear
  await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

async function getBrandCardNames(page: import('@playwright/test').Page): Promise<string[]> {
  const names = page.locator('.brand-card__name');
  return names.allTextContents().then(texts => texts.map(t => t.trim()));
}

async function clickBrandBack(page: import('@playwright/test').Page): Promise<void> {
  const btn = page.locator('.brand-selection__back-button');
  await expect(btn).toBeVisible();
  await btn.click();
  await waitForAngular(page);
}

/** Navigate to brand page: select branch on location page */
async function goToBrandPage(page: import('@playwright/test').Page, branchName = 'Volkswagen Zentrum Essen'): Promise<void> {
  await goToLocationPage(page);
  await selectBranchByName(page, branchName);
}

/** Navigate to services page: select branch + brand */
async function goToServicesPage(
  page: import('@playwright/test').Page,
  branchName = 'Audi Zentrum Essen',
  brandName = 'Audi'
): Promise<void> {
  await goToBrandPage(page, branchName);
  await selectBrandByName(page, brandName);
}

// =============================================
// TESTS
// =============================================

test.describe('REQ-013: Branch-Marke-Tausch', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow — Location first, then Brand', () => {

    test('TC-1: Wizard entry redirects to /home/location with ALL branches visible', async ({ page }) => {
      await setLanguage(page, 'de');
      await navigateTo(page, '/');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      // Wait for branch cards to load (async fetch from branch-config.json)
      await page.locator('.location-card').first().waitFor({ state: 'visible', timeout: 15000 });

      const cards = page.locator('.location-card');
      const count = await cards.count();
      expect(count).toBeGreaterThan(0);

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '01-location-selection');
    });

    test('TC-2: Selecting a branch auto-navigates to /home/brand (AC-3, AC-18)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToLocationPage(page);

      // Select the first visible branch
      const firstCard = page.locator('.location-card').first();
      await expect(firstCard).toBeVisible();
      const branchName = (await firstCard.locator('.location-card__name').textContent() ?? '').trim();
      await firstCard.click();
      await waitForAngular(page);

      // Auto-navigation: should land on brand page
      await page.locator('.brand-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');

      // Branch name should be visible in info banner
      const infoBanner = page.locator('.brand-selection__info-text');
      await expect(infoBanner).toBeVisible();
      await expect(infoBanner).toContainText(branchName);

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '02-brand-selection');
    });

    test('TC-3: Brands filtered by branch — multi-brand branch shows correct brands', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Volkswagen Zentrum Essen');

      const brandNames = await getBrandCardNames(page);
      expect(brandNames.length).toBe(4);
      expect(brandNames).toEqual(expect.arrayContaining(['VW', 'VW Nutzfahrzeuge', 'SEAT', 'CUPRA']));

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '03-brands-multi');
    });

    test('TC-4: Brands filtered by branch — single brand branch shows only 1 brand', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'ŠKODA Zentrum Essen');

      const brandNames = await getBrandCardNames(page);
      expect(brandNames.length).toBe(1);
      expect(brandNames[0]).toContain('ŠKODA');

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '04-brands-single');
    });

    test('TC-9: Store order — branch selected first, then brand', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Audi Zentrum Essen');

      // Verify brand page shows filtered brands
      const brandNames = await getBrandCardNames(page);
      expect(brandNames).toEqual(expect.arrayContaining(['Audi']));

      // Select brand — auto-navigates to services
      await selectBrandByName(page, 'Audi');

      // Should navigate to /home/services
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');
    });
  });

  // =============================================
  // BACK NAVIGATION (Section 5)
  // =============================================

  test.describe('Back Navigation', () => {

    test('TC-5: Back button on brand page navigates to /home/location', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Volkswagen Zentrum Essen');

      // Click back button
      await clickBrandBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      // Location page should be visible again
      await expect(page.locator('.location-selection')).toBeVisible();
    });

    test('TC-6: No back button AND no continue button on location selection (AC-6, AC-18)', async ({ page }) => {
      await goToLocationPage(page);

      // There should be no dedicated back button on the location selection
      const backButton = page.locator('.location-selection__back-button');
      await expect(backButton).toHaveCount(0);

      // There should be no continue button (auto-navigation via card click)
      const continueButton = page.locator('.location-selection__continue-button');
      await expect(continueButton).toHaveCount(0);

      // There should be no nav bar at all
      const nav = page.locator('.location-selection__nav');
      await expect(nav).toHaveCount(0);
    });

    test('TC-10: Changing branch resets brand selection', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Volkswagen Zentrum Essen');

      const brands1 = await getBrandCardNames(page);
      expect(brands1.length).toBeGreaterThan(0);

      // Go back to location
      await clickBrandBack(page);

      // Select a DIFFERENT branch — auto-navigates to brand page
      await selectBranchByName(page, 'Audi Zentrum Essen');

      // Brands should be different (Audi branch has only Audi)
      const brands2 = await getBrandCardNames(page);
      expect(brands2).toEqual(expect.arrayContaining(['Audi']));
      // Previous brand should NOT be auto-selected — no brand card should have selected state
      // (With auto-navigation, just verify we are on brand page with correct brands)
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });
  });

  // =============================================
  // GUARDS (Section 6)
  // =============================================

  test.describe('Guards and Redirects', () => {

    test('TC-7: Direct access to /home/brand without branch redirects to /home/location', async ({ page }) => {
      // Navigate directly to brand page without selecting a branch
      await navigateTo(page, '/home/brand');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('TC-8: Direct access to /home/services without brand redirects to /home/brand', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Volkswagen Zentrum Essen');

      // Now try to go directly to services (branch selected but no brand picked)
      await navigateTo(page, '/home/services');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Should redirect to /home/brand since no brand was selected
      expect(route).toBe('/home/brand');
    });
  });

  // =============================================
  // WIZARD BREADCRUMB (Section 11)
  // =============================================

  test.describe('Wizard Breadcrumb', () => {

    test('TC-11a: Breadcrumb visible on location page with Step 1 active', async ({ page }) => {
      await goToLocationPage(page);

      const wizard = page.locator('.wizard');
      await expect(wizard).toBeVisible();

      // First step should be active
      const activeStep = page.locator('.wizard__step--active');
      await expect(activeStep).toHaveCount(1);

      // Active step should be the first step
      const steps = page.locator('.wizard__step');
      const firstStepClasses = await steps.first().getAttribute('class');
      expect(firstStepClasses).toContain('wizard__step--active');
    });

    test('TC-11b: Breadcrumb shows Step 1 done and Step 2 active on brand page', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Volkswagen Zentrum Essen');

      const wizard = page.locator('.wizard');
      await expect(wizard).toBeVisible();

      // First step should be done
      const doneSteps = page.locator('.wizard__step--done');
      const doneCount = await doneSteps.count();
      expect(doneCount).toBeGreaterThanOrEqual(1);

      // Second step should be active
      const steps = page.locator('.wizard__step');
      const secondStepClasses = await steps.nth(1).getAttribute('class');
      expect(secondStepClasses).toContain('wizard__step--active');

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '05-breadcrumb-brand');
    });
  });

  // =============================================
  // INFO BANNER (Section 11)
  // =============================================

  test.describe('Info Banner', () => {

    test('TC-12: Info banner shows selected branch name and address on brand page', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Volkswagen Zentrum Essen');

      const infoBanner = page.locator('.brand-selection__info-banner');
      await expect(infoBanner).toBeVisible();

      const infoText = page.locator('.brand-selection__info-text');
      await expect(infoText).toContainText('Volkswagen Zentrum Essen');

      // Should also show address
      const text = (await infoText.textContent() ?? '').trim();
      expect(text.length).toBeGreaterThan(20);

      // Info icon should be visible
      const icon = page.locator('.brand-selection__info-icon');
      await expect(icon).toBeVisible();
    });

    test('TC-12b: Info banner not visible when no branch selected', async ({ page }) => {
      // This checks guard scenario — navigating to /home/brand without branch
      // should redirect to location, so banner never appears on its own
      await navigateTo(page, '/home/brand');
      await waitForAngular(page);

      // Should be redirected to location page
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });
  });

  // =============================================
  // LOCATION CARD DETAILS
  // =============================================

  test.describe('Location Card Details', () => {

    test('Location cards show branch name, address and brand logos', async ({ page }) => {
      await goToLocationPage(page);

      const firstCard = page.locator('.location-card').first();
      await expect(firstCard).toBeVisible();

      // Should show branch name
      const name = firstCard.locator('.location-card__name');
      await expect(name).toBeVisible();
      const nameText = (await name.textContent() ?? '').trim();
      expect(nameText.length).toBeGreaterThan(0);

      // Should show address
      const address = firstCard.locator('.location-card__address');
      await expect(address).toBeVisible();

      // Should show brand logos
      const logos = firstCard.locator('.location-card__brand-logo');
      const logoCount = await logos.count();
      expect(logoCount).toBeGreaterThan(0);
    });
  });

  // =============================================
  // FULL FLOW — Location → Brand → Services
  // =============================================

  test.describe('Full Flow Integration', () => {

    test('Complete flow: Location → Brand → Services page reached', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServicesPage(page, 'Audi Zentrum Essen', 'Audi');

      // Should be on services page
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      // Services page should be visible
      const serviceCards = page.locator('.service-card');
      const count = await serviceCards.count();
      expect(count).toBeGreaterThan(0);

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '06-services-after-flow');
    });
  });

  // =============================================
  // AUTO-NAVIGATION (AC-18, AC-19)
  // =============================================

  test.describe('Auto-Navigation', () => {

    test('TC-14: AC-18 — Clicking location card auto-navigates to /home/brand', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToLocationPage(page);

      // Click a branch card
      const card = page.locator('.location-card').first();
      await card.click();
      await waitForAngular(page);

      // Should auto-navigate to brand page without any continue button click
      await page.locator('.brand-selection').waitFor({ state: 'visible', timeout: 10000 });
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '07-auto-nav-location');
    });

    test('TC-15: AC-19 — Clicking brand card auto-navigates to /home/services', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToBrandPage(page, 'Audi Zentrum Essen');

      // Brand page should NOT have a continue button
      const continueBtn = page.locator('.brand-selection__continue-button');
      await expect(continueBtn).toHaveCount(0);

      // Click a brand card
      const brandCard = page.locator('.brand-card').filter({
        has: page.locator('.brand-card__name', { hasText: /^Audi$/ })
      });
      await brandCard.click();
      await waitForAngular(page);

      // Should auto-navigate to services page
      await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 10000 });
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '08-auto-nav-brand');
    });
  });

  // =============================================
  // NO VIN FIELD (AC-22)
  // =============================================

  test.describe('No VIN Field', () => {

    test('TC-16: AC-22 — Carinformation page has no VIN/FIN field', async ({ page }) => {
      await setLanguage(page, 'de');

      // Navigate through full flow to carinformation page
      await goToServicesPage(page, 'Audi Zentrum Essen', 'Audi');

      // Select a service (brake-fluid — no options, simple toggle)
      const brakeFluid = page.locator('.service-card').filter({
        has: page.locator('.service-card__title', { hasText: 'Wechsel Bremsflüssigkeit' })
      });
      await brakeFluid.click();
      await waitForAngular(page);

      // Continue to notes
      const serviceContinue = page.locator('.summary-bar__continue-button');
      await expect(serviceContinue).toBeEnabled();
      await serviceContinue.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Continue to appointment
      const notesContinue = page.locator('.notes__continue-button');
      await notesContinue.click();
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Select first appointment and continue
      const appointmentCard = page.locator('.appointment-card').first();
      await appointmentCard.click();
      await waitForAngular(page);
      const appointmentContinue = page.locator('.appointment-selection__continue-button');
      await appointmentContinue.click();
      await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // VIN/FIN field should NOT exist
      const vinField = page.locator('input[formcontrolname="vin"]');
      await expect(vinField).toHaveCount(0);

      // License plate and mileage should still exist
      const licensePlate = page.locator('input[formcontrolname="licensePlate"]');
      await expect(licensePlate).toBeVisible();
      const mileage = page.locator('input[formcontrolname="mileage"]');
      await expect(mileage).toBeVisible();

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '09-no-vin-field');
    });
  });

  // =============================================
  // EXTRAS INSTEAD OF PRICE (AC-23)
  // =============================================

  test.describe('Notes & Options Tile', () => {

    test('TC-17: AC-23 — Booking overview shows "Hinweise & Optionen" tile instead of price', async ({ page }) => {
      await setLanguage(page, 'de');

      // Navigate through full flow to booking overview
      await goToServicesPage(page, 'Audi Zentrum Essen', 'Audi');

      // Select brake-fluid service
      const brakeFluid = page.locator('.service-card').filter({
        has: page.locator('.service-card__title', { hasText: 'Wechsel Bremsflüssigkeit' })
      });
      await brakeFluid.click();
      await waitForAngular(page);

      // Continue to notes
      const serviceContinue = page.locator('.summary-bar__continue-button');
      await serviceContinue.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Continue to appointment
      const notesContinue = page.locator('.notes__continue-button');
      await notesContinue.click();
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Select first appointment and continue
      await page.locator('.appointment-card').first().click();
      await waitForAngular(page);
      const appointmentContinue = page.locator('.appointment-selection__continue-button');
      await appointmentContinue.click();
      await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Fill customer + vehicle forms
      await page.locator('input[formcontrolname="email"]').fill('max@mustermann.de');
      await page.locator('input[formcontrolname="firstName"]').fill('Max');
      await page.locator('input[formcontrolname="lastName"]').fill('Mustermann');
      await page.locator('input[formcontrolname="street"]').fill('Musterweg 1');
      await page.locator('input[formcontrolname="postalCode"]').fill('30159');
      await page.locator('input[formcontrolname="city"]').fill('Berlin');
      await page.locator('input[formcontrolname="mobilePhone"]').fill('017012345678');
      await page.locator('input[formcontrolname="licensePlate"]').fill('B-MS1234');
      await page.locator('input[formcontrolname="mileage"]').fill('5000');
      await waitForAngular(page);

      // Accept privacy consent
      const checkbox = page.locator('mat-checkbox .mdc-checkbox__native-control, mat-checkbox input[type="checkbox"]').first();
      await checkbox.click({ force: true });
      await waitForAngular(page);

      // Continue to booking overview
      const carinfoContinue = page.locator('.carinformation__continue-button');
      await carinfoContinue.click();
      await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // "Hinweise & Optionen" tile should be visible
      const notesOptionsTile = page.locator('.booking-overview').locator('text=Hinweise');
      await expect(notesOptionsTile).toBeVisible();

      // Price tile should NOT exist
      const priceTile = page.locator('.price-tile__total, .booking-overview__price-total');
      await expect(priceTile).toHaveCount(0);

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '10-notes-options-tile');
    });
  });

  // =============================================
  // BREADCRUMB RESET + DATA RETENTION (AC-24)
  // =============================================

  test.describe('Breadcrumb Reset & Data Retention', () => {

    test('TC-18: AC-24 — Breadcrumb reset from services to location clears services, keeps customer data', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServicesPage(page, 'Audi Zentrum Essen', 'Audi');

      // Select a service
      const brakeFluid = page.locator('.service-card').filter({
        has: page.locator('.service-card__title', { hasText: 'Wechsel Bremsflüssigkeit' })
      });
      await brakeFluid.click();
      await waitForAngular(page);

      // Verify service is selected (check icon or selected state)
      const selectedCount = page.locator('.service-card--selected');
      await expect(selectedCount).toHaveCount(1);

      // Click breadcrumb Step 1 (Location) to go back
      const wizardSteps = page.locator('.wizard__step');
      await wizardSteps.first().click();
      await waitForAngular(page);

      // Should be on location page
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      // Navigate back to services (select branch + brand again)
      await selectBranchByName(page, 'Audi Zentrum Essen');
      await selectBrandByName(page, 'Audi');

      // Services should be reset (no service selected)
      const selectedAfterReset = page.locator('.service-card--selected');
      await expect(selectedAfterReset).toHaveCount(0);

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '11-breadcrumb-reset');
    });

    test('TC-19: AC-24b — Customer/vehicle data persists after navigating back and forth', async ({ page }) => {
      await setLanguage(page, 'de');

      // Navigate to carinformation via full flow
      await goToServicesPage(page, 'Audi Zentrum Essen', 'Audi');

      // Select brake-fluid
      const brakeFluid = page.locator('.service-card').filter({
        has: page.locator('.service-card__title', { hasText: 'Wechsel Bremsflüssigkeit' })
      });
      await brakeFluid.click();
      await waitForAngular(page);

      // Continue to notes
      const serviceContinue = page.locator('.summary-bar__continue-button');
      await serviceContinue.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Continue to appointment
      const notesContinue = page.locator('.notes__continue-button');
      await notesContinue.click();
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Select appointment and continue to carinformation
      await page.locator('.appointment-card').first().click();
      await waitForAngular(page);
      const appointmentContinue = page.locator('.appointment-selection__continue-button');
      await appointmentContinue.click();
      await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Fill customer form with recognizable data
      await page.locator('input[formcontrolname="email"]').fill('test@example.com');
      await page.locator('input[formcontrolname="firstName"]').fill('Testvorname');
      await page.locator('input[formcontrolname="lastName"]').fill('Testnachname');
      await page.locator('input[formcontrolname="street"]').fill('Teststrasse 42');
      await page.locator('input[formcontrolname="postalCode"]').fill('12345');
      await page.locator('input[formcontrolname="city"]').fill('Teststadt');
      await page.locator('input[formcontrolname="mobilePhone"]').fill('017099999999');

      // Fill vehicle form
      await page.locator('input[formcontrolname="licensePlate"]').fill('E-TS9999');
      await page.locator('input[formcontrolname="mileage"]').fill('12345');
      await waitForAngular(page);

      // Navigate back to services via breadcrumb or back button
      const backButton = page.locator('.carinformation__back-button');
      await backButton.click();
      await waitForAngular(page);

      // Go back to appointment
      await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });

      // Go forward again to carinformation
      await page.locator('.appointment-card').first().click();
      await waitForAngular(page);
      const appointmentContinue2 = page.locator('.appointment-selection__continue-button');
      await appointmentContinue2.click();
      await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
      await waitForAngular(page);

      // Customer data should still be there
      await expect(page.locator('input[formcontrolname="email"]')).toHaveValue('test@example.com');
      await expect(page.locator('input[formcontrolname="firstName"]')).toHaveValue('Testvorname');
      await expect(page.locator('input[formcontrolname="lastName"]')).toHaveValue('Testnachname');
      await expect(page.locator('input[formcontrolname="street"]')).toHaveValue('Teststrasse 42');
      await expect(page.locator('input[formcontrolname="city"]')).toHaveValue('Teststadt');

      // Vehicle data should still be there
      await expect(page.locator('input[formcontrolname="licensePlate"]')).toHaveValue('E-TS9999');
      await expect(page.locator('input[formcontrolname="mileage"]')).toHaveValue('12345');

      await saveScreenshot(page, 'REQ-013-Branch-Marke-Tausch', '12-data-retention');
    });
  });
});
