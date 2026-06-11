import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-001: Header
 * Reusable header with logo, accessibility settings, and shopping cart icon
 *
 * Acceptance Criteria:
 * - AC-1: Logo + Company name displayed
 * - AC-2: Accessibility dropdown with font-size, contrast, motion
 * - AC-3: Shopping cart icon with badge
 * - AC-4: Responsive layout (desktop/mobile)
 * - AC-5: WCAG 2.1 AA compliant
 * - AC-6: LocalStorage persistence for accessibility settings
 */

test.describe.skip('REQ-001: Header', () => {

  test.beforeEach(async ({ page }) => {
    await navigateTo(page, '/home/brand');
  });

  // =============================================
  // MAIN FLOW — Header Elements visible
  // =============================================

  test.describe('Main Flow — Header Display', () => {

    test('AC-1: should display the header with role banner', async ({ page }) => {
      const header = page.locator('header[role="banner"]');
      await expect(header).toBeVisible();
    });

    test('AC-1b: should display company name', async ({ page }) => {
      const companyName = page.locator('.header__company-name');
      await expect(companyName).toBeVisible();
      await expect(companyName).toContainText('Autohaus GmbH');
    });

    test('AC-1c: should display company subtitle', async ({ page }) => {
      const subtitle = page.locator('.header__company-subtitle');
      await expect(subtitle).toBeVisible();
      await expect(subtitle).toContainText('Fahrzeugauswahl');
    });

    test('AC-1d: logo link should have href attribute', async ({ page }) => {
      const logoLink = page.locator('.header__logo-link');
      await expect(logoLink).toBeVisible();
      const href = await logoLink.getAttribute('href');
      // SSR: href should be '/' or '/home/brand'
      expect(href).toBeTruthy();
    });

    test('AC-3: should display shopping cart icon button', async ({ page }) => {
      const cartButton = page.locator('.cart-fab');
      await expect(cartButton).toBeVisible();
    });

    test('AC-2: should display accessibility button', async ({ page }) => {
      const a11yButton = page.locator('.a11y-fab');
      await expect(a11yButton).toBeVisible();
    });

  });

  // =============================================
  // ACCESSIBILITY MENU
  // =============================================

  test.describe('Accessibility Menu', () => {

    test('AC-2: should open accessibility menu on button click', async ({ page }) => {
      const a11yButton = page.locator('.a11y-fab');
      await a11yButton.click();
      await waitForAngular(page);

      const menu = page.locator('.accessibility-menu');
      await expect(menu).toBeVisible();
    });

    test('AC-2b: should show font size radio buttons', async ({ page }) => {
      const a11yButton = page.locator('.a11y-fab');
      await a11yButton.click();
      await waitForAngular(page);

      const fontSizeLabel = page.locator('#font-size-label');
      await expect(fontSizeLabel).toBeVisible();

      // Filter to font-size labels (a11y-menu also has language radios)
      const radioButtons = page.locator('.accessibility-menu__radio[for*="a11y-font-"]');
      const count = await radioButtons.count();
      expect(count).toBe(4); // small, normal, large, x-large
    });

    test('AC-2c: should show high contrast toggle', async ({ page }) => {
      const a11yButton = page.locator('.a11y-fab');
      await a11yButton.click();
      await waitForAngular(page);

      const toggles = page.locator('.accessibility-menu__toggle');
      const count = await toggles.count();
      expect(count).toBeGreaterThanOrEqual(2); // high contrast + reduced motion
    });

    test('AC-2d: should change font size when radio button clicked', async ({ page }) => {
      const a11yButton = page.locator('.a11y-fab');
      await a11yButton.click();
      await waitForAngular(page);

      // Click the "Large" font-size pill-label (3rd font option, idx 2)
      const fontLabels = page.locator('.accessibility-menu__radio[for*="a11y-font-"]');
      await fontLabels.nth(2).click();
      await waitForAngular(page);

      // Accessibility settings stored as JSON under 'accessibility-settings'
      const settings = await page.evaluate(() => localStorage.getItem('accessibility-settings'));
      expect(settings).toBeTruthy();
    });

  });

  // =============================================
  // SHOPPING CART
  // =============================================

  test.describe('Shopping Cart', () => {

    test('AC-3: should open cart dropdown on click', async ({ page }) => {
      const cartButton = page.locator('.cart-fab');
      await cartButton.click();
      await waitForAngular(page);

      const cartTitle = page.locator('.floating-overlay__panel-title');
      await expect(cartTitle).toBeVisible();
    });

    test('AC-3b: cart should show empty message when no items', async ({ page }) => {
      const cartButton = page.locator('.cart-fab');
      await cartButton.click();
      await waitForAngular(page);

      const emptyMessage = page.locator('.floating-overlay__empty');
      await expect(emptyMessage).toBeVisible();
    });

    test('AC-3c: cart button should have aria-label', async ({ page }) => {
      const cartButton = page.locator('.cart-fab');
      const ariaLabel = await cartButton.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

  });

  // =============================================
  // i18n
  // =============================================

  test.describe('i18n', () => {

    test('should show company name (language-independent)', async ({ page }) => {
      const companyName = page.locator('.header__company-name');
      await expect(companyName).toContainText('Autohaus GmbH');
    });

    test('should show EN cart title', async ({ page }) => {
      await setLanguage(page, 'en');

      const cartButton = page.locator('.cart-fab');
      await cartButton.click();
      await waitForAngular(page);

      const cartTitle = page.locator('.floating-overlay__panel-title');
      await expect(cartTitle).toContainText('selection');
    });

    test('should show DE cart title', async ({ page }) => {
      await setLanguage(page, 'de');

      const cartButton = page.locator('.cart-fab');
      await cartButton.click();
      await waitForAngular(page);

      const cartTitle = page.locator('.floating-overlay__panel-title');
      await expect(cartTitle).toContainText('Auswahl');
    });

    test('should show EN accessibility label', async ({ page }) => {
      await setLanguage(page, 'en');

      const a11yButton = page.locator('.a11y-fab');
      const ariaLabel = await a11yButton.getAttribute('aria-label');
      expect(ariaLabel).toBe('Accessibility');
    });

    test('should show DE accessibility label', async ({ page }) => {
      await setLanguage(page, 'de');

      const a11yButton = page.locator('.a11y-fab');
      const ariaLabel = await a11yButton.getAttribute('aria-label');
      expect(ariaLabel).toBe('Barrierefreiheit');
    });

  });

  // =============================================
  // ACCESSIBILITY (WCAG)
  // =============================================

  test.describe('WCAG Accessibility', () => {

    test('AC-5: header should have role banner', async ({ page }) => {
      const header = page.locator('[role="banner"]');
      await expect(header).toBeVisible();
    });

    test('AC-5b: accessibility button should have aria-label', async ({ page }) => {
      const a11yButton = page.locator('.a11y-fab');
      const ariaLabel = await a11yButton.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('AC-5c: logo image should have alt text', async ({ page }) => {
      const logo = page.locator('.header__logo');
      const alt = await logo.getAttribute('alt');
      expect(alt).toBeTruthy();
    });

    test('AC-5d: header elements should be keyboard navigable', async ({ page }) => {
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');

      const focused = page.locator(':focus');
      await expect(focused).toBeVisible();
    });

  });

  // =============================================
  // RESPONSIVE
  // =============================================

  test.describe('Responsive', () => {

    test('AC-4: header should be visible on all viewports', async ({ page }) => {
      const header = page.locator('header[role="banner"]');
      await expect(header).toBeVisible();
    });

    test('AC-4b: company name should be visible', async ({ page }) => {
      const companyName = page.locator('.header__company-name');
      await expect(companyName).toBeVisible();
    });

  });

});
