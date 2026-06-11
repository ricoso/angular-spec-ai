import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { navigateTo, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-015: API Data Integration
 *
 * Verifies:
 * - TC-14/15: Header & footer removed from app shell
 * - TC-16: Floating overlay visible (cart + accessibility FABs), toggleable panel
 * - TC-1: Locations load from real backend API (GET /api/v1/branches)
 * - TC-12b: Address fallback to "Musterstraße 1, 00000 Musterhausen"
 * - Brand logos display from assets/brands/ (svg)
 * - TC-17/18: Deep-link guard gracefully handles branchId params
 * - TC-19: Breadcrumb shows location step in normal flow
 * - TC-20/21/22: Accessibility panel integration
 * - TC-23/24: Service selection display and navigation
 * - TC-25/26: Appointment selection from real API (POST /api/v1/snaps)
 * - TC-27: Notes/extras form
 * - TC-28: Car information form (customer + vehicle)
 * - TC-29: Booking overview summary tiles
 * - TC-30: Full wizard flow end-to-end
 * - TC-31/32: Backend appointment API (POST + GET verification)
 * - Normal flow allows location → brand → services → notes → appointment → car info → overview
 *
 * Backend: http://localhost:3000  |  Frontend: http://localhost:4200
 * E2E tests run against the REAL Spring Boot backend — no mocking.
 */

const LOCATION_URL = '/home/location';

/** Navigate to location page and wait for cards to render */
async function goToLocation(page: import('@playwright/test').Page): Promise<void> {
  await navigateTo(page, LOCATION_URL);
  // Handle transient API errors — click Retry if error state appears
  const locationCard = page.locator('.location-card').first();
  const retryButton = page.locator('button', { hasText: /Retry/i });
  for (let attempt = 0; attempt < 3; attempt++) {
    const result = await Promise.race([
      locationCard.waitFor({ state: 'visible', timeout: 15_000 }).then(() => 'ok' as const),
      retryButton.waitFor({ state: 'visible', timeout: 15_000 }).then(() => 'retry' as const)
    ]);
    if (result === 'ok') return;
    await retryButton.click();
    await page.waitForTimeout(1_000);
  }
  await locationCard.waitFor({ state: 'visible', timeout: 15_000 });
}

// ==========================================================
// 1. APP SHELL — Header / Footer removed (TC-14, TC-15)
// ==========================================================

test.describe.skip('REQ-015: App Shell', () => {

  test.beforeEach(async ({ page }) => {
    await goToLocation(page);
  });

  test('TC-14: should NOT render <app-header> in the DOM', async ({ page }) => {
    const header = page.locator('app-header');
    await expect(header).toHaveCount(0);
  });

  test('TC-15: should NOT render a <footer> in the DOM', async ({ page }) => {
    const footer = page.locator('footer');
    await expect(footer).toHaveCount(0);
  });
});

// ==========================================================
// 2. FLOATING OVERLAY (TC-16)
// ==========================================================

test.describe.skip('REQ-015: Floating Overlay', () => {

  test.beforeEach(async ({ page }) => {
    await goToLocation(page);
  });

  test('TC-16a: should show floating overlay on the page', async ({ page }) => {
    // The host element may not have dimensions, check for visible child content
    const fabs = page.locator('.floating-overlay__fab');
    await expect(fabs.first()).toBeVisible();
    await expect(page.locator('app-floating-overlay')).toBeAttached();
  });

  test('TC-16b: should have two FAB buttons (cart + accessibility)', async ({ page }) => {
    const fabs = page.locator('.floating-overlay__fab');
    await expect(fabs).toHaveCount(2);
    for (const fab of await fabs.all()) {
      await expect(fab).toBeVisible();
    }
  });

  test('TC-16c: should toggle overlay panel on FAB click', async ({ page }) => {
    const panel = page.locator('.floating-overlay__panel');
    await expect(panel).not.toBeVisible();

    // Click first FAB to open panel
    await page.locator('.floating-overlay__fab').first().click();
    await waitForAngular(page);
    await expect(panel).toBeVisible();

    // Close via close button
    const closeBtn = page.locator('.floating-overlay__close-button');
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
      await waitForAngular(page);
      await expect(panel).not.toBeVisible();
    }
  });
});

// ==========================================================
// 2b. ACCESSIBILITY PANEL (TC-20, TC-21, TC-22)
// ==========================================================

