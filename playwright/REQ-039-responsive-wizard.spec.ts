import { expect, type Page, test } from '@playwright/test';

import { ensureGermanLanguage, waitForAngular } from './helpers/app.helpers';
import {
  acceptPrivacyConsent,
  clickCarinformationContinue,
  clickNotesContinue,
  fillCustomerForm,
  fillVehicleForm,
  goToAppointmentPage,
  goToBookingOverviewPage,
  goToBrandSelection,
  goToCarinformationPage,
  goToLocationSelection,
  goToNotesPage,
  goToServiceSelection
} from './helpers/booking.helpers';

/**
 * REQ-039: Responsive Wizard + Mietwagen-Verfügbarkeit pro Termin (Abschnitt 18) + freiwillige FIN.
 * Läuft gegen das echte Backend; die Mietwagen-UI-Fälle mocken `rentcategories` und `snaps`,
 * damit sie unabhängig vom SoftNet-Bestand der Test-Filiale deterministisch sind.
 */

/** AC-11: Breiten aus TC-12. */
const AUDIT_WIDTHS = [320, 375, 390, 768, 1024, 1280] as const;

interface WidthFinding {
  width: number;
  scrollWidth: number;
  smallTargets: number;
  overlappingTargets: number;
}

/** TC-12/TC-13: kein horizontales Scrollen, Schrittziele ≥ 44 × 44 CSS-Pixel ohne Überlappung. */
async function auditWidths(page: Page): Promise<WidthFinding[]> {
  const findings: WidthFinding[] = [];
  for (const width of AUDIT_WIDTHS) {
    await page.setViewportSize({ width, height: 900 });
    await page.waitForTimeout(150);
    findings.push(await page.evaluate((w) => {
      const rects = Array.from(document.querySelectorAll('.wizard__step-button'))
        .map(el => el.getBoundingClientRect());
      const smallTargets = rects.filter(r => r.width < 43.5 || r.height < 43.5).length;
      let overlappingTargets = 0;
      for (let i = 1; i < rects.length; i++) {
        if (rects[i].left < rects[i - 1].right - 0.5 && rects[i].top < rects[i - 1].bottom - 0.5) {
          overlappingTargets++;
        }
      }
      return { width: w, scrollWidth: document.documentElement.scrollWidth, smallTargets, overlappingTargets };
    }, width));
  }
  return findings;
}

function expectResponsive(findings: WidthFinding[]): void {
  for (const f of findings) {
    expect.soft(f.scrollWidth, `kein horizontales Scrollen bei ${f.width}px`).toBeLessThanOrEqual(f.width + 1);
    expect.soft(f.smallTargets, `Schrittziele ≥ 44px bei ${f.width}px`).toBe(0);
    expect.soft(f.overlappingTargets, `keine überlappenden Schrittziele bei ${f.width}px`).toBe(0);
  }
}

test.describe('REQ-039 Responsive Wizard — Breiten 320 bis 1280 (TC-12, TC-13)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium-desktop', 'Breitenschleife nur einmal (Desktop-Projekt)');
    // Voller Wizard-Durchlauf gegen SoftNet + sechs Breiten — 30 s Default reicht unter Last nicht.
    test.setTimeout(120_000);
    await page.goto('/home/location');
    await ensureGermanLanguage(page);
  });

  test('Standort', async ({ page }) => {
    await goToLocationSelection(page);
    expectResponsive(await auditWidths(page));
  });

  test('Marke', async ({ page }) => {
    await goToBrandSelection(page);
    expectResponsive(await auditWidths(page));
  });

  test('Service', async ({ page }) => {
    await goToServiceSelection(page);
    expectResponsive(await auditWidths(page));
  });

  test('Hinweise', async ({ page }) => {
    await goToNotesPage(page);
    expectResponsive(await auditWidths(page));
  });

  test('Termin', async ({ page }) => {
    await goToAppointmentPage(page);
    expectResponsive(await auditWidths(page));
  });

  test('Kontakt- & Fahrzeugdaten', async ({ page }) => {
    await goToCarinformationPage(page);
    expectResponsive(await auditWidths(page));
  });

  test('Übersicht', async ({ page }) => {
    await goToBookingOverviewPage(page);
    expectResponsive(await auditWidths(page));
  });
});

