# Feature-Dokumentation: REQ-015 API Data Integration — Booking Feature

> **Requirement:** REQ-015 — API Data Integration
> **Status:** Implementiert | **Priorität:** Hoch
> **Wizard-Step:** Cross-Cutting (betrifft Steps 1–8)
> **Letzte Aktualisierung:** Echte API-Anbindung für Buchungsabschluss

---

## 1. Funktionsübersicht

Das Booking-Feature implementiert einen mehrstufigen Buchungsassistenten (Wizard) für Werkstatttermine. Der Benutzer durchläuft folgende Schritte:

1. **Standortwahl** — Filiale aus der Backend-API auswählen
2. **Markenwahl** — Fahrzeugmarke basierend auf der gewählten Filiale
3. **Dienstleistungen** — Service-Leistungen auswählen (statische Daten)
4. **Notizen & Extras** — Mobilitätsoption, Terminpräferenz, Rückrufwunsch
5. **Terminwahl** — Verfügbare Zeitslots aus SoftNet-API (`POST /api/v1/snaps`)
6. **Werkstattkalender** — Alternative Kalenderansicht für Terminslots
7. **Fahrzeuginformationen** — Kunden- und Fahrzeugdaten per Reactive Forms
8. **Buchungsübersicht** — Zusammenfassung + **echte Buchung via `POST /api/v1/appointments`**

REQ-015 migriert Standort- und Markendaten von statischen Dateien auf die NestJS Backend-API. Deep-Linking via URL-Parameter ermöglicht den direkten Einstieg in die Markenauswahl. Die Buchungsübersicht sendet die Daten nun über die echte Appointments-API an das SoftNet-Backend.

---

## 2. Architektur

### 2.1 Container/Presentational Pattern

Jede Wizard-Route verwendet ein **Container/Presentational**-Muster:

| Container-Component | Pfad |
|---------------------|------|
| `LocationSelectionContainerComponent` | `src/app/features/booking/components/location-selection/` |
| `BrandSelectionContainerComponent` | `src/app/features/booking/components/brand-selection/` |
| `ServiceSelectionContainerComponent` | `src/app/features/booking/components/service-selection/` |
| `NotesContainerComponent` | `src/app/features/booking/components/notes/` |
| `AppointmentSelectionContainerComponent` | `src/app/features/booking/components/appointment-selection/` |
| `WorkshopCalendarContainerComponent` | `src/app/features/booking/components/workshop-calendar/` |
| `CarinformationContainerComponent` | `src/app/features/booking/components/carinformation/` |
| `BookingOverviewContainerComponent` | `src/app/features/booking/components/booking-overview/` |

- **Container-Components** injizieren den `BookingStore` und koordinieren Datenfluss
- **Presentational-Components** erhalten Daten via `input()` und emittieren Events via `output()`
- Alle Components nutzen `OnPush` Change Detection

### 2.2 NgRx Signal Store

**Datei:** `src/app/features/booking/stores/booking.store.ts`

Der `BookingStore` ist ein `signalStore` mit `providedIn: 'root'` und verwaltet den gesamten Wizard-State:

```
BookingState {
  branches, selectedBranch, brands, selectedBrand,
  locations, selectedLocation, services, selectedServices,
  bookingNote, appointments, selectedAppointment,
  workshopCalendarDate, workshopCalendarDays,
  customerInfo, vehicleInfo, privacyConsent,
  notesExtras, bookingSubmitted, isLoading, error, isDeepLinked,
  bookingSubmitting,   // NEU — Ladezustand während API-Aufruf
  bookingError,        // NEU — Fehlermeldung bei Buchungsfehler
  appointmentId        // NEU — Vom Backend zurückgegebene Termin-ID
}
```