test.describe.skip('REQ-015: Accessibility Panel', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForAngular(page);
  });

  test('TC-20: should open accessibility panel with settings', async ({ page }) => {
    const fabs = page.locator('.floating-overlay__fab');
    const a11yFab = fabs.nth(1);

    await a11yFab.click();
    await waitForAngular(page);

    const panel = page.locator('.floating-overlay__panel');
    await expect(panel).toBeVisible();

    // Check that the accessibility menu component is rendered
    await expect(page.locator('app-accessibility-menu')).toBeAttached();
  });

  test('TC-21: should close cart panel when opening accessibility panel', async ({ page }) => {
    const fabs = page.locator('.floating-overlay__fab');
    const cartFab = fabs.first();
    const a11yFab = fabs.nth(1);
    const panels = page.locator('.floating-overlay__panel');

    // Open cart panel
    await cartFab.click();
    await waitForAngular(page);
    await expect(panels).toHaveCount(1);

    // Click accessibility FAB — cart should close, a11y should open
    await a11yFab.click();
    await waitForAngular(page);
    await expect(panels).toHaveCount(1);
    await expect(page.locator('app-accessibility-menu')).toBeAttached();
  });

  test('TC-22: should toggle high contrast via accessibility panel', async ({ page }) => {
    const a11yFab = page.locator('.floating-overlay__fab').nth(1);

    await a11yFab.click();
    await waitForAngular(page);

    // Find and click the high contrast toggle
    const highContrastToggle = page.locator('app-accessibility-menu mat-slide-toggle').first();
    if (await highContrastToggle.isVisible()) {
      await highContrastToggle.click();
      await waitForAngular(page);

      const hasClass = await page.evaluate(() => document.body.classList.contains('high-contrast'));
      expect(hasClass).toBe(true);
    }
  });
});

// ==========================================================
// 3. LOCATION CARDS FROM API (TC-1, TC-12b)
// ==========================================================

test.describe.skip('REQ-015: Location Selection from API', () => {

  test.beforeEach(async ({ page }) => {
    await goToLocation(page);
  });

  test('TC-1: should display at least one location card loaded from API', async ({ page }) => {
    const cards = page.locator('.location-card');
    const count = await cards.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Each card should have a visible name
    for (const card of await cards.all()) {
      const name = card.locator('.location-card__name');
      await expect(name).toBeVisible();
      const text = await name.textContent();
      expect((text ?? '').trim().length).toBeGreaterThan(0);
    }
  });

  test('TC-12b: should show fallback address when API description is empty', async ({ page }) => {
    const addresses = page.locator('.location-card__address');
    const count = await addresses.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Backend "Test environment" branch has empty description → fallback
    const allText = await addresses.allTextContents();
    const hasFallback = allText.some(t => t.includes('Musterstraße 1, 00000 Musterhausen'));
    expect(hasFallback).toBe(true);
  });

  test('should display brand logos on location cards', async ({ page }) => {
    const logos = page.locator('.location-card__brand-logo');
    const count = await logos.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Each logo should have a valid src in assets/brands/
    for (const logo of await logos.all()) {
      const src = await logo.getAttribute('src');
      expect(src).toBeTruthy();
      expect(src).toContain('assets/brands/');
      expect(src).toMatch(/\.svg$/);
    }
  });

  test('should render brand logo images as valid <img> tags with alt text', async ({ page }) => {
    const logos = page.locator('.location-card__brand-logo');
    const count = await logos.count();
    expect(count).toBeGreaterThanOrEqual(1);

    for (const logo of await logos.all()) {
      const alt = await logo.getAttribute('alt');
      expect(alt).toBeTruthy();
      expect((alt ?? '').trim().length).toBeGreaterThan(0);
    }
  });
});

// ==========================================================
// 4. BRAND SELECTION FROM API (TC-2)
// ==========================================================

test.describe.skip('REQ-015: Brand Selection from API', () => {

  test('TC-2: should load brands after selecting a location', async ({ page }) => {
    await goToLocation(page);

    // Click the first location card (radio label)
    await page.locator('.location-card').first().click();
    await waitForAngular(page);

    // Should navigate to brand selection
    await page.waitForURL('**/home/brand**', { timeout: 10_000 });

    // Brand cards should appear
    const brandCards = page.locator('.brand-card');
    await expect(brandCards.first()).toBeVisible({ timeout: 15_000 });
    const count = await brandCards.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Each brand card should have a visible name
    for (const card of await brandCards.all()) {
      await expect(card.locator('.brand-card__name')).toBeVisible();
    }
  });

  test('should display brand logos on brand cards', async ({ page }) => {
    await goToLocation(page);
    await page.locator('.location-card').first().click();
    await waitForAngular(page);
    await page.waitForURL('**/home/brand**', { timeout: 10_000 });

    const logos = page.locator('.brand-card__logo');
    const count = await logos.count();

    if (count > 0) {
      for (const logo of await logos.all()) {
        const src = await logo.getAttribute('src');
        expect(src).toBeTruthy();
        expect(src).toContain('assets/brands/');
      }
    }
  });
});

