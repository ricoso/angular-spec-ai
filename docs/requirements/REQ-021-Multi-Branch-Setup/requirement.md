# REQ-021: Multi-Branch-Setup + Radwechsel-Saison Feature-Toggle

**Status:** Draft
**Priority:** High
**Type:** Functional (Architektur-Aenderung)
**Created:** 2026-04-23
**Updated:** 2026-04-23
**Author:** Claude Code
**Scope:** Backend (`softnet`, `setup`, `config`) + Frontend (`BookingStore`, `BookingApiService`) + E2E

---

## 1. Overview

### 1.1 Purpose

Der Autohaus-Verbund betreibt **27 Filialen** (Gottfried Schultz Gruppe). Das aktuelle Backend hat genau **eine** `GROUP_ID` und **eine** `BRANCH_ID` als globale Env-Variable — das reicht nicht. Jede Filiale hat zudem **zwei separate Group-IDs**:

- Eine fuer den **Standard-Service** (`<Filial-Name> soft-planning+`)
- Eine fuer den **Radwechsel** (`<Filial-Name> Radwechsel soft-planning+`)

Die Radwechsel-Variante ist **saisonal** (Fruehjahr + Herbst). Ausserhalb der Saison soll sie komplett unsichtbar sein. Innerhalb der Saison wird sie per Feature-Toggle aktiviert.

Dieses Requirement fuehrt ein:

1. Einen Filial-Katalog (`server/config/setup.json`) mit allen 27 Filialen + je 2 Group-IDs
2. Einen `SetupService`, der das JSON beim App-Start laedt und Branch→Group-Mappings bereitstellt
3. Ein Feature-Toggle `RADWECHSEL_ENABLED`, der die Radwechsel-Variante ein-/ausblendet

### 1.2 Scope

**Included:**
- (a) **Setup-JSON** `server/config/setup.json` mit 27 Filialen × 2 Group-IDs (Datenquelle: `Names_and_IDs.xlsx`)
- (b) **SetupService** als Spring-Bean, laedt JSON bei `@PostConstruct`, bietet Lookup-API
- (c) **Env-Variablen-Pfad-Toggle:** `SETUP_JSON_PATH` (Datei-Pfad) **ODER** `SETUP_JSON` (direkter JSON-Inhalt) — fuer Container-Deployment analog zu Fingerprint
- (d) **Refactoring:** `SoftnetApiClient` + alle abhaengigen Services arbeiten nicht mehr mit globaler `properties.groupId()`, sondern mit per-Request resolvter `groupId` vom `SetupService`
- (e) **Feature-Toggle `RADWECHSEL_ENABLED`** (`0`/`1`, Default `0`) filtert die `tireChange`-Groups und den Reifenwechsel-JobType im Backend
- (f) **Frontend-Anpassung:** `BookingStore` trackt `selectedVariant` (`default` | `tireChange`) — abgeleitet aus der gewaehlten Service-Liste
- (g) **E2E-Tests:** Playwright-Tests fuer beide Toggle-Zustaende und Branch-Varianten-Buchung
- (h) **Manuelles Hinzufuegen:** Editieren der `setup.json` + Backend-Restart — keine Admin-UI, keine DB

**Nachtrag 2026-04-23 — Dynamische SoftNet-Datenbindung (Prod-Rollout):**

- (i) **OperationIdMapper dynamisch:** Frontend-Service-Keys (z.B. `inspection`) werden zur Laufzeit per Name-Match gegen `GET /api/planning/operations` der jeweiligen Group resolved. Kein hardcoded Frontend→SoftNet-ID-Mapping mehr. Per-Group-Cache (10 Min TTL). Hintergrund: Operation-IDs unterscheiden sich **pro Group**; hardcoded Int-IDs funktionierten auf Prod nicht ("missing operations in list" 400).
- (j) **Rentcategories dynamisch statt hardcoded Mobility-Dropdown:** Altes Dropdown `Mobilitaetsoptionen` (`none`/`compact-car`/`mid-range`/`luxury`) im Notes-Step ersetzt durch echte Rentcategories aus `GET /api/planning/rentcategories` (Kleinwagen/Mittelklasse/Oberklasse mit echten Preisen pro Group). Keine Doppelung mehr ("Mobilitaet" und "Mietwagen" waren inhaltlich dasselbe).
- (k) **Rueckruf (`callbackPreference`) ans SoftNet weiterleiten:** Frontend-Dropdown "Rueckruf erwuenscht" sammelte die Auswahl bisher nur lokal — wurde nie ans Backend gesendet, effektlos. Neuer Flow:
  - Frontend `BookingStore` sendet `callbackRequested: boolean` mit im Booking-Request
  - Backend `CreateAppointmentRequest` nimmt `callbackRequested` an
  - `AppointmentsService` / `SnapPatcher` setzt `snap.wishlist.callbackPreference = true` vor dem Appointment-POST
- (l) **`mobilityOption`-Type komplett entfernt:** Union (`'none'|'compact-car'|...`) raus aus Model + Store + price-tile + booking-overview. Ersetzt durch `rentCategoryId: string | null` (Mongo-ID oder null). Label im Booking-Overview aus `rentCategories`-Liste im Store gelookupt.
- (m) **AM/PM-Filter ans Backend weitergeben:** Das Frontend-Dropdown `appointmentPreference` (`anytime|morning|afternoon`) sammelte die Auswahl bisher nur im Store — ging nie in den Snaps-Request. Neuer Mapping:
  - `morning`   → `timePreference: 'AM'`
  - `afternoon` → `timePreference: 'PM'`
  - `anytime`   → Feld weggelassen (kein Filter, alle Slots)
  - Helper `mapAppointmentPreferenceToSoftnet(pref)` in `booking.store.ts`. Wert wird ueber `AppointmentApiService.getAppointments(...)` an `/api/v1/snaps` als optionales `timePreference`-Feld gehaengt. Serverseitig existiert der Pfad schon (`SnapsService.java:55-57` → SoftNet `checkInTimePreference`).
  - **Slot-Picking pro Tag (Schnellauswahl):** erster AM- bzw. PM-Slot, **Fallback auf fruehesten verfuegbaren** wenn kein passender da ist (Soft-Preference) — User sieht immer Termine.
  - **Fenster fuer Schnellauswahl 14 → 28 Tage** damit der Filter genug Kandidaten hat.
  - **Workshop-Kalender:** zeigt **nur den vom User ausgewaehlten Tag** (nicht mehr 3 Tage) mit **allen** Slots, kein AM/PM-Filter auf Detailebene.

- (n) **Cart-Overlay zeigt Leistungs-Namen statt Anzahl:** Der `FloatingOverlayComponent`-Warenkorb gab bisher nur die Anzahl der selektierten Service-Cards aus (`selectedServiceCount`). Wenn der User z.B. 4 Sub-Optionen in der Inspektions-Card auswaehlte, zeigte der Warenkorb „Leistungen: 1" — ein Bug.
  - Neue computed-Property `selectedServiceLabels: TranslationKey[]` im `BookingStore`, die alle Sub-Optionen (oder den Service-Titel bei optionslosen Services wie `brake-fluid`) flach auflistet.
  - HTML rendert statt Zahl eine `<ul>`-Liste mit jedem Eintrag uebersetzt.
  - Empty-State-Check angepasst: `selectedServiceLabels().length === 0` statt `selectedServiceCount() === 0`.