test.describe('REQ-039 Breadcrumb — mobiler Rücksprung und Statusanzeige (TC-4, TC-6, TC-16)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/home/location');
    await ensureGermanLanguage(page);
  });

  test('Icon-Klick auf früheren Schritt navigiert zurück, aktive/zukünftige Ziele bleiben wirkungslos', async ({ page }) => {
    await goToNotesPage(page);
    await page.setViewportSize({ width: 390, height: 844 });

    await expect(page.locator('.wizard__current-title')).toHaveCount(1);
    await expect(page.locator('.wizard__step-button[aria-current="step"]')).toHaveCount(1);
    await expect(page.locator('.wizard__current-title')).toHaveText('Hinweise');

    // Zukünftiger Schritt (Termin): fokussierbar, aria-disabled, kein Routenwechsel
    const future = page.locator('.wizard__step-button[aria-disabled="true"]').last();
    await future.click({ force: true });
    await expect(page).toHaveURL(/\/home\/notes/);

    // Früherer Schritt (Service) per Marker — Label ist auf Mobile ausgeblendet
    const serviceButton = page.locator('.wizard__step--done .wizard__step-button').last();
    await serviceButton.locator('.wizard__marker').click();
    await expect(page).toHaveURL(/\/home\/services/);
    // Während des Routenwechsels hängen kurz alter und neuer Header im DOM — erst wenn genau
    // einer übrig ist, gilt der Titel (ein dauerhaft doppelter Header würde hier scheitern).
    await expect(page.locator('.wizard__current-title')).toHaveCount(1);
    await expect(page.locator('.wizard__current-title')).toHaveText('Service');
  });
});

test.describe('REQ-039 Aktionsleiste — Zurück links, Weiter rechts (löst REQ-023 Mobile-Stack ab)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/home/location');
    await ensureGermanLanguage(page);
  });

  test('Mobile: klebende Leiste in einer Zeile am unteren Rand, ab Tablet statisch', async ({ page }) => {
    await goToNotesPage(page);
    const nav = page.locator('.wizard-nav');
    const back = nav.locator('.btn-outline-secondary');
    const next = nav.locator('.btn-primary');

    await page.setViewportSize({ width: 375, height: 667 });
    await expect(nav).toHaveCSS('position', 'sticky');
    await expect(nav).toHaveCSS('flex-direction', 'row');
    const backBox = await back.boundingBox();
    const nextBox = await next.boundingBox();
    const navBox = await nav.boundingBox();
    expect(backBox && nextBox && backBox.x + backBox.width <= nextBox.x).toBe(true);
    // Mockup: die Leiste ist ohne Scrollen sichtbar
    expect(navBox && navBox.y + navBox.height <= 668).toBe(true);

    await page.setViewportSize({ width: 1024, height: 800 });
    await expect(nav).toHaveCSS('position', 'static');
    await expect(nav).toHaveCSS('flex-direction', 'row');
  });
});

// ─── Abschnitt 18: Mietwagen-Verfügbarkeit pro Termin ───

const MOCK_CATEGORIES = [
  { id: '6908808e54f4bb2b69b0538b', groupId: 'g', name: 'Kleinwagen', description: '', brands: [], branches: [] },
  { id: '6908808e54f4bb2b69b0538c', groupId: 'g', name: 'Mittelklasse', description: '', brands: [], branches: [] }
];

function snapDays(rentalCarIncluded: boolean): unknown[] {
  return [
    {
      date: '2026-11-17', displayDate: '17.11.2026', dayAbbreviation: 'Di',
      slots: [
        { id: 'req039-slot-1', time: '08:00', displayTime: '08:00 Uhr', available: true, resource: null, rentalCarIncluded },
        { id: 'req039-slot-2', time: '13:30', displayTime: '13:30 Uhr', available: true, resource: null, rentalCarIncluded }
      ]
    }
  ];
}

interface SnapCall { replacementCar?: boolean }

/** Mocks rent categories and snaps; `withRentalCarDays` decides what a rental request returns. */
async function mockRentalBackend(page: Page, withRentalCarDays: boolean, categories = MOCK_CATEGORIES): Promise<SnapCall[]> {
  const calls: SnapCall[] = [];
  await page.route('**/api/v1/branches/*/rentcategories**', route =>
    route.fulfill({ json: { data: categories } }));
  await page.route('**/api/v1/snaps', async route => {
    const body = route.request().postDataJSON() as SnapCall;
    calls.push(body);
    let days: unknown[] = snapDays(false);
    if (body.replacementCar === true) {
      days = withRentalCarDays ? snapDays(true) : [];
    }
    await route.fulfill({ json: { data: days } });
  });
  return calls;
}

