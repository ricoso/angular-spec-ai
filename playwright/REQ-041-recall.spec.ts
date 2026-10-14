import { expect, test } from '@playwright/test';

import { ensureGermanLanguage } from './helpers/app.helpers';
import {
  acceptPrivacyConsent,
  clickAppointmentContinue,
  clickCarinformationContinue,
  clickNotesContinue,
  fillCustomerForm,
  selectAppointmentCard,
  selectFirstLocation
} from './helpers/booking.helpers';

/**
 * REQ-041: Rückrufaktion-Einstieg ins OTV. Die Rückruf-URL (`?recall=true&vin=…`) startet direkt in
 * der Standortauswahl (Rückruf-Banner), der Service-Schritt entfällt (eigene Rückruf-Group, ein fest
 * ausgewählter Service), und die FIN ist aus der URL vorbefüllt + Pflicht. Läuft real gegen INT —
 * die Rückruf-Group (soft-net) liefert echte Snaps. Kein finaler Submit (Stopp auf der Übersicht).
 */
const RECALL_VIN = 'WVWZZZ1KZAW000123';

test.describe('REQ-041 Rückrufaktion — Deep-Link-Einstieg', () => {
  test.beforeEach(async ({ page }) => {
    test.setTimeout(120_000);
    await page.goto(`/home/location?recall=true&vin=${RECALL_VIN}`);
    await ensureGermanLanguage(page);
  });

  test('Einstieg zeigt Rückruf-Banner auf der Standortauswahl, Service-Schritt entfällt', async ({ page }) => {
    await expect(page.locator('.location-selection__recall')).toBeVisible();
    await expect(page.locator('.location-selection__recall-vin')).toContainText(RECALL_VIN);
    // Kein separater Hinweis „Serviceauswahl entfällt" im Banner (Nutzerentscheid).
    await expect(page.locator('.location-selection__recall')).not.toContainText('Serviceauswahl entfällt');
    // Service-Schritt ist im Breadcrumb ausgeblendet.
    await expect(page.locator('.wizard__step-button[aria-label="Service"]')).toHaveCount(0);
    // Standortliste ohne Radwechsel-/Boxenstopp-Karten (nur echte Standorte).
    await expect(page.locator('.location-card').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('input.location-grid__radio[id$="__tireChange"]')).toHaveCount(0);
    // Interner Fehler-Report-Button ist für Rückruf-Kunden ausgeblendet.
    await expect(page.locator('.report-status-button')).toHaveCount(0);
    // Standorte alphabetisch nach Stadt (Karte zeigt "Straße, PLZ Stadt").
    const addresses = await page.locator('.location-card__address').allTextContents();
    const cities = addresses.map(address => address.trim().replace(/^.*\b\d{5}\s+/, ''));
    const collator = new Intl.Collator('de', { sensitivity: 'base' });
    expect(cities).toEqual([...cities].sort(collator.compare));
  });

  test('Happy-Path: Standort → Marke → (Service entfällt) → Hinweise → Termin → FIN vorbefüllt → Übersicht', async ({ page }) => {
    await selectFirstLocation(page);

    // Marke wählen → im Rückruf-Modus direkt zu Hinweise (Service übersprungen).
    await page.locator('.brand-card').first().waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('.brand-card').first().click();
    await page.locator('.notes').waitFor({ state: 'visible', timeout: 15_000 });
    await expect(page).toHaveURL(/\/home\/notes/);
    // Rückruf-Group hat keine Mietwagen-Kategorien → kein Mietwagen-Schalter, sondern Hinweis
    // (Kategorien kommen aus derselben Group wie Snaps/Buchung).
    await expect(page.locator('.notes-extras__rental-unavailable')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('#rental-heading')).toHaveCount(0);

    await clickNotesContinue(page);
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 15_000 });
    await selectAppointmentCard(page, 0);
    await clickAppointmentContinue(page);

    await page.locator('.carinformation').waitFor({ state: 'visible', timeout: 15_000 });
    // FIN aus der URL vorbefüllt + Pflichtfeld sichtbar.
    await expect(page.locator('#vehicle-vin')).toBeVisible();
    await expect(page.locator('input[formcontrolname="vin"]')).toHaveValue(RECALL_VIN);

    await fillCustomerForm(page);
    await page.locator('input[formcontrolname="licensePlate"]').fill('B-RC 1234');
    await page.locator('input[formcontrolname="mileage"]').fill('50000');
    await acceptPrivacyConsent(page);
    await clickCarinformationContinue(page);

    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 15_000 });
    await expect(page.locator('.booking-overview')).toContainText(RECALL_VIN);
  });

  test('Rückruf-Modus: "Zurück" auf Hinweise führt zur Marke, nicht in den ausgeblendeten Service-Schritt', async ({ page }) => {
    await selectFirstLocation(page);
    await page.locator('.brand-card').first().waitFor({ state: 'visible', timeout: 15_000 });
    await page.locator('.brand-card').first().click();
    await page.locator('.notes').waitFor({ state: 'visible', timeout: 15_000 });
    await expect(page).toHaveURL(/\/home\/notes/);

    // Der Service-Schritt ist im Rückruf-Modus übersprungen — "Zurück" darf ihn nicht öffnen,
    // sonst überschreibt der Default-Katalog die Rückruf-Operation (falsche Abrechnung).
    await page.locator('.notes__back-button').click();
    await expect(page).toHaveURL(/\/home\/brand/, { timeout: 15_000 });
  });
});
