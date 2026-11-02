# Feature Documentation: REQ-015 API Data Integration — Booking Feature

> **Requirement:** REQ-015 — API Data Integration
> **Status:** Implemented | **Priority:** High
> **Wizard Step:** Cross-Cutting (affects Steps 1–8)
> **Last Updated:** Real API integration for booking submission

---

## 1. Feature Overview

The Booking feature implements a multi-step wizard for scheduling workshop appointments. The user progresses through the following steps:

1. **Location Selection** — Choose a branch from the backend API
2. **Brand Selection** — Choose a vehicle brand based on the selected branch
3. **Service Selection** — Select desired services (static data)
4. **Notes & Extras** — Mobility option, appointment preference, callback request
5. **Appointment Selection** — Available time slots from SoftNet API (`POST /api/v1/snaps`)
6. **Workshop Calendar** — Alternative calendar view for time slots
7. **Car Information** — Customer and vehicle data via reactive forms
8. **Booking Overview** — Summary + **real booking submission via `POST /api/v1/appointments`**

REQ-015 migrates location and brand data from static files to the NestJS backend API. Deep-linking via URL parameters enables direct entry into brand selection. The booking overview now submits data via the real Appointments API to the SoftNet backend.

---

## 2. Architecture

### 2.1 Container/Presentational Pattern

Each wizard route follows the **Container/Presentational** pattern:

| Container Component | Path |
|---------------------|------|
| `LocationSelectionContainerComponent` | `src/app/features/booking/components/location-selection/` |
| `BrandSelectionContainerComponent` | `src/app/features/booking/components/brand-selection/` |
| `ServiceSelectionContainerComponent` | `src/app/features/booking/components/service-selection/` |
| `NotesContainerComponent` | `src/app/features/booking/components/notes/` |
| `AppointmentSelectionContainerComponent` | `src/app/features/booking/components/appointment-selection/` |
| `WorkshopCalendarContainerComponent` | `src/app/features/booking/components/workshop-calendar/` |
| `CarinformationContainerComponent` | `src/app/features/booking/components/carinformation/` |
| `BookingOverviewContainerComponent` | `src/app/features/booking/components/booking-overview/` |

- **Container components** inject the `BookingStore` and coordinate data flow
- **Presentational components** receive data via `input()` and emit events via `output()`
- All components use `OnPush` change detection

### 2.2 NgRx Signal Store

**File:** `src/app/features/booking/stores/booking.store.ts`

The `BookingStore` is a `signalStore` with `providedIn: 'root'` that manages the entire wizard state:

```
BookingState {
  branches, selectedBranch, brands, selectedBrand,
  locations, selectedLocation, services, selectedServices,
  bookingNote, appointments, selectedAppointment,
  workshopCalendarDate, workshopCalendarDays,
  customerInfo, vehicleInfo, privacyConsent,
  notesExtras, bookingSubmitted, isLoading, error, isDeepLinked,
  bookingSubmitting,   // NEW — loading state during API call
  bookingError,        // NEW — error message on submission failure
  appointmentId        // NEW — appointment ID returned by backend
}
```

**Computed Signals:**
- `hasBranchSelected`, `hasBrandSelected`, `hasLocationSelected` — Guard checks
- `hasServicesSelected`, `selectedServiceCount` — Service status
- `isBookingComplete` — Validation for the booking overview step
- `hasWorkshopSlotSelected` — Combination of appointment + calendar date

**rxMethod Methods (API Calls):**
- `loadBranches()` — `BookingApiService.getBranches()`
- `loadBrands(branchId)` — `BookingApiService.getBrands()` / `getBrandsByBranch()`
- `loadLocations()` — `BookingApiService.getLocations(brand?)`
- `loadServices()` — `BookingApiService.getServices()` (static)
- `loadAppointments()` — `AppointmentApiService.getAppointments()`
- `loadWorkshopCalendarDays(fromDate)` — `WorkshopCalendarApiService.getWorkshopCalendarDays()`
- `submitBooking()` — **NEW** — `AppointmentApiService.createAppointment()` (real API)

**Booking Submission Flow (`submitBooking`):**
```
submitBooking()
  → patchState({ bookingSubmitting: true, bookingError: null })
  → Build request from store signals: branchId, snapId, customerId, carId, jobTypeIds, operationIds, note, mobilityOption, appointmentPreference, callbackOption
  → AppointmentApiService.createAppointment(request) → POST /api/v1/appointments
  → Success: patchState({ bookingSubmitted: true, bookingSubmitting: false, appointmentId })
  → Error: patchState({ bookingSubmitting: false, bookingError: message })
```

**State Management Methods:**
- `setBrand()`, `setLocation()`, `selectBranch()` — Set selections
- `toggleService()`, `confirmServiceOptions()`, `deselectService()` — Service management
- `setCustomerInfo()`, `setVehicleInfo()`, `setPrivacyConsent()` — Customer data
- `setNotesExtras()` — Notes & extras
- `resetFromStep(targetIndex)` — Cascading reset from a given wizard step
- `setDeepLinked()` — Activate deep-link mode

### 2.3 Services