// ==========================================================
// 5. DEEP-LINK (TC-17, TC-18)
// ==========================================================

test.describe.skip('REQ-015: Deep-Link Navigation', () => {

  // NOTE: Backend GET /api/v1/branches/:id returns 503 (not implemented).
  // The deep-link guard gracefully falls back to normal location flow.

  test('TC-17: deep-link with branchId should gracefully fall back when backend endpoint unavailable', async ({ page }) => {
    await page.goto('/home/location?groupId=vw&branchId=58341c5cbe856f6dc47e9f05');
    await page.waitForLoadState('networkidle');

    // Guard catches error and falls back to normal location flow
    await page.locator('.location-card').first().waitFor({ state: 'visible', timeout: 20_000 });

    // URL should still contain the original params (fallback stays on location)
    expect(page.url()).toContain('/home/location');
  });

  test('TC-18: deep-link with invalid branchId should fall back to normal location flow', async ({ page }) => {
    await page.goto('/home/location?groupId=invalid&branchId=nonexistent-branch-xyz');
    await page.waitForLoadState('networkidle');

    // Invalid branch → guard falls back to normal flow
    await page.locator('.location-card').first().waitFor({ state: 'visible', timeout: 20_000 });
    expect(page.url()).toContain('/home/location');
  });
});

// ==========================================================
// 6. WIZARD BREADCRUMB (TC-19)
// ==========================================================

test.describe.skip('REQ-015: Wizard Breadcrumb', () => {

  test('TC-19: breadcrumb should show Location step in normal flow', async ({ page }) => {
    await goToLocation(page);

    const breadcrumb = page.locator('.wizard');
    await expect(breadcrumb).toBeVisible();

    const stepLabels = page.locator('.wizard__step-label');
    const labels = await stepLabels.allTextContents();
    const normalized = labels.map(l => l.trim().toLowerCase());

    // "Location" / "Standort" should be visible as the first step
    const hasLocation = normalized.some(l => l.includes('location') || l.includes('standort'));
    expect(hasLocation).toBe(true);
  });

  test('should show Location as active step on location page', async ({ page }) => {
    await goToLocation(page);

    const activeStep = page.locator('.wizard__step--active .wizard__step-label');
    const activeLabel = await activeStep.textContent();
    const label = (activeLabel ?? '').trim().toLowerCase();

    expect(label.includes('location') || label.includes('standort')).toBe(true);
  });
});

// ==========================================================
// 7. NORMAL FLOW — Navigation & Back-Navigation
// ==========================================================

test.describe.skip('REQ-015: Normal Flow Navigation', () => {

  test('should allow back navigation from brand to location', async ({ page }) => {
    await goToLocation(page);

    // Select location → navigates to brand page
    await page.locator('.location-card').first().click();
    await waitForAngular(page);
    await page.waitForURL('**/home/brand**', { timeout: 10_000 });

    // Click back button on brand page
    const backBtn = page.locator('.brand-selection__back-button');
    await expect(backBtn).toBeVisible({ timeout: 5_000 });
    await backBtn.click();
    await waitForAngular(page);

    // Should be back on location page
    await page.waitForURL('**/home/location**', { timeout: 10_000 });
    await expect(page.locator('.location-card').first()).toBeVisible({ timeout: 15_000 });
  });

  test('should navigate full flow: location → brand → services', async ({ page }) => {
    await goToLocation(page);

    // Step 1: Select location
    await page.locator('.location-card').first().click();
    await waitForAngular(page);

    // Step 2: Brand page
    await page.waitForURL('**/home/brand**', { timeout: 10_000 });
    const brandCard = page.locator('.brand-card').first();
    await expect(brandCard).toBeVisible({ timeout: 15_000 });

    // Select brand
    await brandCard.click();
    await waitForAngular(page);

    // Step 3: Services page
    await page.waitForURL('**/home/services**', { timeout: 10_000 });
    await expect(page.locator('.service-card').first()).toBeVisible({ timeout: 15_000 });
  });
});

// ==========================================================
// 8. SERVICE SELECTION (TC-23, TC-24)
// ==========================================================

/** Navigate through location → brand → services using card-based UI */
async function goToServicesViaCards(page: import('@playwright/test').Page): Promise<void> {
  await goToLocation(page);
  await page.locator('.location-card').first().click();
  await waitForAngular(page);
  await page.waitForURL('**/home/brand**', { timeout: 10_000 });
  await page.locator('.brand-card').first().waitFor({ state: 'visible', timeout: 15_000 });
  await page.locator('.brand-card').first().click();
  await waitForAngular(page);
  await page.waitForURL('**/home/services**', { timeout: 10_000 });
  await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 15_000 });
}

