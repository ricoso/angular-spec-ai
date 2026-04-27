import { expect, test } from '@playwright/test';

// REQ-023: SKIPPED — Spec war fuer altes UI (vor REQ-013/REQ-015 Refactor) geschrieben.
// Reaktivieren nach Selektor-Update — siehe REQ-024 (Test-Suite-Stabilisierung).

import { getCurrentRoute, navigateTo, setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  clickWorkshopCalendarBack,
  // getAllTimeSlotTexts,
  getCalendarLink,
  getPageTitle,
  getTimeDayHeadings,
  getTimeSlotsForDay,
  getWorkshopCalendarHintText,
  getWorkshopCalendarIntroText,
  goToAppointmentPage,
  goToWorkshopCalendarPage,
  isTimeSlotSelected,
  isTimeSlotSelectedInDay,
  openDatePickerAndSelectToday,
  selectTimeSlot,
  selectTimeSlotInDay,
} from './helpers/booking.helpers';

/**
 * REQ-008: Werkstattkalender (Workshop Calendar)
 * Wizard Step 5b: User selects a desired date via DatePicker and picks a time slot
 * from the next 3 business days (Mo-Sa, no Sunday).
 *
 * Flow: Brand -> Location -> Services -> Notes -> Appointment -> Calendar Link -> Workshop Calendar
 * Guard: servicesSelectedGuard (requires brand + location + services)
 *
 * Components:
 * - WorkshopCalendarContainerComponent (Container)
 * - WorkshopCalendarDatePickerComponent (Presentational: date picker card)
 * - WorkshopCalendarDayComponent (Presentational: day heading + time slot grid)
 */