**Computed Signals:**
- `hasBranchSelected`, `hasBrandSelected`, `hasLocationSelected` — Guard-Prüfungen
- `hasServicesSelected`, `selectedServiceCount` — Service-Status
- `isBookingComplete` — Validierung für Buchungsübersicht
- `hasWorkshopSlotSelected` — Kombination aus Termin + Kalenderdatum

**rxMethod-Methoden (API-Aufrufe):**
- `loadBranches()` — `BookingApiService.getBranches()`
- `loadBrands(branchId)` — `BookingApiService.getBrands()` / `getBrandsByBranch()`
- `loadLocations()` — `BookingApiService.getLocations(brand?)`
- `loadServices()` — `BookingApiService.getServices()` (statisch)
- `loadAppointments()` — `AppointmentApiService.getAppointments()`
- `loadWorkshopCalendarDays(fromDate)` — `WorkshopCalendarApiService.getWorkshopCalendarDays()`
- `submitBooking()` — **NEU** — `AppointmentApiService.createAppointment()` (echte API)

**Buchungsablauf (`submitBooking`):**
```
submitBooking()
  → patchState({ bookingSubmitting: true, bookingError: null })
  → Request aus Store-Signalen: branchId, snapId, customerId, carId, jobTypeIds, operationIds, note, mobilityOption, appointmentPreference, callbackOption
  → AppointmentApiService.createAppointment(request) → POST /api/v1/appointments
  → Erfolg: patchState({ bookingSubmitted: true, bookingSubmitting: false, appointmentId })
  → Fehler: patchState({ bookingSubmitting: false, bookingError: message })
```

**Zustandsmanagement-Methoden:**
- `setBrand()`, `setLocation()`, `selectBranch()` — Auswahl setzen
- `toggleService()`, `confirmServiceOptions()`, `deselectService()` — Service-Verwaltung
- `setCustomerInfo()`, `setVehicleInfo()`, `setPrivacyConsent()` — Kundendaten
- `setNotesExtras()` — Notizen & Extras
- `resetFromStep(targetIndex)` — Kaskadierendes Reset ab einem Wizard-Schritt
- `setDeepLinked()` — Deep-Link-Modus aktivieren

### 2.3 Services

| Service | Datei | Verantwortung |
|---------|-------|---------------|
| `BookingApiService` | `services/booking-api.service.ts` | Branches, Locations, Brands, Services via HTTP |
| `AppointmentApiService` | `services/appointment-api.service.ts` | Terminslots (`POST /snaps`) + **Buchung erstellen/verifizieren** |
| `WorkshopCalendarApiService` | `services/workshop-calendar-api.service.ts` | Werkstattkalender via `POST /api/v1/snaps` |
| `AccessibilityService` | `shared/services/accessibility.service.ts` | Barrierefreiheit-Einstellungen, High-Contrast-Toggle |

**Neue Methoden in `AppointmentApiService`:**

| Methode | Signatur | Endpunkt |
|---------|----------|----------|
| `createAppointment()` | `(request: CreateAppointmentRequest) → Promise<AppointmentResponse>` | `POST /api/v1/appointments` |
| `getAppointment()` | `(appointmentId: string) → Promise<AppointmentResponse>` | `GET /api/v1/appointments/:id` |

Alle Services nutzen `HttpClient` mit `firstValueFrom()` für Promise-basierte API-Aufrufe.

---

## 3. API-Integration

### 3.1 Branches (Filialen) — `GET /api/v1/branches`

| Methode | Endpunkt | Beschreibung |
|---------|----------|--------------|
| `GET` | `/api/v1/branches` | Alle Filialen laden |
| `GET` | `/api/v1/branches/:id` | Einzelne Filiale (Deep-Link) |

**Response-Format:**
```json
{
  "data": [{
    "id": "vw-essen",
    "name": "VW Essen",
    "description": "Rüttenscheider Str. 123, 45128 Essen",
    "brands": ["VW", "Audi", "ŠKODA"]
  }]
}
```

### 3.2 Snaps (Terminslots) — `POST /api/v1/snaps`