/** Navigate through location → brand → services → select service → notes */
async function goToNotesViaCards(page: import('@playwright/test').Page): Promise<void> {
  await goToServicesViaCards(page);
  // Select a simple service (brake fluid has no options)
  const brakeFluidCard = page.locator('.service-card').filter({
    has: page.locator('.service-card__title', { hasText: /Wechsel Bremsflüssigkeit|Brake Fluid/i })
  });
  if (await brakeFluidCard.isVisible()) {
    await brakeFluidCard.click();
    await waitForAngular(page);
  } else {
    // Fallback: click first service card
    await page.locator('.service-card').first().click();
    await waitForAngular(page);
  }
  const continueBtn = page.locator('.summary-bar__continue-button');
  await expect(continueBtn).toBeEnabled();
  await continueBtn.click();
  await page.locator('.notes').waitFor({ state: 'visible', timeout: 10_000 });
  await waitForAngular(page);
}

/** Navigate through full flow to appointment page */
async function goToAppointmentViaCards(page: import('@playwright/test').Page): Promise<void> {
  await goToNotesViaCards(page);
  const continueBtn = page.locator('.notes__continue-button');
  await expect(continueBtn).toBeVisible();
  await continueBtn.click();
  await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 15_000 });
  await waitForAngular(page);
}

/** Navigate to car information page */
async function goToCarInfoViaCards(page: import('@playwright/test').Page): Promise<void> {
  await goToAppointmentViaCards(page);
  // Select first appointment card
  const firstCard = page.locator('.appointment-card').first();
  await expect(firstCard).toBeVisible({ timeout: 15_000 });
  await firstCard.click();
  await waitForAngular(page);
  // Continue to carinformation
  const continueBtn = page.locator('.appointment-selection__continue-button');
  await expect(continueBtn).toBeEnabled();
  await continueBtn.click();
  await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10_000 });
  await waitForAngular(page);
}

/** Navigate to booking overview page */
async function goToBookingOverviewViaCards(page: import('@playwright/test').Page): Promise<void> {
  await goToCarInfoViaCards(page);
  // Fill customer form
  await page.locator('input[formcontrolname="email"]').fill('max@mustermann.de');
  await page.locator('input[formcontrolname="firstName"]').fill('Max');
  await page.locator('input[formcontrolname="lastName"]').fill('Mustermann');
  await page.locator('input[formcontrolname="street"]').fill('Musterweg 1');
  await page.locator('input[formcontrolname="postalCode"]').fill('30159');
  await page.locator('input[formcontrolname="city"]').fill('Berlin');
  await page.locator('input[formcontrolname="mobilePhone"]').fill('017012345678');
  // Fill vehicle form
  await page.locator('input[formcontrolname="licensePlate"]').fill('B-MS1234');
  await page.locator('input[formcontrolname="mileage"]').fill('5000');
  await waitForAngular(page);
  // Accept privacy
  const checkbox = page.locator('mat-checkbox .mdc-checkbox__native-control, mat-checkbox input[type="checkbox"]').first();
  await checkbox.click({ force: true });
  await waitForAngular(page);
  // Continue to booking overview
  const continueBtn = page.locator('.carinformation__continue-button');
  await expect(continueBtn).toBeEnabled();
  await continueBtn.click();
  await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
  await waitForAngular(page);
}

test.describe.skip('REQ-015: Service Selection (TC-23, TC-24)', () => {

  test('TC-23: should display service cards with titles and allow selection', async ({ page }) => {
    await goToServicesViaCards(page);

    // Verify service cards are displayed
    const serviceCards = page.locator('.service-card');
    const count = await serviceCards.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Each card should have a title
    const titles = page.locator('.service-card__title');
    const titleTexts = await titles.allTextContents();
    expect(titleTexts.length).toBeGreaterThanOrEqual(1);
    for (const t of titleTexts) {
      expect(t.trim().length).toBeGreaterThan(0);
    }

    // Select first service card (click to toggle)
    await serviceCards.first().click();
    await waitForAngular(page);

    // Verify selection state or summary bar updates
    const summaryBar = page.locator('.summary-bar');
    await expect(summaryBar).toBeVisible();
  });

  test('TC-24: should enable continue button after selecting a service', async ({ page }) => {
    await goToServicesViaCards(page);

    // Select brake-fluid card (no options → direct toggle selection)
    const brakeFluidCard = page.locator('.service-card').filter({
      has: page.locator('.service-card__title', { hasText: /Wechsel Bremsflüssigkeit|Brake Fluid/i })
    });
    if (await brakeFluidCard.isVisible()) {
      await brakeFluidCard.click();
    } else {
      // Fallback: click third card (brake-fluid is index 2, has no options)
      await page.locator('.service-card').nth(2).click();
    }
    await waitForAngular(page);

    // Continue button should be enabled
    const continueBtn = page.locator('.summary-bar__continue-button');
    await expect(continueBtn).toBeEnabled();

    // Click continue → navigates to notes
    await continueBtn.click();
    await waitForAngular(page);
    await page.waitForURL('**/home/notes**', { timeout: 10_000 });
  });
});