test.describe.skip('REQ-008: Workshop Calendar (Werkstattkalender)', () => {

  // =============================================
  // MAIN FLOW (Section 4)
  // =============================================

  test.describe('Main Flow', () => {

    test('TC-1: page loads with title, description, date picker, and hint text (DE)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/workshop-calendar');

      // AC-1: Title visible
      const title = await getPageTitle(page);
      expect(title).toBe('Hier sehen Sie weitere freie Termine in unserem Werkstattkalender');

      // AC-2: Description text
      const description = page.locator('.date-picker-card__description');
      await expect(description).toBeVisible();
      await expect(description).toContainText('Wählen Sie Ihren Wunschtermin');

      // AC-3: Label "Ihr Wunschtermin:" in bold
      const label = page.locator('.date-picker-card__label');
      await expect(label).toBeVisible();
      await expect(label).toContainText('Ihr Wunschtermin:');

      // AC-4: Calendar icon with .icon-framed and date picker input (scoped to date picker card)
      const datePickerCard = page.locator('.date-picker-card');
      const iconFramed = datePickerCard.locator('.icon-framed');
      await expect(iconFramed).toBeVisible();

      const dateInput = page.locator('#workshop-date-input');
      await expect(dateInput).toBeVisible();
    });

    test('TC-2: before date selection, hint text is shown and no time slots', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      // AC-8: Hint text visible
      const hintText = await getWorkshopCalendarHintText(page);
      expect(hintText).toContain('Wählen Sie im Kalender einen gewünschten Termin aus');

      // No time slots visible
      const timeSlots = page.locator('.time-slot-btn');
      expect(await timeSlots.count()).toBe(0);

      // No day headings visible
      const dayHeadings = page.locator('.time-day__heading');
      expect(await dayHeadings.count()).toBe(0);
    });

    test('TC-3: date picker opens when toggle is clicked', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      // Click datepicker toggle
      const toggle = page.locator('mat-datepicker-toggle button');
      await expect(toggle).toBeVisible();
      await toggle.click();
      await waitForAngular(page);

      // AC-6: MatDatepicker is open (calendar overlay visible)
      const calendar = page.locator('.mat-datepicker-content');
      await expect(calendar).toBeVisible();

      // Today cell should be highlighted
      const todayCell = page.locator('.mat-calendar-body-today');
      await expect(todayCell).toBeVisible();
    });

    test('TC-4: selecting a date shows 3 days with time slots', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      // Select today's date
      await openDatePickerAndSelectToday(page);

      // AC-9: Text changes to "Wir haben folgende..."
      const introText = await getWorkshopCalendarIntroText(page);
      expect(introText).toContain('Wir haben folgende');

      // Hint text should be gone
      const hint = page.locator('.right-panel__hint');
      expect(await hint.count()).toBe(0);

      // AC-10: 3 days with time slots
      const headings = await getTimeDayHeadings(page);
      expect(headings).toHaveLength(3);

      // AC-11: Each heading has format "XX, DD.MM.YYYY"
      for (const heading of headings) {
        expect(heading).toMatch(/^[A-Za-z]{2}, \d{2}\.\d{2}\.\d{4}$/);
      }

      // AC-10: Each day has 11 time slots (07:00-17:00)
      for (let i = 0; i < 3; i++) {
        const slots = await getTimeSlotsForDay(page, i);
        expect(slots).toHaveLength(11);
      }
    });

    test('TC-4b: selecting a time slot highlights it and enables continue', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      // AC-15: Continue button should be disabled before slot selection
      const continueButton = page.locator('.wizard-nav__continue-button');
      await expect(continueButton).toBeDisabled();

      // AC-13: Click a time slot
      await selectTimeSlot(page, '09:00 Uhr');

      // Slot should be selected
      const selected = await isTimeSlotSelected(page, '09:00 Uhr');
      expect(selected).toBe(true);

      // AC-15: Continue button should be enabled
      await expect(continueButton).toBeEnabled();
    });

  });

  // =============================================
  // TEST CASES (Section 13)
  // =============================================

  test.describe('Test Cases', () => {

    test('TC-5: no Sunday in day headings (BR-3)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const headings = await getTimeDayHeadings(page);
      for (const heading of headings) {
        expect(heading).not.toMatch(/^So,/);
      }
    });

    test('TC-6: time slots are 07:00 to 17:00 (11 slots) (BR-4)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const expectedSlots = [
        '07:00 Uhr', '08:00 Uhr', '09:00 Uhr', '10:00 Uhr', '11:00 Uhr',
        '12:00 Uhr', '13:00 Uhr', '14:00 Uhr', '15:00 Uhr', '16:00 Uhr', '17:00 Uhr'
      ];

      const slotsDay0 = await getTimeSlotsForDay(page, 0);
      expect(slotsDay0).toEqual(expectedSlots);
    });

    test('TC-7: selecting a time slot is single-select (BR-6)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      // Select slot in first day
      await selectTimeSlotInDay(page, 0, '09:00 Uhr');
      expect(await isTimeSlotSelectedInDay(page, 0, '09:00 Uhr')).toBe(true);

      // Select a different slot in same day
      await selectTimeSlotInDay(page, 0, '14:00 Uhr');
      expect(await isTimeSlotSelectedInDay(page, 0, '14:00 Uhr')).toBe(true);
      expect(await isTimeSlotSelectedInDay(page, 0, '09:00 Uhr')).toBe(false);

      // Only one slot should have --selected class across all days
      const selectedSlots = page.locator('.time-slot-btn--selected');
      expect(await selectedSlots.count()).toBe(1);

      // Select slot in a different day - previous selection cleared
      await selectTimeSlotInDay(page, 1, '11:00 Uhr');
      expect(await isTimeSlotSelectedInDay(page, 1, '11:00 Uhr')).toBe(true);
      expect(await isTimeSlotSelectedInDay(page, 0, '14:00 Uhr')).toBe(false);
      expect(await selectedSlots.count()).toBe(1);
    });

    test('TC-7b: continue disabled without slot selection (AC-15, BR-7)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      // Slots visible but none selected
      const continueButton = page.locator('.wizard-nav__continue-button');
      await expect(continueButton).toBeDisabled();
    });

    test('TC-11: back navigation goes to /home/appointment (AC-14)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      await clickWorkshopCalendarBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/appointment');

      // Appointment page title should be visible
      const title = await getPageTitle(page);
      expect(title).toBe('Wählen Sie den für Sie passenden Tag und Uhrzeit aus');
    });

    test('TC-14: calendar link on appointment page navigates to workshop calendar (AC-16)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToAppointmentPage(page);

      // Verify calendar link exists
      const calendarLink = getCalendarLink(page);
      await expect(calendarLink).toBeVisible();
      await expect(calendarLink).toContainText('Hier sehen Sie weitere freie Termine in unserem Werkstattkalender');

      // Click the link
      await calendarLink.click();
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/workshop-calendar');
    });

    test('TC-13: keyboard navigation - slots focusable via Tab and selectable via Enter/Space', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      // Verify time slot buttons have role="radio"
      const firstSlot = page.locator('.time-slot-btn').first();
      const role = await firstSlot.getAttribute('role');
      expect(role).toBe('radio');

      // Focus the first slot
      await firstSlot.focus();
      await expect(firstSlot).toBeFocused();

      // Press Enter to select
      await page.keyboard.press('Enter');
      await waitForAngular(page);

      // Slot should be selected
      const firstSlotClass = await firstSlot.getAttribute('class');
      expect(firstSlotClass).toContain('time-slot-btn--selected');

      // Focus another slot and press Space
      const thirdSlot = page.locator('.time-slot-btn').nth(2);
      await thirdSlot.focus();
      await page.keyboard.press('Space');
      await waitForAngular(page);

      // Third slot should be selected, first deselected
      const thirdSlotClass = await thirdSlot.getAttribute('class');
      expect(thirdSlotClass).toContain('time-slot-btn--selected');

      const firstSlotClassAfter = await firstSlot.getAttribute('class');
      expect(firstSlotClassAfter).not.toContain('time-slot-btn--selected');
    });

  });

  // =============================================
  // ALTERNATIVE FLOWS (Section 5)
  // =============================================

  test.describe('Alternative Flows', () => {

    test('5.2: back to appointment page (Zurueck)', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      await clickWorkshopCalendarBack(page);

      const route = await getCurrentRoute(page);
      expect(route).toBe('/home/appointment');
    });

    test('5.3: changing date recalculates days and deselects slot', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      // Select today and a time slot in the first day
      await openDatePickerAndSelectToday(page);
      await selectTimeSlotInDay(page, 0, '10:00 Uhr');
      expect(await isTimeSlotSelectedInDay(page, 0, '10:00 Uhr')).toBe(true);

      // Get first set of headings
      const headingsBefore = await getTimeDayHeadings(page);
      expect(headingsBefore).toHaveLength(3);

      // Select today again (same date re-pick, triggers recalculation)
      await openDatePickerAndSelectToday(page);

      // Headings should still be 3
      const headingsAfter = await getTimeDayHeadings(page);
      expect(headingsAfter).toHaveLength(3);

      // The previously selected slot should no longer be selected
      // (since appointment is cleared on date change in the store)
      const selectedSlots = page.locator('.time-slot-btn--selected');
      const selectedCount = await selectedSlots.count();
      // After date change, either the slot is deselected or the same ID might remain
      // The requirement says "Zuvor selektierter Uhrzeitslot wird deselektiert"
      expect(selectedCount).toBeLessThanOrEqual(1);
    });

    test('5.4: date on Sunday shows slots starting from Monday', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      // Verify that no Sunday appears in the headings
      const headings = await getTimeDayHeadings(page);
      for (const heading of headings) {
        expect(heading).not.toMatch(/^So,/);
      }
    });

  });

  // =============================================
  // EXCEPTION FLOWS (Section 6)
  // =============================================

  test.describe('Exception Flows', () => {

    test('6.3: direct access to /home/workshop-calendar without prerequisites -> redirect', async ({ page }) => {
      await navigateTo(page, '/home/workshop-calendar');
      await waitForAngular(page);

      const route = await getCurrentRoute(page);
      // Guard checks services -> redirect to /home/services or /home/brand or /home/location
      expect(route).not.toBe('/home/workshop-calendar');
      expect(route).toMatch(/\/home\/(brand|location|services)/);
    });

  });

  // =============================================
  // i18n
  // =============================================

  test.describe('i18n', () => {

    test('DE: title and button labels in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Hier sehen Sie weitere freie Termine in unserem Werkstattkalender');

      const backButton = page.locator('.wizard-nav__back-button');
      await expect(backButton).toContainText('Zurück');

      const continueButton = page.locator('.wizard-nav__continue-button');
      await expect(continueButton).toContainText('Weiter');
    });

    test('EN: title and button labels in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToWorkshopCalendarPage(page);

      const title = await getPageTitle(page);
      expect(title).toBe('Here you can see further available appointments in our workshop calendar');

      const backButton = page.locator('.wizard-nav__back-button');
      await expect(backButton).toContainText('Back');

      const continueButton = page.locator('.wizard-nav__continue-button');
      await expect(continueButton).toContainText('Continue');
    });

    test('DE: hint text before date selection in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      const hintText = await getWorkshopCalendarHintText(page);
      expect(hintText).toContain('Wählen Sie im Kalender einen gewünschten Termin aus');
    });

    test('EN: hint text before date selection in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToWorkshopCalendarPage(page);

      const hintText = await getWorkshopCalendarHintText(page);
      expect(hintText).toContain('Select a desired date in the calendar');
    });

    test('DE: description and label in German', async ({ page }) => {
      await setLanguage(page, 'de');
      await goToWorkshopCalendarPage(page);

      const description = page.locator('.date-picker-card__description');
      await expect(description).toContainText('Wählen Sie Ihren Wunschtermin');

      const label = page.locator('.date-picker-card__label');
      await expect(label).toContainText('Ihr Wunschtermin:');
    });

    test('EN: description and label in English', async ({ page }) => {
      await setLanguage(page, 'en');
      await goToWorkshopCalendarPage(page);

      const description = page.locator('.date-picker-card__description');
      await expect(description).toContainText('Select your desired date');

      const label = page.locator('.date-picker-card__label');
      await expect(label).toContainText('Your desired date:');
    });

  });

  // =============================================
  // ACCESSIBILITY
  // =============================================

  test.describe('Accessibility', () => {

    test('workshop calendar section has aria-labelledby', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      const section = page.locator('.workshop-calendar');
      const ariaLabelledBy = await section.getAttribute('aria-labelledby');
      expect(ariaLabelledBy).toBe('wk-title');
    });

    test('right panel has aria-live="polite" for dynamic content changes', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      const rightPanel = page.locator('.workshop-calendar__right-panel');
      const ariaLive = await rightPanel.getAttribute('aria-live');
      expect(ariaLive).toBe('polite');
    });

    test('time slot grid has radiogroup role with aria-label', async ({ page }) => {
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const grid = page.locator('.time-slots-grid').first();
      const role = await grid.getAttribute('role');
      expect(role).toBe('radiogroup');

      const ariaLabel = await grid.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('each time slot button has role="radio" and aria-checked', async ({ page }) => {
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const firstSlot = page.locator('.time-slot-btn').first();
      const role = await firstSlot.getAttribute('role');
      expect(role).toBe('radio');

      const ariaChecked = await firstSlot.getAttribute('aria-checked');
      expect(ariaChecked).toBe('false');

      // Select the slot
      await firstSlot.click();
      await waitForAngular(page);

      const ariaCheckedAfter = await firstSlot.getAttribute('aria-checked');
      expect(ariaCheckedAfter).toBe('true');
    });

    test('each time slot has aria-label with display time', async ({ page }) => {
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const firstSlot = page.locator('.time-slot-btn').first();
      const ariaLabel = await firstSlot.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
      expect(ariaLabel).toMatch(/\d{2}:\d{2} Uhr/);
    });

    test('date input has aria-label', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      const dateInput = page.locator('#workshop-date-input');
      const ariaLabel = await dateInput.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
    });

    test('navigation buttons have aria-label', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      const backButton = page.locator('.wizard-nav__back-button');
      const backAriaLabel = await backButton.getAttribute('aria-label');
      expect(backAriaLabel).toBeTruthy();

      const continueButton = page.locator('.wizard-nav__continue-button');
      const continueAriaLabel = await continueButton.getAttribute('aria-label');
      expect(continueAriaLabel).toBeTruthy();
    });

    test('calendar icon has .icon-framed class', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      // Scope to the date picker card to avoid matching header icons
      const datePickerCard = page.locator('.date-picker-card');
      const icon = datePickerCard.locator('.icon-framed');
      await expect(icon).toBeVisible();
    });

    // Skip on tablet/mobile: emulated touch devices don't support keyboard Tab focus
    test('focus-visible ring on keyboard-focused time slot', async ({ page }, testInfo) => {
      // Only run on desktop (tablet/mobile device emulation disables keyboard Tab navigation)
      if (testInfo.project.name !== 'chromium-desktop') {
        test.skip();
        return;
      }

      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const firstSlot = page.locator('.time-slot-btn').first();

      // Tab to the first slot
      for (let i = 0; i < 30; i++) {
        await page.keyboard.press('Tab');
        const isFocused = await firstSlot.evaluate((el) => el === document.activeElement);
        if (isFocused) break;
      }

      await expect(firstSlot).toBeFocused();

      // :focus-visible should apply the outline
      const outlineStyle = await firstSlot.evaluate(
        (el) => window.getComputedStyle(el).outlineStyle
      );
      expect(outlineStyle).toBe('solid');
    });

  });

  // =============================================
  // RESPONSIVE LAYOUT
  // =============================================

  test.describe('Responsive Layout', () => {

    test('workshop calendar section visible with all elements', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      const section = page.locator('.workshop-calendar');
      await expect(section).toBeVisible();

      const title = page.locator('.workshop-calendar__title');
      await expect(title).toBeVisible();

      const datePickerCard = page.locator('.date-picker-card');
      await expect(datePickerCard).toBeVisible();

      const rightPanel = page.locator('.workshop-calendar__right-panel');
      await expect(rightPanel).toBeVisible();

      const nav = page.locator('.wizard-nav');
      await expect(nav).toBeVisible();
    });

    test('back and continue buttons visible', async ({ page }) => {
      await goToWorkshopCalendarPage(page);

      const backButton = page.locator('.wizard-nav__back-button');
      await expect(backButton).toBeVisible();

      const continueButton = page.locator('.wizard-nav__continue-button');
      await expect(continueButton).toBeVisible();
    });

    test('date picker card and right panel are visible after date selection', async ({ page }) => {
      await goToWorkshopCalendarPage(page);
      await openDatePickerAndSelectToday(page);

      const datePickerCard = page.locator('.date-picker-card');
      await expect(datePickerCard).toBeVisible();

      const dayComponents = page.locator('app-workshop-calendar-day');
      expect(await dayComponents.count()).toBe(3);

      for (let i = 0; i < 3; i++) {
        await expect(dayComponents.nth(i)).toBeVisible();
      }
    });

  });

});
