import { expect, test } from '@playwright/test';

import { setLanguage, waitForAngular } from './helpers/app.helpers';

/**
 * REQ-023 / AC-5: Mobile-Wizard-Nav-Stack.
 *
 * Hintergrund (REQ-021 (u/w)): styles.scss enthaelt eine zentrale Regel,
 * die auf Mobile (<48em / <768px) alle Wizard-Nav-Container vertikal stapelt
 * (flex-direction: column) und die Reihenfolge umkehrt:
 * Weiter-Button (continue/submit) `order: 1`, Back-Button `order: 2`.
 *
 * Auf Tablet (>= 48em) sind die Buttons horizontal (row), Weiter rechts, Back links.
 */

const VALID_BRANCH_ID = '68f606798d2bb4389ed1af51';
const VALID_GROUP_ID = '6908807454f4bb2b69b03fde';

// REQ-039: SKIPPED — der Mockup (docs/requirements/REQ-039-Responsive-Wizard/mockups) ersetzt den
// mobilen Stapel durch eine klebende Aktionsleiste in einer Zeile (Zurück links, Weiter rechts).
// Der neue Vertrag wird in playwright/REQ-039-responsive-wizard.spec.ts („Aktionsleiste") geprüft.
test.describe.skip('REQ-023: Wizard-Nav Mobile-Stack', () => {
  /**
   * Wir validieren die Mobile-Stack-Regel via direktem CSS-Check auf der globalen
   * Stylesheet-Eintragung — unabhaengig davon, welche Seite gerade aktiv ist.
   * Der Test mountet ein minimales DOM-Fragment mit den Ziel-Klassen, um die
   * Cascade aus `src/styles.scss` deterministisch zu pruefen.
   */
  async function setupAppShell(page: import('@playwright/test').Page) {
    await setLanguage(page, 'de');
    await page.goto(`/home/location?branchId=${VALID_BRANCH_ID}&groupId=${VALID_GROUP_ID}`);
    await waitForAngular(page);
    // Test-Container ans body anhaengen — erbt globalen Stylesheet
    await page.evaluate(() => {
      const wrapper = document.createElement('nav');
      wrapper.className = 'brand-selection__nav';
      wrapper.id = 'test-nav';
      const back = document.createElement('button');
      back.className = 'brand-selection__back-button';
      back.textContent = 'Zurueck';
      const cont = document.createElement('button');
      cont.className = 'brand-selection__continue-button';
      cont.textContent = 'Weiter';
      wrapper.append(back, cont);
      document.body.appendChild(wrapper);
    });
  }

  test('Mobile (375px): Wizard-Nav-Buttons sind vertikal gestapelt', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await setupAppShell(page);

    const nav = page.locator('#test-nav');
    const flexDir = await nav.evaluate(el => getComputedStyle(el).flexDirection);
    expect(flexDir).toBe('column');
  });

  test('Tablet (1024px): Wizard-Nav-Buttons sind horizontal', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 800 });
    await setupAppShell(page);

    const nav = page.locator('#test-nav');
    const flexDir = await nav.evaluate(el => getComputedStyle(el).flexDirection);
    expect(flexDir).toBe('row');
  });

  test('Mobile (375px): Continue-Button hat order:1, Back-Button order:2', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await setupAppShell(page);

    const continueBtn = page.locator('#test-nav .brand-selection__continue-button');
    const backBtn = page.locator('#test-nav .brand-selection__back-button');

    const continueOrder = await continueBtn.evaluate(el => getComputedStyle(el).order);
    const backOrder = await backBtn.evaluate(el => getComputedStyle(el).order);

    expect(continueOrder).toBe('1');
    expect(backOrder).toBe('2');
  });
});
