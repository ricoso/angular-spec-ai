/**
 * General-purpose Screenshot Script for E2E Documentation
 *
 * Auto-discovers all REQ-* folders under docs/requirements/ and creates
 * responsive screenshots (Desktop, Tablet, Mobile) for each.
 *
 * Usage:
 *   node playwright/take-screenshots.js                        # All REQs
 *   node playwright/take-screenshots.js REQ-002-Markenauswahl  # Single REQ
 *   node playwright/take-screenshots.js REQ-001-Header --all-langs  # All languages
 *
 * Adding a new feature:
 *   1. Add an entry to SCREENSHOT_CONFIG below
 *   2. Define route + optional setup function
 *   3. Run the script
 *
 * Prerequisites:
 *   - Dev server running on http://localhost:4200 (or started via npm start)
 *   - Playwright + Chromium installed (npx playwright install chromium)
 */

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

// Default to the Angular dev server, but allow pointing at any host that serves
// the app + /api (e.g. the Spring Boot backend on :3000, which serves the built
// SPA together with the API) via SCREENSHOT_BASE_URL.
const BASE_URL = process.env.SCREENSHOT_BASE_URL || 'http://localhost:4200';
const HASH = `${BASE_URL}/#`;
const DOCS = path.resolve(__dirname, '..', 'docs', 'requirements');

const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 720 },
  { name: 'tablet',  width: 768,  height: 1024 },
  { name: 'mobile',  width: 375,  height: 667 },
];

const ALL_LANGUAGES = ['de', 'en', 'uk', 'fr', 'ar'];

// ---------------------------------------------------------------------------
// Screenshot Config — one entry per REQ
//
// route:  the hash-route to screenshot
// setup:  optional async fn(page) to run BEFORE the final navigation
//         (e.g. select a brand so a guard lets you through)
//
// If a REQ folder exists but has no config entry it will be skipped
// with a warning so you know to add one.
// ---------------------------------------------------------------------------

