import { expect, type Page, test } from '@playwright/test';

import { clickNotesContinue, goToNotesPage, selectFirstLocation } from './helpers/booking.helpers';

/**
 * REQ-044: Rückrufaktion — Lieferengpass-Hinweis statt „Keine Termine verfügbar.“
 *
 * Findet die Terminsuche im Rückruf-Modus (REQ-041) keinen Slot, nennt der Leerzustand der
 * Terminauswahl den Lieferengpass der Schraube und verweist auf einen späteren Zeitpunkt oder einen
 * anderen Standort. Jeder andere Einstieg behält den allgemeinen Text. Die Terminsuche
 * (`POST /api/v1/snaps`) wird lokal mit „kein Termin“ beantwortet; Standorte, Marken und Hinweise
 * kommen wie in REQ-041 vom lokalen Backend. Es entsteht keine Buchung.
 */

const RECALL_VIN = 'WVWZZZ1KZAW000123';

const RECALL_TITLE = 'Aufgrund von Lieferengpässen der Schraube sind aktuell in diesem Standort keine Termine verfügbar.';
const RECALL_HINT = 'Bitte versuchen Sie es zu einem späteren Zeitpunkt oder versuchen Sie es in einem unserer anderen Standorte.';
const GENERIC_TITLE = 'Keine Termine verfügbar.';
const GENERIC_HINT = 'Bitte versuchen Sie es erneut oder wählen Sie einen anderen Standort oder Service.';

interface SnapRequest {
  variant?: string;
}

/** Answers every appointment search with „kein Termin“ and hands back the captured request bodies. */
async function stubEmptySnaps(page: Page): Promise<SnapRequest[]> {
  const requests: SnapRequest[] = [];
  await page.route('**/api/v1/snaps', async (route) => {
    requests.push(route.request().postDataJSON() as SnapRequest);
    await route.fulfill({ json: { data: [] } });
  });
  return requests;
}

test.describe('REQ-044 Rückrufaktion — keine Termine', () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(120_000);
    // German UI from the very first render (the helper in REQ-041 only sets it after the load).
    await page.addInitScript(() => {
      window.localStorage.setItem('app-language', 'de');
    });
  });

  test('Rückruf-Modus: der Leerzustand nennt den Lieferengpass, „Neu laden“ bleibt', async ({ page }) => {
    const snapRequests = await stubEmptySnaps(page);

    await page.goto(`/home/location?recall=true&vin=${RECALL_VIN}`);
    await selectFirstLocation(page);
    await page.locator('.brand-card').first().waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('.brand-card').first().click();
    await page.locator('.notes').waitFor({ state: 'visible', timeout: 15_000 });
    await clickNotesContinue(page);
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 15_000 });

    const empty = page.locator('.appointment-selection__empty');
    await expect(empty).toBeVisible();
    await expect(empty.locator('strong')).toHaveText(RECALL_TITLE);
    await expect(empty).toContainText(RECALL_HINT);
    await expect(empty).not.toContainText(GENERIC_TITLE);
    await expect(empty).not.toContainText(GENERIC_HINT);

    // The search really ran in the recall group (otherwise this would not be the recall mode).
    expect(snapRequests.at(-1)?.variant).toBe('recall');

    // „Neu laden“ stays and repeats the search.
    const searchesBeforeReload = snapRequests.length;
    await page.locator('.appointment-selection__reload-button').click();
    await expect.poll(() => snapRequests.length).toBeGreaterThan(searchesBeforeReload);
    await expect(empty).toContainText(RECALL_TITLE);
  });

  test('Normalmodus: der Leerzustand behält den allgemeinen Text', async ({ page }) => {
    const snapRequests = await stubEmptySnaps(page);

    await page.goto('/home/location');
    await goToNotesPage(page);
    await clickNotesContinue(page);
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 15_000 });

    const empty = page.locator('.appointment-selection__empty');
    await expect(empty).toBeVisible();
    await expect(empty.locator('strong')).toHaveText(GENERIC_TITLE);
    await expect(empty).toContainText(GENERIC_HINT);
    await expect(empty).not.toContainText('Lieferengpässen');

    expect(snapRequests.at(-1)?.variant).toBe('default');
  });
});
