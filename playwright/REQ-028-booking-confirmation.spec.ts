import { expect, test } from '@playwright/test';

import { setLanguage } from './helpers/app.helpers';
import { clickBookingOverviewSubmit, goToBookingOverviewPage } from './helpers/booking.helpers';

/**
 * REQ-028: Buchungsbestätigung (Confirmation Message)
 *
 * Happy path: after a successful submission a prominent confirmation message
 * appears directly below the submit button (in addition to the button text
 * switching to "Anfrage gesendet"). The message is hidden before submission.
 *
 * The full wizard (branches, snaps/availability, navigation) runs against the real
 * backend. Only the final, state-mutating `POST /api/v1/appointments` write is
 * stubbed so the test is deterministic and does not create real bookings on the
 * shared SoftNet INT system (which would otherwise conflict across parallel
 * workers/viewports competing for the same slot).
 */
test.describe('REQ-028: Booking confirmation message', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/appointments', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { id: 'e2e-req028-confirmation', status: 'confirmed' },
            meta: { timestamp: '2026-06-19T00:00:00Z', requestId: 'e2e-req028' },
          }),
        });
        return;
      }
      await route.continue();
    });
  });

  test('shows the highlighted confirmation below the submit button after submitting (DE)', async ({ page }) => {
    await setLanguage(page, 'de');
    await goToBookingOverviewPage(page);

    const confirmation = page.locator('.booking-overview__confirmation');

    // Before submitting there is no confirmation message.
    await expect(confirmation).toHaveCount(0);

    await clickBookingOverviewSubmit(page);

    // After a successful submission the confirmation appears.
    await expect(confirmation).toBeVisible({ timeout: 15_000 });
    await expect(confirmation).toContainText(
      'Vielen Dank, Ihr Termin ist reserviert. Wir freuen uns auf Ihren Besuch.'
    );

    // A11y: announced politely to screen readers.
    await expect(confirmation).toHaveAttribute('role', 'status');
    await expect(confirmation).toHaveAttribute('aria-live', 'polite');

    // The existing submit-button toggle is preserved.
    await expect(page.locator('.booking-overview__submit-button')).toContainText('Anfrage gesendet');
  });

  test('shows the confirmation in English after submitting', async ({ page }) => {
    await setLanguage(page, 'en');
    await goToBookingOverviewPage(page);

    await clickBookingOverviewSubmit(page);

    const confirmation = page.locator('.booking-overview__confirmation');
    await expect(confirmation).toBeVisible({ timeout: 15_000 });
    await expect(confirmation).toContainText(
      'Thank you, your appointment is reserved. We look forward to your visit.'
    );
  });
});
