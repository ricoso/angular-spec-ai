# Qualitätsbericht: REQ-039-Responsive-Wizard

**Generiert:** 2026-10-09 11:45
**Feature:** booking (Responsive Wizard nach Mockup + Abschnitt 18 Mietwagen-Verfügbarkeit + freiwillige FIN)
**Gesamtscore:** 92/100 ✅ (Score-Gate ≥ 90 erfüllt)
**Status:** ❌ Nicht bereit für PR. Grund: Akzeptanzkriterien nicht vollständig nachgewiesen bzw. erfüllt, siehe AC-Matrix.

---

## Übersicht

Gewichtung laut `/check-all`: Architecture, Security, Quality, Feature je 20 %, E2E und Documentation je 10 %.

| Kategorie | Score | Status | Quelle |
|-----------|-------|--------|--------|
| Architecture | 91/100 | ✅ | Prüf-Agent |
| Security | 92/100 | ✅ | Prüf-Agent |
| Quality | 92/100 | ✅ | Orchestrator; der Prüf-Agent brach am Session-Limit ab |
| Feature Checks | 92/100 | ✅ | Prüf-Agent |
| E2E Testing | 93/100 | ✅ | Lokale Suite |
| Documentation | 94/100 | ✅ | Orchestrator |

Berechnung: 0,2 × (91 + 92 + 92 + 92) + 0,1 × (93 + 94) = 92,1 → **92/100**.

> **Ablauf der Bewertung:**
> - Die Agent-Scores wurden **vor** der Behebung ihrer [NEU]-Befunde gemessen. Die Befunde sind unten als „behoben“ markiert; die Scores wurden danach nicht angehoben.
> - Nach allen Fixes liefen Step 6 und Step 6.5 erneut grün:
>   - Lint: 0 Fehler
>   - Type-Check: ✅
>   - Jest: 795/795
>   - Backend: 1372/1372
>   - Smoke: ✅
>   - E2E: 292/292 in 3 Viewports
> - **Quality** wurde nicht unabhängig geprüft, sondern vom Orchestrator selbst: ESLint, `tsc`, ein Skript auf px/Hex-Farben/`!important`/`track $index`/OnPush und eine Fokus-Style-Prüfung.

---

## Anlass dieser Runde

Der Nutzer meldete am 2026-10-08, dass die App nicht den Mockups entsprach. Ursachen:
- Auf :4200 lief ein veralteter Dev-Server.
- CORS-Preflight bekam 403.
- Die erste Umsetzung hatte nur das Fortschrittsband angepasst.

Danach wurde die App Seite für Seite nach den Mockups umgebaut (Requirement 11.4) und vollständig erneut geprüft.

---

## 📐 Architecture (20 %)

### check-architecture
**Score:** 88/100 ⚠️

**Geprüft und in Ordnung:**
- Alle Routen-Container sind OnPush, nutzen `inject(BookingStore)` und `onXxx()`-Handler.
- Presentational-Komponenten haben nur `input()`/`output()`; abgeleitete Werte sind `computed()`.
- Die FIN-Fehler laufen über `vinErrors`/`vinInvalid`.

**Behoben (NEU):**
- Toter Code entfernt:
  - `selectedServiceCount` im Service-Container.
  - Die gesamte tote Bestätigen/Abwählen-Kette der `ServiceCardComponent`: `serviceConfirmed`, `serviceDeselected`, `isExpanded`, `selectedCheckboxOptions`, `hasOptionsChanged`, `showConfirm/DeselectButton`, der Effect, `onCheckboxChange`/`onConfirm`/`onDeselect`.
  - Im Container die zugehörigen `onServiceConfirm`/`onServiceDeselect`/`onServiceClick`.
- Mietwagen-Leerzustand: Er war in Termin- und Kalender-Template doppelt; jetzt nutzen beide die Presentational-Komponente `RentalCarEmptyStateComponent` mit Mockup-Klassen. Die Terminseite hat nur noch eine Aktionsleiste.
- Die Warnung `template/cyclomatic-complexity` ist weg; Vormittag/Nachmittag laufen über das `computed` `slotSections`.
- Typen und Helfer liegen nicht mehr im Container:
  - `DateTile`, `SlotSection` → `models/appointment.model.ts`;
  - `ServiceSelectionSummary` → `models/service.model.ts`;
  - `LOCALE_BY_LANGUAGE`/`withoutDot`/`toLocalDate` → `shared/utils/date-locale.util.ts`.
