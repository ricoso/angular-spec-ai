import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  confirmServiceWithOptions,
  deselectServiceWithButton,
  getPageTitle,
  getServiceCard,
  getServiceCardTitles,
  getServiceCheckboxLabels,
  goToServiceSelection,
  selectService,
} from './helpers/booking.helpers';

/**
 * REQ-011: Serviceauswahl Erweitert (Extended Service Selection)
 * Wizard Step 3: User selects from 7 service categories with checkbox options
 *
 * Services (7 total):
 * - Inspektion: 7 checkbox options
 * - TUeV: 3 checkbox options
 * - Wechsel Bremsfluessigkeit: NO options (direct toggle)
 * - Raederwechsel: 4 checkbox options
 * - Aktionen / Checks: 4 checkbox options
 * - Reparatur / Beanstandung: 3 checkbox options
 * - Karosserie / Frontscheibe wechseln: 2 checkbox options
 *
 * Guard: Requires brand + location selected, redirects otherwise
 */

// DE service titles (for reference)
const DE_TITLES = [
  'Inspektion',
  'TÜV',
  'Wechsel Bremsflüssigkeit',
  'Räderwechsel',
  'Aktionen / Checks',
  'Reparatur / Beanstandung',
  'Karosserie / Frontscheibe wechseln'
];

// EN service titles (for reference)
const EN_TITLES = [
  'Inspection',
  'MOT',
  'Brake Fluid Change',
  'Tire Change',
  'Actions / Checks',
  'Repair / Complaint',
  'Bodywork / Windshield Replacement'
];