- (o) **Mobilfunknummer akzeptiert `+49`:** `carinformation-container.component.ts` Regex erweitert von `/^0[0-9]+$/` auf `/^(\+|0)[0-9\s]+$/`. Akzeptiert nun `01701234567`, `+491701234567`, `+49 170 1234567`. i18n-Fehlertext DE + EN angepasst.
- (p) **Hint-Text unter Mobilfunknummer entfernt:** `<mat-hint>` im `customer-form.component.html` gelöscht — sagte „ohne Sonderzeichen im Format 017012345678", was seit (o) nicht mehr stimmt. Nur der Error bei ungültiger Eingabe bleibt.
- (q) **Wizard-Breadcrumb: Separator-Strich mobil halbiert.** `wizard-breadcrumb.component.scss` — `&__separator { width: 1.5em → 0.75em }` auf Mobile (<48em). Tablet/Desktop bleibt 2em (`@include tablet`).
- (r) **Floating-Overlay-Fix nach Regression:** Kurzzeitig `display: none` auf Mobile gesetzt → Warenkorb war gar nicht mehr sichtbar → zurueckgerollt auf `display: flex` ueberall. Cart + A11y-FAB bleiben auf allen Breakpoints sichtbar.
- (u) **Wizard-Navigation mobile immer uebereinander.** Auf Mobile (<48em) werden Back + Weiter-Buttons gestackt (Weiter oben, Zurueck unten); ab Tablet nebeneinander (Zurueck links, Weiter rechts). Vorher: nur `notes__actions` stackte — andere Seiten zeigten die Buttons immer horizontal → Overflow/zu kleine Touch-Targets. Jetzt zentral in `src/styles.scss` fuer alle Nav-Klassen: `.wizard-nav`, `.booking-overview__navigation`, `.brand-selection__nav`, `.appointment-selection__nav`, `.carinformation__nav`, `.carinformation__actions`. Order via Attribut-Selector `[class*='continue-button']` / `[class*='back-button']`.

- (v) **Workshop-Kalender: `calendar_today`-Icon neben Datepicker entfernt.** War redundant weil der Datepicker-Toggle bereits ein Icon hat.

- (w) **Service-Seite `.summary-bar` in globale Mobile-Stack-Regel aufgenommen.** `.summary-bar` hatte eigenes `flex`-Styling ohne Mobile-Stack. Jetzt von globaler `styles.scss`-Regel erfasst → Zurueck/Weiter uebereinander + volle Breite auf Mobile.

- (x) **Booking-Overview Submit-Button: Text groesser, Icon kleiner.** Text `var(--font-size-lg)` + `font-weight: 600`. Icon auf `font-size: 1em` relativ zum Text. Zurueckhaltendes Senden-Icon, praegnanter „Jetzt anfragen"-Text.