- Das doppelte Rank→Symbol-Mapping aus Service-Karte und Container steckt jetzt in `features/booking/utils/category-icon.util.ts`. Das war Bestand und beseitigt zugleich eine Komplexitätswarnung.
- Die Übersicht liest keine Store-Signale mehr direkt im Template; der Store ist `private`.
- `notes-extras-form`: Die zwei Input-Kopier-Effects sind durch `linkedSignal` ersetzt.
- Die ungenutzten `RouterLink`-Importe in drei Übersichtskacheln sind entfernt (Bestand).

**Offen:**
- [NEU, Info] `isLast` im Breadcrumb wird vom Template nicht mehr gelesen. Es bleibt, weil BR-2/10.1 es als Vertragsbestandteil führen.
- [BESTAND] Methodenaufrufe im Template: `form().get(...)` in Kunden-/Fahrzeugformular und `activeSelectedSet().has()`.
- [BESTAND] Shared-Komponenten injizieren den Feature-Store (Breadcrumb, Kopfzeile).
- [BESTAND] Geschäftslogik in API-Services: Slot-Auswahl, 2-Tage-Auswahl.
- [BESTAND] Tote Member: `HeaderContainerComponent` ist ungenutzt, `callbackOptionItems` und `selectedServiceCount` im Overlay stehen nur in Specs.

### check-stores
**Score:** 90/100 ✅

**Geprüft und in Ordnung:**
- `signalStore({ providedIn: 'root' })` mit `withState`/`withComputed`/`withMethods`, ohne `onInit`.
- Geladen wird über `rxMethod` + Resolver; der neue State ist typisiert und hat Defaults.

**Behoben:**
- Der AC-M8-JSDoc steht jetzt über `setRentalCarRequested`.
- `continueWithoutRentalCar()`, `rentCategoriesLoaded` und die Mietwagen-Computeds sind in 18.8, 18.11 und 18.17 dokumentiert, einschließlich der bewussten Abweichung „`workshopCalendarDate` bleibt“.

**Offen:**
- [minor] `resetFromStep(≤3)` setzt `rentalCarRequested` direkt.
- [BESTAND] Alle Loader teilen sich `isLoading`.
- [BESTAND] Kategorien/Operations werden per Container-Effect geladen.
- [BESTAND] Store-Größe und Komplexität von `submitBooking`.

### check-routing
**Score:** 96/100 ✅
- Keine Routing-Änderung: Routen, Guards und Resolver sind gegenüber HEAD unverändert.
- „Spätere Termine anzeigen“ nutzt die bestehende, geschützte Route `/home/workshop-calendar`.
- Die Markenseite hat im Deep-Link-Fluss keine Aktionsleiste; das ist jetzt in 7.3/11.4 dokumentiert.

---

## 🔒 Security (20 %)

### check-security
**Score:** 92/100 ✅

**Geprüft und in Ordnung:**
- Kein `[innerHTML]`, kein `bypassSecurityTrust*`, kein `eval`.
- Der Preflight-Bypass im `SessionTokenFilter` gilt nur für echte CORS-Preflights (`OPTIONS` + `Origin` + `Access-Control-Request-Method`). Spring führt dabei keinen Controller aus, und der `OriginFilter` blockt fremde Origins weiter.
- Neue Logzeilen enthalten keine personenbezogenen Daten; die Statistik bleibt anonym.

**Behoben:**
- [LOW, NEU] Die Aussage „Für Produktion ändert sich nichts“ in 11.4 ist korrigiert. Der Fix gilt in allen Umgebungen, die Betriebsempfehlung ist dokumentiert.
- [LOW, NEU] Die FIN wird im Backend getrimmt und groß geschrieben (`AppointmentsService.normalizeVin`, Test).
- [LOW, NEU] Die `brandName`-Whitelist hat Negativtests: CR/LF, Tab, U+202E, Anführungszeichen, `<`, `_` und mehr als 50 Zeichen (`SnapRequestDtoValidationTest`, 17 Fälle).

**Offen:**
- **[LOW, Betrieb]** `CORS_ALLOWED_ORIGINS` enthält in Produktion `localhost`-Einträge. Empfehlung: entfernen (Infrastruktur-Entscheidung, nicht im Code).
- [LOW, BESTAND] `variant` (Snaps/Appointments) und `snapId` gelangen ungefiltert in Logzeilen.
- [LOW, BESTAND] `console.log` mit sessionId bzw. Token-Ablaufzeit (`trace.service.ts`, `session-token.service.ts`).

---

## 📝 Quality (20 %)

> Der Prüf-Agent brach am Session-Limit ab. Diese Prüfung hat der Orchestrator selbst durchgeführt.

### check-eslint
**Score:** 92/100 ✅
- `npm run lint:fix`: 0 Fehler, 261 Warnungen; vorher 266. Die Warnungen sind überwiegend `template/no-call-expression` für Signal-Reads (Projektkonvention) oder Bestand.
- Zwei Komplexitätswarnungen sind beseitigt.
- In den geänderten Dateien gibt es keine Warnung außer Signal-Reads.

### check-typescript
**Score:** 94/100 ✅
- `tsc --noEmit`: 0 Fehler; kein `any`.
- Neue Interfaces stehen in `models/`, Helfer in `utils/`.

### check-performance
**Score:** 94/100 ✅
- OnPush in allen 20 geänderten Komponenten.
- Kein `track $index`; Templates lesen nur `computed()`/`signal()`.

### check-styling
**Score:** 88/100 ⚠️
- REQ-039 bringt keine neuen px-Werte, Hex-Farben oder `!important`. Alle Treffer in `_bootstrap-theme.scss` sind Bestand.
- Tokens, BEM, Mobile-First, Container-Query `wizard` (56em).
- `:focus-visible` an allen interaktiven Elementen: Schrittziele, Kopfzeilen-Symbole, Markenkacheln, Kategorie-Schaltflächen, Schalter, Pills, Datumskacheln, Slots.
- Behoben:
  - Bei 320 px überlappten sich 7 Schrittziele; das Fortschrittsband hat jetzt 0,25 rem Seitenabstand unter 22,5em.
  - Kennzeichen- und Kilometerfeld waren ungleich hoch (`align-content: start`).
- ❌ Offen (WCAG 1.4.11): `--color-border: #999999` hat 2,85:1 auf Weiß (< 3:1). Die Referenzpalette hat Vorrang, deshalb ist eine PO-Entscheidung nötig.

---

## 🌍 Feature Checks (20 %)

### check-i18n
**Score:** 88/100 ⚠️

**Geprüft und in Ordnung:**
- Alle REQ-039-Keys sind in de/en/uk/fr/ar mit identischer Struktur vorhanden (maschinell verglichen: 0 fehlende Pfade).
- Feature-Aliase, keine hartkodierten Texte; die Platzhalter `{current}`, `{total}`, `{category}` und `{brand}` werden per `computed()` ersetzt.

**Behoben:**
- Im FR-Termin-Block fehlten 8 Keys.
- Die Service-Aktionsleiste hat ein eigenes Label `booking.services.navAriaLabel`.
- Ungenutzte Keys entfernt: `notes.mobilityOptions.*`, `*.label` in drei Hinweise-Blöcken, `tiles.appointment.date/timeLabel`.
- Bestandslücke: `tiles.extras.*` in uk/fr/ar ergänzt.
- Bestand: Der Warenkorb-Button nennt die Anzahl (`cartBadgeLabel`), beide Panels schließen mit „Schließen“, der Notiz-Zähler nutzt `charCountAriaLabel`, und das irreführende Gruppenlabel ist entfernt.

**Offen:**
- [NEU, minor] Trennzeichen sind im Template hartkodiert: „:“, „—“, „ / “, „; “. Französisch bräuchte „ :“.
- [BESTAND] Uhrzeit „HH:MM Uhr“ und Datum „dd.MM.yyyy“ kommen vom Backend und bleiben in allen Sprachen deutsch. Betroffen sind Slots, Zusammenfassung, Übersicht und Kalender. Der Sprachwähler bietet produktiv nur Deutsch.
- [BESTAND] uk/fr/ar enthalten ungenutzte `tiles.price.*`.

### check-forms
**Score:** 91/100 ✅ (vor den Fixes gemessen)

**Behoben (Bestandsfehler im selben Ablauf):**
- Nach „Weiter“ mit leeren Pflichtfeldern zeigt das Kundenformular jetzt die Fehlertexte, nicht nur rote Rahmen. Es nutzt dafür die Child-Control-`events` wie das Fahrzeugformular; ein Test belegt das.
- Der Einwilligungsfehler verschwindet beim Anhaken; vorher war `privacyControl.invalid` nicht reaktiv (Test).

**Offen:**
- [BESTAND] Methodenaufrufe für `is-invalid`/`aria-invalid` im Template.
- [BESTAND] Statisches `aria-describedby` auf bedingt gerenderte Fehler-IDs.
- [BESTAND] Validierung zusätzlich im Template: `min`, `maxlength`.

### check-code-language
**Score:** 97/100 ✅
- Alle neuen Identifier, CSS-Klassen und Key-Pfade sind englisch.
- [BESTAND] `inspektion`, `INSPEKTION_ONLY_NAMES` im Store/Container.

---

## 🧪 Test Coverage

| Metrik | Wert | Ziel | Status |
|--------|------|------|--------|
| Statements | 92,78 % | 80 % | ✅ |
| Branches | 83,71 % | 80 % | ✅ |
| Functions | 92,59 % | 80 % | ✅ |
| Lines | 94,07 % | 80 % | ✅ |

Jest: 68 Suites, 795/795 Tests. Backend (JUnit + JaCoCo): 1372/1372 Tests, Line Coverage 94,67 %, Branch 86,68 %.

Neue Tests dieser Runde (Frontend):
- `date-locale.util`, `category-icon.util`, `RentalCarEmptyStateComponent`
- `slotSections`, Gruppierung nach Berliner Zeit
- Submit-Fehler im Kundenformular, Einwilligungsfehler
- Warenkorb-Name mit Anzahl, Zeichenzähler-Text

Neue Tests dieser Runde (Backend):
- `normalizeVin`, `SnapRequestDtoValidationTest`

---

## 🧪 E2E Testing (Playwright — Lokale Test-Suite)

### check-e2e
**Score:** 93/100 ✅

**Umgebung:**
- Das Spring-Boot-Backend liefert das aktuelle Angular-Bundle auf `http://localhost:3000` aus (`npm run test:local`: killt, baut und startet frisch).
- Konfiguration `playwright-backend.config.ts`, drei Projekte.

**Läufe (2026-10-09, finaler Stand):**

| Lauf | Befehl | Ergebnis |
|------|--------|----------|
| Smoke (Step 6.5a) | `npm run test:local:smoke` | ✅ Health + `<app-root>` |
| Gesamte Suite (Step 6.5b) | `npm run test:local` | ✅ 292 passed, 0 failed (Desktop 102, Tablet 95, Mobile 95), 1241 skipped |

Die 1241 Skips sind vorbestehende bedingte `describe.skip`, dazu die 3 × 3 Läufe des abgelösten REQ-023-Mobile-Stack-Tests.

**Angepasste bzw. neue Tests:**

| Datei | Änderung |
|-------|----------|
| `playwright/REQ-039-responsive-wizard.spec.ts` | Breadcrumb-Test auf `.wizard__marker`/`.wizard__step--done`. Neuer Test „Aktionsleiste“: mobil klebend in einer Zeile, Zurück links, ab Tablet statisch. Schalter per Label-Klick. Leerzustand-Selektor `.rental-empty__without-rental-button` |
| `playwright/REQ-021-rentcategory-callback.spec.ts` | Schalter per Label-Klick (Mockup-Schalter = visuell verstecktes Input + Label) |
| `playwright/REQ-023-mobile-nav-stack.spec.ts` | `describe.skip`, abgelöst durch die REQ-039-Aktionsleiste laut Mockup; der neue Vertrag steht in der REQ-039-Spec |
| `playwright/take-screenshots.js` | Schalter per Label-Klick |

**REQ-039-Spec (15 Tests, 31 Läufe über 3 Projekte):**