const SCREENSHOT_CONFIG = {

  'REQ-001-Header': {
    route: '/home/brand',
  },

  'REQ-002-Markenauswahl': {
    route: '/home/brand',
  },

  'REQ-003-Standortwahl': {
    route: '/home/location',
    setup: async (page) => {
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-004-Serviceauswahl': {
    route: '/home/services',
    setup: async (page) => {
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-011-Serviceauswahl-Erweitert': {
    route: '/home/services',
    setup: async (page) => {
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-005-Hinweisfenster': {
    route: '/home/notes',
    setup: async (page) => {
      // Brand -> Location -> Services -> select HU/AU -> Continue to Notes
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Select brake-fluid service (direct toggle, no options)
      await page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click Continue button to navigate to notes
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-012-Hinweisseite-Erweitert': {
    route: '/home/notes',
    setup: async (page) => {
      // Brand -> Location -> Services -> Continue to Notes (same as REQ-005)
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-006-Terminauswahl': {
    route: '/home/appointment',
    setup: async (page) => {
      // Brand -> Location -> Services -> select HU/AU -> Continue to Notes -> Continue to Appointment
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Select brake-fluid service (direct toggle, no options)
      await page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click Continue on services -> notes
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Click Continue on notes -> appointment
      await page.locator('.notes__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-008-Werkstattkalender': {
    route: '/home/workshop-calendar',
    setup: async (page) => {
      // Brand -> Location -> Services -> select HU/AU -> Continue to Notes -> Continue to Appointment -> Calendar Link
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Select brake-fluid service (direct toggle, no options)
      await page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click Continue on services -> notes
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Click Continue on notes -> appointment
      await page.locator('.notes__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Click calendar link -> workshop calendar
      await page.locator('.appointment-selection__calendar-link').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Open datepicker and select today to show time slots
      const toggle = page.locator('mat-datepicker-toggle button');
      await toggle.click();
      await page.waitForTimeout(500);
      const todayCell = page.locator('.mat-calendar-body-today');
      await todayCell.click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-009-carinformation': {
    route: '/home/carinformation',
    setup: async (page) => {
      // Brand -> Location -> Services -> select HU/AU -> Continue to Notes -> Continue to Appointment
      // -> Select first appointment card -> Continue to Car Information
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Select brake-fluid service (direct toggle, no options)
      await page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click Continue on services -> notes
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Click Continue on notes -> appointment
      await page.locator('.notes__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Select the first appointment card
      await page.locator('.appointment-card').first().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click Continue on appointment -> carinformation
      await page.locator('.appointment-selection__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-007-WizardStateSync': {
    route: '/home/services',
    setup: async (page) => {
      // REQ-007 is a cross-cutting requirement with no own UI.
      // Screenshot shows the services page after back-navigation from notes:
      // Brand -> Location -> Services -> select HU/AU -> Continue to Notes -> Back to Services
      // This demonstrates the back-navigation that clears bookingNote and returns to services.
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Select brake-fluid service (direct toggle, no options)
      await page.locator('.service-card', { hasText: 'Wechsel Bremsflüssigkeit' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click Continue on services -> notes
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // Click Back on notes -> services (demonstrates REQ-007 back navigation)
      await page.locator('.notes__back-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-010-Buchungsübersicht': {
    route: '/home/booking-overview',
    setup: async (page) => {
      // Full wizard: Brand -> Location -> Services -> Notes -> Appointment -> Car Info -> Booking Overview
      await page.goto(`${HASH}/home/brand`);
      await page.waitForLoadState('networkidle');
      await page.locator('button', { hasText: 'Audi' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('button', { hasText: 'München' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('.service-card', { hasText: 'HU/AU' }).click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('.summary-bar__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      await page.locator('.notes__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      await page.locator('.appointment-card').first().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('.appointment-selection__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
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
      await page.locator('input[formcontrolname="vin"]').fill('WDB8XXXXXXA123456');
      // Accept privacy consent
      const checkbox = page.locator('mat-checkbox .mdc-checkbox__native-control, mat-checkbox input[type="checkbox"]').first();
      await checkbox.click({ force: true });
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      // Click continue to booking overview
      await page.locator('.carinformation__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
    },
  },

  'REQ-028-Buchungsbestaetigung': {
    route: '/home/booking-overview',
    // Full wizard up to booking-overview, then SUBMIT to render the confirmation.
    // The final state-mutating POST /api/v1/appointments is stubbed (deterministic,
    // no real INT bookings) — everything else (branches/snaps) runs against the
    // real backend, mirroring the green E2E spec.
    setup: async (page) => {
      // Stub only the final appointment write.
      await page.route('**/api/v1/appointments', async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              data: { id: 'screenshot-req028-confirmation', status: 'confirmed' },
              meta: { timestamp: '2026-06-19T00:00:00Z', requestId: 'screenshot-req028' },
            }),
          });
          return;
        }
        await route.continue();
      });

      // REQ-013 wizard order: Location (Step 1) -> Brand (Step 2) -> Services -> Notes -> Appointment -> Car Info.
      await page.goto(`${HASH}/home/location`);
      await page.waitForLoadState('networkidle');
      await page.locator('.location-card').first().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      if (!page.url().includes('/home/brand')) {
        await page.goto(`${HASH}/home/brand`);
        await page.waitForLoadState('networkidle');
      }
      await page.locator('.brand-card').first().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);

      // Services (REQ-026): category carousel + detail panel with operation checkboxes.
      // Prefer "Inspektion" (reliably bookable on INT); fall back to any operation.
      await page.locator('app-service-card').first().waitFor({ state: 'visible', timeout: 15000 });
      const categoryCards = page.locator('app-service-card');
      const cardCount = await categoryCards.count();
      const preferred = page
        .locator('.service-option', { hasText: /inspektion|inspection/i })
        .locator('input[type="checkbox"]')
        .first();
      const anyOption = page.locator('.service-option input[type="checkbox"]').first();
      let operationSelected = false;
      for (let pass = 0; pass < 2 && !operationSelected; pass++) {
        const target = pass === 0 ? preferred : anyOption;
        for (let i = -1; i < cardCount && !operationSelected; i++) {
          if (i >= 0) {
            await categoryCards.nth(i).click();
            await page.waitForTimeout(200);
          }
          if (await target.isVisible().catch(() => false)) {
            if (!(await target.isChecked())) {
              await target.check({ force: true });
              await page.waitForTimeout(200);
            }
            operationSelected = true;
          }
        }
      }
      // Continue to notes via the services bottom bar.
      const servicesContinue = page.locator('.services-bottom-bar .btn-primary');
      await servicesContinue.click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await page.waitForTimeout(300);

      // Notes -> continue.
      await page.locator('.notes__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      // Appointment (REQ-026): pick a date tile, then the first available slot.
      const dateTiles = page.locator('.date-tile');
      const tileCount = await dateTiles.count();
      const slots = page.locator('app-appointment-card .slot');
      for (let i = 0; i < tileCount; i++) {
        await dateTiles.nth(i).click();
        await page.waitForTimeout(300);
        if (await slots.first().isVisible().catch(() => false)) {
          break;
        }
      }
      await slots.first().click();
      await page.waitForTimeout(300);
      await page.locator('.appointment-selection__continue-button').click();
      await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
      await page.waitForTimeout(500);

      // Car information forms.
      await page.locator('input[formcontrolname="email"]').fill('max@mustermann.de');
      await page.locator('input[formcontrolname="firstName"]').fill('Max');
      await page.locator('input[formcontrolname="lastName"]').fill('Mustermann');
      await page.locator('input[formcontrolname="street"]').fill('Musterweg 1');
      await page.locator('input[formcontrolname="postalCode"]').fill('30159');
      await page.locator('input[formcontrolname="city"]').fill('Berlin');
      await page.locator('input[formcontrolname="mobilePhone"]').fill('017012345678');
      await page.locator('input[formcontrolname="licensePlate"]').fill('B-MS1234');
      await page.locator('input[formcontrolname="mileage"]').fill('5000');
      await page.locator('#privacy-consent').check({ force: true });
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      await page.locator('.carinformation__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      // On the booking-overview page now: SUBMIT to reveal the confirmation.
      await page.locator('.booking-overview__submit-button').click();
      // Wait for the confirmation to appear (mocked POST resolves instantly).
      await page.locator('.booking-overview__confirmation').waitFor({ state: 'visible', timeout: 15000 });
      await page.waitForTimeout(500);
    },
  },

  'REQ-031-Booking-Bugfixes': {
    route: '/home/carinformation',
    // REQ-031 Bugfix 2 (mileage validation): screenshot the vehicle-data form on
    // /home/carinformation with the mileage field (#vehicle-mileage) and its
    // permanent hint "Bitte Werte ab 10 km eingeben". The snap-conflict banner
    // (Bugfix 1) only appears on a real HTTP 410 and cannot be surfaced without a
    // mock here, so the responsive doc screenshots focus on the mileage feature.
    // Runs the full wizard against the real backend (no POST is triggered).
    setup: async (page) => {
      // REQ-013 wizard order: Location (Step 1) -> Brand (Step 2) -> Services -> Notes -> Appointment -> Car Info.
      await page.goto(`${HASH}/home/location`);
      await page.waitForLoadState('networkidle');
      await page.locator('.location-card').first().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);
      if (!page.url().includes('/home/brand')) {
        await page.goto(`${HASH}/home/brand`);
        await page.waitForLoadState('networkidle');
      }
      await page.locator('.brand-card').first().click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);

      // Services (REQ-026): category carousel + detail panel with operation checkboxes.
      // Prefer "Inspektion" (reliably bookable on INT); fall back to any operation.
      await page.locator('app-service-card').first().waitFor({ state: 'visible', timeout: 15000 });
      const categoryCards = page.locator('app-service-card');
      const cardCount = await categoryCards.count();
      const preferred = page
        .locator('.service-option', { hasText: /inspektion|inspection/i })
        .locator('input[type="checkbox"]')
        .first();
      const anyOption = page.locator('.service-option input[type="checkbox"]').first();
      let operationSelected = false;
      for (let pass = 0; pass < 2 && !operationSelected; pass++) {
        const target = pass === 0 ? preferred : anyOption;
        for (let i = -1; i < cardCount && !operationSelected; i++) {
          if (i >= 0) {
            await categoryCards.nth(i).click();
            await page.waitForTimeout(200);
          }
          if (await target.isVisible().catch(() => false)) {
            if (!(await target.isChecked())) {
              await target.check({ force: true });
              await page.waitForTimeout(200);
            }
            operationSelected = true;
          }
        }
      }
      // Continue to notes via the services bottom bar.
      await page.locator('.services-bottom-bar .btn-primary').click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await page.waitForTimeout(300);

      // Notes -> continue.
      await page.locator('.notes__continue-button').click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      // Appointment (REQ-026): pick a date tile, then the first available slot.
      const dateTiles = page.locator('.date-tile');
      const tileCount = await dateTiles.count();
      const slots = page.locator('app-appointment-card .slot');
      for (let i = 0; i < tileCount; i++) {
        await dateTiles.nth(i).click();
        await page.waitForTimeout(300);
        if (await slots.first().isVisible().catch(() => false)) {
          break;
        }
      }
      await slots.first().click();
      await page.waitForTimeout(300);
      await page.locator('.appointment-selection__continue-button').click();
      await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
      await page.waitForTimeout(500);

      // Fill the vehicle form so the mileage field (>= 10) and its permanent hint
      // are both visible in the screenshot. No submit — this is the feature state.
      await page.locator('input[formcontrolname="licensePlate"]').fill('E-RQ0031');
      await page.locator('#vehicle-mileage').fill('54321');
      await page.locator('#vehicle-mileage-hint').waitFor({ state: 'visible', timeout: 5000 });
      await page.waitForTimeout(300);
    },
  },

  'REQ-039-Responsive-Wizard': {
    route: '/home/notes',
    // REQ-039: responsive wizard breadcrumb (compact on mobile, full on desktop) plus
    // Abschnitt 18 — the rental-car switch on the notes page, switched ON so the
    // optional category wish ("Keine Präferenz" preselected) is visible.
    // The app uses path routing (not hash) — therefore `${BASE_URL}` instead of `${HASH}`.
    // Runs against the real backend; no booking is submitted.
    setup: async (page) => {
      await page.goto(`${BASE_URL}/home/location`);
      await page.waitForLoadState('networkidle');
      const locationCard = page.locator('.location-card').first();
      await locationCard.waitFor({ state: 'visible', timeout: 15000 });
      await locationCard.click();
      // In-app navigation only: a goto('/home/brand') would reload the app, drop the
      // store and let the guard bounce back to /home/location.
      try {
        await page.waitForURL(/\/home\/brand/, { timeout: 10000 });
      } catch {
        await locationCard.click();
        await page.waitForURL(/\/home\/brand/, { timeout: 15000 });
      }
      await page.waitForLoadState('networkidle');
      await page.locator('.brand-card').first().waitFor({ state: 'visible', timeout: 15000 });
      await page.locator('.brand-card').first().click();
      await page.waitForLoadState('networkidle');

      // Services (REQ-026): prefer "Inspektion", fall back to any operation.
      await page.locator('app-service-card').first().waitFor({ state: 'visible', timeout: 15000 });
      const categoryCards = page.locator('app-service-card');
      const cardCount = await categoryCards.count();
      const preferred = page
        .locator('.service-option', { hasText: /inspektion|inspection/i })
        .locator('input[type="checkbox"]')
        .first();
      const anyOption = page.locator('.service-option input[type="checkbox"]').first();
      let operationSelected = false;
      for (let pass = 0; pass < 2 && !operationSelected; pass++) {
        const target = pass === 0 ? preferred : anyOption;
        for (let i = -1; i < cardCount && !operationSelected; i++) {
          if (i >= 0) {
            await categoryCards.nth(i).click();
            await page.waitForTimeout(200);
          }
          if (await target.isVisible().catch(() => false)) {
            if (!(await target.isChecked())) {
              await target.check({ force: true });
              await page.waitForTimeout(200);
            }
            operationSelected = true;
          }
        }
      }
      await page.locator('.services-bottom-bar .btn-primary').click();
      await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(300);

      // Abschnitt 18: switch the rental car on (only rendered when the branch offers categories).
      const rentSwitch = page.locator('#rentSwitch');
      if (await rentSwitch.count() > 0) {
        // REQ-039: switch = visually hidden input + label (mockup 03) — click the label like a user.
        await page.locator('label[for="rentSwitch"]').click();
        await page.locator('label[for="rentClass-none"]').waitFor({ state: 'visible', timeout: 5000 });
      } else {
        console.warn('    ⚠️  #rentSwitch not rendered — branch offers no rent categories');
      }
      await page.waitForTimeout(300);
    },
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function setLanguage(page, lang = 'de') {
  const url = page.url();
  if (!url || url === 'about:blank') {
    await page.goto(`${HASH}/home/brand`);
    await page.waitForLoadState('networkidle');
  }
  await page.evaluate((l) => localStorage.setItem('app-language', l), lang);
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(300);
}

function discoverReqs() {
  if (!fs.existsSync(DOCS)) return [];
  return fs.readdirSync(DOCS).filter((d) =>
    d.startsWith('REQ-') && fs.statSync(path.join(DOCS, d)).isDirectory()
  );
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

(async () => {
  const args = process.argv.slice(2);
  const allLangs = args.includes('--all-langs');
  const filter = args.find((a) => !a.startsWith('--')) || null;
  const allReqs = discoverReqs();
  const reqs = filter ? allReqs.filter((r) => r === filter) : allReqs;

  if (reqs.length === 0) {
    console.error(filter
      ? `REQ "${filter}" not found. Available: ${allReqs.join(', ')}`
      : 'No REQ-* folders found under docs/requirements/');
    process.exit(1);
  }

  // Check dev server
  try {
    const res = await fetch(BASE_URL);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
  } catch {
    console.error(`Dev server not reachable at ${BASE_URL}. Start with: npm start`);
    process.exit(1);
  }

  console.log(`Taking screenshots for ${reqs.length} REQ(s)...\n`);

  const browser = await chromium.launch();
  let total = 0;
  const skipped = [];

  for (const reqId of reqs) {
    const config = SCREENSHOT_CONFIG[reqId];
    if (!config) {
      skipped.push(reqId);
      console.log(`  ⏭️  ${reqId} — no config entry, skipping`);
      continue;
    }

    const dir = path.join(DOCS, reqId, 'screenshots');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

    const languages = allLangs ? ALL_LANGUAGES : ['de'];

    for (const lang of languages) {
      const langSuffix = languages.length > 1 ? `-${lang}` : '';
      if (languages.length > 1) {
        console.log(`  🌍 Language: ${lang}`);
      }

      for (const vp of VIEWPORTS) {
        const ctx = await browser.newContext({
          viewport: { width: vp.width, height: vp.height },
        });
        const page = await ctx.newPage();

        await setLanguage(page, lang);

        if (config.setup) {
          await config.setup(page);
        } else {
          await page.goto(`${HASH}${config.route}`);
          await page.waitForLoadState('networkidle');
          await page.waitForTimeout(300);
        }

        const fileName = `e2e-responsive-${vp.name}${langSuffix}.png`;
        const file = path.join(dir, fileName);
        await page.screenshot({ path: file, fullPage: true });
        console.log(`  ✅ ${reqId}/screenshots/${fileName}`);
        total++;

        await ctx.close();
      }
    }
  }

  await browser.close();

  console.log(`\nDone — ${total} screenshots saved.`);
  if (skipped.length) {
    console.log(`Skipped (no config): ${skipped.join(', ')}`);
    console.log('Add entries to SCREENSHOT_CONFIG in playwright/take-screenshots.js');
  }
})();