- (y) **Kundendaten-Seite bereinigt:**
  - „Schon einmal bei uns gewesen?"-Banner mit „Jetzt Daten abrufen!"-Button komplett entfernt (Placeholder fuer zukuenftiges REQ-010+). HTML-, SCSS- und Handler-Code in `carinformation-container.component.{html,ts,scss}` geloescht, Spec-Block fuer `onReturningCustomer` entfernt.
  - Zurueck-Button (`carinformation__back-button`): `arrow_back`-Icon vor dem Text.
  - Weiter-Button (`carinformation__continue-button`): `arrow_forward`-Icon vor dem Text („→ Zur Buchungsuebersicht") — konsistent mit Zurueck, Pfeil-Optik vorne.
  - `MatIconModule` in `carinformation-container.component.ts` importiert.

- (z) **Fehlertexte in Kundendaten-Formular reaktiv:** Das `computed(...)`-Pattern fuer `xxxErrors`-Signals hatte nur `form()` (Input-Signal) als Dependency. Da Reactive-Forms nicht Signal-basiert sind, wurde das Computed nie neu evaluiert, wenn `FormControl.valid`/`FormControl.touched` sich aenderte — Fehlermeldungen erschienen nicht (z. B. bei Mobilfunknummer mit Eingabe `12345` fehlte „Muss mit 0 oder + beginnen"). Fix in `customer-form.component.ts` + `vehicle-form.component.ts`: `toSignal(merge(form.valueChanges, form.statusChanges))` als globale `formEvents`-Dependency, alle Error-Computeds lesen es zu Beginn → reagieren jetzt auf Tippen/Status-Aenderungen.

- (aa) **Setup-Variant Fallback auf `default` (500er Fix):** `SetupService.resolveGroupId(branchId, variant)` warf `VariantNotFoundException` (HTTP 500), wenn eine Variant (z. B. `tireChange`) in `setup.json` fuer eine Branch nicht existiert ODER durch `applyFeatureToggle` bei ausgeschalteter Radwechsel-Saison herausgefiltert wurde. Frontend fragte trotzdem `tireChange` an → Crash. Fix: fallback auf `default`-Variant, wenn die angefragte Variant nicht verfuegbar ist (mit `log.warn`). Nur noch Exception, wenn auch keine `default`-Gruppe existiert. Neue Tests: `resolveGroupId_fallsBackToDefaultWhenVariantMissing`, `..._fallsBackToDefaultWhenSeasonDisabled`, `..._throwsVariantNotFoundWhenNoDefaultExists`.

- (bb) **Brand-Selection-Text umformuliert:** „Welche Marke möchten Sie servicen lassen?" (DE) / „Which brand would you like to have serviced?" (EN) war stilistisch sperrig. Neu: „Welche Fahrzeugmarke fahren Sie?" / „Which vehicle brand do you drive?"; Subtitle: „Bitte wählen Sie die gewünschte Marke aus." / „Please choose the brand you need." — kürzer, näher am Kundenwording. Keys in `src/app/core/i18n/translations.ts`.

- (cc) **iframe-URL-Format: Hash-Routing-Bug.** Im Excel `Names_and_IDs.xlsx` waren alle 54 `iframe-URL`-Spalten im Format `.../#/home/location?branchId=...&groupId=...`. Angular läuft aber mit **Path-Routing** (`provideRouter(routes)` ohne `withHashLocation()`), d. h. alles nach `#` wird vom Browser verworfen → der `deepLinkGuard` sieht keine Query-Params → Filialauswahl wird gezeigt. Fix: Excel-Spalte via Python-Skript vereinheitlicht auf `.../home/location?branchId=<branchId>` (ohne `#`, ohne `groupId` — Radwechsel-Variant wird später anhand der `branchId` bestimmt). Default + tireChange-Zeilen bekommen identische URL; `group-ID`-Spalte bleibt als interne Referenz. `docs/iframe-embed-test.html` analog gefixt (`buildUrl()` baut `?branchId=` statt `/#/home/...`).

- (dd) **iframe-Embed-Test-HTML (`docs/iframe-embed-test.html`) ausgebaut:** Von 4 Wuppertal-Einträgen auf alle 54 aus dem Excel. Gruppierung nach `branchId` (robust bei Namens-Varianten wie „Essen Kray" vs. „Essen -Kray" oder „Velbert" vs. „Volkswagen und Audi Velbert"). Search-Filter oben (filtert Branch-Name + Adresse + Full-Name), Counter unten („X von 29 Standorten"). Bei Filialen mit mehreren default-Einträgen (Leverkusen BS + GmbH) werden Vollnamen auf den Buttons angezeigt. Close-Button (×) des Overlay neu gestylt: transparent, dunkelgrau, `font-size: 3rem`, `font-weight: 300`, Hover-Opacity — kein Hintergrund, kein Border.

- (ee) **Kunden-Einbindungs-Anleitung (`docs/kunden-einbindung.md`)** neu angelegt: CSP-Freigabe-Hinweis (`https://www.gottfried-schultz.de` whitelisted), Minimal-iframe-Snippet, Deep-Link mit `branchId`, responsive Höhe (Desktop 900px / Mobile 1200px), Overlay-/Modal-Variante als Option, Feature-Überblick, Kontakt-Punkt für CSP-Probleme. Dient als copy-paste-tauglicher Brief/Mail an Kundenseiten-Entwickler.

- (ff) **CSP `APP_FRAME_ANCESTORS` um localhost-Testdomains erweitert:** Für lokales iframe-Testing via `npx http-server -p 8080` (Port 8080 von `docs/`) wurde `http://localhost:8080` und `http://127.0.0.1:8080` in die Azure-Env-Variable `APP_FRAME_ANCESTORS` aufgenommen — zusätzlich zum Prod-Host `https://www.gottfried-schultz.de` und `'self'`. Setzung per `az containerapp update --set-env-vars`, keine Image-Rebuild nötig.

- (gg) **Floating-Overlay FAB-Bug: Viereck im Kreis.** Der Warenkorb-FAB (`floating-overlay__fab`, `mat-icon-button`) zeigte ein sichtbares viereckiges Element mitten im runden blauen Button. Ursache: `.icon-framed` hat default einen viereckigen `border` + `box-shadow`, die in MDC-Buttons über eine Override-Regel (`_utilities.scss` Zeile 154 ff.) abgeschaltet werden — aber nur für `mat-flat-button`, `mat-raised-button`, `mat-fab`, `mat-mini-fab`. `mat-icon-button` fehlte in der Liste. Fix: `[mat-icon-button] &` zur Liste ergänzt. Zusätzlich: FAB von `width/height/min-width: 3em` auf `2.5em` verkleinert (kompakterer blauer Rand um das Icon).

- (hh) **iframe-Detection + Brand-Selection Back-Button im iframe ausblenden.** Wenn die App per Deep-Link (`?branchId=...`) im iframe eingebettet läuft, ist die Filiale bereits durch die URL gesetzt — der „Zurück"-Button auf der Marken-Auswahl-Seite würde zurück zur Filialauswahl führen, was in diesem Kontext sinnlos/irreführend ist. Umsetzung: `main.ts` prüft `window.self !== window.top` (mit try/catch gegen Cross-Origin-Fehler) und setzt `document.documentElement.classList.add('is-embedded')`. Globale CSS-Regel in `src/styles.scss`: `:root.is-embedded .brand-selection__back-button { display: none; }`. Gezielt nur auf dieser einen Seite und nur im iframe — alle anderen Zurück-Buttons und der direkte Aufruf (nicht eingebettet) bleiben unverändert.

- (ii) **Kundendaten-Formular: Zurück/Weiter-Button mit Pfeil-Icons, Anpassung:** Weiter-Button (`carinformation__continue-button`) hat jetzt `arrow_forward`-Icon **vor** dem Text („→ Zur Buchungsübersicht") — konsistent mit dem `arrow_back` des Zurück-Buttons, beide Pfeile in Leserichtung links vom Text.

- (t) **Workshop-Kalender: Ausgewaehlter Tag + naechster Business-Tag, ALLE Slots pro Tag.**
  - Vorher: `mode: 'date-range'` + `.find(day => day.date === fromDate)` → nur der EINE ausgewaehlte Tag mit evtl. 5-7 Slots (weil SoftNet bei `checkInDate=<date>` stingy ist).
  - Jetzt: `mode: 'next-available'` (SoftNet `asSoonAsPossible`, breiterer Scan) + 14-Tage-Fenster + KEIN `timePreference` → Backend holt viele Slots ueber mehrere Tage.
  - Frontend zeigt genau **2 Tage**: den ausgewaehlten Tag + den **naechsten Business-Tag** (Sa/So werden uebersprungen — `isBusinessDay()` Helper).
  - Jeder Tag zeigt **ALLE verfuegbaren Slots** — keine Slot-Limitierung, kein AM/PM-Filter (Notes-Auswahl wird im Kalender bewusst ignoriert, User soll frei waehlen koennen).
  - Beispiel Fr → Mo: Fr-Slots + Mo-Slots (Sa/So ausgespart). Mo → Di: Mo-Slots + Di-Slots.
  - Frontend iteriert unveraendert ueber `workshopCalendarDays()` — Container-Code nicht beruehrt.

- (s) **Angular-Template-Warnings behoben:**
  - **NG8011** in `booking-overview-container.component.html` — `<mat-icon>` innerhalb von `@if`/`@else` mit mehreren Root-Nodes wurde nicht in den MatButton-Slot projiziert. Fix: Icon in `<ng-container>` gekapselt und Text in `<span>` (nur ein Node pro Slot). Bonus: Send-Icon bekam `iconPositionEnd`-Attribut.
  - **NG8102** in `service-selection-container.component.html` — `selectedOptionsMap()[service.id] ?? []` war typseitig ueberfluessig, weil `Record<string, string[]>` kein `undefined` zulaesst. Fix: Typ auf `Partial<Record<ServiceType, string[]>>` geaendert (TS weiss jetzt, dass Access `string[] | undefined` liefern kann), `?? []` bleibt fuer Laufzeit-Safety.

---

## Nachtrag — Zusammenfassung aller Prod-Rollout-Fixes (Scope-Punkte i–r)

Diese Änderungen entstanden in einer einzigen Iteration, nachdem die App das erste Mal im Prod-System (SoftNet + neuer Fingerprint + echte Filial-Daten) lief:

| Punkt | Thema | Dateien |
|-------|-------|---------|
| (i) | OperationIdMapper dynamisch (Name-Match + Cache) | `OperationIdMapper.java`, `SnapsService.java` |
| (j) | Rentcategories statt hardcoded Mobility-Dropdown | `notes-extras-form.*`, `notes-extras.model.ts`, `booking.store.ts` |
| (k) | Rückruf als `callbackPreference` ans SoftNet | `AppointmentsService.java`, `CreateAppointmentRequest.java`, `booking.store.ts`, `appointment-api.service.ts` |
| (l) | `MobilityOption`-Type komplett entfernt | Model + Store + price-tile + booking-overview |
| (m) | AM/PM-Filter + Slot-Picking mit Fallback, 28-Tage-Fenster, Workshop-Kalender-Single-Day | `appointment-api.service.ts`, `workshop-calendar-api.service.ts`, `booking.store.ts` (`mapAppointmentPreferenceToSoftnet`) |
| (n) | Cart-Overlay zeigt Leistungs-Namen (statt Anzahl) + E2E-Lücke | `booking.store.ts` (`selectedServiceLabels`), `floating-overlay.component.html`, `REQ-cart-overlay.spec.ts` |
| (o) | Mobile-Nummer `+49` Support | `carinformation-container.component.ts` + spec |
| (p) | Mobile-Nummer-Hint entfernt | `customer-form.component.html` |
| (q) | Stepper-Separator mobil halbiert | `wizard-breadcrumb.component.scss` |
| (r) | Floating-Overlay Regression rueckgaengig (bleibt sichtbar) | `floating-overlay.component.scss` |
| (s) | Angular-Template-Warnings (NG8011 + NG8102) behoben | `booking-overview-container.component.html`, `service-selection-container.component.{html,ts}` |
| (t) | Workshop-Kalender: gewaehlter Tag + naechster Business-Tag (Sa/So uebersprungen), ALLE Slots pro Tag | `workshop-calendar-api.service.ts` + spec + neuer Playwright-Test |
| (u) | Wizard-Navigation mobile Stack (Weiter oben, Zurueck unten) fuer alle Seiten | `src/styles.scss` (zentrale Regel) |
| (v) | `calendar_today`-Icon vor Datepicker entfernt (Redundanz) | `workshop-calendar-date-picker.component.html` |
| (w) | Service-Seite `.summary-bar` in Mobile-Stack-Regel | `styles.scss` |
| (x) | Submit-Button: Text groesser, Icon kleiner | `booking-overview-container.component.scss` |
| (y) | Kundendaten-Seite: Returning-Banner raus, Back/Weiter mit Pfeil-Icons | `carinformation-container.component.{html,ts,scss,spec.ts}` |
| (z) | Form-Error-Texte reaktiv via `toSignal(valueChanges \| statusChanges)` | `customer-form.component.ts`, `vehicle-form.component.ts` |
| (aa) | `SetupService.resolveGroupId` Fallback auf `default`-Variant (500er Fix) | `SetupService.java` + `SetupServiceTest.java` |
| (bb) | Brand-Selection-Text umformuliert (DE + EN) | `core/i18n/translations.ts` |
| (cc) | iframe-URLs Hash-Routing-Fix + Excel vereinheitlicht auf `?branchId=` | `Names_and_IDs.xlsx`, `docs/iframe-embed-test.html` |
| (dd) | Test-HTML: alle 54 Einträge, Suche, Gruppierung nach branchId, Close-Button-Styling | `docs/iframe-embed-test.html` |
| (ee) | Kunden-Einbindungs-Anleitung (iframe-Snippets, CSP, Deep-Link, Responsive) | `docs/kunden-einbindung.md` |
| (ff) | CSP: `APP_FRAME_ANCESTORS` um `localhost:8080` + `127.0.0.1:8080` erweitert | Azure Env-Var (kein Code) |
| (gg) | Floating-Overlay FAB: Viereck-Bug gefixt + FAB kleiner (3em → 2.5em) | `_utilities.scss`, `floating-overlay.component.scss` |
| (hh) | iframe-Detection + Brand-Selection-Back-Button im iframe ausblenden | `main.ts`, `src/styles.scss` |
| (ii) | Kundendaten: `arrow_forward`-Icon vor Text auf Weiter-Button (konsistent mit Back) | `carinformation-container.component.html` |

**Deploy-Historie (Azure Container App `gs-terminbuchungs-app`, Registry `lhindmobility.azurecr.io`):**

| Revision | Build-Run | Inhalt |
|----------|-----------|--------|
| `v202604240701` | `dn7` | Basis-Fixes i–x |
| `v202604240731` | `dn9` | SetupService-Fallback (aa) |
| `v202604240958` | `dna` | Brand-Text + Kundendaten (bb, y) |
| `v202604241013` | `dnb` | Re-Deploy |
| `csp202604241026` | (nur Env) | CSP-Freigabe localhost (ff) |
| `v202604241050` | `dnc` | Brand-Text (bb) |
| `v202604241114` | `dnd` | FAB-Shrink + icon-framed Fix (gg) |
| `v202604241125` | `dne` | iframe-Detection + Back-Button-Hide (hh) |

**Test-Status nach Iteration:** 604 Jest-Tests grün, TypeScript-Compile grün, Backend-Gradle 226 Tests grün.

**E2E-Test-Luecke (Nachtrag):**

Beim Testen wurde der Cart-Overlay-Bug (n) erst manuell bemerkt, weil bestehende Playwright-Specs (REQ-004 Service-Selection, REQ-007 Wizard-State-Sync) zwar den Store-State und die Navigation verifizieren, aber **nie den tatsaechlichen DOM-Inhalt des Warenkorb-Panels** pruefen:
- Kein Klick auf `.floating-overlay__fab` (Cart-FAB) in den bisherigen Flows
- Keine Assertion auf `.floating-overlay__item-value` oder `.floating-overlay__service-list-item`
- Keine Korrespondenz-Pruefung „Anzahl Checkboxen aktiviert" ↔ „im Warenkorb sichtbar"

Nachgezogen in `playwright/REQ-cart-overlay.spec.ts`:
- TC-1: 3 Sub-Optionen auf einer Service-Card wahlen → Cart-Panel zeigt genau 3 Listeneintraege mit korrekten Namen
- TC-2: Service ohne Sub-Options (z.B. Bremsfluessigkeit) → Cart-Panel zeigt genau 1 Eintrag mit dem Service-Titel

Empfehlung: bei jeder neuen Wizard-Seite auch einen Cart-Overlay-Sichtbarkeits-Test schreiben, damit diese Luecke nicht wieder entsteht.

**Zusaetzliche Acceptance Criteria (Nachtrag):**

- [ ] AC-9: `OperationIdMapper.mapToSoftnetOperations(groupId, frontendIds)` resolved per Name-Match; mit Cache; ohne hardcoded IDs
- [ ] AC-10: Rentcategory-Dropdown im Notes-Step ist einziges Mobility-UI (Mobility-Options-Dropdown entfernt)
- [ ] AC-11: Rueckruf-Auswahl wird als `callbackRequested` im Booking-Request ans Backend gesendet und landet als `callbackPreference: true` im SoftNet-Snap
- [ ] AC-12: `MobilityOption`-Type existiert nicht mehr im Codebase
- [ ] AC-13: `OperationIdMapperTest` nutzt Mock-`SoftnetApiClient`, deckt Cache + Name-Match + Passthrough ab
- [ ] AC-14: Unit-Tests fuer `SnapPatcher.setCallbackPreference()` (bei `true` gesetzt, bei `false` nicht gesetzt/`false`)
- [ ] AC-15: Frontend-Tests fuer neue price-tile (rentCategoryName statt mobilityOption) + notes-extras-form (callback emit)
- [ ] AC-16: Playwright-E2E: kompletter Buchungsflow inklusive Rentcategory-Auswahl und Rueckruf-Flag
- [ ] AC-17: Frontend sendet `timePreference: 'AM'|'PM'` im Snaps-Request, wenn `appointmentPreference` `morning`/`afternoon` ist; `anytime` → Feld nicht gesetzt
- [ ] AC-18: Unit-Test fuer `mapAppointmentPreferenceToSoftnet` deckt alle 3 Branches (morning/afternoon/anytime) ab + End-to-End via `loadAppointments`-Spec
- [ ] AC-19: Cart-Overlay rendert `selectedServiceLabels` als Liste von Sub-Options (oder Service-Titel bei optionslosen Services) — nicht mehr nur die Card-Anzahl
- [ ] AC-20: Unit-Test fuer `selectedServiceLabels` deckt drei Cases ab: leer, Sub-Options, keine Sub-Options (Titel)
- [ ] AC-21: Playwright-E2E `REQ-cart-overlay.spec.ts` validiert Cart-Panel DOM-Inhalt nach Service-Auswahl
- [ ] AC-22: Mobilfunknummer-Feld akzeptiert `+49...`/`+41...` und `01701234567` — getestet in `customer-form.component.spec.ts`
- [ ] AC-23: Kundendaten-Seite zeigt kein „Schon einmal bei uns gewesen?"-Banner mehr; Zurueck-Button hat `arrow_back`-Icon links vom Text, Weiter-Button hat `arrow_forward`-Icon links vom Text („→ Zur Buchungsuebersicht")
- [ ] AC-24: `onReturningCustomer`-Handler entfernt (unused nach Banner-Loeschung); `MatIconModule` in Container-Component importiert
- [ ] AC-25: Form-Fehlertexte im Kundendaten-Formular werden bei jedem Tippen reaktiv neu berechnet — z. B. Mobilfunknummer „12345" zeigt sofort „Muss mit 0 oder + beginnen". Realisiert ueber `toSignal(merge(form.valueChanges, form.statusChanges))` als Dependency aller `xxxErrors`-Computeds (statt nur `form()`)
- [ ] AC-26: `SetupService.resolveGroupId(branchId, variant)` faellt auf `default`-Variant zurueck, wenn die angefragte Variant nicht (mehr) verfuegbar ist — verhindert HTTP 500 bei Reifenwechsel-Anfrage waehrend ausgeschalteter Radwechsel-Saison. Tests: `resolveGroupId_fallsBackToDefaultWhenVariantMissing`, `...WhenSeasonDisabled`, `...throwsWhenNoDefaultExists`
- [ ] AC-27: Brand-Selection-Seite zeigt „Welche Fahrzeugmarke fahren Sie?" (DE) / „Which vehicle brand do you drive?" (EN) als Titel und „Bitte wählen Sie die gewünschte Marke aus." / „Please choose the brand you need." als Subtitle — i18n-Keys `booking.brand.title` und `booking.brand.subtitle`
- [ ] AC-28: `Names_and_IDs.xlsx` iframe-URL-Spalte ist für alle 54 Zeilen auf `https://gs-terminbuchungs-app.<region>.azurecontainerapps.io/home/location?branchId=<BID>` vereinheitlicht — ohne `#`, ohne `groupId`. Default- und tireChange-Zeilen haben identische URLs (Radwechsel-Variant wird später per branchId-Regel bestimmt)
- [ ] AC-29: `docs/iframe-embed-test.html` listet alle 29 Standorte aus dem Excel, gruppiert nach `branchId`, mit Suchfeld (Filter nach Name + Adresse + Full-Name) und Counter („X von 29 Standorten"). Overlay-Close-Button (×) transparent, dunkelgrau (`#1f2937`), `font-size: 3rem`, kein Border/Background
- [ ] AC-30: `docs/kunden-einbindung.md` existiert und enthält: CSP-Voraussetzung, Minimal-iframe-Snippet, Deep-Link pro Filiale (nur `branchId`), responsive Höhen (Desktop 900px / Mobile 1200px), Overlay-Variante, Feature-Überblick, Kontaktpunkt
- [ ] AC-31: Azure-Env `APP_FRAME_ANCESTORS` enthält neben `'self'` + `https://www.gottfried-schultz.de` auch `http://localhost:8080` + `http://127.0.0.1:8080` (für lokale iframe-Tests über `npx http-server`). Header `content-security-policy: frame-ancestors …` wird korrekt vom Backend ausgeliefert
- [ ] AC-32: Floating-Overlay-FAB (Warenkorb) ist `2.5em × 2.5em` (statt 3em), `border-radius: 50%`, keine sichtbare viereckige Innen-Fläche mehr. `_utilities.scss`-Override `[mat-icon-button] &` entfernt `border` + `background` + `box-shadow` vom inneren `.icon-framed`
- [ ] AC-33: `main.ts` setzt `document.documentElement.classList.add('is-embedded')`, wenn `window.self !== window.top` (mit try/catch für Cross-Origin-Fehler). Globale CSS-Regel `:root.is-embedded .brand-selection__back-button { display: none; }` blendet den Zurück-Button auf der Marken-Auswahl-Seite **nur im iframe** aus. Direktaufruf und alle anderen Zurück-Buttons bleiben sichtbar
- [ ] AC-34: Weiter-Button auf der Kundendaten-Seite (`carinformation__continue-button`) zeigt `arrow_forward`-Icon **links** vom Text („→ Zur Buchungsübersicht") — parallel zum Zurück-Button mit `arrow_back` links vom Text

**Excluded:**
- Admin-UI fuer Filial-Management
- Dynamische Toggle-Umschaltung zur Laufzeit (Design-Entscheidung: Umschaltung erfordert Redeploy)
- Automatische Saison-Erkennung per Datum (bewusst: Business-Entscheidung, nicht Code-Logik)

### 1.3 Related Requirements

- **REQ-017**: Loading-Spinner + Fingerprint-Config (Voraussetzung, liefert das Env-Variablen-Pattern)
- **REQ-014**: Backend-Foundation
- **REQ-015**: API-Data-Integration

---

## 2. User Story

**Als** Betreiber des Autohaus-Verbundes
**moechte ich** alle 27 Filialen im Backend konfigurieren
**damit** Kunden Termine in jeder Filiale buchen koennen — mit der korrekten Group-ID je nach Service-Variante.

**Als** IT-Verantwortlicher
**moechte ich** die Radwechsel-Saison per Env-Variable aktivieren/deaktivieren
**damit** ich zur Saison Radwechsel-Termine oeffnen und ausserhalb der Saison verbergen kann — ohne Code-Deployment.

**Als** Backend-Entwickler
**moechte ich** neue Filialen per JSON-Edit hinzufuegen
**damit** Erweiterung ohne Code-Change moeglich ist.

### Acceptance Criteria

**Setup-JSON + SetupService:**
- [ ] AC-1: `server/config/setup.json` existiert und enthaelt genau 27 Branch-Eintraege aus `Names_and_IDs.xlsx`
- [ ] AC-2: Jede Filiale hat `branchId`, `name`, `address`, `groups: [{ variant, groupId, fullName }]` — mindestens Variante `default`, zusaetzlich `tireChange`
- [ ] AC-3: `SetupService` laedt `setup.json` bei Backend-Start (`@PostConstruct`). Fehler beim Laden → Exception beim App-Start (fail-fast)
- [ ] AC-4: `SetupService.getAllBranches()` liefert alle geladenen Branches
- [ ] AC-5: `SetupService.findByBranchId(id)` liefert die passende Branch oder `null`
- [ ] AC-6: `SetupService.resolveGroupId(branchId, variant)` liefert korrekte Group-ID — oder wirft `NotFoundException` wenn Branch/Variant unbekannt
- [ ] AC-7: Backend unterstuetzt beide Env-Varianten: `SETUP_JSON_PATH` (Pfad) **und** `SETUP_JSON` (direkter JSON-Inhalt); `SETUP_JSON` hat Vorrang wenn beide gesetzt sind
- [ ] AC-8: Manuelles Hinzufuegen einer Filiale: `setup.json` editieren + Backend-Restart. Kein Code-Change noetig.

**Refactoring (Global GroupId entfernen):**
- [ ] AC-9: `SoftnetProperties.groupId` und `.branchId` sind aus dem Record entfernt
- [ ] AC-10: `softnet.group-id` / `softnet.branch-id` sind aus `application.yml` entfernt (nicht mehr benoetigt)
- [ ] AC-11: `SoftnetApiClient` haengt `x-nrg-group` NICHT mehr aus globaler Property an, sondern bekommt die `groupId` pro Request-Methode als Parameter
- [ ] AC-12: Alle betroffenen Services (`BrandsService`, `JobTypesService`, `SnapsService`, `MobilityService`, `RentCategoriesService`, `CustomersService`, `AppointmentsService`) rufen `setupService.resolveGroupId(branchId, variant)` auf
- [ ] AC-13: `BranchesService.getBranches()` liefert Branch-Liste aus `setupService.getAllBranches()` (Setup-JSON als Source of Truth fuer Metadata)

**Variant-Tracking Frontend:**
- [ ] AC-14: `BookingStore` hat Signal `selectedVariant: 'default' | 'tireChange'`
- [ ] AC-15: Beim Waehlen von Services in der Service-Auswahl wird `selectedVariant` gesetzt: Enthaelt die Auswahl `tire-change` → `'tireChange'`, sonst `'default'`
- [ ] AC-16: Alle Frontend API-Calls, die bisher ohne Group-Context liefen, senden jetzt `branchId` + `variant` im Request-Body/Query. Das Backend resolved dann die Group-ID.
- [ ] AC-17: Neues Frontend-Model `branch-setup.model.ts` spiegelt das Backend-DTO

**Feature-Toggle Radwechsel-Saison:**
- [ ] AC-18: Env-Variable `RADWECHSEL_ENABLED` (Werte `0` | `1`, Default `0`) — in `application.yml` als `features.radwechsel-enabled: ${RADWECHSEL_ENABLED:0}`
- [ ] AC-19: Wenn Toggle `false`: `SetupService.getAllBranches()` liefert Branches MIT nur `variant: "default"` Groups (tireChange wird herausgefiltert)
- [ ] AC-20: Wenn Toggle `false`: `JobTypesService` filtert den Reifenwechsel-JobType aus der Response
- [ ] AC-21: Beim Backend-Start wird der Toggle-Status geloggt: `Radwechsel-Saison: enabled=true` oder `=false`
- [ ] AC-22: Frontend hat KEINE Feature-Flag-Logik — zeigt nur, was Backend liefert (Single Source of Truth im Backend)
- [ ] AC-23: Optionaler `/api/v1/config`-Endpoint liefert `{ tireChangeEnabled: true|false }` fuer UI-Debug

**E2E & Tests:**
- [ ] AC-24: Neue Playwright-Spec `playwright/REQ-021-multi-branch-setup.spec.ts` mit:
  - Szenario Toggle=0: Reifenwechsel NICHT in Service-Auswahl sichtbar
  - Szenario Toggle=1: Reifenwechsel sichtbar, Standard-Buchung in "Audi Zentrum Neuss" verwendet `68d27df7bc2f5a9f555f8f72`
  - Szenario Toggle=1: Radwechsel-Buchung in "Audi Zentrum Neuss" verwendet `68d27e02794a7b452c5227ad`
  - Szenario Toggle=1: Filial-Wechsel zu "Essen Wickenburg" + Radwechsel → korrekte Branch + tireChange-Group
  - Fehlerfall: Request mit unbekannter Branch-ID → HTTP 404 + Error-Message
- [ ] AC-25: Test laeuft in allen 3 Playwright-Viewports (Desktop/Tablet/Mobile)
- [ ] AC-26: Backend-Unit-Test `SetupServiceTest`: Load, not-found, variant-resolution, Feature-Toggle (beide Zustaende)
- [ ] AC-27: Backend-Integration-Test `SetupServiceIntegrationTest`: laedt echte `server/config/setup.json` und verifiziert alle 27 Branches
- [ ] AC-28: Backend-Test `SoftnetApiClientTest` + alle betroffenen Service-Tests aktualisiert auf neue Signatur mit `groupId`-Parameter
- [ ] AC-29: Frontend Jest-Tests fuer `BookingStore.selectedVariant` + `BookingApiService.getBranches()`-Struktur

---

## 3. Preconditions

### 3.1 System
- REQ-017 implementiert (Fingerprint via `SOFTNET_FINGERPRINT_JSON` Env-Variable)
- Spring Boot Backend laeuft
- Angular Frontend konsumiert Backend

### 3.2 Data
- `Names_and_IDs.xlsx` im Repository-Root vorhanden (Master-Liste der Filialen)
- SoftNet API liefert konsistente Branch/Group-IDs, die zu den Werten aus dem Excel passen

---

## 4. Main Flow

### 4.1 Backend-Start

**Step 1:** Spring Boot startet
- **System:** `DotenvConfig` laedt `.env`
- **System:** `SetupService.@PostConstruct init()` wird aufgerufen
- **System:** Entweder `SETUP_JSON` (direkt) oder Datei unter `SETUP_JSON_PATH` wird gelesen
- **System:** JSON wird in `List<BranchSetup>` geparst und in Memory gehalten
- **System:** Toggle-Status wird geloggt: `Radwechsel-Saison: enabled=false (tireChange variants filtered from 27 branches)`

**Step 2:** Fehlerfall beim Laden
- **System:** `FileNotFoundException` / `JsonParseException` → RuntimeException → Spring-Start bricht ab (fail-fast)

### 4.2 User-Buchung (Standard-Service)

**Step 1:** Frontend ruft `GET /api/v1/branches` auf
- **Backend:** `BranchesController` → `BranchesService.getBranches()` → `setupService.getAllBranches()` → Liste von 27 Branches (wenn Toggle=0: jeweils nur `default` Groups, sonst beide)
- **Frontend:** zeigt Filialen in Location-Selection

**Step 2:** User waehlt Filiale "Audi Zentrum Neuss" (branchId = `68d25d008d2bb4389eb9d11a`)
- **Frontend:** `BookingStore.selectBranch(branch)` — `selectedVariant` bleibt Default (`'default'`)

**Step 3:** User waehlt Service "Inspektion"
- **Frontend:** `BookingStore.toggleService('inspection')` + `updateSelectedVariant()` → bleibt `'default'`

**Step 4:** Frontend laedt Termine: `POST /api/v1/snaps` mit `{ branchId: "68d25d...", variant: "default", jobTypeIds: [...] }`
- **Backend:** `SnapsService` → `setupService.resolveGroupId("68d25d...", "default")` → `"68d27df7bc2f5a9f555f8f72"`
- **Backend:** `SoftnetApiClient.getSnaps(request, groupId: "68d27df7bc2f5a9f555f8f72")` → schickt `x-nrg-group: 68d27df7bc2f5a9f555f8f72` an SoftNet
- **Backend:** Gibt Snaps zurueck

**Step 5:** User bucht → analog mit `variant="default"`, Backend benutzt die `default`-Group-ID fuer alle Aufrufe

### 4.3 User-Buchung (Radwechsel, Toggle=1)

**Step 1:** Frontend ruft `GET /api/v1/jobtypes` auf → Backend liefert Liste **inkl.** `tire-change`
- (Wenn Toggle=0 waere, haette der Reifenwechsel-JobType gefehlt)

**Step 2:** User waehlt "Audi Zentrum Neuss" + Service "Reifenwechsel"
- **Frontend:** `BookingStore.toggleService('tire-change')` → `updateSelectedVariant()` → `'tireChange'`

**Step 3:** Frontend laedt Termine: `POST /api/v1/snaps` mit `{ branchId: "68d25d...", variant: "tireChange", jobTypeIds: ["tire-change"] }`
- **Backend:** `setupService.resolveGroupId("68d25d...", "tireChange")` → `"68d27e02794a7b452c5227ad"`
- **Backend:** Ruft SoftNet mit der Radwechsel-Group-ID auf

**Step 4:** User bucht → `POST /api/v1/appointments` mit gleichem `variant`, alle Backend-Aufrufe nutzen tireChange-Group-ID

---

## 5. Exception Flows

### 5.1 `setup.json` nicht gefunden/invalid
**Trigger:** Datei fehlt, Pfad falsch, JSON-Syntax kaputt
**Flow:** `SetupService.init()` wirft RuntimeException, Spring-Start bricht ab. Log: `Failed to load setup.json: <reason>`

### 5.2 Unbekannte `branchId` im Request
**Trigger:** Frontend sendet eine `branchId`, die nicht in `setup.json` steht
**Flow:** `setupService.resolveGroupId()` wirft `BranchNotFoundException` → GlobalExceptionHandler → HTTP 404 mit Body `{ error: "Branch not found in setup", branchId: "..." }`

### 5.3 Unbekannte `variant` im Request
**Trigger:** Frontend sendet `variant="foo"` (weder `default` noch `tireChange`)
**Flow:** `setupService.resolveGroupId()` wirft `VariantNotFoundException` → HTTP 400 mit Body `{ error: "Variant not supported for branch", variant: "foo", availableVariants: ["default"] }`

### 5.4 Radwechsel angefragt obwohl Toggle=false
**Trigger:** Frontend sendet `variant="tireChange"` obwohl `RADWECHSEL_ENABLED=0`
**Flow:** `setupService.resolveGroupId()` wirft `VariantNotFoundException` (tireChange wurde beim Laden bereits rausgefiltert) → HTTP 400
**Prevention:** Da das Backend den tire-change JobType bereits filtert, kann das Frontend die Auswahl gar nicht erst treffen. Verteidigung ist fuer direkte API-Clients.

---

## 6. Non-Functional Requirements

### Performance
- `SetupService` laedt JSON einmalig beim Start — alle Lookups sind `O(n)` ueber 27 Branches oder `O(1)` wenn per `Map<branchId, BranchSetup>` indiziert (empfohlen)
- Kein zusaetzlicher Latency-Impact vs. aktueller Loesung

### Security
- `setup.json` enthaelt **keine** Secrets (Group-IDs sind oeffentlich identifiable, aber nicht auth-relevant)
- `RADWECHSEL_ENABLED` wird im Prod ueber GitLab Secret Store provisioniert → K8s/ContainerApp Env-Variable
- Keine sensiblen Logs: beim Laden werden keine Group-IDs im Log ausgegeben (nur Anzahl Branches)

### Availability
- Feature-Toggle-Umschaltung benoetigt Redeploy — bewusst kein Hot-Reload, verhindert Inkonsistenzen bei laufenden Sessions
- Deployment-Downtime: Rolling-Restart der Container → kein Outage

---

## 7. Aktueller Stand

| Bereich | Status | Notizen |
|---------|--------|---------|
| `setup.json` | ✅ Bereits vorhanden | `server/config/setup.json` — generiert aus `Names_and_IDs.xlsx` |
| `SetupService` | ❌ Noch nicht implementiert | |
| `BranchSetup` / `GroupEntry` Models | ❌ Noch nicht implementiert | |
| Refactoring `SoftnetApiClient` | ❌ Noch nicht implementiert | Akut: `properties.groupId()` ist hartkodiert |
| Feature-Toggle `RADWECHSEL_ENABLED` | ❌ Noch nicht implementiert | |
| Frontend `selectedVariant` | ❌ Noch nicht implementiert | |
| Playwright-Spec | ❌ Noch nicht geschrieben | |

---

## 8. Datenstruktur — `setup.json` Format

```json
{
  "branches": [
    {
      "branchId": "68d25d008d2bb4389eb9d11a",
      "name": "Audi Zentrum Neuss",
      "address": "Jülicher Landstrasse 41-43",
      "groups": [
        {
          "variant": "default",
          "groupId": "68d27df7bc2f5a9f555f8f72",
          "fullName": "Audi Zentrum Neuss soft-planning+"
        },
        {
          "variant": "tireChange",
          "groupId": "68d27e02794a7b452c5227ad",
          "fullName": "Audi Zentrum Neuss Radwechsel soft-planning+"
        }
      ]
    }
    // ... 26 weitere Branches
  ]
}
```

**Format-Regeln:**
- `branchId` + `groupId` sind MongoDB ObjectId-Strings (24 Hex-Zeichen)
- `variant` ist Enum-String: aktuell `"default"` oder `"tireChange"` — Extensible fuer Zukunft
- `fullName` ist optional, dient Debugging/Logs
- UTF-8, schoen formatiert (2 Leerzeichen Indent), in Repository committed

---

## 9. Implementation

### 9.1 Neue Dateien

```
server/
├── config/
│   └── setup.json                                  ← bereits vorhanden
├── src/main/java/com/autohaus/backend/
│   ├── config/
│   │   ├── FeatureProperties.java                  ← NEU (record mit radwechselEnabled)
│   │   └── setup/
│   │       ├── BranchSetup.java                    ← NEU (record)
│   │       ├── GroupEntry.java                     ← NEU (record)
│   │       ├── SetupService.java                   ← NEU
│   │       └── BranchNotFoundException.java        ← NEU
│   └── ...
└── src/test/java/com/autohaus/backend/
    └── config/setup/
        ├── SetupServiceTest.java                   ← NEU
        ├── SetupServiceIntegrationTest.java        ← NEU
        └── SetupServiceTogglesTest.java            ← NEU

src/app/features/booking/
├── models/
│   └── branch-setup.model.ts                       ← NEU
└── stores/booking.store.ts                         ← ERWEITERN (selectedVariant)

playwright/
└── REQ-021-multi-branch-setup.spec.ts              ← NEU
```

### 9.2 Geaenderte Dateien

```
server/src/main/java/com/autohaus/backend/
├── config/
│   ├── AppProperties.java                          ← ERWEITERN (FeatureProperties einbinden)
│   └── SoftnetProperties.java                      ← ENTFERNEN groupId + branchId
├── softnet/
│   └── SoftnetApiClient.java                       ← ALLE Methoden groupId als Parameter
├── branches/BranchesService.java                   ← nutzt SetupService statt SoftNet
├── brands/BrandsService.java                       ← groupId per Call
├── jobtypes/JobTypesService.java                   ← filtert tire-change bei Toggle=0
├── snaps/SnapsService.java                         ← groupId per Call
├── mobility/MobilityService.java                   ← groupId per Call
├── rentcategories/RentCategoriesService.java       ← groupId per Call
├── customers/CustomersService.java                 ← groupId per Call
└── appointments/AppointmentsService.java           ← groupId per Call

server/src/main/resources/
└── application.yml                                 ← entfernen softnet.group-id/branch-id, hinzufuegen setup.*, features.*

src/app/features/booking/
├── services/booking-api.service.ts                 ← getBranches() erweitert
└── components/service-selection/*                  ← Variant-Abstimmung nach ServiceToggle
```

### 9.3 Implementation Steps

**Phase 1 — Backend Setup-Service (ohne Toggle):**
1. Records erstellen: `BranchSetup`, `GroupEntry`
2. `BranchNotFoundException` + `VariantNotFoundException` definieren
3. `SetupService` implementieren (init, getAllBranches, findByBranchId, resolveGroupId)
4. Unit-Tests fuer `SetupService`

**Phase 2 — Refactoring SoftnetApiClient + Services:**
5. `SoftnetApiClient`-Methoden bekommen `groupId`-Parameter
6. `SoftnetProperties.groupId` + `.branchId` entfernen
7. `application.yml` aufräumen
8. Alle abhaengigen Services anpassen (Brands, JobTypes, Snaps, Mobility, RentCategories, Customers, Appointments)
9. Alle betroffenen Tests aktualisieren

**Phase 3 — Frontend Variant-Tracking:**
10. `BookingStore.selectedVariant` hinzufuegen (computed aus `selectedServices`)
11. API-Service-Methoden erweitern um `variant` im Request
12. Frontend-Tests aktualisieren

**Phase 4 — Feature-Toggle:**
13. `FeatureProperties.java` + `application.yml`-Eintrag
14. `SetupService` filtert Groups beim Laden je nach Toggle
15. `JobTypesService` filtert tire-change bei Toggle=0
16. Optionaler `/api/v1/config` Endpoint
17. Unit-Tests fuer beide Toggle-Zustaende

**Phase 5 — E2E:**
18. Playwright-Spec schreiben mit 5 Szenarien
19. In allen 3 Viewports laufen lassen

### Effort
- Backend: 6 Stunden (incl. Tests + Refactoring)
- Frontend: 2 Stunden
- E2E: 3 Stunden
- **Gesamt: ~11 Stunden**

---

## 10. Test Cases

### TC-1: setup.json laedt 27 Branches
- **Given:** `server/config/setup.json` existiert
- **When:** Backend startet
- **Then:** `SetupService.getAllBranches().size() == 27`

### TC-2: Variant-Resolution funktioniert
- **Given:** Backend laeuft mit Toggle=1
- **When:** `resolveGroupId("68d25d008d2bb4389eb9d11a", "default")`
- **Then:** liefert `"68d27df7bc2f5a9f555f8f72"`
- **When:** `resolveGroupId("68d25d008d2bb4389eb9d11a", "tireChange")`
- **Then:** liefert `"68d27e02794a7b452c5227ad"`

### TC-3: Unbekannte Branch-ID
- **Given:** Branch-ID `"xxxxx"` existiert nicht
- **When:** `resolveGroupId("xxxxx", "default")`
- **Then:** `BranchNotFoundException`

### TC-4: Toggle=0 filtert tireChange weg
- **Given:** `RADWECHSEL_ENABLED=0`
- **When:** `getAllBranches()`
- **Then:** Alle Branches haben nur `groups` mit `variant="default"` (tireChange ist gefiltert)
- **Then:** `getJobTypes()` enthaelt KEINEN JobType mit "tire-change"

### TC-5: Toggle=1 zeigt beide Varianten
- **Given:** `RADWECHSEL_ENABLED=1`
- **When:** `getAllBranches()`
- **Then:** Alle 27 Branches haben 2 Groups (default + tireChange)

### TC-6: Frontend-Variant abgeleitet aus Service-Auswahl
- **Given:** User waehlt "Inspektion" + "TUEV"
- **When:** `BookingStore.selectedVariant`
- **Then:** `"default"`
- **When:** User fuegt "tire-change" hinzu
- **Then:** `"tireChange"`

### TC-7: E2E Standard-Buchung
- **Given:** Toggle=1, Filiale "Audi Zentrum Neuss", Service "Inspektion"
- **When:** Booking-Flow bis zum Snap-Request
- **Then:** Request an SoftNet hat `x-nrg-group: 68d27df7bc2f5a9f555f8f72`

### TC-8: E2E Radwechsel-Buchung
- **Given:** Toggle=1, Filiale "Audi Zentrum Neuss", Service "Reifenwechsel"
- **When:** Booking-Flow bis zum Snap-Request
- **Then:** Request an SoftNet hat `x-nrg-group: 68d27e02794a7b452c5227ad`

### TC-9: E2E Toggle=0 versteckt Reifenwechsel
- **Given:** Toggle=0
- **When:** User oeffnet Service-Auswahl
- **Then:** Service-Karte "Reifenwechsel" ist NICHT sichtbar in der Liste

### TC-10: E2E Filial-Wechsel mit Radwechsel
- **Given:** Toggle=1
- **When:** User waehlt "Essen Wickenburg" + Reifenwechsel
- **Then:** Request hat `x-nrg-group: 690cbc4899f4bdb65b6b6719` (= tireChange-Group von Essen Wickenburg)

---

## 11. Dependencies

**Requires:**
- REQ-017: Fingerprint via Env-Variable (Pattern-Vorlage)
- REQ-014: Backend-Foundation (Spring Boot + DotenvConfig)

**Blocks:**
- Zukuenftige Multi-Tenant-Features
- Admin-UI fuer Filial-Management (wenn je gebraucht)

---

## 12. Implementation Notes

### Warum Feature-Toggle nur im Backend?

**Prinzip:** Single Source of Truth — das Frontend zeigt nur, was das Backend liefert. Wenn der Reifenwechsel-JobType nicht in der Antwort ist, verschwindet die Service-Karte automatisch. Kein doppelter State, kein Drift.

### Warum kein Hot-Reload?

Wechsel mid-Session ist unsauber: User koennte in Schritt 3 Reifenwechsel waehlen, dann Admin schaltet Toggle um, User kommt in Schritt 5 an und die Group-ID ist nicht mehr verfuegbar → 400. Redeploy garantiert, dass alle Sessions konsistent sind.

### Variant-Ableitung im Frontend

```typescript
protected readonly selectedVariant = computed(() => {
  const hasTire = this.selectedServices().some(s => s.serviceId === 'tire-change');
  return hasTire ? 'tireChange' : 'default';
});
```

Automatisch, ohne User-Awareness. Der User waehlt einen Service, das Frontend leitet die Variante ab, das Backend resolved die Group-ID.

### Fallback-Strategie bei setup.json Fehler

Bewusst **fail-fast**: Lieber Spring-Start abbrechen als mit falschen Group-IDs produktiv gehen. Das ist sicherer als Silent-Degradation.

---

## 13. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |
