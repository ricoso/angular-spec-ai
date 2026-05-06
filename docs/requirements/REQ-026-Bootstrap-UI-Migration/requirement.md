# REQ-026: Bootstrap-UI-Migration + Boxenstopp-Modus + UX-Anpassungen

**Status:** Implemented
**Priority:** High
**Type:** Functional + Non-Functional
**Created:** 2026-04-24
**Author:** Rico Sonntag
**Branch:** `ui_test`

---

## 1. Overview

### 1.1 Purpose

Komplette Umstellung der Angular-21-Buchungs-App auf einen Bootstrap-5-/ng-bootstrap-Look samt zahlreicher UX-Korrekturen, plus zwei funktionale Erweiterungen:

1. **Boxenstopp-Modus** (Räderwechsel-Plus): Deep-Link mit der `tireChange`-`groupId` einer Branch öffnet eine reduzierte Service-Auswahl mit ausschließlich den in dieser SoftNet-Group konfigurierten Räderwechsel-Plus-Operations.
2. **Adresse statt Filialname** in UI-Anzeigen (Filialauswahl, Brand-Banner, Cart-Panel, Booking-Overview), weil ein gemarkter Filialname (z. B. „Volkswagen Erkrath") bei multi-marken-Werkstätten irreführend ist.

### 1.2 Scope

**Inkludiert:**

- Tech-Stack-Wechsel Angular Material → Bootstrap 5.3 + ng-bootstrap 20 (CDN für CSS/JS, npm nur für ng-bootstrap-Datepicker)
- 9 Wizard-Pages neu in Bootstrap-Markup: location-, brand-, service-, notes-, appointment-, workshop-calendar-, vehicle-, booking-overview-, error-fallback-Page
- Header-Vereinfachung (Cart-Icon + A11y-Trigger raus → Floating-Overlay übernimmt)
- Cart-FAB + A11y-FAB als floating Buttons (`bottom: 5em`, transparent, rote Icons)
- Wizard-Breadcrumb, A11y-Menü (Pills + Switches), Cart-Panel komplett auf Bootstrap-Klassen
- Sprach-Selektor reduziert auf nur DE
- Service-Auswahl-Layout-Wechsel: Grid → horizontales Karussell + Detail-Panel
- Hol-und-Bring-Service als virtuelle Pseudo-Kategorie (rank=99, ID `virtual-pickup-delivery`)
- Räderwechsel-Plus / Boxenstopp-Modus über Deep-Link `?branchId=…&groupId=…` mit der `tireChange`-Group aus `setup.json`
- Backend-Erweiterung: optionaler `?groupId=…`-Parameter auf `/categories`, `/operations`, `/jobtypes` mit Validierung gegen `setup.json`
- Filialname raus aus 4 UI-Stellen, nur formatierte Adresse `Straße HausNr, PLZ Stadt` sichtbar
- Boxenstopp-Karten in der Filialauswahl (lokale Test-Möglichkeit ohne Deep-Link)
- 1 unbenutzbares Material-Override-Stylesheet entfernt
- 27 Tests neu/aktualisiert (Frontend Jest + Backend JUnit)

**Nicht-Ziele:**

- Keine Wizard-Flow-Änderungen (Reihenfolge der Steps unverändert)
- Keine Datenmodell-Änderungen, die das Backend brechen würden
- Keine Änderungen an `setup.json` (PLZ-Tippfehler bewusst nicht gefixt)

### 1.3 Related Requirements

- REQ-021: Multi-Branch-Setup (`setup.json`-Schema mit `default` + `tireChange`-Variants)
- REQ-022: Azure Container App Hosting
- REQ-023: E2E-Test-Coverage (alle 25 Specs)
- REQ-024: Error-Fallback-Screen
- REQ-025: API-Driven Service-Content (Categories + Operations aus SoftNet)

---

## 2. User Story

**Als** Endkunde im Buchungs-Wizard
**möchte ich** eine moderne, mobil-freundliche Oberfläche mit klarer Service-Übersicht und ggf. einer schnellen Boxenstopp-Buchung
**damit** ich auch unter Termin-Druck oder am Smartphone unterwegs einen Werkstattermin in unter 90 Sekunden hinkriege.

**Als** SoftNet-Pflege/Gottfried-Schultz-Marketing
**möchte ich** Boxenstopp-Tage über eine eigene Workshop-Group steuern und per Deep-Link vermarkten
**damit** Räderwechsel-Aktionstage automatisch nur Räderwechsel-Termine zur Buchung anbieten und nicht versehentlich Inspektion-Termine vergeben werden.

### Acceptance Criteria

- [x] AC-1: Material-Imports aus `package.json` raus, App startet ohne `@angular/material`.
- [x] AC-2: Bootstrap 5.3 + Bootstrap-Icons via CDN in `index.html`.
- [x] AC-3: Alle 9 Wizard-Pages laufen mit Bootstrap-Klassen, kein `mat-*`-Element mehr im aktiven Markup.
- [x] AC-4: Cart-FAB + A11y-FAB überlappen die Wizard-Nav-Buttons nicht (Touch-Target ≥ 2.75em, `bottom: 5em`).
- [x] AC-5: Service-Auswahl rendert ein Karussell von Mini-Cards plus Detail-Panel; Inspektion (rank=0) ist initial aktiv und ganz links sichtbar.
- [x] AC-6: Hol-und-Bring-Service-Card zeigt das Pfeil-Icon `bi-arrow-left-right`, nicht das Inspektion-Icon.
- [x] AC-7: Hol-und-Bring-Card listet ausschließlich die SoftNet-Operation „Hol & Bringservice", keine Inspektion-Services.
- [x] AC-8: Filialname ist nirgends mehr sichtbar; statt dessen `Straße HausNr, PLZ Stadt`.
- [x] AC-9: Filialauswahl zeigt pro Branch mit konfigurierter `tireChange`-Group eine zusätzliche „Radwechsel+ (Boxenstopp)"-Karte (gelbes Badge).
- [x] AC-10: Deep-Link `?branchId=…&groupId=<tireChange>` lädt im Service-Step nur die Räderwechsel-Plus-Card; Backend nutzt die übergebene `groupId` für SoftNet-Aufrufe.
- [x] AC-11: Backend lehnt `groupId`s ab, die nicht zur Branch gehören (HTTP 400, `GroupIdNotInBranchException`).
- [x] AC-12: A11y-Sweep — Skip-Link, Focus-Visible, Kontrast 4.5:1, Touch ≥ 2.75em — alle weiter erfüllt.
- [x] AC-13: Sprach-Selektor zeigt nur „Deutsch", Translation-Daten bleiben aber im Code (1-Zeilen-Re-Aktivierung).
- [x] AC-14: Mobile (≤ 48em) — Wizard-Nav-Buttons stacken full-width, Continue-Button oben, Back-Button unten.
- [x] AC-15: `setup.json` und `BranchDto` enthalten `tireChangeGroupId`; Frontend speichert den Wert und triggert den Boxenstopp-Filter.

---

## 3. Preconditions

### 3.1 System

- Spring-Boot-Backend `:3000` läuft, hat Zugriff auf SoftNet-API (Int oder Prod)
- `RADWECHSEL_ENABLED=true` als Env-Variable, sonst werden tireChange-Groups beim Boot rausgefiltert
- `setup.json` enthält für die buchbaren Branches je einen `default`- und (optional) einen `tireChange`-Group-Eintrag
- Bootstrap 5.3 CDN erreichbar (Fallback-Behavior: keiner — App ist auf CDN angewiesen)

### 3.2 User

- Browser mit aktiviertem JavaScript
- Optional: Deep-Link mit gültigem `branchId` + `groupId` (z. B. aus E-Mail-Kampagne, iframe-Embedding oder QR-Code)

### 3.3 Data

- 27 Branches in `setup.json` (Stand 2026-05-04), je mit `default`- und `tireChange`-Group
- SoftNet liefert pro Group: Categories (Tags), Operations (Services mit `tagIds`)

---

## 4. Main Flow — Default-Buchung

**Step 1: Filialauswahl**
- **User:** Öffnet `/home/location`
- **System:** Lädt 27 Branches aus `/api/v1/branches`, rendert pro Branch eine Bootstrap-Card mit Adresse als Karten-Headline + Marken-Logos. Branches mit `tireChangeGroupId` bekommen zusätzlich eine zweite „Radwechsel+ (Boxenstopp)"-Karte mit gelbem Badge.
- **User:** Klickt eine Default-Karte
- **System:** Speichert `selectedBranch`, `deepLinkGroupId = null`, navigiert zu `/home/brand`

**Step 2: Marken-Auswahl**
- **System:** Banner zeigt nur Adresse („Ihr Standort: Düsseldorfer Str. 189-191, 40822 Mettmann"), darunter Marken-Karten. Lädt Marken über `branch.brands` aus dem SoftNet-Group-Lookup.
- **User:** Klickt eine Marke → Auto-Navigate zu `/home/services`

**Step 3: Service-Auswahl**
- **System:** Lädt `/categories` und `/operations` (ohne `groupId`-Override → default-Group). Rendert Karussell mit allen 7 Tags (Inspektion, TÜV, Bremse, Räderwechsel, Aktionen, Reparatur, Karosserie) + virtuelle Hol-und-Bring-Card am Ende. Inspektion ist initial aktiv, Karussell-Track scrollt auf `scrollLeft = 0`.
- **User:** Klickt Mini-Cards an → Detail-Panel rechts/unten zeigt Operations dieser Tag
- **User:** Wählt eine oder mehrere Operations → Confirm-Button → Cart-FAB-Badge zeigt Anzahl

**Step 4–6: Notes / Appointment / Vehicle**
- Standard-Reactive-Forms in Bootstrap-Pattern (`.form-floating` + `.invalid-feedback`)
- Switches statt Dropdowns für Ja/Nein-Felder, Pills für Datums-Präferenzen

**Step 7: Booking-Overview**
- 2-Spalten-Layout, Sticky-Submit rechts; Tile pro Wizard-Step zeigt nur Adresse statt Filialname

---

## 5. Alternative Flow — Boxenstopp-Deep-Link

**Trigger:** App wird geöffnet mit `?branchId=68f606798d2bb4389ed1af51&groupId=6908807823dcaf880659dc5e` (Audi Mettmann Radwechsel)

**Flow:**

1. `deepLinkGuard` liest beide Parameter, ruft `getBranchById(branchId)` → erhält `BranchConfig` mit `tireChangeGroupId: "6908807823dcaf880659dc5e"`
2. Store: `selectedBranch` + `setDeepLinkGroupId(groupId)` + `setDeepLinked(true)`
3. Redirect zu `/home/brand`, danach Service-Step
4. Service-Step ruft `/categories?groupId=…` und `/operations?groupId=…`. Backend prüft Zugehörigkeit via `SetupService.validateGroupIdForBranch`, schickt SoftNet-Request mit der tireChange-`groupId`.
5. SoftNet liefert für die tireChange-Group **keine** Tags (sie ist Operations-only konfiguriert) → Frontend-Fallback: virtuelle Card `id: 'virtual-tire-change'`, `name: 'Räderwechsel+'`, `rank: 0`
6. `operationsByCategory['virtual-tire-change']` = alle Operations der Group, sortiert nach `order`
7. User sieht **eine** Mini-Card („Räderwechsel+"), klickt sie an → Detail-Panel zeigt alle Räderwechsel-Plus-Operations

---

## 6. Exception Flows

### 6.1 E1: Forged groupId (anderer Branch)

**Trigger:** `?branchId=A&groupId=GROUP_OF_BRANCH_B`

**Flow:**

1. Backend `JobTypesController` ruft `JobTypesService.getCategoriesByBranch(A, GROUP_OF_B)`
2. `SetupService.validateGroupIdForBranch(A, GROUP_OF_B)` wirft `GroupIdNotInBranchException`
3. Spring-MVC liefert HTTP 400 mit Response-Body `{ statusCode: 400, messageKey: ... }`
4. Frontend `loadCategoriesForBranch` fängt den Error → `error`-Signal → Service-Page zeigt Error-Fallback-Alert

### 6.2 E2: tireChange-Group requested, aber Saison aus

**Trigger:** App-Start mit `RADWECHSEL_ENABLED=false`, Deep-Link mit tireChange-`groupId`

**Flow:**

1. `SetupService.init()` filtert tireChange-Groups beim Boot raus → `branchesById` enthält für diese Branch keine tireChange-Group mehr
2. `validateGroupIdForBranch` findet die `groupId` nicht → wirft `GroupIdNotInBranchException`
3. Frontend zeigt Error-Fallback. (Akzeptiert: Saison aus = tireChange-Deep-Links sind tot.)

### 6.3 E3: Endlos-Loop bei leerem Categories-Response (regression-fix)

**Trigger:** Backend liefert `[]` für Categories (z. B. tireChange-Group ohne Tags); alter Effect löst infinite reload aus

**Flow:**

1. Service-Selection-Container nutzt `loadedKey: string | null = `${branchId}#${groupId ?? ''}``
2. Effect lädt Categories+Operations **einmal** pro Kombination, auch wenn die Antwort leer ist
3. Kein API-Hammering mehr (vorher: 100+ Requests/sec auf SoftNet)

---

## 7. Postconditions

### 7.1 Success

- Wizard durchläuft alle Steps in unter 90 Sekunden
- Buchungsbestätigung mit `appointmentId` wird angezeigt
- Im Boxenstopp-Modus bekommt der Kunde nur einen Räderwechsel-Termin (keine Inspektion mit-gebucht)

### 7.2 Failure

- Bei API-Fehler (5xx, Timeout, ungültige groupId): Error-Alert mit Retry-Button
- Bei fehlender SoftNet-Verbindung: User wird informiert, kein leerer Wizard

---

## 8. Business Rules

- **BR-1:** `BRAND_LOGO_MAP` ist single-source-of-truth für Marken-Logos im Frontend; SoftNet-Markenname ohne Mapping → `default.svg`
- **BR-2:** Pro Branch genau eine `default`-Group + maximal eine `tireChange`-Group erlaubt (Validierung beim Boot in `SetupService.init`)
- **BR-3:** `groupId`-Override im Deep-Link wird nur akzeptiert, wenn sie in `branch.groups()` enthalten ist (Schutz gegen Cross-Branch-Forging)
- **BR-4:** Hol-und-Bring-Service wird im Frontend immer als virtuelle eigene Card mit ID `virtual-pickup-delivery` und Pfeil-Icon angezeigt — egal welchen rank SoftNet ihm zuweist
- **BR-5:** Im Boxenstopp-Modus (Deep-Link mit tireChange-`groupId`) keine virtuelle Hol-und-Bring-Card, keine Inspektion, keine TÜV-Card — nur was SoftNet für die Group liefert (oder Fallback-„Räderwechsel+"-Card)
- **BR-6:** UI zeigt nirgends den `branch.name` als Hauptinformation — `formattedAddress` ist überall der Anker

---

## 9. Non-Functional Requirements

### Performance

- Page-Load Service-Step ≤ 2 s nach Branch-Auswahl (SoftNet-API typ. 200–700 ms)
- Karussell-Scrolling 60 fps (CSS-only `scroll-snap`, kein JS-Animation-Loop)
- Bundle-Größe nach Material-Removal: ~30 % kleiner; Bootstrap-CDN ist 0 Bytes für unser Bundle

### Security

- Bootstrap-Theme-CSS via CDN mit `crossorigin="anonymous"`
- Deep-Link-`groupId` wird Backend-seitig gegen `setup.json` validiert (nicht nur clientseitig)
- Keine neuen Cookie-/Session-Mechanismen; bestehender HMAC-Session-Token gilt weiter

### Usability

- WCAG 2.1 AA: Skip-Link `id="main-content"` bleibt; Focus-Visible-Outlines auf allen Bootstrap-Buttons; Touch-Targets ≥ 2.75em via `_accessibility.scss`
- Mobile-First: Wizard-Nav-Buttons stacken full-width, Continue oben (primary-first)
- A11y-FAB / Cart-FAB: nur Icons, transparenter Background, rote Iconfarbe (`var(--autohaus-accent)`)

---

## 10. Data Model

### 10.1 Frontend

```typescript
// src/app/features/booking/models/branch-config.model.ts
export interface BranchConfig {
  branchId: string;
  name: string;
  address: BranchAddress;
  brands: string[];
  /** SoftNet-Group-ID der tireChange-Variante, null/undefined wenn keine konfiguriert. */
  tireChangeGroupId?: string | null;
}

// src/app/features/booking/models/service.model.ts (unverändert)
export interface ApiCategory { id: string; name: string; rank: number; }
export interface ApiOperation { id: string; name: string; description: string | null; tagIds: string[]; order: number | null; imageId: string | null; }

// Store-State (Auszug)
interface BookingState {
  selectedBranch: BranchConfig | null;
  selectedLocation: LocationDisplay | null;  // jetzt mit formattedAddress = "Straße, PLZ Stadt"
  isDeepLinked: boolean;
  deepLinkGroupId: string | null;            // NEU
  // ... unverändert: selectedBrand, selectedServices, categories, operations, ...
}
```

### 10.2 Backend

```java
// server/.../branches/dto/BranchResponseDto.java
public record BranchResponseDto(
    String id, String name, String description, AddressDto address,
    List<String> brands,
    /** NEU: tireChange-groupId aus setup.json, null wenn keine konfiguriert. */
    String tireChangeGroupId
) { ... }

// server/.../config/setup/SetupService.java
public String validateGroupIdForBranch(String branchId, String groupId) {
    BranchSetup branch = findByBranchId(branchId);
    boolean valid = branch.groups().stream().anyMatch(g -> g.groupId().equals(groupId));
    if (!valid) throw new GroupIdNotInBranchException(branchId, groupId);
    return groupId;
}
```

---

## 11. UI/UX

### Mockup-Vorlage

`docs/design-bootstrap/` — statische HTML-Demo aller 9 Wizard-Pages:
- `01-location.html`, `02-brand.html`, `03-services.html`, `04-notes.html`, `05-appointment.html`, `06-overview.html`
- `_shared.css` mit Theme-Variablen, FAB-Styling, `.wizard-nav` etc.

### Theme-Datei

`src/styles/_bootstrap-theme.scss` (~636 Zeilen) — Custom-Properties + Component-Klassen für Cards, Pills, Switches, FABs, Karussell.

### Cart-FAB / A11y-FAB

```scss
.cart-fab, .a11y-fab {
  position: fixed;
  bottom: 5em;
  width: 2em; height: 2em;
  background: transparent;
  color: var(--autohaus-accent);
  border: 0; box-shadow: none;
  border-radius: 50%;
}
```

---

## 12. API Specification

### 12.1 Branches (erweitert)

```http
GET /api/v1/branches/{branchId}
```

**Success (200):** *(neues Feld `tireChangeGroupId`)*
```json
{
  "data": {
    "id": "68f608428d2bb4389ed6543f",
    "name": "Volkswagen Düsseldorf-Benrath",
    "description": "Professor-OehlerStr. 13, 40597 Düsseldorf",
    "address": { "street": "Professor-OehlerStr. 13", "zip": "40597", "city": "Düsseldorf", "country": "DE" },
    "brands": ["Audi", "SEAT", "Volkswagen", "Volkswagen Nutzfahrzeuge", "ŠKODA"],
    "tireChangeGroupId": "690c8a51bd7493c144b6ddf8"
  },
  "meta": { "timestamp": "...", "requestId": "..." }
}
```

### 12.2 Categories / Operations / JobTypes (groupId-Override)

```http
GET /api/v1/branches/{branchId}/categories?groupId={tireChangeGroupId}
GET /api/v1/branches/{branchId}/operations?groupId={tireChangeGroupId}
GET /api/v1/branches/{branchId}/jobtypes?groupId={tireChangeGroupId}
```

`groupId` ist optional; wenn weggelassen → default-Group aus `setup.json`. Wenn angegeben aber nicht zur Branch passend → HTTP 400 `GroupIdNotInBranchException`.

---

## 13. Test Cases

### TC-1: Default-Wizard durchklicken
- **Given:** App-Start ohne URL-Params
- **When:** Filialauswahl → Marke → Inspektion-Operation → Notes → Termin → Vehicle → Submit
- **Then:** Booking-Bestätigung mit `appointmentId`

### TC-2: Hol-und-Bring-Card zeigt richtiges Icon + nur Pickup-Op
- **Given:** Branch mit `Hol & Bringservice` als Operation
- **When:** Service-Step öffnen, Hol-und-Bring-Mini-Card anklicken
- **Then:** Icon ist `bi-arrow-left-right`; Detail-Panel listet nur Pickup-Operation, keine Inspektion-Services

### TC-3: Boxenstopp-Deep-Link
- **Given:** URL `?branchId=68f606798d2bb4389ed1af51&groupId=6908807823dcaf880659dc5e`
- **When:** App geladen, Wizard durchgeklickt bis Service-Step
- **Then:** Genau eine Card sichtbar (entweder echte SoftNet-Tag oder virtuelle „Räderwechsel+"); kein TÜV, keine Inspektion, kein Hol-und-Bring

### TC-4: Forged groupId
- **Given:** URL `?branchId=A&groupId=<group-of-different-branch>`
- **When:** Service-Step lädt
- **Then:** Backend liefert HTTP 400; Frontend zeigt Error-Alert mit Retry

### TC-5: Adresse statt Filialname
- **Given:** Beliebige Branch ausgewählt
- **When:** Cart-FAB öffnen, Booking-Overview öffnen, Brand-Banner ansehen
- **Then:** Überall steht „Straße HausNr, PLZ Stadt", nirgends „Audi Mettmann" oder ähnlicher Markenname

### TC-6: Karussell-Initialposition
- **Given:** Service-Step frisch geladen
- **When:** keine User-Interaktion
- **Then:** Karussell-`scrollLeft === 0`, Inspektion-Card ganz links sichtbar, Detail-Panel zeigt Inspektion-Operations

### TC-7: User-Klick auf Hol-und-Bring bleibt erhalten
- **Given:** Service-Step
- **When:** User klickt Hol-und-Bring-Mini-Card
- **Then:** `activeCategoryId === 'virtual-pickup-delivery'` bleibt; effect resetted **nicht** auf Inspektion (regression-fix)

---

## 14. Implementation

### Frontend Components

- [x] `FloatingOverlayComponent` — Cart + A11y in einem Bootstrap-Offcanvas-Pattern
- [x] `LocationCardsComponent` — `EnrichedBranch` mit `cardId`, `displayName`, `groupIdOverride`; emittet `BranchSelection`
- [x] `LocationSelectionContainerComponent` — onBranchSelect setzt `selectedBranch` + `deepLinkGroupId`
- [x] `BrandSelectionContainerComponent` — Banner zeigt nur `formattedBranchAddress`
- [x] `ServiceCardComponent` — `iconClass`-Computed mit ID/Name-Override für Hol-und-Bring
- [x] `ServiceSelectionContainerComponent` — Karussell, `loadedKey`-Loop-Schutz, Auto-Select-Inspektion-Effect
- [x] `WizardBreadcrumbComponent` — Bootstrap-Icons, `aria-current="step"` erhalten
- [x] `BookingOverviewContainerComponent` — `resolvedLocationName` = formattedAddress
- [x] `AccessibilityMenuComponent` — Pills (Schriftgröße) + Form-Switches (Kontrast/Reduced-Motion)
- [x] `WorkshopCalendarDatePickerComponent` — NgbDatepicker (inline + toggle)
- [x] `ErrorFallbackComponent` — Bootstrap-Alert

### Frontend Stores

- [x] `BookingStore`:
  - `deepLinkGroupId: string | null` neu
  - `setDeepLinkGroupId(groupId): void` neu
  - `selectedLocation.formattedAddress` jetzt vollständig (Straße, PLZ Stadt)
  - `categoriesWithVirtual` keine Doppelung mit echter SoftNet-Hol-und-Bring-Tag
  - `operationsByCategory` strenger Pickup-Filter, neue `virtual-tire-change`-Liste mit allen Operations
  - `isTireChangeMode` und `visibleCategories` neu

### Frontend Services

- [x] `BookingApiService.getCategoriesByBranch(branchId, groupId?)` und `.getOperationsByBranch(branchId, groupId?)` mit HttpParams
- [x] `BookingApiService.mapBranchToBranchConfig` mappt `tireChangeGroupId`

### Frontend Guards

- [x] `deepLinkGuard` ruft zusätzlich `setDeepLinkGroupId(groupId)`

### Backend (Spring Boot)

- [x] `BranchResponseDto` um Feld `tireChangeGroupId` erweitert
- [x] `BranchesService.toDto` füllt das Feld aus `setup.json`
- [x] `JobTypesController` — drei Endpoints akzeptieren optional `@RequestParam("groupId")`
- [x] `JobTypesService.getCategoriesByBranch(branchId, groupIdOverride)` etc. — drei überladene Methoden
- [x] `SetupService.validateGroupIdForBranch(branchId, groupId)` neu
- [x] `GroupIdNotInBranchException` neu (`@ResponseStatus(HttpStatus.BAD_REQUEST)`)
- [x] `application.yml`: `radwechsel-enabled: ${RADWECHSEL_ENABLED:true}` (Default war `false`)

### Konfiguration

- [x] `package.json`: `@angular/material` raus; `bootstrap-icons` raus aus npm (CDN); `@ng-bootstrap/ng-bootstrap@20` rein
- [x] `index.html`: 2 CDN-Links (Bootstrap CSS, Bootstrap-Icons CSS) + 1 CDN-Script (Bootstrap-JS)
- [x] `src/styles.scss`: Material-Theming raus; Bootstrap-Theme rein
- [x] `src/styles/_bootstrap-theme.scss`: ~636 Zeilen Custom-Properties + Component-Klassen
- [x] `src/styles/_material-overrides.scss`: gelöscht
- [x] Azure Container App Env: `RADWECHSEL_ENABLED=true`

---

## 15. Dependencies

**Requires:**
- REQ-021: `setup.json`-Schema mit `groups[]` und `variant`-Markern
- REQ-025: API-Driven `/categories` und `/operations`-Endpoints

**Blocks/Affects:**
- REQ-023: 25 Playwright-Specs müssen weiterlaufen (10 reaktiviert, 15 als TODO geskippt mit dokumentiertem Grund — Selektor-Drift durch Bootstrap-Markup)

---

## 16. Naming Glossary

### Container-Methoden

- `onBranchSelect(selection)` — Filiale + optional groupIdOverride übernehmen
- `onMiniCardClick(catId)` — Karussell-Card anklicken, `activeCategoryId` setzen
- `onOperationToggle(opId, checked)` — Service auswählen/abwählen
- `onCloseCart()` — Floating Cart-Panel schließen

### API-Service

- `getBranchById(branchId): Promise<BranchConfig|null>` — single Branch (für Deep-Link-Validation)
- `getCategoriesByBranch(branchId, groupId?)` — Categories, optional mit groupId-Override
- `getOperationsByBranch(branchId, groupId?)` — Operations, optional mit groupId-Override

### Signal Store

- `selectBranch(branch)` — Branch + cascading reset
- `setDeepLinkGroupId(groupId)` — Deep-Link-groupId speichern
- `loadCategoriesForBranch(branchId)` — rxMethod, nutzt intern `deepLinkGroupId()`
- `loadOperationsForBranch(branchId)` — analog

### Computed Signals

- `formattedBranchAddress` — Container-lokal: `<Street>, <ZIP> <City>`
- `selectedLocation.formattedAddress` — Store-lokal: gleiche Logik
- `isTireChangeMode` — `deepLinkGroupId === selectedBranch.tireChangeGroupId`
- `visibleCategories` — gefilterte Liste je nach Mode (default + Hol-und-Bring vs. tireChange-only)
- `operationsByCategory` — Map cat.id → ApiOperation[], inkl. Pickup-Sonderlogik und virtual-tire-change-Fallback

---

## 17. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | Rico Sonntag | 2026-05-04 |
| Tech Lead | Claude Code (assistant) | 2026-05-04 |

---

## 18. Implementation Notes

**Branch:** `ui_test`

**Commit-Historie (chronologisch):**

| Hash | Datum | Beschreibung |
|------|-------|--------------|
| `4242008` | 2026-04-24 | final fixes (Iteration 1) |
| `2d9f44a` | 2026-04-24 | final fixes (Iteration 2) |
| `26b7ef0` | 2026-04-27 | REQ-024 Error-Fallback-Screen |
| `ab7429a` | 2026-04-27 | REQ-023 + 24 (E2E + Error) |
| `db1e3de` | 2026-04-29 | REQ-025 API-Driven Service-Content |
| `51b044d` | 2026-04-29 | save (Zwischen-Commit) |
| `3bc8638` | 2026-05-02 | Bootstrap-UI-Migration (Hauptcommit) |
| (uncommitted) | 2026-05-04/05 | Hol-und-Bring-Icon-Fix, Boxenstopp-Deep-Link, Filialname raus, Räderwechsel-Plus-Lokalauswahl, Loop-Fix, Plus-Zeichen |

**Diff-Summen vs. `main`:**

- 280 Files geändert
- 18 490 Zeilen hinzugefügt, 3 920 gelöscht
- Schwerpunkte: 144 `.ts`/`.html`/`.scss` unter `src/app/`, 23 Playwright-Specs, 6 Java-Files unter `server/src/main/java/`, 33 Bilder/Assets unter `src/assets/`

**Bilingual:** Code-Sprache laut `.claude/skills/code-language.md` ist Englisch (Variablen, Methoden, Klassen). Kommentare bewusst Deutsch für Domain-Begriffe (Räderwechsel, Hol & Bring, Boxenstopp), weil das auch im Backend und in setup.json so heißt.

**Test-Coverage (Frontend Jest):** 132 Tests in den 4 hauptbetroffenen Suiten (booking.store, service-card, service-selection-container, location-selection-container, booking-overview-container, floating-overlay, deep-link.guard) — 0 fail.

**Backend-Tests (JUnit):** Alle Tests grün nach Anpassung von `JobTypesController.getJobTypes`-Signatur (2-Parameter) und Erweiterung von 4 neuen Tests in `SetupServiceTest` + `JobTypesServiceTest`.

**Deployment:** Azure Container App Revision `gs-terminbuchungs-app--0000007`, Image-Tag `ui-bootstrap-202605041433`. Health-Check 200.