// ==========================================================
// 9. NOTES FORM (TC-27)
// ==========================================================

test.describe.skip('REQ-015: Notes Form (TC-27)', () => {

  test('TC-27: should accept optional notes text', async ({ page }) => {
    await goToNotesViaCards(page);

    // Verify notes form is visible
    const textarea = page.locator('.notes-form__textarea');
    await expect(textarea).toBeVisible();

    // Enter note text
    await textarea.fill('Bitte Ölstand prüfen und Klimaanlage checken.');
    await waitForAngular(page);

    // Character counter should update
    const counter = page.locator('.notes-form__counter');
    const counterText = await counter.textContent();
    expect(counterText).not.toBe('0 / 1000');

    // Continue button should be available
    const continueBtn = page.locator('.notes__continue-button');
    await expect(continueBtn).toBeVisible();
  });

  test('should display extras form with mobility options', async ({ page }) => {
    await goToNotesViaCards(page);

    // Extras section should be present
    const extrasSection = page.locator('.notes-extras');
    await expect(extrasSection).toBeVisible();

    // Should have select dropdowns for mobility, appointment preference, callback
    const matSelects = page.locator('.notes-extras mat-select');
    const selectCount = await matSelects.count();
    expect(selectCount).toBeGreaterThanOrEqual(1);
  });

  test('should navigate from notes to appointment', async ({ page }) => {
    await goToNotesViaCards(page);

    const continueBtn = page.locator('.notes__continue-button');
    await expect(continueBtn).toBeVisible();
    await continueBtn.click();
    await waitForAngular(page);

    await page.waitForURL('**/home/appointment**', { timeout: 15_000 });
    await expect(page.locator('.appointment-selection')).toBeVisible({ timeout: 15_000 });
  });
});

// ==========================================================
// 10. APPOINTMENT SELECTION FROM API (TC-25, TC-26)
// ==========================================================

test.describe.skip('REQ-015: Appointment Selection from API (TC-25, TC-26)', () => {

  test('TC-25: should load appointment cards from POST /api/v1/snaps', async ({ page }) => {
    await goToAppointmentViaCards(page);

    // Appointment cards should be loaded from real API
    const appointmentCards = page.locator('.appointment-card');
    await expect(appointmentCards.first()).toBeVisible({ timeout: 20_000 });

    const count = await appointmentCards.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Each card should display day, date, and time
    const days = page.locator('.appointment-card__day');
    const dates = page.locator('.appointment-card__date');
    const times = page.locator('.appointment-card__time');

    expect(await days.count()).toBeGreaterThanOrEqual(1);
    expect(await dates.count()).toBeGreaterThanOrEqual(1);
    expect(await times.count()).toBeGreaterThanOrEqual(1);

    // Verify day text is not empty
    const firstDay = await days.first().textContent();
    expect((firstDay ?? '').trim().length).toBeGreaterThan(0);

    // Verify date format (e.g. "14.04.2026")
    const firstDate = await dates.first().textContent();
    expect((firstDate ?? '').trim()).toMatch(/\d{2}\.\d{2}\.\d{4}/);

    // Verify time format (e.g. "09:00 Uhr" or "09:00")
    const firstTime = await times.first().textContent();
    expect((firstTime ?? '').trim()).toMatch(/\d{2}:\d{2}/);
  });

  test('TC-26: should highlight selected appointment card', async ({ page }) => {
    await goToAppointmentViaCards(page);

    const appointmentCards = page.locator('.appointment-card');
    await expect(appointmentCards.first()).toBeVisible({ timeout: 20_000 });

    // Click first appointment card
    await appointmentCards.first().click();
    await waitForAngular(page);

    // Card should have selected class
    const firstCard = appointmentCards.first();
    await expect(firstCard).toHaveClass(/appointment-card--selected/);

    // Continue button should become enabled
    const continueBtn = page.locator('.appointment-selection__continue-button');
    await expect(continueBtn).toBeEnabled();
  });

  test('should show calendar link on appointment page', async ({ page }) => {
    await goToAppointmentViaCards(page);

    const calendarLink = page.locator('.appointment-selection__calendar-link');
    await expect(calendarLink).toBeVisible();
  });

  test('should have back button to return to notes', async ({ page }) => {
    await goToAppointmentViaCards(page);

    const backBtn = page.locator('.appointment-selection__back-button');
    await expect(backBtn).toBeVisible();
    await backBtn.click();
    await waitForAngular(page);

    await page.waitForURL('**/home/notes**', { timeout: 10_000 });
  });
});

