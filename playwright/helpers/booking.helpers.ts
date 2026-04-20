import { expect, type Page } from '@playwright/test';

import { navigateTo, waitForAngular } from './app.helpers';

/**
 * Booking wizard helpers
 * Reusable actions for the booking flow (brand -> location -> services)
 */

/** Navigate to the brand selection page */
export async function goToBrandSelection(page: Page): Promise<void> {
  await navigateTo(page, '/home/brand');
}

/** Navigate to the location selection page (requires brand to be selected) */
export async function goToLocationSelection(page: Page): Promise<void> {
  await navigateTo(page, '/home/location');
}

/** Select the first available brand dynamically */
export async function selectFirstBrand(page: Page): Promise<string> {
  await goToBrandSelection(page);
  const button = page.locator('.brand-grid__button, [class*="brand"] button').first();
  await expect(button).toBeVisible({ timeout: 15000 });
  const brandName = (await button.textContent() ?? '').trim();
  await button.click();
  await waitForAngular(page);
  return brandName;
}

/** Select the first available location dynamically */
export async function selectFirstLocation(page: Page): Promise<string> {
  const button = page.locator('.location-grid__button').first();
  await expect(button).toBeVisible({ timeout: 15000 });
  const locationName = (await button.textContent() ?? '').trim();
  await button.click();
  await waitForAngular(page);
  return locationName;
}

/** Select a brand by clicking on the brand button */
export async function selectBrand(page: Page, brandName: string): Promise<void> {
  await goToBrandSelection(page);
  const button = page.locator('button', { hasText: brandName });
  await expect(button).toBeVisible({ timeout: 15000 });
  await button.click();
  await waitForAngular(page);
}

/** Select a location by clicking on the location button */
export async function selectLocation(page: Page, locationName: string): Promise<void> {
  const button = page.locator('button', { hasText: locationName });
  await expect(button).toBeVisible();
  await button.click();
  await waitForAngular(page);
}

