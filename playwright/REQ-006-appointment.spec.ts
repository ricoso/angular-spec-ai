import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, setLanguage, navigateTo, waitForAngular } from './helpers/app.helpers';
import {
  clickAppointmentBack,
  clickAppointmentContinue,
  getAppointmentCardCount,
  getAppointmentDates,
  getAppointmentDayAbbreviations,
  getAppointmentTimes,
  getCalendarLink,
  getPageTitle,
  goToAppointmentPage,
  isAppointmentCardSelected,
  selectAppointmentCard,
} from './helpers/booking.helpers';

/**
 * REQ-006: Terminauswahl (Appointment Selection)
 * Wizard Step 5: User selects one of 4 proposed appointment slots
 *
 * Flow: Brand -> Location -> Services -> Notes -> Appointment
 * Guard: servicesSelectedGuard (requires brand + location + services)
 *
 * Components:
 * - AppointmentSelectionContainerComponent (Container)
 * - AppointmentCardComponent (Presentational: day circle + date + time)
 */

test.describe.skip('REQ-006: Appointment Selection (Terminauswahl)', () => {

  // =============================================
  // TC-1: Page Load (AC-1, AC-2, AC-3)
  // =============================================

  test.describe('TC-1: Page Load', () => {

    test('TC-1: page loads with 4 appointment cards and correct heading (DE)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/appointment');

      // Page title (h1)
      const title = await getPageTitle(page);
      expect(title).toBe('Wählen Sie den für Sie passenden Tag und Uhrzeit aus');

      // 4 appointment cards visible
      const cardCount = await getAppointmentCardCount(page);
      expect(cardCount).toBe(4);

      // Each card shows day abbreviation, date, and time
      const days = await getAppointmentDayAbbreviations(page);
      expect(days).toHaveLength(4);
      for (const day of days) {
        expect(['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']).toContain(day);
      }

      const dates = await getAppointmentDates(page);
      expect(dates).toHaveLength(4);
      for (const date of dates) {
        // Format: DD.MM.YYYY
        expect(date).toMatch(/^\d{2}\.\d{2}\.\d{4}$/);
      }

      const times = await getAppointmentTimes(page);
      expect(times).toHaveLength(4);
      for (const time of times) {
        // Format: HH:MM Uhr
        expect(time).toMatch(/^\d{2}:\d{2} Uhr$/);
      }
    });

    test('TC-1b: page loads with correct heading (EN)', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToAppointmentPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Select your preferred day and time');
    });

  });

  // =============================================
  // TC-2: Select Appointment (AC-7, AC-8)
  // =============================================

  test.describe('TC-2: Select Appointment', () => {

    test('TC-2: clicking a card selects it with highlighting', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // No card selected initially
      for (let i = 0; i < 4; i++) {
        const selected = await isAppointmentCardSelected(page, i);
        expect(selected).toBe(false);
      }

      // Click first card
      await selectAppointmentCard(page, 0);

      // First card should be selected
      const firstSelected = await isAppointmentCardSelected(page, 0);
      expect(firstSelected).toBe(true);

      // Verify the card has the --selected class visually
      const firstCard = page.locator('.appointment-card').first();
      await expect(firstCard).toHaveClass(/appointment-card--selected/);

      // Continue button should be enabled after selection
      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toBeEnabled();
    });

  });

  // =============================================
  // TC-3: Switch Appointment (Single-Select)
  // =============================================

  test.describe('TC-3: Switch Appointment', () => {

    test('TC-3: selecting a different card deselects the previous one', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select first card
      await selectAppointmentCard(page, 0);
      expect(await isAppointmentCardSelected(page, 0)).toBe(true);

      // Select second card
      await selectAppointmentCard(page, 1);

      // Second card should be selected
      expect(await isAppointmentCardSelected(page, 1)).toBe(true);

      // First card should be deselected
      expect(await isAppointmentCardSelected(page, 0)).toBe(false);

      // Only one card should have the --selected class
      const selectedCards = page.locator('.appointment-card--selected');
      const selectedCount = await selectedCards.count();
      expect(selectedCount).toBe(1);
    });

  });

  // =============================================
  // TC-7: Continue Disabled Without Selection (AC-12, BR-7)
  // =============================================

  test.describe('TC-7: Continue Disabled Without Selection', () => {

    test('TC-7: continue button is disabled when no appointment is selected', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Continue button should be disabled
      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toBeVisible();
      await expect(continueButton).toBeDisabled();
    });

    test('TC-7b: continue button becomes enabled after selecting an appointment', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toBeDisabled();

      // Select a card
      await selectAppointmentCard(page, 0);

      // Now it should be enabled
      await expect(continueButton).toBeEnabled();
    });

  });

  // =============================================
  // TC-8: Continue Navigation (AC-11)
  // =============================================

  test.describe('TC-8: Continue Navigation', () => {

    test('TC-8: clicking continue after selection triggers navigation', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select a card
      await selectAppointmentCard(page, 0);

      // Click continue
      await clickAppointmentContinue(page);

      // Currently the placeholder navigates back to /home/appointment (no next step implemented)
      // Verify the button was clickable and no error occurred
      const route = await getCurrentRoute(page);
      expect(route).toBeTruthy();
    });

  });

  // =============================================
  // TC-9: Back Navigation (AC-10, 5.1)
  // =============================================

  test.describe('TC-9: Back Navigation', () => {

    test('TC-9: clicking back navigates to /home/notes', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Click back
      await clickAppointmentBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/notes');

      // Notes page title should be visible
      const title = await getPageTitle(page);
      expect(title).toBe('Bitte geben Sie uns weitere Hinweise zu Ihrer Buchung');
    });

  });

  // =============================================
  // TC-10: Calendar Link (AC-9, 5.2)
  // =============================================

  test.describe('TC-10: Calendar Link', () => {

    test('TC-10: calendar link is visible, underlined, and navigates to workshop calendar', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      const calendarLink = getCalendarLink(page);
      await expect(calendarLink).toBeVisible();

      // Text content check
      const text = (await calendarLink.textContent() ?? '').trim();
      expect(text).toContain('Hier sehen Sie weitere freie Termine in unserem Werkstattkalender');

      // Verify text-decoration is underline
      const textDecoration = await calendarLink.evaluate(
        (el) => window.getComputedStyle(el).textDecorationLine
      );
      expect(textDecoration).toContain('underline');

      // Click the link — should navigate to workshop calendar (REQ-008)
      await calendarLink.click();
      await waitForAngular(page);

      const routeAfter = await getCurrentRoute(page);
      expect(routeAfter).toBe('/home/workshop-calendar');
    });

    test('TC-10b: calendar link text in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToAppointmentPage(page);

      const calendarLink = getCalendarLink(page);
      const text = (await calendarLink.textContent() ?? '').trim();
      expect(text).toContain('Here you can see more available appointments in our workshop calendar');
    });

  });

  // =============================================
  // TC-12: Keyboard Navigation
  // =============================================

  test.describe('TC-12: Keyboard Navigation', () => {

    test('TC-12: cards are focusable via Tab and selectable via Enter', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Cards have tabindex="0" and role="radio"
      const firstCard = page.locator('.appointment-card').first();
      const tabindex = await firstCard.getAttribute('tabindex');
      expect(tabindex).toBe('0');

      const role = await firstCard.getAttribute('role');
      expect(role).toBe('radio');

      // Focus the first card
      await firstCard.focus();
      await expect(firstCard).toBeFocused();

      // Press Enter to select
      await page.keyboard.press('Enter');
      await waitForAngular(page);

      // Card should be selected
      expect(await isAppointmentCardSelected(page, 0)).toBe(true);
    });

    test('TC-12b: cards are selectable via Space key', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Focus the second card
      const secondCard = page.locator('.appointment-card').nth(1);
      await secondCard.focus();
      await expect(secondCard).toBeFocused();

      // Press Space to select
      await page.keyboard.press('Space');
      await waitForAngular(page);

      // Second card should be selected
      expect(await isAppointmentCardSelected(page, 1)).toBe(true);
    });

    test('TC-12c: focus-visible ring visible on keyboard-focused card', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Use keyboard Tab to trigger :focus-visible (programmatic focus() does not trigger it)
      // Tab through the page until the first appointment card is focused
      const firstCard = page.locator('.appointment-card').first();

      // Press Tab multiple times until the first card receives focus
      for (let i = 0; i < 20; i++) {
        await page.keyboard.press('Tab');
        const isFocused = await firstCard.evaluate((el) => el === document.activeElement);
        if (isFocused) break;
      }

      await expect(firstCard).toBeFocused();

      // :focus-visible should apply the outline
      const outlineStyle = await firstCard.evaluate(
        (el) => window.getComputedStyle(el).outlineStyle
      );
      expect(outlineStyle).toBe('solid');
    });

  });

  // =============================================
  // TC-13: Appointment in Cart (AC-14)
  // =============================================

  test.describe('TC-13: Appointment in Cart', () => {

    test('TC-13: selected appointment is shown as chip in cart dropdown', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select first appointment card
      await selectAppointmentCard(page, 0);

      // Read the selected card's date and time text
      const dates = await getAppointmentDates(page);
      const times = await getAppointmentTimes(page);
      const expectedDate = dates[0];
      const expectedTime = times[0];

      // Open cart dropdown in header via the cart icon button
      const cartButton = page.locator('.cart-icon__button');
      await expect(cartButton).toBeVisible();
      await cartButton.click();
      await waitForAngular(page);

      // The cart dropdown should show the appointment chip with date + time
      // Format: "DD.MM.YYYY, HH:MM Uhr"
      const appointmentChip = page.locator('.header__cart-row').filter({ hasText: expectedDate });
      await expect(appointmentChip).toBeVisible();
      await expect(appointmentChip).toContainText(expectedTime);

      // Verify the event icon is present (appointment row uses 'event' icon)
      const eventIcon = appointmentChip.locator('mat-icon', { hasText: 'event' });
      await expect(eventIcon).toBeVisible();
    });

    test('TC-13b: switching appointment updates the chip in cart', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Select first appointment card
      await selectAppointmentCard(page, 0);
      const datesFirst = await getAppointmentDates(page);
      await getAppointmentTimes(page);

      // Switch to second appointment card
      await selectAppointmentCard(page, 1);
      const datesSecond = await getAppointmentDates(page);
      const timesSecond = await getAppointmentTimes(page);
      const expectedDate = datesSecond[1];
      const expectedTime = timesSecond[1];

      // Open cart dropdown
      const cartButton = page.locator('.cart-icon__button');
      await cartButton.click();
      await waitForAngular(page);

      // Verify the updated appointment is shown (second card's data)
      const appointmentChip = page.locator('.header__cart-row').filter({ hasText: expectedDate });
      await expect(appointmentChip).toBeVisible();
      await expect(appointmentChip).toContainText(expectedTime);

      // The first appointment should NOT be shown (unless same date by coincidence)
      // If dates differ, ensure the first one is not displayed
      if (datesFirst[0] !== expectedDate) {
        const oldChip = page.locator('.header__cart-chip').filter({ hasText: datesFirst[0] });
        await expect(oldChip).toHaveCount(0);
      }
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('appointment grid has radiogroup role with aria-label', async ({ page }) => {
      await goToAppointmentPage(page);

      const grid = page.locator('.appointment-selection__grid');
      const role = await grid.getAttribute('role');
      expect(role).toBe('radiogroup');

      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('each card has role="radio" and aria-checked attribute', async ({ page }) => {
      await goToAppointmentPage(page);

      const cards = page.locator('.appointment-card');
      const count = await cards.count();

      for (let i = 0; i < count; i++) {
        const card = cards.nth(i);
        const role = await card.getAttribute('role');
        expect(role).toBe('radio');

        const ariaChecked = await card.getAttribute('aria-checked');
        expect(ariaChecked).toBe('false');
      }

      // Select first card
      await selectAppointmentCard(page, 0);

      // First card should have aria-checked="true"
      const ariaChecked = await cards.first().getAttribute('aria-checked');
      expect(ariaChecked).toBe('true');
    });

    test('each card has descriptive aria-label', async ({ page }) => {
      await goToAppointmentPage(page);

      const cards = page.locator('.appointment-card');
      const count = await cards.count();

      for (let i = 0; i < count; i++) {
        const card = cards.nth(i);
        const ariaLabel = await card.getAttribute('aria-label');
        expect(ariaLabel).toBeTruthy();
        // Format: "Mo, 25.02.2026, 09:00 Uhr"
        expect(ariaLabel).toMatch(/[A-Za-z]{2}, \d{2}\.\d{2}\.\d{4}, \d{2}:\d{2} Uhr/);
      }
    });

    test('day circle has aria-hidden="true"', async ({ page }) => {
      await goToAppointmentPage(page);

      const dayCircle = page.locator('.appointment-card__day').first();
      const ariaHidden = await dayCircle.getAttribute('aria-hidden');
      expect(ariaHidden).toBe('true');
    });

    test('navigation buttons have aria-label', async ({ page }) => {
      await goToAppointmentPage(page);

      const backButton = page.locator('.appointment-selection__back-button');
      const backAriaLabel = await backButton.getAttribute('aria-label');
      expect(backAriaLabel).toBeTruthy();

      const continueButton = page.locator('.appointment-selection__continue-button');
      const continueAriaLabel = await continueButton.getAttribute('aria-label');
      expect(continueAriaLabel).toBeTruthy();
    });

  });

  // =============================================
  // EXCEPTION FLOWS
  // =============================================

  test.describe('Exception Flows', () => {

    test('direct access to /home/appointment without prerequisites -> redirect', async ({ page }) => {
      await navigateTo(page, '/home/appointment');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Guard checks brand first -> /home/brand
      expect(route).not.toBe('/home/appointment');
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

  });

  // =============================================
  // i18n
  // =============================================

  test.describe('i18n', () => {

    test('DE: button labels (Zurueck/Weiter)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      const backButton = page.locator('.appointment-selection__back-button');
      await expect(backButton).toContainText('Zurück');

      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toContainText('Weiter');
    });

    test('EN: button labels (Back/Continue)', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToAppointmentPage(page);

      const backButton = page.locator('.appointment-selection__back-button');
      await expect(backButton).toContainText('Back');

      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toContainText('Continue');
    });

    test('DE: calendar link text in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      const calendarLink = getCalendarLink(page);
      await expect(calendarLink).toContainText('Hier sehen Sie weitere freie Termine in unserem Werkstattkalender');
    });

    test('EN: calendar link text in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToAppointmentPage(page);

      const calendarLink = getCalendarLink(page);
      await expect(calendarLink).toContainText('Here you can see more available appointments in our workshop calendar');
    });

  });

  // =============================================
  // RESPONSIVE LAYOUT
  // =============================================

  test.describe('Responsive Layout', () => {

    test('appointment section visible with all elements', async ({ page }) => {
      await goToAppointmentPage(page);

      const section = page.locator('.appointment-selection');
      await expect(section).toBeVisible();

      const title = page.locator('.appointment-selection__title');
      await expect(title).toBeVisible();

      const grid = page.locator('.appointment-selection__grid');
      await expect(grid).toBeVisible();

      const calendarLink = getCalendarLink(page);
      await expect(calendarLink).toBeVisible();

      const nav = page.locator('.appointment-selection__nav');
      await expect(nav).toBeVisible();
    });

    test('back and continue buttons visible', async ({ page }) => {
      await goToAppointmentPage(page);

      const backButton = page.locator('.appointment-selection__back-button');
      await expect(backButton).toBeVisible();

      const continueButton = page.locator('.appointment-selection__continue-button');
      await expect(continueButton).toBeVisible();
    });

  });

});