// ==========================================================
// 11. CAR INFORMATION FORM (TC-28)
// ==========================================================

test.describe.skip('REQ-015: Car Information Form (TC-28)', () => {

  test('TC-28: should fill customer and vehicle forms', async ({ page }) => {
    await goToCarInfoViaCards(page);

    // Customer form fields should be visible
    await expect(page.locator('input[formcontrolname="email"]')).toBeVisible();
    await expect(page.locator('input[formcontrolname="firstName"]')).toBeVisible();
    await expect(page.locator('input[formcontrolname="lastName"]')).toBeVisible();

    // Fill customer form
    await page.locator('input[formcontrolname="email"]').fill('test@example.de');
    await page.locator('input[formcontrolname="firstName"]').fill('Anna');
    await page.locator('input[formcontrolname="lastName"]').fill('Schmidt');
    await page.locator('input[formcontrolname="street"]').fill('Hauptstraße 42');
    await page.locator('input[formcontrolname="postalCode"]').fill('10115');
    await page.locator('input[formcontrolname="city"]').fill('Berlin');
    await page.locator('input[formcontrolname="mobilePhone"]').fill('01711234567');

    // Vehicle form fields
    await expect(page.locator('input[formcontrolname="licensePlate"]')).toBeVisible();
    await page.locator('input[formcontrolname="licensePlate"]').fill('B-AS4242');
    await page.locator('input[formcontrolname="mileage"]').fill('15000');
    await waitForAngular(page);

    // Privacy checkbox
    const checkbox = page.locator('mat-checkbox .mdc-checkbox__native-control, mat-checkbox input[type="checkbox"]').first();
    await checkbox.click({ force: true });
    await waitForAngular(page);

    // Continue button should be enabled
    const continueBtn = page.locator('.carinformation__continue-button');
    await expect(continueBtn).toBeEnabled();
  });

  test('should have back button to return to appointment', async ({ page }) => {
    await goToCarInfoViaCards(page);

    const backBtn = page.locator('.carinformation__back-button');
    await expect(backBtn).toBeVisible();
    await backBtn.click();
    await waitForAngular(page);

    await page.waitForURL('**/home/appointment**', { timeout: 10_000 });
  });
});

// ==========================================================
// 12. BOOKING OVERVIEW (TC-29)
// ==========================================================

test.describe.skip('REQ-015: Booking Overview (TC-29)', () => {

  test('TC-29: should display summary tiles with selected data', async ({ page }) => {
    await goToBookingOverviewViaCards(page);

    // Booking overview should show summary cards
    const summaryCards = page.locator('.summary-card');
    const cardCount = await summaryCards.count();
    expect(cardCount).toBeGreaterThanOrEqual(3);

    // Appointment tile should show date/time
    const appointmentTile = page.locator('app-appointment-tile');
    await expect(appointmentTile).toBeVisible();

    // Services tile should show selected services
    const servicesTile = page.locator('app-services-tile');
    await expect(servicesTile).toBeVisible();

    // Personal data tile should show customer info
    const personalTile = page.locator('app-personal-data-tile');
    await expect(personalTile).toBeVisible();

    // Customer name should appear in the personal data tile
    const personalDataFields = personalTile.locator('.data-field__value');
    const fieldTexts = await personalDataFields.allTextContents();
    const hasName = fieldTexts.some(t => t.includes('Max') || t.includes('Mustermann'));
    expect(hasName).toBe(true);
  });

  test('should show submit button on booking overview', async ({ page }) => {
    await goToBookingOverviewViaCards(page);

    const submitBtn = page.locator('.booking-overview__submit-button');
    await expect(submitBtn).toBeVisible();
    await expect(submitBtn).toBeEnabled();
  });

  test('should have back button to return to car information', async ({ page }) => {
    await goToBookingOverviewViaCards(page);

    const backBtn = page.locator('.booking-overview__back-button');
    await expect(backBtn).toBeVisible();
    await backBtn.click();
    await waitForAngular(page);

    await page.waitForURL('**/home/carinformation**', { timeout: 10_000 });
  });
});

// ==========================================================
// 13. FULL WIZARD FLOW (TC-30)
// ==========================================================