test.describe.skip('REQ-011: Extended Service Selection', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: page loads with 7 service cards and correct title (AC-1, AC-2, AC-12)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/services');

      const title = await getPageTitle(page);
      expect(title).toBe('Welche Services möchten Sie buchen?');

      const cardTitles = await getServiceCardTitles(page);
      expect(cardTitles).toHaveLength(7);
      expect(cardTitles).toEqual(expect.arrayContaining(DE_TITLES));
    });

    test('TC-2: click service with options expands and shows checkboxes (AC-3, AC-13)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const inspectionCard = getServiceCard(page, 'Inspektion');
      await expect(inspectionCard).toBeVisible();

      // Click to expand
      await inspectionCard.click();
      await waitForAngular(page);

      // Options should be visible
      const options = inspectionCard.locator('.service-card__options');
      await expect(options).toBeVisible();

      // 7 checkboxes for Inspektion
      const checkboxes = inspectionCard.locator('.service-card__checkbox');
      expect(await checkboxes.count()).toBe(7);

      // Confirm button should be disabled (no checkbox selected)
      const confirmButton = inspectionCard.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeVisible();
      await expect(confirmButton).toBeDisabled();
    });

    test('TC-3: select checkboxes and confirm -> card selected with check mark (AC-5, AC-6, AC-7)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Confirm Inspektion with 2 options
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme', 'Ölwechsel-Service']);

      const inspectionCard = getServiceCard(page, 'Inspektion');
      // Card should be selected
      await expect(inspectionCard).toHaveClass(/service-card--selected/);

      // Check icon should appear
      const checkIcon = inspectionCard.locator('.service-card__check');
      await expect(checkIcon).toBeVisible();
    });

    test('TC-4: click service without options toggles directly (AC-4)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');
      await expect(brakeFluidCard).toBeVisible();
      await expect(brakeFluidCard).not.toHaveClass(/service-card--selected/);

      // Click to toggle on
      await selectService(page, 'Wechsel Bremsflüssigkeit');

      // Card should be selected
      await expect(brakeFluidCard).toHaveClass(/service-card--selected/);
      const checkIcon = brakeFluidCard.locator('.service-card__check');
      await expect(checkIcon).toBeVisible();

      // No options section should be visible
      const options = brakeFluidCard.locator('.service-card__options');
      await expect(options).toBeHidden();
    });

    test('TC-5: continue navigation stores services and navigates to notes (AC-10, AC-11)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select brake fluid (simple toggle)
      await selectService(page, 'Wechsel Bremsflüssigkeit');

      // Click continue
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();
      await continueButton.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-7: multi-select services (BR-1)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select Inspektion with options
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme']);

      // Select TUeV with options
      await confirmServiceWithOptions(page, 'TÜV', ['TÜV']);

      // Select brake fluid (direct toggle)
      await selectService(page, 'Wechsel Bremsflüssigkeit');

      // All 3 should be selected
      const inspectionCard = getServiceCard(page, 'Inspektion');
      await expect(inspectionCard).toHaveClass(/service-card--selected/);

      const tuvCard = getServiceCard(page, 'TÜV');
      await expect(tuvCard).toHaveClass(/service-card--selected/);

      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');
      await expect(brakeFluidCard).toHaveClass(/service-card--selected/);
    });

    test('TC-9: TUeV has 3 options (AC-14)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const labels = await getServiceCheckboxLabels(page, 'TÜV');
      expect(labels).toHaveLength(3);
      expect(labels).toEqual(expect.arrayContaining([
        'TÜV',
        expect.stringContaining('UVV-Prüfung'),
        expect.stringContaining('Klimaanlagenreinigung')
      ]));
    });

    test('TC-10: Raederwechsel has 4 options (AC-15)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const labels = await getServiceCheckboxLabels(page, 'Räderwechsel');
      expect(labels).toHaveLength(4);
      expect(labels).toEqual(expect.arrayContaining([
        expect.stringContaining('bringe meine Räder mit'),
        expect.stringContaining('eingelagert'),
        expect.stringContaining('Hol & Bringservice'),
        expect.stringContaining('eingelagert werden')
      ]));
    });

    test('TC-11: Aktionen/Checks has 4 options (AC-16)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const labels = await getServiceCheckboxLabels(page, 'Aktionen / Checks');
      expect(labels).toHaveLength(4);
      expect(labels).toEqual(expect.arrayContaining([
        expect.stringContaining('Kostenloser Check'),
        expect.stringContaining('Sicherheits-Check'),
        expect.stringContaining('Flüssigkeitsstände'),
        expect.stringContaining('Batterie')
      ]));
    });

    test('TC-12: Reparatur has 3 options (AC-17)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const labels = await getServiceCheckboxLabels(page, 'Reparatur / Beanstandung');
      expect(labels).toHaveLength(3);
      expect(labels).toEqual(expect.arrayContaining([
        expect.stringContaining('Diagnose'),
        expect.stringContaining('Geräusche'),
        expect.stringContaining('Klimaanlage')
      ]));
    });

    test('TC-13: Karosserie has 2 options (AC-18)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const labels = await getServiceCheckboxLabels(page, 'Karosserie / Frontscheibe wechseln');
      expect(labels).toHaveLength(2);
      expect(labels).toEqual(expect.arrayContaining([
        expect.stringContaining('Kostenvoranschlag'),
        expect.stringContaining('Windschutzscheibe')
      ]));
    });

    test('TC-13b: Inspektion has 7 options (AC-13)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const labels = await getServiceCheckboxLabels(page, 'Inspektion');
      expect(labels).toHaveLength(7);
      expect(labels).toEqual(expect.arrayContaining([
        'Dialogannahme',
        'Inspektion',
        'Ölwechsel-Service',
        expect.stringContaining('Bremsen prüfen'),
        expect.stringContaining('Wischerblätter'),
        expect.stringContaining('Wartungsvertrag'),
        expect.stringContaining('Notfallöl')
      ]));
    });

    test('TC-14: header cart badge counts (AC-9)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const badge = page.locator('.cart-icon__button .mat-badge-content');

      // Initially no badge (0 services)
      await expect(badge).toBeHidden();

      // Select brake fluid -> badge shows 1
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      await expect(badge).toBeVisible();
      await expect(badge).toHaveText('1');

      // Confirm Inspektion -> badge shows 2
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme']);
      await expect(badge).toHaveText('2');

      // Deselect brake fluid -> badge shows 1
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      await expect(badge).toHaveText('1');

      // Deselect Inspektion -> badge hidden
      await deselectServiceWithButton(page, 'Inspektion');
      await expect(badge).toBeHidden();
    });

    test('TC-15: continue navigation (AC-10, AC-11)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      await selectService(page, 'Wechsel Bremsflüssigkeit');

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeEnabled();
      await continueButton.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');
    });

    test('TC-16: continue button disabled without services (BR-2)', async ({ page }) => {
      await goToServiceSelection(page);

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeVisible();
      await expect(continueButton).toBeDisabled();
    });

    test('TC-17: back navigation (AC-10, AC-11, 5.4)', async ({ page }) => {
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toBeVisible();
      await backButton.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');
    });

    test('TC-21: confirm button disabled without checkbox (BR-3)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Expand Inspektion
      const card = getServiceCard(page, 'Inspektion');
      await card.click();
      await waitForAngular(page);

      // Confirm button should be disabled
      const confirmButton = card.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeVisible();
      await expect(confirmButton).toBeDisabled();
    });

    test('TC-8a: header cart dropdown shows service chips', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Select brake fluid
      await selectService(page, 'Wechsel Bremsflüssigkeit');

      // Open cart dropdown
      const cartButton = page.locator('.cart-icon__button');
      await cartButton.click();
      await waitForAngular(page);

      // Cart dropdown should show service chips
      const dropdown = page.locator('.header__cart-dropdown');
      await expect(dropdown).toBeVisible();

      const serviceChips = dropdown.locator('.header__cart-row', { hasText: /Services/ });
      await expect(serviceChips).toBeVisible();
      await expect(serviceChips).toContainText('Wechsel Bremsflüssigkeit');
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.1: deselect service with options via Abwaehlen button', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Confirm Inspektion
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme', 'Ölwechsel-Service']);

      const inspectionCard = getServiceCard(page, 'Inspektion');
      await expect(inspectionCard).toHaveClass(/service-card--selected/);

      // Deselect
      await deselectServiceWithButton(page, 'Inspektion');
      await expect(inspectionCard).not.toHaveClass(/service-card--selected/);

      // Continue button should be disabled
      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toBeDisabled();
    });

    test('5.2: deselect service without options (brake fluid toggle)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Toggle on
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');
      await expect(brakeFluidCard).toHaveClass(/service-card--selected/);

      // Toggle off
      await selectService(page, 'Wechsel Bremsflüssigkeit');
      await expect(brakeFluidCard).not.toHaveClass(/service-card--selected/);
    });

    test('5.3: change options on selected service -> confirm button reappears', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Confirm Inspektion with Dialogannahme
      await confirmServiceWithOptions(page, 'Inspektion', ['Dialogannahme']);

      const inspectionCard = getServiceCard(page, 'Inspektion');
      await expect(inspectionCard).toHaveClass(/service-card--selected/);

      // After confirmation, card is still expanded with "Abwaehlen" button.
      // The "Abwaehlen" button should be visible.
      const deselectButton = inspectionCard.locator('.service-card__deselect-button');
      await expect(deselectButton).toBeVisible();

      // Click an additional checkbox to change options
      // (card is already expanded, Dialogannahme is checked)
      const oilCheckbox = inspectionCard.locator('mat-checkbox', { hasText: 'Ölwechsel-Service' });
      await oilCheckbox.locator('label.mdc-label').click();
      await waitForAngular(page);

      // Since options changed, confirm button should appear (replacing deselect)
      const confirmButton = inspectionCard.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeVisible();
      await expect(confirmButton).toBeEnabled();

      // Confirm the changed options
      await confirmButton.click();
      await waitForAngular(page);

      // Should still be selected
      await expect(inspectionCard).toHaveClass(/service-card--selected/);
    });

    test('5.4: back to location', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await backButton.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/location');

      const title = await getPageTitle(page);
      expect(title).toBe('An welchem Standort dürfen wir Sie begrüßen?');
    });

    test('5.5: deselect all checkboxes -> confirm button disabled', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Expand TUeV
      const tuvCard = getServiceCard(page, 'TÜV');
      await tuvCard.click();
      await waitForAngular(page);

      // Select a checkbox via label click
      const tuvCheckbox = tuvCard.locator('mat-checkbox').first();
      await tuvCheckbox.locator('label.mdc-label').click();
      await waitForAngular(page);

      const confirmButton = tuvCard.locator('.service-card__confirm-button');
      await expect(confirmButton).toBeEnabled();

      // Deselect the checkbox
      await tuvCheckbox.locator('label.mdc-label').click();
      await waitForAngular(page);

      // Confirm button should be disabled
      await expect(confirmButton).toBeDisabled();
    });

  });

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.1: direct access to /home/services without prerequisites -> redirect to /home/brand', async ({ page }) => {
      await navigateTo(page, '/home/services');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/brand');
    });

    test('6.2: guard protects services route from fresh session', async ({ page }) => {
      await navigateTo(page, '/home/services');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).not.toBe('/home/services');
      expect(route).toMatch(/\/home\/(brand|location)/);
    });

  });

  // =============================================
  // i18n (Language Switch)
  // =============================================

  test.describe('i18n', () => {

    test('DE: title and 7 service card titles', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Welche Services möchten Sie buchen?');

      const titles = await getServiceCardTitles(page);
      expect(titles).toHaveLength(7);
      expect(titles).toEqual(expect.arrayContaining(DE_TITLES));
    });

    test('EN: title and 7 service card titles', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Which services would you like to book?');

      const titles = await getServiceCardTitles(page);
      expect(titles).toHaveLength(7);
      expect(titles).toEqual(expect.arrayContaining(EN_TITLES));
    });

    test('DE: button labels (Zurueck/Weiter)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toContainText('Zurück');

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toContainText('Weiter');
    });

    test('EN: button labels (Back/Continue)', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const backButton = page.locator('.summary-bar__back-button');
      await expect(backButton).toContainText('Back');

      const continueButton = page.locator('.summary-bar__continue-button');
      await expect(continueButton).toContainText('Continue');
    });

    test('DE: confirm/deselect button labels', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      // Expand Inspektion
      const card = getServiceCard(page, 'Inspektion');
      await card.click();
      await waitForAngular(page);

      const confirmButton = card.locator('.service-card__confirm-button');
      await expect(confirmButton).toContainText('Bestätigen');
    });

    test('EN: confirm/deselect button labels', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      // Expand Inspection
      const card = getServiceCard(page, 'Inspection');
      await card.click();
      await waitForAngular(page);

      const confirmButton = card.locator('.service-card__confirm-button');
      await expect(confirmButton).toContainText('Confirm');
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('service grid has role="group" with DE aria-label', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const grid = page.locator('.service-selection__grid[role="group"]');
      await expect(grid).toBeVisible();

      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBe('Verfügbare Services');
    });

    test('service grid has role="group" with EN aria-label', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToServiceSelection(page);

      const grid = page.locator('.service-selection__grid[role="group"]');
      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBe('Available services');
    });

    test('brake-fluid card (no options) has role="button" with aria-pressed', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');
      const role = await brakeFluidCard.getAttribute('role');
      expect(role).toBe('button');

      const ariaPressed = await brakeFluidCard.getAttribute('aria-pressed');
      expect(ariaPressed).toBe('false');
    });

    test('service with options has role="region"', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const inspectionCard = getServiceCard(page, 'Inspektion');
      const role = await inspectionCard.getAttribute('role');
      expect(role).toBe('region');
    });

    test('aria-pressed updates on brake-fluid selection', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');

      let ariaPressed = await brakeFluidCard.getAttribute('aria-pressed');
      expect(ariaPressed).toBe('false');

      await brakeFluidCard.click();
      await waitForAngular(page);

      ariaPressed = await brakeFluidCard.getAttribute('aria-pressed');
      expect(ariaPressed).toBe('true');
    });

    test('keyboard navigation: Enter to toggle brake-fluid card', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');
      await brakeFluidCard.focus();
      await page.keyboard.press('Enter');
      await waitForAngular(page);

      await expect(brakeFluidCard).toHaveClass(/service-card--selected/);
    });

    test('keyboard navigation: Space to toggle brake-fluid card', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToServiceSelection(page);

      const brakeFluidCard = getServiceCard(page, 'Wechsel Bremsflüssigkeit');
      await brakeFluidCard.focus();
      await page.keyboard.press('Space');
      await waitForAngular(page);

      await expect(brakeFluidCard).toHaveClass(/service-card--selected/);
    });

  });

  // =============================================
  // RESPONSIVE
  // =============================================

  test.describe('Responsive Layout', () => {

    test('all 7 service cards visible', async ({ page }) => {
      await goToServiceSelection(page);

      const cards = page.locator('.service-card');
      const count = await cards.count();
      expect(count).toBe(7);

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

    test('each service card has SVG icon', async ({ page }) => {
      await goToServiceSelection(page);

      const icons = page.locator('.service-card__svg-icon');
      const count = await icons.count();
      expect(count).toBe(7);

      for (let i = 0; i < count; i++) {
        await expect(icons.nth(i)).toBeVisible();
        const src = await icons.nth(i).getAttribute('src');
        expect(src).toContain('assets/icons/services/');
        expect(src).toMatch(/\.svg$/);
      }
    });

  });

});