| Kategorie | Status |
|-----------|--------|
| Breiten 320/375/390/768/1024/1280 je Schritt (7 Schritte; kein horizontales Scrollen, Ziele ≥ 44 px, keine Überlappung) | ✅ 7/7 (Desktop-Projekt) |
| Breadcrumb: Marker-Klick bei 390 px, zukünftiger Schritt ohne Wirkung, genau ein `aria-current`, Titel synchron | ✅ 3/3 |
| Aktionsleiste (löst REQ-023 AC-5 ab) | ✅ 3/3 |
| Abschnitt 18: TC-M1/M9, TC-M2, TC-M6, TC-M8 (`rentcategories`/`snaps` gemockt) | ✅ 12/12 |
| FIN freiwillig / ungültige FIN blockiert | ✅ 6/6 |

**Viewports:**

| Viewport | Gesamtsuite | Status |
|----------|-------------|--------|
| Desktop (1280x720) | 102 passed | ✅ |
| Tablet (768x1024) | 95 passed | ✅ |
| Mobile (375x667) | 95 passed | ✅ |

**Screenshots:**
- `screenshots/e2e-responsive-{desktop,tablet,mobile}.png` neu erzeugt via `SCREENSHOT_BASE_URL=http://localhost:3000 node playwright/take-screenshots.js REQ-039-Responsive-Wizard`.

**Issues:**
- ⚠️ EN-Texte der REQ-039-Neuerungen sind nicht per E2E geprüft, nur DE (Unit-Tests decken den Sprachwechsel der Datumsbeschriftung ab).
- ⚠️ Die Mietwagen-UI-Fälle laufen mit Mocks; die echte Filterung ist per Backend-Unit-Tests abgedeckt.
- ⚠️ **Vorbestehend:** `REQ-021 TC-3` lässt `POST /api/v1/appointments` per `route.continue()` durch. Jeder volle E2E-Lauf schickt damit pro Viewport eine echte Buchungsanfrage über das lokale Backend an SoftNet. Empfehlung: `route.fulfill()` statt `continue()`. Das ist nicht geändert, die Entscheidung liegt beim Nutzer.
- ℹ️ Im mobilen Ganzseiten-Screenshot steht die klebende Aktionsleiste mitten im Inhalt — ein Aufnahme-Artefakt von `position: sticky`.

---

## 📄 Feature Documentation

### check-documentation
**Score:** 94/100 ✅

**Generierte Dokumente:**

| Sprache | Datei | Status |
|---------|-------|--------|
| DE | [feature-documentation-de.md](./feature-documentation-de.md) | ✅ neu geschrieben (Kopfzeile, Marker, Seitenlayouts, Aktionsleiste) |
| EN | [feature-documentation-en.md](./feature-documentation-en.md) | ✅ neu geschrieben |

**Dokumentations-Qualität:**
- ✅ Alle Main-Flow-Schritte dokumentiert: 6/6 plus Standort-Einstieg und direkte Korrektur.
- ✅ 18 Screenshots der echten App neu aufgenommen am 2026-10-09: `doc-overview-<lang>`, `doc-step-01…08-<lang>`.
- ✅ Die Screenshots sind visuell gegen die Mockups geprüft. Dabei wurden zwei Fehler gefunden und behoben: Vormittag/Nachmittag nach UTC statt Berliner Zeit (Bestand) und ungleiche Feldhöhen.
- ✅ Barrierefreiheit, Mietwagen-Logik, offene Punkte O-1…O-4 und bekannte Auffälligkeiten sind in beiden Sprachen dokumentiert.

**Aufnahme:**
- Playwright gegen `http://localhost:3000`: Deep-Link Audi Mettmann, Inspektion, Mietwagen an.
- Desktop 1280 × 800 (Full-Page), Mobile 390 × 844 (Viewport).
- `POST /api/v1/appointments` netzwerkseitig blockiert; Kundendaten synthetisch.

**Issues:**
- ⚠️ Leerzustand und „Standort ohne Mietwagen-Kategorien“ gibt es nicht als echte App-Aufnahmen, weil SoftNet sie nicht lieferte. Die Doku verweist auf gekennzeichnete Design-Mockups; E2E deckt beide gemockt ab.

---

## ✅ Akzeptanzkriterien (Acceptance Criteria)

