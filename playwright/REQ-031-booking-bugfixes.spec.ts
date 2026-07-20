import { expect, type Page, type Route, test } from '@playwright/test';

import { setLanguage, waitForAngular } from './helpers/app.helpers';
import {
  acceptPrivacyConsent,
  clickBookingOverviewSubmit,
  fillCustomerForm,
  goToBookingOverviewPage,
  goToCarinformationPage,
  goToWorkshopCalendarPage,
  openDatePickerAndSelectToday,
  selectAppointmentCard,
} from './helpers/booking.helpers';

/**
 * REQ-031: Booking bugfixes — snap conflict (HTTP 410) + mileage validation.
 *
 * TC-A: Mileage validation on the vehicle form (#vehicle-mileage):
 *       required, whole number >= 10, permanent hint, error messages,
 *       submit blocked while invalid.
 * TC-B: Snap conflict: POST /api/v1/appointments answers 410 (snap taken in
 *       the meantime) -> automatic redirect to /home/appointment with the
 *       warn banner (.snap-conflict-banner), dismissible via X button,
 *       slots selectable again afterwards.
 * TC-C: All previously entered form data (license plate + mileage) survives
 *       the 410 redirect and prefills the vehicle form again.
 * TC-D: Bugfix 3 (workshop calendar timezone, AC-17/AC-18): the time shown in
 *       the workshop calendar (.time-slot-btn) is a plausible Berlin opening
 *       time (>= 07:00, not the raw UTC which would be 2h earlier) and matches
 *       exactly the time later shown in the booking overview appointment tile —
 *       no UTC/Berlin drift between the two views.
 *
 * The full wizard (branches, snaps/availability, navigation) runs against the
 * real backend. Only the final, state-mutating `POST /api/v1/appointments`
 * write is stubbed (same pattern as REQ-028) so the test is deterministic and
 * does not create real bookings on the shared SoftNet INT system. GET requests
 * to the same URL pattern are passed through untouched.
 */

const APPOINTMENTS_ENDPOINT = '**/api/v1/appointments';

/** Vehicle test data — mileage must be >= 10 (REQ-031 BR-6) to reach the overview. */
const VEHICLE = { licensePlate: 'E-RQ0031', mileage: '54321' };

/** Route handler: only POST (booking submit) answers 410 snapGone; everything else continues. */
async function fulfillPostWithSnapGone(route: Route): Promise<void> {
  if (route.request().method() === 'POST') {
    await route.fulfill({
      status: 410,
      contentType: 'application/json',
      body: JSON.stringify({
        statusCode: 410,
        messageKey: 'error.snapGone',
        message: 'Requested time slot is no longer available',
      }),
    });
    return;
  }
  await route.continue();
}

/**
 * Walk the full wizard to the booking overview, stub the booking POST with 410,
 * submit, and wait for the automatic redirect back to the appointment page.
 */
async function submitBookingIntoSnapConflict(page: Page): Promise<void> {
  await goToBookingOverviewPage(page, { vehicle: VEHICLE });

  await page.route(APPOINTMENTS_ENDPOINT, fulfillPostWithSnapGone);

  await clickBookingOverviewSubmit(page);

  // REQ-031: 410 -> automatic redirect back to the appointment selection page.
  await page.waitForURL(/\/home\/appointment/, { timeout: 15_000 });
  await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10_000 });
}