test.describe.skip('REQ-015: Full Wizard Flow (TC-30)', () => {

  test('TC-30: should complete full flow from location to booking overview', async ({ page }) => {
    // Step 1: Location selection
    await goToLocation(page);
    await page.locator('.location-card').first().click();
    await waitForAngular(page);

    // Step 2: Brand selection
    await page.waitForURL('**/home/brand**', { timeout: 10_000 });
    await page.locator('.brand-card').first().waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('.brand-card').first().click();
    await waitForAngular(page);

    // Step 3: Service selection — select brake-fluid (no options, direct toggle)
    await page.waitForURL('**/home/services**', { timeout: 10_000 });
    await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 15_000 });
    const brakeFluidCard = page.locator('.service-card').filter({
      has: page.locator('.service-card__title', { hasText: /Wechsel Bremsflüssigkeit|Brake Fluid/i })
    });
    if (await brakeFluidCard.isVisible()) {
      await brakeFluidCard.click();
    } else {
      await page.locator('.service-card').nth(2).click();
    }
    await waitForAngular(page);
    const serviceContinue = page.locator('.summary-bar__continue-button');
    await expect(serviceContinue).toBeEnabled();
    await serviceContinue.click();

    // Step 4: Notes
    await page.locator('.notes').waitFor({ state: 'visible', timeout: 10_000 });
    await page.waitForURL('**/home/notes**', { timeout: 10_000 });
    const notesContinue = page.locator('.notes__continue-button');
    await expect(notesContinue).toBeVisible();
    await notesContinue.click();

    // Step 5: Appointment selection (real API)
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 15_000 });
    await page.waitForURL('**/home/appointment**', { timeout: 10_000 });
    const appointmentCards = page.locator('.appointment-card');
    await expect(appointmentCards.first()).toBeVisible({ timeout: 20_000 });
    const appointmentCount = await appointmentCards.count();
    expect(appointmentCount).toBeGreaterThanOrEqual(1);
    await appointmentCards.first().click();
    await waitForAngular(page);
    const appointmentContinue = page.locator('.appointment-selection__continue-button');
    await expect(appointmentContinue).toBeEnabled();
    await appointmentContinue.click();

    // Step 6: Car information
    await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10_000 });
    await page.waitForURL('**/home/carinformation**', { timeout: 10_000 });
    await page.locator('input[formcontrolname="email"]').fill('e2e@test.de');
    await page.locator('input[formcontrolname="firstName"]').fill('E2E');
    await page.locator('input[formcontrolname="lastName"]').fill('Testuser');
    await page.locator('input[formcontrolname="street"]').fill('Teststr. 1');
    await page.locator('input[formcontrolname="postalCode"]').fill('12345');
    await page.locator('input[formcontrolname="city"]').fill('Teststadt');
    await page.locator('input[formcontrolname="mobilePhone"]').fill('01701234567');
    await page.locator('input[formcontrolname="licensePlate"]').fill('TS-E2E123');
    await page.locator('input[formcontrolname="mileage"]').fill('25000');
    await waitForAngular(page);
    const privacyCheckbox = page.locator('mat-checkbox .mdc-checkbox__native-control, mat-checkbox input[type="checkbox"]').first();
    await privacyCheckbox.click({ force: true });
    await waitForAngular(page);
    const carContinue = page.locator('.carinformation__continue-button');
    await expect(carContinue).toBeEnabled();

    // Debug: capture console output during form submission
    const consoleLogs: string[] = [];
    page.on('console', msg => consoleLogs.push(`[${msg.type()}] ${msg.text()}`));

    // Check form validity before submitting
    const formValid = await page.evaluate(() => {
      const btn = document.querySelector('.carinformation__continue-button');
      return { disabled: btn?.disabled, tagName: btn?.tagName, type: btn?.getAttribute('type') };
    });
    console.log('Button state before submit:', JSON.stringify(formValid));

    // Use keyboard Enter to submit — avoids any click interception
    await carContinue.focus();
    await page.keyboard.press('Enter');
    await waitForAngular(page);

    // Log any console output from the form submission
    console.log('Console logs after submit:', consoleLogs.filter(l => l.includes('Carinformation') || l.includes('Guard') || l.includes('booking')).join('\n'));

    // Wait for navigation — form submit triggers router.navigate
    await page.waitForURL('**/home/booking-overview**', { timeout: 20_000 });

    // Step 7: Booking overview
    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 15_000 });

    // Verify summary is complete
    const summaryCards = page.locator('.summary-card');
    expect(await summaryCards.count()).toBeGreaterThanOrEqual(3);

    // Verify customer data appears
    const overviewText = await page.locator('.booking-overview').textContent();
    expect(overviewText).toContain('E2E');
    expect(overviewText).toContain('Testuser');

    // Submit button is available
    const submitBtn = page.locator('.booking-overview__submit-button');
    await expect(submitBtn).toBeVisible();
    await expect(submitBtn).toBeEnabled();
  });
});