**Request-Body:**
```json
{
  "branchId": "vw-essen",
  "jobTypeIds": ["inspection", "oil-change"],
  "fromDate": "2026-03-10",
  "toDate": "2026-03-24",
  "mode": "next-available",
  "brandName": "VW"
}
```

**Modi:** `next-available` (4 Tage) | `date-range` (7-Tage-Fenster, max. 3 Tage)

### 3.3 Appointments (Buchungen) — `POST /api/v1/appointments`

| Methode | Endpunkt | Beschreibung |
|---------|----------|--------------|
| `POST` | `/api/v1/appointments` | **Termin buchen** (echte SoftNet-Anbindung) |
| `GET` | `/api/v1/appointments/:id` | **Termin verifizieren** (nach Buchung) |
| `PUT` | `/api/v1/appointments/:id/cancel` | Termin stornieren |

**`CreateAppointmentRequest`:**
```json
{
  "branchId": "vw-essen",
  "snapId": "snap-2026-03-10-0900",
  "customerId": "max-mustermann",
  "carId": "E-MU-1234",
  "jobTypeIds": ["inspection"],
  "operationIds": ["op-1"],
  "note": "Bitte Reifen prüfen",
  "mobilityOption": "courtesy-car",
  "appointmentPreference": "morning",
  "callbackOption": "no"
}
```

**`AppointmentResponse`:** `id`, `status`, `date`, `time`, `branchName`, `services[]`, `customerName`, `licensePlate`

### 3.4 HTTP-Infrastruktur

- **SessionTokenInterceptor** — Fügt automatisch `X-Session-Token`, `X-Correlation-Id`, `X-Session-Id` hinzu
- **CircuitBreakerService** — Rate-Limiting zum DoS-Schutz (OPEN → HALF_OPEN → CLOSED)
- **TraceService** — Logging/Tracing für API-Aufrufe

---

## 4. Datenfluss (Resolver → Store → Component)

### 4.1 Daten laden (Standorte / Marken / Termine)

```
/home/location → deepLinkGuard → locationsResolver
  → loadBranches() + loadLocations() → GET /api/v1/branches → LocationDisplay[]

/home/brand → locationSelectedGuard → brandsResolver
  → loadBrands(branchId) → getBrandsByBranch() → BrandDisplay[]

/home/appointment → servicesSelectedGuard → appointmentsResolver
  → loadAppointments() → POST /api/v1/snaps → AppointmentSlot[] (4 Tage)
```

### 4.2 Buchung absenden (NEU)

```
BookingOverviewContainerComponent → BookingStore.submitBooking()
  → patchState({ bookingSubmitting: true })
  → AppointmentApiService.createAppointment(request)
    → POST /api/v1/appointments
  → Erfolg: patchState({ bookingSubmitted: true, appointmentId })
  → Fehler: patchState({ bookingError: message })
```

### 4.3 Deep-Linking

```
/#/home/location?groupId=vw&branchId=vw-essen
  → deepLinkGuard → getBranchById → selectBranch → loadBrands → Redirect /home/brand
```

---

## 5. Routing & Guards

**Datei:** `src/app/features/booking/booking.routes.ts`

| Route | Component | Guard(s) | Resolver |
|-------|-----------|----------|----------|
| `/home/location` | `LocationSelectionContainerComponent` | `deepLinkGuard` | `locationsResolver` |
| `/home/brand` | `BrandSelectionContainerComponent` | `locationSelectedGuard` | `brandsResolver` |
| `/home/services` | `ServiceSelectionContainerComponent` | `brandSelectedGuard` | `servicesResolver` |
| `/home/notes` | `NotesContainerComponent` | `servicesSelectedGuard` | — |
| `/home/appointment` | `AppointmentSelectionContainerComponent` | `servicesSelectedGuard` | `appointmentsResolver` |
| `/home/workshop-calendar` | `WorkshopCalendarContainerComponent` | `servicesSelectedGuard` | — |
| `/home/carinformation` | `CarinformationContainerComponent` | `carInformationGuard` | — |
| `/home/booking-overview` | `BookingOverviewContainerComponent` | `bookingOverviewGuard` | — |

Alle Components werden per **Lazy Loading** geladen (`loadComponent`).

---

## 6. Internationalisierung (i18n)

**Datei:** `src/app/core/i18n/translations.ts`

Typ-sichere i18n-Keys via Proxy-Pattern (`{{ t.feature.key | translate }}`). Unterstützte Sprachen:

| Sprache | Code | Beispiel `submitting` | Beispiel `submitted` |
|---------|------|-----------------------|----------------------|
| Deutsch | `de` | „Wird gesendet..." | „Anfrage gesendet" |
| Englisch | `en` | "Sending..." | "Request Sent" |
| Ukrainisch | `uk` | „Надсилається..." | „Запит надіслано" |
| Französisch | `fr` | „Envoi en cours..." | „Demande envoyée" |
| Arabisch | `ar` | „جارٍ الإرسال..." | „تم إرسال الطلب" |

**Neue i18n-Keys (Buchungsabschluss):**
- `booking.navigation.submitting` — Ladezustand während der Buchung
- `booking.navigation.submitted` — Bestätigung nach erfolgreicher Buchung

Alle UI-Texte nutzen `ngx-translate` mit Feature-Alias-Pattern:
```typescript
protected readonly bookingoverview = i18nKeys.booking.bookingoverview;
// Template: {{ bookingoverview.submitting | translate }}
```

---

## 7. Barrierefreiheit (WCAG 2.1 AA)

### 7.1 AccessibilityService

**Datei:** `src/app/shared/services/accessibility.service.ts`

Der `AccessibilityService` verwaltet drei Barrierefreiheit-Einstellungen:

| Einstellung | HTML-Attribut | CSS-Klasse |
|-------------|---------------|------------|
| Schriftgröße | `data-font-size` auf `<html>` | — |
| Hoher Kontrast | `data-high-contrast` auf `<html>` | `high-contrast` auf `<body>` |
| Reduzierte Bewegung | — | `reduce-motion` auf `<html>` |

**`applyToDocument(state)`** setzt Attribute/Klassen auf das DOM:
- `state.highContrast === true` → `document.body.classList.add('high-contrast')`
- `state.reducedMotion === true` → `html.classList.add('reduce-motion')`

### 7.2 Fokus-Management

- **`:focus-visible`** auf allen interaktiven Elementen (Buttons, Karten, Inputs)
- `outline: 0.1875em solid var(--color-focus-ring)` mit `outline-offset: 0.125em`
- Keine `outline: none` ohne `:focus-visible`-Alternative (WCAG-konform)

### 7.3 Touch-Targets & Kontrast

- Mindestgröße: `var(--touch-target-min)` (2.75em / 44px)
- Kontrastverhältnis: mindestens 4.5:1 (Text), 3:1 (große Elemente)
- `prefers-reduced-motion` Media Query wird respektiert

---

## 8. Styling & CSS-Variablen

Alle Farben und Abstände nutzen CSS-Variablen statt Hardcoded-Werte:

| Variable | Verwendung |
|----------|------------|
| `--color-background-surface` | Hintergrund (Overlays, Modals, Dropdowns) |
| `--color-primary` | Primäre Aktionsfarbe |
| `--color-text-primary` | Haupttextfarbe |
| `--color-text-secondary` | Sekundäre Textfarbe |
| `--color-border` | Rahmenfarbe |
| `--color-focus-ring` | Fokus-Indikator |
| `--spacing-*` | Abstände (xs, sm, md, lg, xl) |
| `--font-size-*` | Schriftgrößen |
| `--radius-*` | Eckenradien |
| `--shadow-*` | Schatteneffekte |
| `--touch-target-min` | Mindest-Touch-Zielgröße |