| AC | Beschreibung | Status | Nachweis (Test / Code) |
|----|-------------|--------|------------------------|
| AC-1 | Elf Referenzen inventarisiert, Abweichungen dokumentiert | ✅ Erfüllt | `references/`, `design-analysis.md`, Requirement 11.4 |
| AC-2 | Sechs HTML-Ansichten + Galerie ohne JS, 12 PNGs + Einstiegsscreenshot | ✅ Erfüllt | `mockups/01–06`, `mockup.html`, `screenshots/mockup-0X-*` |
| AC-3 | Deep-Link 6 Schritte, normaler Einstieg 7 | ✅ Erfüllt | Breadcrumb-Unit-Tests; E2E (7), Doku-Screenshots (6) |
| AC-4 | Desktop volle Labels, kompakt Punkte + zentrierter Titel | ✅ Erfüllt | Container-Query 56em; `doc-step-07`, `e2e-responsive-*`; zusätzlich „Schritt X von Y“ laut Mockup |
| AC-5 | Status unterscheidbar (Palette + Form + Text) | ✅ Erfüllt | Marker grün/Häkchen, Ring/Punkt, grau; `sr-only`-Status; Breadcrumb-Spec |
| AC-6 | Ein nativer Button je Schritt, Rücksprung ohne Label | ✅ Erfüllt | E2E Marker-Klick bei 390 px (3 Viewports) |
| AC-7 | Aktive/zukünftige Ziele ohne Wirkung | ✅ Erfüllt | E2E, Unit `onStepClick` |
| AC-8 | Vorwärts/Zurück + Guards unverändert | ✅ Erfüllt | Gesamtsuite 292/292 |
| AC-9 | Rücksprung nutzt `resetFromStep(originalIndex)` | ✅ Erfüllt | Breadcrumb- und Store-Unit-Tests |
| AC-10 | Status/Titel aus derselben Liste, synchron | ✅ Erfüllt | `activeStep`/`stepCounter` computed; E2E Titelwechsel |
| AC-11 | Kein horizontales Scrollen 320–1280 | ✅ Erfüllt | E2E-Breitenschleife (7 Schritte × 6 Breiten) |
| AC-12 | Trefferflächen ≥ 44 px, keine Überlappung | ⚠️ Teilweise | E2E bei Standardschrift ✅ (Überlappung bei 320 px behoben); Schriftstufe „klein“ nicht gemessen |
| AC-13 | 200 % Text, 400 % Zoom, RTL ohne Verlust | ❌ Nicht nachgewiesen | TC-14/TC-17 nicht ausgeführt |
| AC-14 | Tastatur, Fokus, `aria-current`, Status | ⚠️ Teilweise | `aria-*` per Unit/E2E ✅, Fokus-Styles geprüft; TC-15 Tastaturbedienung nicht automatisiert |
| AC-15 | Standard-/Kontrastmodus über zentrale Tokens | ⚠️ Teilweise | Tokens zentral ✅; `--color-border` 2,85:1; Kontrastmodus nicht gemessen |
| AC-16 | App-Shell, Overlay/Skip-Link bedienbar, verdecken nichts | ✅ Erfüllt (mit Nachtrag) | Kopfzeile laut Nutzerentscheid (AC-16-Nachtrag); verdeckt keine Aktion mehr (schwebende Buttons entfallen) |
| AC-17 | Lade/Fehler/Konflikt/Erfolg unverändert, keine VIN-Pflicht | ✅ Erfüllt | REQ-028/031-Specs grün; FIN freiwillig (BR-9) |
| AC-18 | Keine API-Änderung außer Abschnitt 18, kein Request durch Resize | ✅ Erfüllt | Nur 18.13; CORS-Preflight-Fix ist keine API-/DTO-Änderung |
| AC-19 | DE/EN + uk/fr/ar für neue Keys | ✅ Erfüllt | Maschineller Strukturvergleich: 0 fehlende Pfade |
| AC-20 | VISION-Ziele mit Messberichten (Lighthouse, WCAG) | ❌ Nicht nachgewiesen | Coverage gemessen; Lighthouse/axe nicht gelaufen |
| AC-M1 | `replacementCar` → `replacementPreference` ohne Kategorie | ✅ Erfüllt | `SnapsServiceTest`, E2E TC-M1 |
| AC-M2 | Nur Slots mit `replacementResource` | ✅ Erfüllt | `SnapsServiceTest` |
| AC-M3 | Ohne Mietwagen unverändert | ✅ Erfüllt | `SnapsServiceTest`, E2E TC-M2 |
| AC-M4 | Marke in SoftNet-Schreibweise | ✅ Erfüllt | `SoftnetBrandResolverTest` |
| AC-M5 | Snap inkl. `replacement*`, ohne Fleet-Felder | ✅ Erfüllt | `AppointmentsServiceTest` |
| AC-M6 | Kategorie nur Wunsch (Notiz-Präfix) | ✅ Erfüllt | `AppointmentsServiceTest`, Store-Spec |
| AC-M7 | Hinweis + 2 Aktionen, kein Fallback | ✅ Erfüllt | E2E TC-M6, `RentalCarEmptyStateComponent`-Spec |
| AC-M8 | Wechsel mit/ohne verwirft Termine | ✅ Erfüllt | Store-Spec, Container-Specs |
| AC-M9 | Kein Schalter ohne Kategorien | ✅ Erfüllt | E2E TC-M8, Store-Spec |
| AC-M10 | „inkl. Mietwagen“ in Slot/Übersicht/Bestätigung | ✅ Erfüllt | E2E TC-M1, `doc-step-04/06` |
| AC-M11 | Statistik-Merkmal aus `replacement*` | ✅ Erfüllt | `AppointmentsServiceTest` |
| AC-M12 | Texte in 5 Sprachen | ✅ Erfüllt | Strukturvergleich |
| AC-M13 | Freigabe-Bedingung 18.5 (O-2/O-3) erfüllt | ❌ Nicht erfüllt | Auf Nutzerentscheid übergangen; O-2/O-3 vor Go-Live offen |