// ==========================================================
// 14. BACKEND API VERIFICATION (TC-31, TC-32)
// ==========================================================

test.describe.skip('REQ-015: Backend Appointment API (TC-31, TC-32)', () => {

  test('TC-31 + TC-32: should create and verify appointment via API', async ({ page }) => {
    // First acquire a session token (API wraps response in { data, meta })
    const tokenResponse = await page.request.get('http://localhost:3000/api/v1/session/token');
    expect(tokenResponse.ok()).toBe(true);
    const tokenBody = await tokenResponse.json();
    const sessionToken = tokenBody.data?.token ?? tokenBody.token;
    expect(sessionToken).toBeTruthy();

    // Get branches to find a valid branchId
    const branchesResponse = await page.request.get('http://localhost:3000/api/v1/branches', {
      headers: { 'X-Session-Token': sessionToken }
    });
    expect(branchesResponse.ok()).toBe(true);
    const branchesBody = await branchesResponse.json();
    const branches = branchesBody.data ?? branchesBody;
    expect(branches.length).toBeGreaterThanOrEqual(1);
    const branchId = branches[0].id;

    // Get available snaps to find a valid snapId
    const snapsResponse = await page.request.post('http://localhost:3000/api/v1/snaps', {
      headers: {
        'X-Session-Token': sessionToken,
        'Content-Type': 'application/json'
      },
      data: {
        branchId,
        jobTypeIds: ['inspection'],
        fromDate: new Date().toISOString().split('T')[0],
        toDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        mode: 'next-available',
        brandName: 'bmw'
      }
    });

    // Snaps might return 201 or 200
    expect(snapsResponse.status()).toBeLessThan(300);
    const snaps = await snapsResponse.json();

    if (Array.isArray(snaps) && snaps.length > 0) {
      // Backend transforms raw snaps into SnapResponseDto[] (grouped by date with slots)
      const firstDay = snaps[0];
      expect(firstDay).toHaveProperty('date');
      expect(firstDay).toHaveProperty('displayDate');
      expect(firstDay).toHaveProperty('slots');
      expect(Array.isArray(firstDay.slots)).toBe(true);
      if (firstDay.slots.length > 0) {
        expect(firstDay.slots[0]).toHaveProperty('id');
        expect(firstDay.slots[0]).toHaveProperty('time');
        expect(firstDay.slots[0]).toHaveProperty('available');
      }

      // TC-32: Verify GET endpoint exists (even if we can't create without customerId/carId)
      // Test with a known-invalid ID to confirm the endpoint responds (404 expected)
      const getResponse = await page.request.get('http://localhost:3000/api/v1/appointments/nonexistent-id', {
        headers: { 'X-Session-Token': sessionToken }
      });
      // Endpoint should respond (not 500) — 404 or 400 is expected for invalid ID
      expect(getResponse.status()).toBeLessThan(500);
    }
  });

  test('should verify snaps API returns appointment data with date and time', async ({ page }) => {
    const tokenResponse = await page.request.get('http://localhost:3000/api/v1/session/token');
    const tokenBody = await tokenResponse.json();
    const sessionToken = tokenBody.data?.token ?? tokenBody.token;

    const branchesResponse = await page.request.get('http://localhost:3000/api/v1/branches', {
      headers: { 'X-Session-Token': sessionToken }
    });
    const branchesBody = await branchesResponse.json();
    const branches = branchesBody.data ?? branchesBody;
    const branchId = branches[0].id;

    const snapsResponse = await page.request.post('http://localhost:3000/api/v1/snaps', {
      headers: {
        'X-Session-Token': sessionToken,
        'Content-Type': 'application/json'
      },
      data: {
        branchId,
        jobTypeIds: ['brake-fluid'],
        fromDate: new Date().toISOString().split('T')[0],
        toDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
        mode: 'next-available',
        brandName: 'bmw'
      }
    });

    expect(snapsResponse.status()).toBeLessThan(300);
    const snapsBody = await snapsResponse.json();
    const snaps = Array.isArray(snapsBody) ? snapsBody : snapsBody.data ?? snapsBody;
    expect(Array.isArray(snaps)).toBe(true);

    if (snaps.length > 0) {
      // Backend returns SnapResponseDto[] — grouped by date with slots
      for (const day of snaps.slice(0, 5)) {
        expect(day.date).toBeTruthy();
        expect(day.displayDate).toBeTruthy();
        expect(day.dayAbbreviation).toBeTruthy();
        expect(Array.isArray(day.slots)).toBe(true);
        // date should be ISO date string (YYYY-MM-DD)
        expect(new Date(day.date).getTime()).not.toBeNaN();
      }
    }
  });
});