test.describe('REQ-039 Abschnitt 18 — Mietwagen pro Termin', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/home/location');
    await ensureGermanLanguage(page);
  });

  test('TC-M1/TC-M9: mit Mietwagen nur Termine mit Wagen, Kennzeichnung „inkl. Mietwagen“', async ({ page }) => {
    const calls = await mockRentalBackend(page, true);
    await goToNotesPage(page);

    // Schalter = visuell verstecktes Input + Label (Mockup 03) — Klick auf das Label wie ein Nutzer
    await page.locator('label[for="rentSwitch"]').click();
    await expect(page.locator('#rentSwitch')).toBeChecked();
    // BR-M5: Wunsch ist optional, „Keine Präferenz“ ist vorausgewählt
    await expect(page.locator('#rentClass-none')).toBeChecked();
    await clickNotesContinue(page);
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10_000 });

    expect(calls.at(-1)?.replacementCar).toBe(true);
    await expect(page.locator('.appointment-selection__rental-filter')).toBeVisible();
    await expect(page.locator('.slot__rental').first()).toContainText('inkl. Mietwagen');
  });

  test('TC-M2: ohne Mietwagen bleibt der Abruf unverändert', async ({ page }) => {
    const calls = await mockRentalBackend(page, true);
    await goToNotesPage(page);
    await expect(page.locator('#rentSwitch')).not.toBeChecked();
    await clickNotesContinue(page);
    await page.locator('.appointment-selection').waitFor({ state: 'visible', timeout: 10_000 });

    expect(calls.at(-1)?.replacementCar).toBeUndefined();
    await expect(page.locator('.appointment-selection__rental-filter')).toHaveCount(0);
    await expect(page.locator('.slot__rental')).toHaveCount(0);
  });

  test('TC-M6: kein Termin mit Wagen → Hinweis, „Ohne Mietwagen fortfahren“ lädt ungefiltert', async ({ page }) => {
    const calls = await mockRentalBackend(page, false);
    await goToNotesPage(page);
    await page.locator('label[for="rentSwitch"]').click();
    await expect(page.locator('#rentSwitch')).toBeChecked();
    await clickNotesContinue(page);

    const empty = page.locator('.appointment-selection__rental-empty');
    await expect(empty).toBeVisible({ timeout: 10_000 });
    await expect(empty).toContainText('kein Termin mit Mietwagen');

    await page.locator('.appointment-selection__rental-empty .rental-empty__without-rental-button').click();
    await waitForAngular(page);
    expect(calls.at(-1)?.replacementCar).toBeUndefined();
    await expect(page.locator('.date-tile').first()).toBeVisible();
  });

  test('TC-M8: Standort ohne Mietwagen-Kategorien zeigt keinen Schalter', async ({ page }) => {
    await mockRentalBackend(page, true, []);
    await goToNotesPage(page);
    await expect(page.locator('#rentSwitch')).toHaveCount(0);
    await expect(page.locator('.notes-extras__rental-unavailable')).toBeVisible();
  });
});

// ─── FIN: ohne eingelagerte Räder nicht abgefragt (REQ-040 löst REQ-039 „freiwillig“ ab) ───

test.describe('REQ-039/REQ-040 FIN — ohne eingelagerte Räder kein FIN-Feld', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/home/location');
    await ensureGermanLanguage(page);
  });

  test('ohne FIN erreicht man die Übersicht', async ({ page }) => {
    await goToBookingOverviewPage(page);
    await expect(page.locator('.booking-overview')).toBeVisible();
  });

  // REQ-040: die FIN ist nicht mehr generell optional, sondern nur beim stored-tires-Service
  // sichtbar/Pflicht. Im Standard-Flow (Inspektion) darf das Feld gar nicht erscheinen.
  test('Standardservice zeigt kein FIN-Feld', async ({ page }) => {
    await goToCarinformationPage(page);
    await expect(page.locator('input[formcontrolname="vin"]')).toHaveCount(0);

    await fillCustomerForm(page);
    await fillVehicleForm(page);
    await acceptPrivacyConsent(page);
    await clickCarinformationContinue(page);
    await page.locator('.booking-overview').waitFor({ state: 'visible', timeout: 10_000 });
    await expect(page.locator('.booking-overview')).toBeVisible();
  });
});
