import { expect, type Page, test } from '@playwright/test';

import { waitForAngular } from './helpers/app.helpers';
import {
  acceptPrivacyConsent,
  clickBookingOverviewSubmit,
  clickCarinformationContinue,
  fillCustomerForm,
  fillVehicleForm,
  goToBookingOverviewPage,
  goToCarinformationPage,
  goToCarinformationWithStoredTires,
} from './helpers/booking.helpers';

/**
 * REQ-043: Bugfixing + KPI.
 *
 * - B-1: the license plate holds at most 20 chars (SoftNet `car.licenseNr`, HTTP 500 beyond).
 * - B-2: a wheel/tire change booking sends `tireChangeBooking: true`, the backend then appends
 *   „Buchung erfolgte über OTV" to the note (asserted in AppointmentsServiceTest).
 * - K-2: S0 „Auf einen Blick" is hidden on the statistics tab.
 *
 * The final `POST /api/v1/appointments` is answered locally (as in REQ-028) — no real booking
 * reaches SoftNet. Credentials as in REQ-037: REPORT_ADMIN_PASSWORD=e2e-local-secret.
 */

const ADMIN_USER = process.env['REPORT_ADMIN_USER'] ?? 'admin';
const ADMIN_PASSWORD = process.env['REPORT_ADMIN_PASSWORD'] ?? 'e2e-local-secret';

const VALID_VIN = 'WVWZZZ1JZXW000001';

/** Stubs the booking write and returns a getter for the captured request body. */
async function captureBooking(page: Page): Promise<() => Record<string, unknown> | null> {
  let body: Record<string, unknown> | null = null;
  await page.route('**/api/v1/appointments', async (route) => {
    if (route.request().method() === 'POST') {
      body = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: { id: 'e2e-req043', status: 'confirmed' },
          meta: { timestamp: '2026-10-29T00:00:00Z', requestId: 'e2e-req043' },
        }),
      });
      return;
    }
    await route.continue();
  });
  return () => body;
}

test.describe('REQ-043 B-1: license plate limit', () => {
  test('the plate input stops at 20 characters', async ({ page }) => {
    await goToCarinformationPage(page);

    const plate = page.locator('#vehicle-licensePlate');
    await expect(plate).toHaveAttribute('maxlength', '20');
    await plate.pressSequentially('max.mustermann@example.de');
    await expect(plate).toHaveValue('max.mustermann@examp');
  });
});

test.describe('REQ-043 B-2: tire change flag on the booking', () => {
  test('a stored-tires booking sends tireChangeBooking=true', async ({ page }) => {
    const sentBooking = await captureBooking(page);
    await goToCarinformationWithStoredTires(page);
    await fillCustomerForm(page);
    await fillVehicleForm(page, { vin: VALID_VIN });
    await acceptPrivacyConsent(page);
    await clickCarinformationContinue(page);
    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
    await waitForAngular(page);

    await clickBookingOverviewSubmit(page);

    await expect.poll(() => sentBooking()).not.toBeNull();
    expect(sentBooking()?.['tireChangeBooking']).toBe(true);
  });

  test('a booking without tire change sends no flag', async ({ page }) => {
    const sentBooking = await captureBooking(page);
    await goToBookingOverviewPage(page);

    await clickBookingOverviewSubmit(page);

    await expect.poll(() => sentBooking()).not.toBeNull();
    expect(sentBooking()?.['tireChangeBooking']).toBeUndefined();
  });
});

test.describe('REQ-043 K-2: statistics tab without „Auf einen Blick"', () => {
  test.use({ httpCredentials: { username: ADMIN_USER, password: ADMIN_PASSWORD } });

  test('neither section nor jump link of S0 is rendered', async ({ page }) => {
    await page.goto('/internal/report');

    await expect(page.locator('section.us')).toBeVisible();
    await expect(page.locator('section.ib')).toHaveCount(0);
    await expect(page.locator('nav.st-toc a[href="#briefing"]')).toHaveCount(0);
  });
});