| Service | File | Responsibility |
|---------|------|----------------|
| `BookingApiService` | `services/booking-api.service.ts` | Branches, locations, brands, services via HTTP |
| `AppointmentApiService` | `services/appointment-api.service.ts` | Appointment slots (`POST /snaps`) + **create/verify bookings** |
| `WorkshopCalendarApiService` | `services/workshop-calendar-api.service.ts` | Workshop calendar via `POST /api/v1/snaps` |
| `AccessibilityService` | `shared/services/accessibility.service.ts` | Accessibility settings, high-contrast toggle |

**New Methods in `AppointmentApiService`:**

| Method | Signature | Endpoint |
|--------|-----------|----------|
| `createAppointment()` | `(request: CreateAppointmentRequest) → Promise<AppointmentResponse>` | `POST /api/v1/appointments` |
| `getAppointment()` | `(appointmentId: string) → Promise<AppointmentResponse>` | `GET /api/v1/appointments/:id` |

All services use `HttpClient` with `firstValueFrom()` for Promise-based API calls.

---

## 3. API Integration

### 3.1 Branches — `GET /api/v1/branches`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/branches` | Load all branches |
| `GET` | `/api/v1/branches/:id` | Load a single branch (deep-link) |

**Response Format:**
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

### 3.2 Snaps (Appointment Slots) — `POST /api/v1/snaps`

**Request Body:**
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

**Modes:** `next-available` (4 days) | `date-range` (7-day window, max 3 days)

### 3.3 Appointments (Bookings) — `POST /api/v1/appointments`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/appointments` | **Create an appointment** (real SoftNet integration) |
| `GET` | `/api/v1/appointments/:id` | **Verify an appointment** (post-booking) |
| `PUT` | `/api/v1/appointments/:id/cancel` | Cancel an appointment |

**`CreateAppointmentRequest`:**
```json
{
  "branchId": "vw-essen",
  "snapId": "snap-2026-03-10-0900",
  "customerId": "max-mustermann",
  "carId": "E-MU-1234",
  "jobTypeIds": ["inspection"],
  "operationIds": ["op-1"],
  "note": "Please check tires",
  "mobilityOption": "courtesy-car",
  "appointmentPreference": "morning",
  "callbackOption": "no"
}
```

**`AppointmentResponse`:** `id`, `status`, `date`, `time`, `branchName`, `services[]`, `customerName`, `licensePlate`

### 3.4 HTTP Infrastructure

- **SessionTokenInterceptor** — Automatically attaches `X-Session-Token`, `X-Correlation-Id`, `X-Session-Id` headers
- **CircuitBreakerService** — Rate-limiting for DoS protection (OPEN → HALF_OPEN → CLOSED)
- **TraceService** — Logging/tracing for API calls

---

## 4. Data Flow (Resolver → Store → Component)

### 4.1 Loading Data (Locations / Brands / Appointments)

```
/home/location → deepLinkGuard → locationsResolver
  → loadBranches() + loadLocations() → GET /api/v1/branches → LocationDisplay[]

/home/brand → locationSelectedGuard → brandsResolver
  → loadBrands(branchId) → getBrandsByBranch() → BrandDisplay[]

/home/appointment → servicesSelectedGuard → appointmentsResolver
  → loadAppointments() → POST /api/v1/snaps → AppointmentSlot[] (4 days)
```

### 4.2 Submitting a Booking (NEW)

```
BookingOverviewContainerComponent → BookingStore.submitBooking()
  → patchState({ bookingSubmitting: true })
  → AppointmentApiService.createAppointment(request)
    → POST /api/v1/appointments
  → Success: patchState({ bookingSubmitted: true, appointmentId })
  → Error: patchState({ bookingError: message })
```

### 4.3 Deep-Linking

```
/#/home/location?groupId=vw&branchId=vw-essen
  → deepLinkGuard → getBranchById → selectBranch → loadBrands → Redirect /home/brand
```

---

## 5. Routing & Guards

**File:** `src/app/features/booking/booking.routes.ts`

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

All components are loaded via **lazy loading** (`loadComponent`).

---

## 6. Internationalization (i18n)

**File:** `src/app/core/i18n/translations.ts`

Type-safe i18n keys via Proxy pattern (`{{ t.feature.key | translate }}`). Supported languages:

| Language | Code | Example `submitting` | Example `submitted` |
|----------|------|-----------------------|----------------------|
| German | `de` | "Wird gesendet..." | "Anfrage gesendet" |
| English | `en` | "Sending..." | "Request Sent" |
| Ukrainian | `uk` | "Надсилається..." | "Запит надіслано" |
| French | `fr` | "Envoi en cours..." | "Demande envoyée" |
| Arabic | `ar` | "جارٍ الإرسال..." | "تم إرسال الطلب" |

**New i18n Keys (Booking Submission):**
- `booking.navigation.submitting` — Loading state during booking
- `booking.navigation.submitted` — Confirmation after successful booking

All UI text uses `ngx-translate` with the feature alias pattern:
```typescript
protected readonly bookingoverview = i18nKeys.booking.bookingoverview;
// Template: {{ bookingoverview.submitting | translate }}
```

---

## 7. Accessibility (WCAG 2.1 AA)

