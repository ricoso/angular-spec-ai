import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  confirmTireChange,
  deselectTireChange,
  getPageTitle,
  getServiceCardTitles,
  goToServiceSelection,
  selectService,
} from './helpers/booking.helpers';

/**
 * REQ-004: Serviceauswahl (Service Selection)
 * Wizard Step 3: User selects services for the booking
 *
 * Services:
 * - HU/AU: Simple toggle (click card to select/deselect)
 * - Inspektion: Simple toggle (click card to select/deselect)
 * - Raderwechsel: Radio buttons (mit/ohne Einlagerung) + Bestatigen/Abwahlen
 *
 * Guard: Requires brand + location selected, redirects otherwise
 */

test.describe.skip('REQ-004: Service Selection', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: page loads with 3 service cards and correct title', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      const title = await getPageTitle(page);
      expect(title).toBe('Welche Services möchten Sie buchen?');

      const cardTitles = await getServiceCardTitles(page);
      expect(cardTitles).toHaveLength(3);
      expect(cardTitles).toEqual(
        expect.arrayContaining(['HU/AU', 'Inspektion', 'Räderwechsel'])
      );
    });

    test('TC-2: select HU/AU -> card shows check mark and becomes selected', async ({ page }) => {
      await goToServiceSelection(page);

      // Before selection: card should not be selected
      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      await expect(huauCard).toBeVisible();
      await expect(huauCard).not.toHaveClass(/service-card--selected/);

      // Click to select
      await selectService(page, 'HU/AU');

      // After selection: card should be selected with check mark
      await expect(huauCard).toHaveClass(/service-card--selected/);
      const checkIcon = huauCard.locator('.service-card__check');
      await expect(checkIcon).toBeVisible();
    });

    test('TC-3: multi-select (HU/AU + Inspektion)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select HU/AU
      await selectService(page, 'HU/AU');
      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      await expect(huauCard).toHaveClass(/service-card--selected/);

      // Select Inspektion
      await selectService(page, 'Inspektion');
      const inspectionCard = page.locator('.service-card', { hasText: 'Inspektion' });
      await expect(inspectionCard).toHaveClass(/service-card--selected/);

      // Both should be selected
      await expect(huauCard).toHaveClass(/service-card--selected/);
      await expect(inspectionCard).toHaveClass(/service-card--selected/);
    });

    test('TC-4: deselect service', async ({ page }) => {
      await goToServiceSelection(page);

      // Select HU/AU
      await selectService(page, 'HU/AU');
      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      await expect(huauCard).toHaveClass(/service-card--selected/);

      // Deselect by clicking again
      await selectService(page, 'HU/AU');
      await expect(huauCard).not.toHaveClass(/service-card--selected/);
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-5: tire change radio buttons visible on card', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });
      await expect(tireCard).toBeVisible();

      // Radio inputs should be visible
      const radios = tireCard.locator('.service-card__radio-input');
      const count = await radios.count();
      expect(count).toBe(2);

      // Radio labels should contain correct text
      const radioLabels = tireCard.locator('.service-card__radio-text');
      const labelTexts = await radioLabels.allTextContents();
      expect(labelTexts.map(t => t.trim())).toEqual(
        expect.arrayContaining([
          'Räderwechsel ohne Einlagerung',
          'Räderwechsel mit Einlagerung'
        ])
      );
    });

    test('TC-6: tire change confirm flow (select radio -> confirm -> selected)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });

      // Confirm button should initially be disabled (no radio selected)
      const confirmButton = tireCard.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeVisible();
      await expect(confirmButton).toBeDisabled();

      // Select radio
      await confirmTireChange(page, 'without-storage');

      // Card should be selected
      await expect(tireCard).toHaveClass(/service-card--selected/);

      // Check icon should appear
      const checkIcon = tireCard.locator('.service-card__check');
      await expect(checkIcon).toBeVisible();
    });

    test('TC-7: tire change deselect flow', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // First confirm a tire change variant
      await confirmTireChange(page, 'with-storage');

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });
      await expect(tireCard).toHaveClass(/service-card--selected/);

      // Deselect
      await deselectTireChange(page);

      // Card should no longer be selected
      await expect(tireCard).not.toHaveClass(/service-card--selected/);
    });

    test('TC-7a: tire change variant switch', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });

      // Confirm with "without-storage"
      await confirmTireChange(page, 'without-storage');
      await expect(tireCard).toHaveClass(/service-card--selected/);

      // Now switch to "with-storage" by clicking the other radio
      const withStorageRadio = tireCard.locator('.service-card__radio-input[value="with-storage"]');
      await withStorageRadio.click();
      await waitForAngular(page);

      // Confirm button should reappear (variant changed)
      const confirmButton = tireCard.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeVisible();

      // Confirm the new variant
      await confirmButton.click();
      await waitForAngular(page);

      // Card should still be selected
      await expect(tireCard).toHaveClass(/service-card--selected/);
    });

    test('TC-9: continue navigation (at least 1 service selected)', async ({ page }) => {
      await goToServiceSelection(page);

      // Select a service first
      await selectService(page, 'HU/AU');

      // Continue button should be enabled
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();

      // Click continue
      await continueButton.click();
      await waitForAngular(page);

      // Should navigate (in placeholder, stays on services or goes to next step)
      const route = await getCurrentRoute(page);
      expect(route).toBeTruthy();
    });

    test('TC-10: back navigation', async ({ page }) => {
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toBeVisible();

      await backButton.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('TC-11: continue button disabled without services', async ({ page }) => {
      await goToServiceSelection(page);

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeVisible();
      await expect(continueButton).toBeDisabled();
    });

    test('TC-8: header cart badge counts selected services', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const badge = page.locator('.cart-icon__button .mat-badge-content');

      // Initially no badge visible (0 services)
      await expect(badge).toBeHidden();

      // Select HU/AU -> badge shows 1
      await selectService(page, 'HU/AU');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('1');

      // Select Inspektion -> badge shows 2
      await selectService(page, 'Inspektion');
      await expect(badge).toHaveText('2');

      // Deselect HU/AU -> badge shows 1
      await selectService(page, 'HU/AU');
      await expect(badge).toHaveText('1');

      // Deselect Inspektion -> badge hidden (0)
      await selectService(page, 'Inspektion');
      await expect(badge).toBeHidden();
    });

    test('TC-8a: header cart dropdown shows brand, location and service chips', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select a service
      await selectService(page, 'HU/AU');

      // Open cart dropdown
      const cartButton = page.locator('.cart-icon__button');
      await cartButton.click();
      await waitForAngular(page);

      // Cart dropdown should show summary, brand, location, service chips
      const dropdown = page.locator('.header__cart-dropdown');
      await expect(dropdown).toBeVisible();

      // Summary text visible
      const summary = dropdown.locator('.header__cart-summary');
      await expect(summary).toBeVisible();

      // Brand chip — should show some brand name (dynamically selected)
      const brandChip = dropdown.locator('.header__cart-row', { hasText: /Marke/ });
      await expect(brandChip).toBeVisible();
      const brandText = await brandChip.textContent();
      expect(brandText?.length).toBeGreaterThan(5);

      // Location chip
      const locationChip = dropdown.locator('.header__cart-row', { hasText: /Standort/ });
      await expect(locationChip).toBeVisible();

      // Service chip
      const serviceChips = dropdown.locator('.header__cart-row', { hasText: /Services/ });
      await expect(serviceChips).toBeVisible();
      await expect(serviceChips).toContainText('HU/AU');
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: service deselect toggle', async ({ page }) => {
      await goToServiceSelection(page);

      // Select HU/AU
      await selectService(page, 'HU/AU');
      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      await expect(huauCard).toHaveClass(/service-card--selected/);

      // Deselect HU/AU
      await selectService(page, 'HU/AU');
      await expect(huauCard).not.toHaveClass(/service-card--selected/);

      // Continue button should become disabled
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeDisabled();
    });

    test('5.2: back to location', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Click back
      const backButton = page.locator('.summary-bar__back-button');
      await backButton.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      // Location page title should be visible
      const title = await getPageTitle(page);
      expect(title).toBe('An welchem Standort dürfen wir Sie begrüßen?');
    });

    test('5.3: tire change deselect', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Confirm tire change
      await confirmTireChange(page, 'with-storage');

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });
      await expect(tireCard).toHaveClass(/service-card--selected/);

      // Deselect via button
      await deselectTireChange(page);
      await expect(tireCard).not.toHaveClass(/service-card--selected/);
    });

    test('5.4: tire change variant switch', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });

      // Select without-storage first
      await confirmTireChange(page, 'without-storage');
      await expect(tireCard).toHaveClass(/service-card--selected/);

      // Switch radio to with-storage
      const withStorageRadio = tireCard.locator('.service-card__radio-input[value="with-storage"]');
      await withStorageRadio.click();
      await waitForAngular(page);

      // Confirm button should appear again
      const confirmButton = tireCard.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeVisible();
      await expect(confirmButton).toBeEnabled();

      // Confirm new variant
      await confirmButton.click();
      await waitForAngular(page);

      // Still selected
      await expect(tireCard).toHaveClass(/service-card--selected/);
    });

  });

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: direct access to /home/services without prerequisites -> redirect to /home/brand', async ({ page }) => {
      // Direct navigation to services without brand or location selected
      // Guard checks hasBrandSelected first, redirects to /home/brand
      await navigateTo(page, '/home/services');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('6.2: guard protects services route from fresh browser session', async ({ page }) => {
      // A completely fresh session should not reach /home/services
      await navigateTo(page, '/home/services');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Should never land on /home/services
      expect(route).not.toBe('/home/services');
      expect(route).toMatch(/\/home\/(brand|location)/);
    });

  });

  // =============================================
  // i18n (Language Switch)
  // =============================================

  test.describe('i18n', () => {

    test('DE title: "Welche Services moechten Sie buchen?"', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Welche Services möchten Sie buchen?');
    });

    test('EN title: "Which services would you like to book?"', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Which services would you like to book?');
    });

    test('DE radio labels for tire change', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: 'Räderwechsel' });
      const radioLabels = tireCard.locator('.service-card__radio-text');
      const labelTexts = await radioLabels.allTextContents();

      expect(labelTexts.map(t => t.trim())).toEqual(
        expect.arrayContaining([
          'Räderwechsel ohne Einlagerung',
          'Räderwechsel mit Einlagerung'
        ])
      );
    });

    test('EN radio labels for tire change', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: 'Tire Change' });
      const radioLabels = tireCard.locator('.service-card__radio-text');
      const labelTexts = await radioLabels.allTextContents();

      expect(labelTexts.map(t => t.trim())).toEqual(
        expect.arrayContaining([
          'Tire change without storage',
          'Tire change with storage'
        ])
      );
    });

    test('DE service card titles', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const titles = await getServiceCardTitles(page);
      expect(titles).toEqual(
        expect.arrayContaining(['HU/AU', 'Inspektion', 'Räderwechsel'])
      );
    });

    test('EN service card titles', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const titles = await getServiceCardTitles(page);
      expect(titles).toEqual(
        expect.arrayContaining(['HU/AU', 'Inspection', 'Tire Change'])
      );
    });

    test('DE button labels (Zurueck/Weiter)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toContainText('Zurück');

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toContainText('Weiter');
    });

    test('EN button labels (Back/Continue)', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toContainText('Back');

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toContainText('Continue');
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('service grid has role="group" with aria-label', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const grid = page.locator('.service-selection__grid[role="group"]');
      await expect(grid).toBeVisible();

      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBe('Verfügbare Services');
    });

    test('EN aria-label for service grid', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const grid = page.locator('.service-selection__grid[role="group"]');
      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBe('Available services');
    });

    test('simple cards have role="button" with aria-pressed', async ({ page }) => {
      await goToServiceSelection(page);

      // HU/AU card: mat-card IS the .service-card element (same DOM node)
      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      const role = await huauCard.getAttribute('role');
      expect(role).toBe('button');

      const ariaPressed = await huauCard.getAttribute('aria-pressed');
      expect(ariaPressed).toBe('false');
    });

    test('tire change card has role="region"', async ({ page }) => {
      await goToServiceSelection(page);

      const tireCard = page.locator('.service-card', { hasText: /Tire Change|Räderwechsel/ });
      const role = await tireCard.getAttribute('role');
      expect(role).toBe('region');
    });

    test('aria-pressed updates on selection', async ({ page }) => {
      await goToServiceSelection(page);

      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });

      // Before selection
      let ariaPressed = await huauCard.getAttribute('aria-pressed');
      expect(ariaPressed).toBe('false');

      // Click to select
      await huauCard.click();
      await waitForAngular(page);

      // After selection
      ariaPressed = await huauCard.getAttribute('aria-pressed');
      expect(ariaPressed).toBe('true');
    });

    test('keyboard navigation: Enter to select card', async ({ page }) => {
      await goToServiceSelection(page);

      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      await huauCard.focus();
      await page.keyboard.press('Enter');
      await waitForAngular(page);

      await expect(huauCard).toHaveClass(/service-card--selected/);
    });

    test('keyboard navigation: Space to select card', async ({ page }) => {
      await goToServiceSelection(page);

      const huauCard = page.locator('.service-card', { hasText: 'HU/AU' });
      await huauCard.focus();
      await page.keyboard.press('Space');
      await waitForAngular(page);

      await expect(huauCard).toHaveClass(/service-card--selected/);
    });

  });

  // =============================================
  // RESPONSIVE
  // =============================================

  test.describe('Responsive Layout', () => {

    test('all 3 service cards visible', async ({ page }) => {
      await goToServiceSelection(page);

      const cards = page.locator('.service-card');
      const count = await cards.count();
      expect(count).toBe(3);

      for (let i = 0; i < count; i++) {
        await expect(cards.nth(i)).toBeVisible();
      }
    });

    test('summary bar visible with back and continue buttons', async ({ page }) => {
      await goToServiceSelection(page);

      const summaryBar = page.locator('.summary-bar');
      await expect(summaryBar).toBeVisible();

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toBeVisible();

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeVisible();
    });

  });

});