test.describe('REQ-031: Booking bugfixes (snap conflict + mileage validation)', () => {
  // Full wizard flows against the real backend need more headroom than the 30s default.
  test.describe.configure({ timeout: 90_000 });

  test('TC-A: mileage validation — hint, required error, min error, valid value navigates to overview (DE)', async ({ page }) => {
    await setLanguage(page, 'de');
    await goToCarinformationPage(page);

    const mileageInput = page.locator('#vehicle-mileage');
    const mileageError = page.locator('#vehicle-mileage-error');
    const mileageHint = page.locator('#vehicle-mileage-hint');
    const continueButton = page.locator('.carinformation__continue-button');

    // Permanent hint below the field (visible before any interaction).
    await expect(mileageHint).toBeVisible();
    await expect(mileageHint).toContainText('Bitte Werte ab 10 km eingeben');

    // Fill everything else validly so only the mileage blocks submission.
    await fillCustomerForm(page);
    await page.locator('#vehicle-licensePlate').fill(VEHICLE.licensePlate);
    await acceptPrivacyConsent(page);

    // 1) Empty mileage -> submit -> required error, page does not change.
    await expect(mileageInput).toHaveValue('');
    await continueButton.click();
    await waitForAngular(page);

    await expect(mileageError).toBeVisible();
    await expect(mileageError).toHaveAttribute('role', 'alert');
    await expect(mileageError).toContainText('Bitte geben Sie den Kilometerstand ein.');
    expect(page.url()).toContain('/home/carinformation');
    await expect(page.locator('.carinformation')).toBeVisible();

    // 2) Mileage "0" (below minimum 10) -> min error, submit still blocked.
    await mileageInput.fill('0');
    await continueButton.click();
    await waitForAngular(page);

    await expect(mileageError).toBeVisible();
    await expect(mileageError).toContainText('Bitte geben Sie einen Kilometerstand von mindestens 10 km ein.');
    expect(page.url()).toContain('/home/carinformation');

    // 3) Valid value -> error disappears, hint stays, submit navigates to the overview.
    await mileageInput.fill('50000');
    await waitForAngular(page);
    await expect(mileageError).toHaveCount(0);
    await expect(mileageHint).toBeVisible();

    await continueButton.click();
    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
    await page.waitForURL(/\/home\/booking-overview/, { timeout: 10_000 });
  });

  test('TC-B: 410 on submit -> redirect to appointment page, warn banner shown, dismissible via X, slot selectable again (DE)', async ({ page }) => {
    await setLanguage(page, 'de');
    await submitBookingIntoSnapConflict(page);

    // Warn banner with role="alert", title and message about the taken slot.
    const banner = page.locator('.snap-conflict-banner');
    await expect(banner).toBeVisible({ timeout: 10_000 });
    await expect(banner).toHaveAttribute('role', 'alert');

    const bannerTitle = banner.locator('.snap-conflict-banner__title');
    await expect(bannerTitle).toContainText('Wunschtermin nicht mehr verfügbar');

    const bannerMessage = banner.locator('.snap-conflict-banner__message');
    await expect(bannerMessage).toContainText('belegt');

    // X button dismisses the banner.
    const closeButton = banner.locator('.snap-conflict-banner__close');
    await expect(closeButton).toBeVisible();
    await expect(closeButton).toHaveAttribute('aria-label', 'Hinweis schließen');
    await closeButton.click();
    await waitForAngular(page);
    await expect(banner).toHaveCount(0);

    // Remove the 410 stub so a fresh booking attempt would go through again.
    await page.unroute(APPOINTMENTS_ENDPOINT);

    // Appointment can be picked again: time slots are visible and selectable.
    const slots = page.locator('app-appointment-card .slot');
    await expect(slots.first()).toBeVisible({ timeout: 15_000 });

    await selectAppointmentCard(page, 0);
    await expect(page.locator('.appointment-selection__summary')).toBeVisible();
    await expect(page.locator('.appointment-selection__continue-button')).toBeEnabled();
  });

  test('TC-C: form data preserved after 410 redirect — vehicle form is prefilled again (DE)', async ({ page }) => {
    await setLanguage(page, 'de');
    await submitBookingIntoSnapConflict(page);

    // Banner confirms the conflict flow before we move on.
    await expect(page.locator('.snap-conflict-banner')).toBeVisible({ timeout: 10_000 });

    // Remove the 410 stub before continuing through the wizard again.
    await page.unroute(APPOINTMENTS_ENDPOINT);

    // Pick a fresh slot and continue to the car information page.
    await selectAppointmentCard(page, 0);
    const continueButton = page.locator('.appointment-selection__continue-button');
    await expect(continueButton).toBeEnabled();
    await continueButton.click();
    await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10_000 });
    await waitForAngular(page);

    // Vehicle data entered before the conflict is prefilled again (REQ-031 AC).
    await expect(page.locator('#vehicle-licensePlate')).toHaveValue(VEHICLE.licensePlate);
    await expect(page.locator('#vehicle-mileage')).toHaveValue(VEHICLE.mileage);

    // Customer data survives as well (spot check).
    await expect(page.locator('input[formcontrolname="email"]')).toHaveValue('max@mustermann.de');

    // No validation errors on the prefilled form.
    await expect(page.locator('#vehicle-mileage-error')).toHaveCount(0);
  });

  test('TC-D: workshop calendar time is Berlin time and matches the booking overview (no UTC drift) (DE)', async ({ page }) => {
    await setLanguage(page, 'de');

    // 1) Walk the wizard to the workshop calendar.
    await goToWorkshopCalendarPage(page);

    // 2) Pick a date so the 3-day slot grid renders (REQ-008 TC-4 pattern).
    await openDatePickerAndSelectToday(page);

    // 3) Read the first available slot's displayed time — this is the calendar
    //    time, now converted to Berlin time by the berlinTime pipe (Bugfix 3).
    const firstSlot = page.locator('.time-slot-btn').first();
    await expect(firstSlot).toBeVisible({ timeout: 15_000 });
    const calendarTime = (await firstSlot.textContent() ?? '').trim();

    // Format must be "HH:MM Uhr" and a plausible workshop opening time.
    // The raw UTC value would be 2h earlier (MESZ = UTC+2), so >= 07:00 proves
    // the Berlin conversion happened and the raw UTC is not shown.
    const match = /^(\d{2}):(\d{2}) Uhr$/.exec(calendarTime);
    expect(match, `calendar slot text should be "HH:MM Uhr", got "${calendarTime}"`).not.toBeNull();
    const calendarHour = Number(match?.[1]);
    expect(calendarHour).toBeGreaterThanOrEqual(7);

    // 4) Select that slot, continue to the car information page.
    await firstSlot.click();
    await waitForAngular(page);
    await expect(firstSlot).toHaveClass(/time-slot-btn--selected/);

    const continueButton = page.locator('.wizard-nav__continue-button');
    await expect(continueButton).toBeEnabled();
    await continueButton.click();
    await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 10_000 });
    await waitForAngular(page);

    // Fill vehicle + customer + privacy, then go to the booking overview.
    await page.locator('#vehicle-licensePlate').fill(VEHICLE.licensePlate);
    await page.locator('#vehicle-mileage').fill(VEHICLE.mileage);
    await fillCustomerForm(page);
    await acceptPrivacyConsent(page);

    await page.locator('.carinformation__continue-button').click();
    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
    await waitForAngular(page);

    // 5) Read the time shown in the booking overview appointment tile.
    //    Value format: "<displayDate> — HH:MM Uhr" — extract the time part.
    const overviewValue = (
      await page.locator('app-appointment-tile .summary-row__value').textContent() ?? ''
    ).trim();
    const overviewMatch = /(\d{2}:\d{2} Uhr)/.exec(overviewValue);
    expect(overviewMatch, `overview value should contain "HH:MM Uhr", got "${overviewValue}"`).not.toBeNull();
    const overviewTime = overviewMatch?.[1] ?? '';

    // 6) Core assertion: calendar time === overview time (no UTC/Berlin drift).
    expect(overviewTime).toBe(calendarTime);
  });
});