### 7.1 AccessibilityService

**File:** `src/app/shared/services/accessibility.service.ts`

The `AccessibilityService` manages three accessibility settings:

| Setting | HTML Attribute | CSS Class |
|---------|---------------|-----------|
| Font Size | `data-font-size` on `<html>` | — |
| High Contrast | `data-high-contrast` on `<html>` | `high-contrast` on `<body>` |
| Reduced Motion | — | `reduce-motion` on `<html>` |

**`applyToDocument(state)`** sets attributes/classes on the DOM:
- `state.highContrast === true` → `document.body.classList.add('high-contrast')`
- `state.reducedMotion === true` → `html.classList.add('reduce-motion')`

### 7.2 Focus Management

- **`:focus-visible`** on all interactive elements (buttons, cards, inputs)
- `outline: 0.1875em solid var(--color-focus-ring)` with `outline-offset: 0.125em`
- No `outline: none` without a `:focus-visible` alternative (WCAG-compliant)

### 7.3 Touch Targets & Contrast

- Minimum size: `var(--touch-target-min)` (2.75em / 44px)
- Contrast ratio: at least 4.5:1 (text), 3:1 (large elements)
- `prefers-reduced-motion` media query is respected

---

## 8. Styling & CSS Variables

All colors and spacing use CSS variables instead of hardcoded values:

| Variable | Usage |
|----------|-------|
| `--color-background-surface` | Backgrounds (overlays, modals, dropdowns) |
| `--color-primary` | Primary action color |
| `--color-text-primary` | Main text color |
| `--color-text-secondary` | Secondary text color |
| `--color-border` | Border color |
| `--color-focus-ring` | Focus indicator |
| `--spacing-*` | Spacing (xs, sm, md, lg, xl) |
| `--font-size-*` | Font sizes |
| `--radius-*` | Border radii |
| `--shadow-*` | Shadow effects |
| `--touch-target-min` | Minimum touch target size |

**Mobile-First Breakpoints:** `< 48em` (Mobile) | `≥ 48em` (Tablet) | `≥ 64em` (Desktop)

---

## 9. Testing Strategy

### 9.1 Unit Tests (Jest)

**Target:** ≥ 85% code coverage

| Test Area | Files |
|-----------|-------|
| API Services | `booking-api.service.spec.ts`, `appointment-api.service.spec.ts`, `workshop-calendar-api.service.spec.ts` |
| Guards | `guards/*.spec.ts` (6 files) |
| Resolvers | `appointments.resolver.spec.ts`, `services.resolver.spec.ts` |
| Models | `branch-config.model.spec.ts`, `location.model.spec.ts`, `notes-extras.model.spec.ts`, `service.model.spec.ts` |
| Store | `booking.store.ts` (incl. `submitBooking` tests) |
| Accessibility | `accessibility.service.spec.ts` |

**Test Patterns:**
- Services: `HttpClientTestingModule` with mock responses
- Guards: Mock store state, verify router navigation
- Store `submitBooking`: Mock `AppointmentApiService.createAppointment`, verify success/error flows

### 9.2 E2E Tests (Playwright)

- Full wizard flow: Location → Brand → Service → Appointment → **Submit Booking**
- Deep-link scenarios: Direct URL with query parameters
- Error scenarios: API unreachable, booking failure, circuit breaker active
- **Accessibility:** High-contrast toggle, focus navigation
- 3 viewports: Mobile, Tablet, Desktop

```bash
npm test                # Jest watch mode
npm run test:coverage   # Coverage report
npm run e2e             # Playwright E2E (3 viewports)
```

---

## 10. Security

### 10.1 Session Token Management

- **SessionTokenInterceptor** automatically attaches on every API call:
  - `X-Session-Token` — Authentication
  - `X-Correlation-Id` — Request tracking
  - `X-Session-Id` — Session binding
- Tokens are stored in HttpOnly cookies (no localStorage)

### 10.2 Origin Guard & Input Validation

- **Origin Guard:** Validates the origin of incoming requests
- **Input Validation:** Reactive forms with typed validators (no `ngModel`)
- **No `[innerHTML]`** — Angular template escaping for all user input
- **No `eval()`** — Static code analysis via ESLint rule

### 10.3 DoS Protection

- **CircuitBreakerService:** OPEN → HALF_OPEN → CLOSED states
- Rate-limiting at API level prevents overload

---

## 11. Error Handling

All `rxMethod` calls in the store use `catchError`:

```
API error → catchError → patchState({ error/bookingError, isLoading/bookingSubmitting: false }) → EMPTY
```

| State | Signal | UI Effect |
|-------|--------|-----------|
| Loading data | `isLoading` | Spinner |
| Booking in progress | `bookingSubmitting` | Submit button disabled + spinner |
| Load error | `error` | Error message + retry button |
| Booking error | `bookingError` | Error message in overview step |
| Booking successful | `bookingSubmitted` + `appointmentId` | Confirmation view |

- **Fallback address:** `BranchDto.description` empty → `"Musterstraße 1, 00000 Musterhausen"`
- **Deep-link errors:** Invalid branch ID → silent fallback to normal flow

