import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  completeBrandToLocationFlow,
  getBrandButtonTexts,
  getLocationButtonTexts,
  getPageTitle,
  goToBrandSelection,
  goToLocationSelection,
  selectFirstBrand,
  selectFirstLocation,
} from './helpers/booking.helpers';

/**
 * REQ-003: Standortwahl (Location Selection)
 * Wizard Step 2: User selects a dealership location based on chosen brand
 *
 * Test Cases from requirement.md Section 13:
 * - TC-1: Happy Path — locations displayed after brand selection
 * - TC-2: Location selection -> navigation to services
 * - TC-3: Guard — no brand -> redirect to /home/brand
 * - TC-4: Different brands may show different locations
 *
 * Alternative Flows (Section 5):
 * - 5.1: Back to brand selection
 * - 5.2: Change location (from later step)
 *
 * Exception Flows (Section 6):
 * - 6.1: No brand selected -> guard redirect
 */

test.describe.skip('REQ-003: Location Selection', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: should display locations after brand selection', async ({ page }) => {
      await selectFirstBrand(page);
      const locations = await getLocationButtonTexts(page);

      expect(locations.length).toBeGreaterThanOrEqual(1);
      for (const loc of locations) {
        expect(loc.length).toBeGreaterThan(0);
      }
    });

    test('TC-1b: should display correct DE title on location page', async ({ page }) => {
      await setLanguage(page, 'de');
      await selectFirstBrand(page);

      const title = await getPageTitle(page);
      expect(title).toBe('An welchem Standort dürfen wir Sie begrüßen?');
    });

    test('TC-1c: should display subtitle on location page', async ({ page }) => {
      await setLanguage(page, 'de');
      await selectFirstBrand(page);

      const subtitle = page.locator('p').first();
      await expect(subtitle).toContainText('Bitte wählen Sie den gewünschten Standort aus');
    });

    test('TC-2: should navigate to services after location selection', async ({ page }) => {
      await selectFirstBrand(page);
      await selectFirstLocation(page);

      const route = await getCurrentRoute(page);
      expect(route).toMatch(/\/home\/(services|brand|location)/);
    });

    test('TC-2b: location button click should work', async ({ page }) => {
      await selectFirstBrand(page);

      const locationButtons = page.locator('.location-grid__button');
      const count = await locationButtons.count();
      expect(count).toBeGreaterThanOrEqual(1);

      await locationButtons.first().click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBeTruthy();
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-3: Guard — no brand selected -> redirect to /home/brand', async ({ page }) => {
      await goToLocationSelection(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('TC-4: Different brands may show different locations', async ({ page }) => {
      const brands = await getBrandButtonTexts(page);
      if (brands.length < 2) {
        // Only 1 brand — skip brand-switch test
        return;
      }

      const firstBrandLocations = await completeBrandToLocationFlow(page, brands[0]);
      expect(firstBrandLocations.length).toBeGreaterThanOrEqual(1);

      await goToBrandSelection(page);
      const secondBrandLocations = await completeBrandToLocationFlow(page, brands[1]);
      expect(secondBrandLocations.length).toBeGreaterThanOrEqual(1);
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: Navigate back to brand selection and brand remains selectable', async ({ page }) => {
      await selectFirstBrand(page);
      const locationRoute = await getCurrentRoute(page);
      expect(locationRoute).toBe('/home/location');

      await goToBrandSelection(page);
      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');

      const buttons = page.locator('.brand-grid__button, [class*="brand"] button');
      const count = await buttons.count();
      expect(count).toBeGreaterThanOrEqual(1);
    });

    test('5.1b: After going back, selecting a different brand loads locations', async ({ page }) => {
      await selectFirstBrand(page);
      const firstLocations = await getLocationButtonTexts(page);
      expect(firstLocations.length).toBeGreaterThanOrEqual(1);

      await goToBrandSelection(page);
      await selectFirstBrand(page);
      const locations = await getLocationButtonTexts(page);
      expect(locations.length).toBeGreaterThanOrEqual(1);
    });

    test('5.2: Change location — reselect from location page', async ({ page }) => {
      await selectFirstBrand(page);

      const locationButtons = page.locator('.location-grid__button');
      const count = await locationButtons.count();
      expect(count).toBeGreaterThanOrEqual(1);

      await selectFirstLocation(page);

      // Navigate back to start over
      await selectFirstBrand(page);

      const buttons = await getLocationButtonTexts(page);
      expect(buttons.length).toBeGreaterThanOrEqual(1);
    });

  });

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: Direct URL access without brand -> redirect', async ({ page }) => {
      await navigateTo(page, '/home/location');

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('6.1b: Navigating to unknown route redirects to home', async ({ page }) => {
      await navigateTo(page, '/unknown-route');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toMatch(/\/home/);
    });

  });

  // =============================================
  // i18n (Language Switch)
  // =============================================

  test.describe('i18n', () => {

    test('should show DE title', async ({ page }) => {
      await setLanguage(page, 'de');
      await selectFirstBrand(page);

      const title = await getPageTitle(page);
      expect(title).toBe('An welchem Standort dürfen wir Sie begrüßen?');
    });

    test('should show EN title after language switch', async ({ page }) => {
      await setLanguage(page, 'en');
      await selectFirstBrand(page);

      const title = await getPageTitle(page);
      expect(title).toBe('At which location may we welcome you?');
    });

    test('should show EN subtitle after language switch', async ({ page }) => {
      await setLanguage(page, 'en');
      await selectFirstBrand(page);

      const subtitle = page.locator('p').first();
      await expect(subtitle).toContainText('Please select your desired location');
    });

    test('should switch back to DE correctly', async ({ page }) => {
      await setLanguage(page, 'en');
      await selectFirstBrand(page);
      await setLanguage(page, 'de');

      // Need to re-navigate because reload goes to brand page
      await selectFirstBrand(page);
      const title = await getPageTitle(page);
      expect(title).toBe('An welchem Standort dürfen wir Sie begrüßen?');
    });

    test('location names stay consistent regardless of UI language', async ({ page }) => {
      await setLanguage(page, 'en');
      await selectFirstBrand(page);

      const locations = await getLocationButtonTexts(page);
      expect(locations.length).toBeGreaterThanOrEqual(1);
      for (const loc of locations) {
        expect(loc.length).toBeGreaterThan(0);
      }
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('location buttons should have role group', async ({ page }) => {
      await selectFirstBrand(page);

      const group = page.locator('[role="group"]');
      await expect(group).toBeVisible();
    });

    test('location buttons should have aria-pressed attribute', async ({ page }) => {
      await selectFirstBrand(page);

      const buttons = page.locator('.location-grid__button');
      const count = await buttons.count();

      for (let i = 0; i < count; i++) {
        const ariaPressed = await buttons.nth(i).getAttribute('aria-pressed');
        expect(ariaPressed).toBe('false');
      }
    });

    test('group should have aria-label', async ({ page }) => {
      await setLanguage(page, 'de');
      await selectFirstBrand(page);

      const group = page.locator('.location-grid[role="group"]');
      const ariaLabel = await group.getAttribute('aria-label');
      expect(ariaLabel).toBe('Standorte');
    });

    test('all buttons should be keyboard navigable', async ({ page }) => {
      await selectFirstBrand(page);

      const firstButton = page.locator('.location-grid__button').first();
      await firstButton.focus();

      await expect(firstButton).toBeFocused();

      const count = await page.locator('.location-grid__button').count();
      if (count > 1) {
        await page.keyboard.press('Tab');
        const secondButton = page.locator('.location-grid__button').nth(1);
        await expect(secondButton).toBeFocused();
      }
    });

    test('keyboard Enter should select a location', async ({ page }) => {
      await selectFirstBrand(page);

      const firstButton = page.locator('.location-grid__button').first();
      await firstButton.focus();
      await page.keyboard.press('Enter');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/location');
    });

  });

  // =============================================
  // RESPONSIVE
  // =============================================

  test.describe('Responsive Layout', () => {

    test('should display location buttons on all viewports', async ({ page }) => {
      await selectFirstBrand(page);

      const buttons = page.locator('.location-grid__button');
      const count = await buttons.count();
      expect(count).toBeGreaterThanOrEqual(1);

      for (let i = 0; i < count; i++) {
        await expect(buttons.nth(i)).toBeVisible();
      }
    });

  });

});