**Mobile-First-Breakpoints:** `< 48em` (Mobile) | `≥ 48em` (Tablet) | `≥ 64em` (Desktop)

---

## 9. Testing-Strategie

### 9.1 Unit Tests (Jest)

**Ziel:** ≥ 85% Code Coverage

| Testbereich | Dateien |
|-------------|---------|
| API-Services | `booking-api.service.spec.ts`, `appointment-api.service.spec.ts`, `workshop-calendar-api.service.spec.ts` |
| Guards | `guards/*.spec.ts` (6 Dateien) |
| Resolver | `appointments.resolver.spec.ts`, `services.resolver.spec.ts` |
| Models | `branch-config.model.spec.ts`, `location.model.spec.ts`, `notes-extras.model.spec.ts`, `service.model.spec.ts` |
| Store | `booking.store.ts` (inkl. `submitBooking`-Tests) |
| Accessibility | `accessibility.service.spec.ts` |

**Testmuster:**
- Services: `HttpClientTestingModule` mit Mock-Responses
- Guards: Store-State mocken, Router-Navigation verifizieren
- Store `submitBooking`: Mock `AppointmentApiService.createAppointment`, Erfolg/Fehler prüfen

### 9.2 E2E Tests (Playwright)

- Wizard-Durchlauf: Standort → Marke → Service → Termin → **Buchung absenden**
- Deep-Link-Szenarien: Direkte URL mit Query-Params
- Fehlerszenarien: API nicht erreichbar, Buchungsfehler, Circuit-Breaker aktiv
- **Barrierefreiheit:** High-Contrast-Toggle, Fokus-Navigation
- 3 Viewports: Mobile, Tablet, Desktop

```bash
npm test                # Jest Watch-Modus
npm run test:coverage   # Coverage-Report
npm run e2e             # Playwright E2E (3 Viewports)
```

---

## 10. Sicherheit

### 10.1 Session-Token-Verwaltung

- **SessionTokenInterceptor** fügt bei jedem API-Aufruf automatisch hinzu:
  - `X-Session-Token` — Authentifizierung
  - `X-Correlation-Id` — Request-Tracking
  - `X-Session-Id` — Session-Zuordnung
- Tokens werden in HttpOnly Cookies gespeichert (kein localStorage)

### 10.2 Origin Guard & Input-Validierung

- **Origin Guard:** Validiert die Herkunft eingehender Requests
- **Input-Validierung:** Reactive Forms mit typisierten Validatoren (kein `ngModel`)
- **Kein `[innerHTML]`** — Angular Template Escaping für alle Benutzereingaben
- **Kein `eval()`** — Statische Code-Analyse per ESLint-Rule

### 10.3 DoS-Schutz

- **CircuitBreakerService:** OPEN → HALF_OPEN → CLOSED Zustände
- Rate-Limiting auf API-Ebene verhindert Überlastung

---

## 11. Fehlerbehandlung

Alle `rxMethod`-Aufrufe im Store nutzen `catchError`:

```
API-Fehler → catchError → patchState({ error/bookingError, isLoading/bookingSubmitting: false }) → EMPTY
```

| Zustand | Signal | UI-Auswirkung |
|---------|--------|---------------|
| Daten laden | `isLoading` | Spinner |
| Buchung läuft | `bookingSubmitting` | Submit-Button deaktiviert + Spinner |
| Ladefehler | `error` | Fehlermeldung + Retry-Button |
| Buchungsfehler | `bookingError` | Fehlermeldung im Übersichts-Step |
| Buchung erfolgreich | `bookingSubmitted` + `appointmentId` | Bestätigungsansicht |

- **Fallback-Adresse:** `BranchDto.description` leer → `"Musterstraße 1, 00000 Musterhausen"`
- **Deep-Link-Fehler:** Ungültige Branch-ID → stiller Fallback zum normalen Flow