**Ergebnis:** 27/33 erfüllt, 3 teilweise (AC-12, AC-14, AC-15), 3 nicht nachgewiesen/erfüllt (AC-13, AC-20, AC-M13) ❌

---

## Zusammenfassung

### Bestanden (✅)
- Step 6: Lint 0 Fehler, Type-Check, Jest 795/795, Backend 1372/1372.
- Step 6.5: Smoke ✅, E2E 292/292 (Desktop 102, Tablet 95, Mobile 95).
- Die Oberfläche entspricht den Mockups, mit dokumentierten Ausnahmen (11.4).
- Abschnitt 18 (Mietwagen) und die freiwillige FIN sind umgesetzt und getestet.
- Vier Bestandsfehler im Ablauf sind behoben:
  - Vormittag/Nachmittag nach UTC
  - Fehlertexte im Kundenformular
  - Einwilligungsfehler
  - fehlende FR-/UK-/AR-Texte

### Warnungen (⚠️)
- Uhrzeit/Datum vom Backend bleiben in EN deutsch (Bestand).
- Trennzeichen sind im Template hartkodiert.
- `REQ-021 TC-3` sendet bei jedem vollen E2E-Lauf echte Buchungsanfragen (Bestand).
- `localhost` steht in den Prod-CORS-Origins (Betriebsempfehlung).

### Fehler (❌)
- AC-13 (Zoom/RTL) und AC-20 (Lighthouse/axe) sind nicht nachgewiesen.
- AC-M13: Die Freigabe-Bedingung ist offen — O-2 (Reservierung beim Buchen) und O-3 (Abfragemodus).
- `--color-border` #999999 liegt unter 3:1 (Referenzpalette vs. WCAG 1.4.11).

---

## Empfehlung

**Status:** ❌ Nicht bereit für PR (Score 92 ≥ 90, aber ACs nicht vollständig)

**Begründung:** Code-, Test- und E2E-Gates sind grün. Offen sind Nachweise (Zoom/RTL, Lighthouse) und fachliche Freigaben (O-2/O-3, Rahmenfarbe); bekannte Funktionsfehler gibt es keine.

**Nächste Schritte:**
- [ ] O-2: einmalige, sofort stornierte Testbuchung mit Mietwagen-Snap (PO-Freigabe nötig) → `rentResource` prüfen
- [ ] O-3: tagesgenauer Lese-Abruf „beliebige Kategorie“ vs. „explizit“
- [ ] PO-Entscheid `--color-border` (Referenz #999999 vs. ≥ 3:1)
- [ ] PO-Entscheid „Weitere Services hinzufügen“ auf der Übersicht (würde den Termin verwerfen)
- [ ] TC-14/TC-17 (200 %/400 % Zoom, RTL) und Lighthouse/axe ausführen und hier eintragen
- [ ] `REQ-021 TC-3` auf `route.fulfill()` umstellen (Nutzerentscheid)
- [ ] Danach `/check-all` erneut, PR erstellen

---

## Changelog

| Datum | Score | Änderungen |
|-------|-------|------------|
| 2026-10-08 | E2E 92/100 | check-e2e: REQ-039-Spec 28/28 passed (3 Viewports), Gesamtsuite 298/298 passed; Screenshots e2e-responsive-{desktop,tablet,mobile}.png erzeugt |
| 2026-10-08 | 92/100 | /check-all konsolidiert: Phase 1 (Arch 94, Security 88, Quality 89, Feature 94), NEUE Befunde behoben, Step 6 + 6.5 erneut grün; AC-Matrix 27/33 |
| 2026-10-12 | — | **Produktion (3):** Log-Analyse nach den Buchungs-Fehlern zeigte: (a) die REQ-039-`replacementPreference`-Buchung ließ SoftNet 30 s hängen → 23 gescheiterte Buchungen auf Rev. 0000043; der **Hybrid** (Rev. 0000044) behob das (0 Fehler, per echter Buchung mit `rentCategoryid` bestätigt). (b) Ein zweiter Fehler: Mietwagen-Schalter erschien bei Marken ohne passende Kategorie (Hyundai/SEAT) → SoftNet-`500 „No matching rent category"`. Fix: Schalter **markenbezogen** (BR-M8, `hasRentalCarOffer`/`rentCategoriesForBrand` in booking.store.ts) + Backend-Schutznetz (`SnapsService` fängt den 500 ab). Tests: Store +2, SnapsService +3; Jest 797/797, Backend 1375/1375, Smoke grün. Deploy Rev. 0000045 (Tag `req039-brandfix-…`). |
| 2026-10-12 | — | **Produktion:** Deploy der neuen Oberfläche (Revision 0000043). Log-Analyse danach: Mietwagen-Buchungen ~15–28/Tag liefen bisher über den bewährten REQ-036-Pfad (`isFleet`/`rentCategoryid`); REQ-039 hatte den auf den unbestätigten `replacementPreference`-Pfad (O-2) umgestellt. Daraufhin **Hybrid** gebaut und deployed: UI + Termin-Filter bleiben, die **Reservierung** geht wieder über `rentCategoryid` (Kategorie = Wunsch → SoftNet-Angebot → erste), `replacement*` wird entfernt. Backend-Änderung `AppointmentsService` (`resolveRentCategoryId`, `patchSnapWithBookingData`, `isRentalCarBooking`, `mobilityLogFields`) + Tests; Backend 1372/1372, Smoke grün. Ausblendungen Warenkorb/Übersichts-Zurück (CSS). Requirement §18-Nachtrag ergänzt. O-2 (`replacementPreference`-Reservierung) bleibt als Verifikation offen. |
| 2026-10-09 | 92/100 | Umbau nach Mockup (Requirement 11.4): Kopfzeile, Marker-Fortschrittsband, Seitenlayouts, klebende Aktionsleiste; CORS-Preflight-Fix. /check-all erneut: Architecture 91, Security 92, Feature 92 (Agenten), Quality 92 (Orchestrator, Agent am Limit abgebrochen). Alle [NEU]-Befunde behoben (u. a. tote Service-Karten-API, gemeinsamer Mietwagen-Leerzustand, FIN-Normalisierung, i18n-Lücken FR/UK/AR, Formularfehler-Reaktivität, Vormittag/Nachmittag nach Berliner Zeit). Step 6: Jest 795/795, Backend 1372/1372; Step 6.5: Smoke ✅, E2E 292/292; 21 Screenshots neu; Feature-Doku DE/EN neu. AC-Matrix 27/33 |