/** Get all visible brand button texts */
export async function getBrandButtonTexts(page: Page): Promise<string[]> {
  const buttons = page.locator('.brand-grid__button, [class*="brand"] button');
  return buttons.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Get all visible location button texts */
export async function getLocationButtonTexts(page: Page): Promise<string[]> {
  const buttons = page.locator('.location-grid__button');
  return buttons.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Get the page title heading text */
export async function getPageTitle(page: Page): Promise<string> {
  const heading = page.locator('h1').first();
  return (await heading.textContent() ?? '').trim();
}

/**
 * Complete flow: Brand -> Location
 * Selects a brand, waits for location page, and returns location button texts.
 */
export async function completeBrandToLocationFlow(
  page: Page,
  brandName: string
): Promise<string[]> {
  await selectBrand(page, brandName);
  await waitForAngular(page);
  return getLocationButtonTexts(page);
}

// =============================================
// SERVICE SELECTION HELPERS (REQ-011)
// =============================================

/**
 * Navigate to the service selection page via brand + location selection.
 * This is necessary because the guard requires both brand and location selected.
 * Waits for each step to render before proceeding.
 */
export async function goToServiceSelection(
  page: Page,
  brandName?: string,
  locationName?: string
): Promise<void> {
  if (brandName) {
    await selectBrand(page, brandName);
  } else {
    await selectFirstBrand(page);
  }
  // Wait for location page to render with buttons
  await page.locator('.location-grid__button').first().waitFor({ state: 'visible', timeout: 15000 });
  if (locationName) {
    await selectLocation(page, locationName);
  } else {
    await selectFirstLocation(page);
  }
  // Wait for service cards to appear (confirms we reached the services page)
  await page.locator('.service-card').first().waitFor({ state: 'visible', timeout: 15000 });
  await waitForAngular(page);
}

/**
 * Click on a service card by its title text.
 * Uses the __title element for precise matching to avoid ambiguity
 * when service names appear in descriptions of other cards.
 * For services without options (brake-fluid): toggles selection directly.
 * For services with options: toggles expand/collapse.
 */
export async function selectService(page: Page, serviceName: string): Promise<void> {
  const card = page.locator('.service-card').filter({
    has: page.locator('.service-card__title', { hasText: serviceName })
  });
  await expect(card).toBeVisible();
  await card.click();
  await waitForAngular(page);
}

/** Get the service card locator by its title text (precise match via __title) */
export function getServiceCard(page: Page, serviceName: string) {
  return page.locator('.service-card').filter({
    has: page.locator('.service-card__title', { hasText: serviceName })
  });
}

/** Get visible service card title texts */
export async function getServiceCardTitles(page: Page): Promise<string[]> {
  const titles = page.locator('.service-card__title');
  return titles.allTextContents().then(texts => texts.map(t => t.trim()));
}

/**
 * Confirm a service with options by selecting checkboxes and clicking Confirm.
 * @param page - Playwright page
 * @param serviceName - Visible service card title (DE or EN)
 * @param optionTexts - Array of checkbox label texts to select
 */
export async function confirmServiceWithOptions(
  page: Page,
  serviceName: string,
  optionTexts: string[]
): Promise<void> {
  const card = getServiceCard(page, serviceName);
  await expect(card).toBeVisible();

  // Expand card if not already expanded
  const options = card.locator('.service-card__options');
  if (!(await options.isVisible())) {
    await card.click();
    await waitForAngular(page);
  }

  // Click each mat-checkbox label to toggle the checkbox
  // The mat-checkbox component handles click on the label element
  for (const text of optionTexts) {
    const matCheckbox = card.locator('mat-checkbox', { hasText: text });
    await expect(matCheckbox).toBeVisible();
    // Use the label element which triggers the mat-checkbox correctly
    await matCheckbox.locator('label.mdc-label').click();
    await waitForAngular(page);
  }

  // Click confirm button
  const confirmButton = card.locator('.service-card__confirm-button');
  await expect(confirmButton).toBeVisible();
  await expect(confirmButton).toBeEnabled();
  await confirmButton.click();
  await waitForAngular(page);
}

/**
 * Click the Deselect button on a service card.
 * The card must be expanded and selected.
 */
export async function deselectServiceWithButton(
  page: Page,
  serviceName: string
): Promise<void> {
  const card = getServiceCard(page, serviceName);
  await expect(card).toBeVisible();

  // Expand card if not already expanded
  const deselectButton = card.locator('.service-card__deselect-button');
  if (!(await deselectButton.isVisible())) {
    await card.click();
    await waitForAngular(page);
  }

  await expect(deselectButton).toBeVisible();
  await deselectButton.click();
  await waitForAngular(page);
}

/** Get checkbox labels within an expanded service card */
export async function getServiceCheckboxLabels(
  page: Page,
  serviceName: string
): Promise<string[]> {
  const card = getServiceCard(page, serviceName);

  // Expand card if not already expanded
  const options = card.locator('.service-card__options');
  if (!(await options.isVisible())) {
    await card.click();
    await waitForAngular(page);
  }

  const checkboxes = card.locator('.service-card__checkbox');
  return checkboxes.allTextContents().then(texts => texts.map(t => t.trim()));
}

// Legacy helpers (REQ-004 compat) — redirect to new pattern

/**
 * @deprecated Use confirmServiceWithOptions() instead.
 * Kept for backward compatibility with workflow tests.
 */
export async function confirmTireChange(
  page: Page,
  _variant: 'with-storage' | 'without-storage'
): Promise<void> {
  // In REQ-011, tire change uses checkboxes. Map old variants to new options.
  const optionText = _variant === 'with-storage'
    ? 'eingelagert'
    : 'bringe meine Räder mit';
  await confirmServiceWithOptions(page, 'Räderwechsel', [optionText]);
}

/**
 * @deprecated Use deselectServiceWithButton() instead.
 */
export async function deselectTireChange(page: Page): Promise<void> {
  await deselectServiceWithButton(page, 'Räderwechsel');
}

/**
 * Complete flow: Brand -> Location -> Services
 * Navigates through brand and location, landing on the services page.
 */
export async function completeBrandToServiceFlow(
  page: Page,
  brandName?: string,
  locationName?: string
): Promise<void> {
  if (brandName) {
    await selectBrand(page, brandName);
  } else {
    await selectFirstBrand(page);
  }
  if (locationName) {
    await selectLocation(page, locationName);
  } else {
    await selectFirstLocation(page);
  }
  await waitForAngular(page);
}

// =============================================
// LOCATION SELECTION HELPERS (REQ-003 / REQ-007)
// =============================================

/** Click the Back button on the location selection page */
export async function clickLocationBack(page: Page): Promise<void> {
  const backButton = page.locator('.location-selection__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

// =============================================
// SERVICE SELECTION BACK HELPER (REQ-004 / REQ-007)
// =============================================

/** Click the Back button on the service selection page (summary bar) */
export async function clickServiceBack(page: Page): Promise<void> {
  const backButton = page.locator('.summary-bar__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

// =============================================
// NOTES PAGE HELPERS (REQ-005)
// =============================================

/**
 * Navigate to the notes page via brand -> location -> service selection -> notes.
 * Selects brake-fluid (no options) as a default service to satisfy the guard.
 * Or selects Inspektion with a default option if serviceNames includes it.
 * Additional services can be selected via the serviceNames parameter.
 */
export async function goToNotesPage(
  page: Page,
  options?: {
    brandName?: string;
    locationName?: string;
    serviceNames?: string[];
  }
): Promise<void> {
  const brandName = options?.brandName;
  const locationName = options?.locationName;
  const defaultServiceNames = options?.serviceNames;

  await goToServiceSelection(page, brandName, locationName);

  // Determine service names to select
  // If none provided, auto-detect language and use brake-fluid (no options, simplest toggle)
  let serviceNames: string[];
  if (defaultServiceNames) {
    serviceNames = defaultServiceNames;
  } else {
    // Auto-detect: check if brake-fluid card has DE or EN title
    const brakeFluidDE = page.locator('.service-card').filter({
      has: page.locator('.service-card__title', { hasText: 'Wechsel Bremsflüssigkeit' })
    });
    const isDE = await brakeFluidDE.isVisible().catch(() => false);
    serviceNames = isDE ? ['Wechsel Bremsflüssigkeit'] : ['Brake Fluid Change'];
  }

  // Select each requested service
  for (const serviceName of serviceNames) {
    // brake-fluid has no options -> direct toggle
    if (serviceName === 'Wechsel Bremsflüssigkeit' || serviceName === 'Brake Fluid Change') {
      await selectService(page, serviceName);
    } else {
      // Services with options need at least 1 checkbox + confirm
      await confirmServiceWithOptions(page, serviceName, [await getFirstOptionText(page, serviceName)]);
    }
  }

  // Click the Continue button on the services page to navigate to /home/notes
  const continueButton = page.locator('.summary-bar__continue-button');
  await expect(continueButton).toBeVisible();
  await expect(continueButton).toBeEnabled();
  await continueButton.click();
  // Wait for notes form to appear
  await page.locator('.notes').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

/** Get the first option text for a service card (used as default selection) */
async function getFirstOptionText(page: Page, serviceName: string): Promise<string> {
  const card = getServiceCard(page, serviceName);

  // Expand card if not already expanded
  const options = card.locator('.service-card__options');
  if (!(await options.isVisible())) {
    await card.click();
    await waitForAngular(page);
  }

  const firstCheckbox = card.locator('.service-card__checkbox').first();
  const text = (await firstCheckbox.textContent() ?? '').trim();

  // Collapse the card again so we start fresh
  await card.click();
  await waitForAngular(page);

  return text;
}

/** Type text into the notes textarea */
export async function enterNote(page: Page, text: string): Promise<void> {
  const textarea = page.locator('.notes-form__textarea');
  await expect(textarea).toBeVisible();
  await textarea.fill(text);
  await waitForAngular(page);
}

/** Get the character counter text (e.g. "5 / 1000") */
export async function getCharCounter(page: Page): Promise<string> {
  const counter = page.locator('.notes-form__counter');
  return (await counter.textContent() ?? '').trim();
}

/** Get visible service hint texts */
export async function getVisibleHintTexts(page: Page): Promise<string[]> {
  const hints = page.locator('.service-hints__text');
  return hints.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Click the Continue button on the notes page */
export async function clickNotesContinue(page: Page): Promise<void> {
  const continueButton = page.locator('.notes__continue-button');
  await expect(continueButton).toBeVisible();
  await continueButton.click();
  await waitForAngular(page);
}

/** Click the Back button on the notes page */
export async function clickNotesBack(page: Page): Promise<void> {
  const backButton = page.locator('.notes__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

// =============================================
// APPOINTMENT SELECTION HELPERS (REQ-006)
// =============================================

/**
 * Navigate to the appointment page via brand -> location -> services -> notes -> appointment.
 * Walks through the full wizard flow to satisfy all guards.
 */
export async function goToAppointmentPage(
  page: Page,
  options?: {
    brandName?: string;
    locationName?: string;
    serviceNames?: string[];
  }
): Promise<void> {
  await goToNotesPage(page, options);

  // Click Continue on notes page to navigate to /home/appointment
  await clickNotesContinue(page);
  // Wait for appointment selection section to appear
  await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

/** Get appointment card count */
export async function getAppointmentCardCount(page: Page): Promise<number> {
  const cards = page.locator('.appointment-card');
  return cards.count();
}

/** Get all appointment card day abbreviation texts (e.g. ['Mo', 'Di', 'Mi', 'Do']) */
export async function getAppointmentDayAbbreviations(page: Page): Promise<string[]> {
  const days = page.locator('.appointment-card__day');
  return days.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Get all appointment card date texts (e.g. ['25.02.2026', '26.02.2026']) */
export async function getAppointmentDates(page: Page): Promise<string[]> {
  const dates = page.locator('.appointment-card__date');
  return dates.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Get all appointment card time texts (e.g. ['09:00 Uhr', '10:30 Uhr']) */
export async function getAppointmentTimes(page: Page): Promise<string[]> {
  const times = page.locator('.appointment-card__time');
  return times.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Select an appointment card by index (0-based) */
export async function selectAppointmentCard(page: Page, index: number): Promise<void> {
  const card = page.locator('.appointment-card').nth(index);
  await expect(card).toBeVisible();
  await card.click();
  await waitForAngular(page);
}

/** Check if an appointment card at the given index is selected */
export async function isAppointmentCardSelected(page: Page, index: number): Promise<boolean> {
  const card = page.locator('.appointment-card').nth(index);
  const classList = await card.getAttribute('class');
  return classList?.includes('appointment-card--selected') ?? false;
}

/** Click the Continue button on the appointment page */
export async function clickAppointmentContinue(page: Page): Promise<void> {
  const continueButton = page.locator('.appointment-selection__continue-button');
  await expect(continueButton).toBeVisible();
  await continueButton.click();
  await waitForAngular(page);
}

/** Click the Back button on the appointment page */
export async function clickAppointmentBack(page: Page): Promise<void> {
  const backButton = page.locator('.appointment-selection__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

/** Get the calendar link element on the appointment page */
export function getCalendarLink(page: Page) {
  return page.locator('.appointment-selection__calendar-link');
}

// =============================================
// WORKSHOP CALENDAR HELPERS (REQ-008)
// =============================================

/**
 * Navigate to the workshop calendar page via the full wizard flow:
 * Brand -> Location -> Services -> Notes -> Appointment -> Calendar Link Click.
 * Walks through all steps to satisfy guards.
 */
export async function goToWorkshopCalendarPage(
  page: Page,
  options?: {
    brandName?: string;
    locationName?: string;
    serviceNames?: string[];
  }
): Promise<void> {
  await goToAppointmentPage(page, options);

  // Click the calendar link on the appointment page to navigate to /home/workshop-calendar
  const calendarLink = getCalendarLink(page);
  await expect(calendarLink).toBeVisible();
  await calendarLink.click();
  // Wait for workshop calendar section to appear
  await page.locator('.workshop-calendar').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

/** Open the MatDatepicker on the workshop calendar page and select today's date */
export async function openDatePickerAndSelectToday(page: Page): Promise<void> {
  // Click the datepicker toggle button to open the calendar
  const toggle = page.locator('mat-datepicker-toggle button');
  await expect(toggle).toBeVisible();
  await toggle.click();
  await waitForAngular(page);

  // Click the today button in the datepicker calendar (it has class .mat-calendar-body-today)
  const todayCell = page.locator('.mat-calendar-body-today');
  await expect(todayCell).toBeVisible();
  await todayCell.click();
  await waitForAngular(page);
}

/** Open the MatDatepicker and select a specific day (1-31) in the currently visible month */
export async function openDatePickerAndSelectDay(page: Page, dayNumber: number): Promise<void> {
  // Click the datepicker toggle button to open the calendar
  const toggle = page.locator('mat-datepicker-toggle button');
  await expect(toggle).toBeVisible();
  await toggle.click();
  await waitForAngular(page);

  // Click the day cell with the given day number
  const dayCell = page.locator(`.mat-calendar-body-cell`).filter({ hasText: new RegExp(`^\\s*${dayNumber}\\s*$`) });
  // There could be multiple matches (prev/next month), click the first enabled one
  const enabledCells = dayCell.locator(':not(.mat-calendar-body-disabled)');
  const count = await enabledCells.count();
  if (count > 0) {
    await enabledCells.first().click();
  } else {
    await dayCell.first().click();
  }
  await waitForAngular(page);
}

/** Get all time-day headings (e.g. ['Mo, 02.03.2026', 'Di, 03.03.2026', 'Mi, 04.03.2026']) */
export async function getTimeDayHeadings(page: Page): Promise<string[]> {
  const headings = page.locator('.time-day__heading');
  return headings.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Get all time slot button texts from all days */
export async function getAllTimeSlotTexts(page: Page): Promise<string[]> {
  const slots = page.locator('.time-slot-btn');
  return slots.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Get time slot button texts for a specific day (0-indexed) */
export async function getTimeSlotsForDay(page: Page, dayIndex: number): Promise<string[]> {
  const dayComponent = page.locator('app-workshop-calendar-day').nth(dayIndex);
  const slots = dayComponent.locator('.time-slot-btn');
  return slots.allTextContents().then(texts => texts.map(t => t.trim()));
}

/** Click a time slot button by its text (e.g. '09:00 Uhr'). Clicks the first match if multiple days have the same slot. */
export async function selectTimeSlot(page: Page, slotText: string): Promise<void> {
  const slot = page.locator('.time-slot-btn', { hasText: slotText }).first();
  await expect(slot).toBeVisible();
  await slot.click();
  await waitForAngular(page);
}

/** Click a time slot button in a specific day (0-indexed) by its text */
export async function selectTimeSlotInDay(page: Page, dayIndex: number, slotText: string): Promise<void> {
  const dayComponent = page.locator('app-workshop-calendar-day').nth(dayIndex);
  const slot = dayComponent.locator('.time-slot-btn', { hasText: slotText });
  await expect(slot).toBeVisible();
  await slot.click();
  await waitForAngular(page);
}

/** Check if a time slot is selected by its text (checks first match) */
export async function isTimeSlotSelected(page: Page, slotText: string): Promise<boolean> {
  const slot = page.locator('.time-slot-btn', { hasText: slotText }).first();
  const classList = await slot.getAttribute('class');
  return classList?.includes('time-slot-btn--selected') ?? false;
}

/** Check if a time slot in a specific day (0-indexed) is selected */
export async function isTimeSlotSelectedInDay(page: Page, dayIndex: number, slotText: string): Promise<boolean> {
  const dayComponent = page.locator('app-workshop-calendar-day').nth(dayIndex);
  const slot = dayComponent.locator('.time-slot-btn', { hasText: slotText });
  const classList = await slot.getAttribute('class');
  return classList?.includes('time-slot-btn--selected') ?? false;
}

/** Click the Back button on the workshop calendar page */
export async function clickWorkshopCalendarBack(page: Page): Promise<void> {
  const backButton = page.locator('.wizard-nav__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

/** Click the Continue button on the workshop calendar page */
export async function clickWorkshopCalendarContinue(page: Page): Promise<void> {
  const continueButton = page.locator('.wizard-nav__continue-button');
  await expect(continueButton).toBeVisible();
  await continueButton.click();
  await waitForAngular(page);
}

/** Get the hint text shown on the right panel before date selection */
export async function getWorkshopCalendarHintText(page: Page): Promise<string> {
  const hint = page.locator('.right-panel__hint');
  return (await hint.textContent() ?? '').trim();
}

/** Get the intro text shown on the right panel after date selection */
export async function getWorkshopCalendarIntroText(page: Page): Promise<string> {
  const intro = page.locator('.slots__intro');
  return (await intro.textContent() ?? '').trim();
}

// =============================================
// CAR INFORMATION HELPERS (REQ-009)
// =============================================

/**
 * Navigate to the carinformation page via the full wizard flow:
 * Brand -> Location -> Services -> Notes -> Appointment (select first) -> Continue.
 */
export async function goToCarinformationPage(
  page: Page,
  options?: {
    brandName?: string;
    locationName?: string;
    serviceNames?: string[];
  }
): Promise<void> {
  await goToAppointmentPage(page, options);
  await selectAppointmentCard(page, 0);
  await clickAppointmentContinue(page);
  await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

/** Fill in the customer form with valid test data */
export async function fillCustomerForm(
  page: Page,
  data?: {
    email?: string;
    firstName?: string;
    lastName?: string;
    street?: string;
    postalCode?: string;
    city?: string;
    mobilePhone?: string;
  }
): Promise<void> {
  const d = {
    email: data?.email ?? 'max@mustermann.de',
    firstName: data?.firstName ?? 'Max',
    lastName: data?.lastName ?? 'Mustermann',
    street: data?.street ?? 'Musterweg 1',
    postalCode: data?.postalCode ?? '30159',
    city: data?.city ?? 'Berlin',
    mobilePhone: data?.mobilePhone ?? '017012345678',
  };
  await page.locator('input[formcontrolname="email"]').fill(d.email);
  await page.locator('input[formcontrolname="firstName"]').fill(d.firstName);
  await page.locator('input[formcontrolname="lastName"]').fill(d.lastName);
  await page.locator('input[formcontrolname="street"]').fill(d.street);
  await page.locator('input[formcontrolname="postalCode"]').fill(d.postalCode);
  await page.locator('input[formcontrolname="city"]').fill(d.city);
  await page.locator('input[formcontrolname="mobilePhone"]').fill(d.mobilePhone);
  await waitForAngular(page);
}

/** Fill in the vehicle form with valid test data */
export async function fillVehicleForm(
  page: Page,
  data?: {
    licensePlate?: string;
    mileage?: string;
  }
): Promise<void> {
  const d = {
    licensePlate: data?.licensePlate ?? 'B-MS1234',
    mileage: data?.mileage ?? '5000',
  };
  await page.locator('input[formcontrolname="licensePlate"]').fill(d.licensePlate);
  await page.locator('input[formcontrolname="mileage"]').fill(d.mileage);
  await waitForAngular(page);
}

/** Check the privacy consent checkbox */
export async function acceptPrivacyConsent(page: Page): Promise<void> {
  const checkbox = page.locator('mat-checkbox .mdc-checkbox__native-control, mat-checkbox input[type="checkbox"]').first();
  await checkbox.click({ force: true });
  await waitForAngular(page);
}

/** Click the Continue button on the carinformation page */
export async function clickCarinformationContinue(page: Page): Promise<void> {
  const continueButton = page.locator('.carinformation__continue-button');
  await expect(continueButton).toBeVisible();
  await continueButton.click();
  await waitForAngular(page);
}

/** Click the Back button on the carinformation page */
export async function clickCarinformationBack(page: Page): Promise<void> {
  const backButton = page.locator('.carinformation__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

// =============================================
// BOOKING OVERVIEW HELPERS (REQ-010)
// =============================================

/**
 * Navigate to the booking overview page via the full wizard flow:
 * Brand -> Location -> Services -> Notes -> Appointment -> Continue ->
 * Car Information (fill forms + privacy) -> Continue.
 */
export async function goToBookingOverviewPage(
  page: Page,
  options?: {
    brandName?: string;
    locationName?: string;
    serviceNames?: string[];
    customer?: {
      email?: string;
      firstName?: string;
      lastName?: string;
      street?: string;
      postalCode?: string;
      city?: string;
      mobilePhone?: string;
    };
    vehicle?: {
      licensePlate?: string;
      mileage?: string;
      vin?: string;
    };
  }
): Promise<void> {
  await goToCarinformationPage(page, options);
  await fillCustomerForm(page, options?.customer);
  await fillVehicleForm(page, options?.vehicle);
  await acceptPrivacyConsent(page);
  await clickCarinformationContinue(page);
  await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10000 });
  await waitForAngular(page);
}

/** Click the Back button on the booking overview page */
export async function clickBookingOverviewBack(page: Page): Promise<void> {
  const backButton = page.locator('.booking-overview__back-button');
  await expect(backButton).toBeVisible();
  await backButton.click();
  await waitForAngular(page);
}

/** Click the Submit button on the booking overview page */
export async function clickBookingOverviewSubmit(page: Page): Promise<void> {
  const submitButton = page.locator('.booking-overview__submit-button');
  await expect(submitButton).toBeVisible();
  await submitButton.click();
  await waitForAngular(page);
}
