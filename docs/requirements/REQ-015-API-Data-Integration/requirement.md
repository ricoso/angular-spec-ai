# REQ-015: API Data Integration

**Status:** In Review  
**Priority:** High  
**Type:** Functional  
**Created:** 2026-03-09  
**Author:** Copilot  
**Wizard-Step:** Cross-Cutting (affects Steps 1 + 2)

---

## 1. Overview

### 1.1 Purpose
Migrate Location and Brand data from static files (`branch-config.json`, `location.model.ts`, `brand.model.ts`) to the NestJS Backend API (`GET /api/v1/branches`). The API is the ONLY data source at runtime — `branch-config.json` is retained for development/seeding purposes only. Services (Dienstleistungen) remain static and are NOT part of this requirement.

### 1.2 Scope
**Included:**
- Load Location data via `GET /api/v1/branches` (all branches → mapped to `LocationDisplay[]`)
- Load Brand data via `GET /api/v1/branches` (filtered by selected location → mapped to `BrandDisplay[]`)
- DoS protection via `CircuitBreakerService` (rate limiting — no user authentication, protects backend from stolen session cookie attacks)
- Loading states (spinner), error states (message + retry button) in UI
- Integrate existing `SessionTokenService`, `sessionTokenInterceptor`, `TraceService`, `CircuitBreakerService`
- Extend existing `BookingApiService` methods (`getBrands`, `getLocations`) to call real API
- Extend existing `BookingStore` `loadLocations` and `loadBrands` rxMethods with error handling
- Remove `<app-header>` from `app.component.html` (header is no longer displayed)
- Remove `<footer>` from `app.component.html` (footer is no longer displayed)
- Add `FloatingOverlayComponent` (shared) with Cart + Accessibility round buttons, fixed bottom-right
- Load Appointment/Snap data via `POST /api/v1/snaps` with real `branchId`, `serviceIds`, `brandName` from Store
- Backend mapping of frontend service IDs to SoftNet operation ObjectIDs (`FRONTEND_TO_SOFTNET_OPERATION`)
- Accessibility panel in floating overlay (language, font size, high contrast, reduced motion via `AccessibilityStore`)

**Unchanged (explicitly preserved):**
- Location cards (`location-cards.component`) — visual design, template, and styling remain identical; only the data source changes from static to API
- Brand cards (`brand-cards.component`) — visual design, template, and styling remain identical; only the data source changes from static to API
- Wizard breadcrumbs (`wizard-breadcrumb.component`) — visually and functionally unchanged

**Excluded:**
- Service data (Dienstleistungen) — remains static via `AVAILABLE_SERVICES`
- New Store creation — existing `BookingStore` is extended
- Deleting `branch-config.json` — kept for development/seeding only (NOT used at runtime)
- Backend changes — `BranchesController` and `BranchesService` are already implemented (REQ-014)
- Redesigning card layouts or breadcrumb navigation — these stay as-is

**Booking API (verification flow):**
- `POST /api/v1/appointments` — creates a real appointment via SoftNet (requires `branchId`, `snapId`, `customerId`, `carId`, `jobTypeIds`, `operationIds`)
- `GET /api/v1/appointments/:id` — verifies that the appointment was created, returns `AppointmentResponseDto` with `id`, `status`, `date`, `time`, `branchName`
- `PUT /api/v1/appointments/:id/cancel` — cancels an existing appointment

> **Note:** Full frontend booking submission (`submitBooking()` → real API) is deferred to a separate requirement refinement. The backend endpoints are already implemented and can be tested via E2E.

### 1.3 Related Requirements
- REQ-014: NestJS Backend (provides `GET /api/v1/branches` endpoint)
- REQ-003: Standortwahl (Location Selection — Step 1 in wizard, consumes `locations` from Store)
- REQ-002: Markenauswahl (Brand Selection — Step 2 in wizard, consumes `brands` from Store)
- REQ-013: Branch-Marke-Tausch (Location→Brand flow order, already implemented)
- REQ-007: WizardStateSync (reset logic when selections change)

---

## 2. User Story

**As a** customer  
**I want** to see real dealership locations and brands from the backend  
**So that** I can book a service at an actually existing dealership with up-to-date data.

**Acceptance Criteria:**
- [x] AC-1: Locations are loaded from `GET /api/v1/branches` on wizard entry
- [x] AC-2: Brands are filtered by selected location from API response
- [x] AC-3: A loading spinner is shown while data is being fetched
- [x] AC-4: If the API is unreachable, an error message is shown to the user
- [x] AC-5: An error message with a retry button is shown on API failure
- [x] AC-6: `SessionTokenInterceptor` attaches `X-Session-Token`, `X-Correlation-Id`, `X-Session-Id` headers automatically
- [x] AC-7: The `CircuitBreakerService` blocks excessive requests to protect the backend from DoS attacks (rate limiting — no user authentication, stolen session cookie protection)
- [x] AC-8: When circuit is HALF_OPEN, a probe request is attempted; on success, circuit closes and API data is used
- [x] AC-9: Wizard flow remains: Location (Step 1) → Brand (Step 2) → Services (Step 3)
- [x] AC-10: Static data files (`branch-config.json`, `ALL_LOCATIONS`, `BRANDS_BY_LOCATION`) are NOT used at runtime
- [x] AC-11: Header (`<app-header>`) is completely removed from `app.component.html`
- [x] AC-12: Footer (`<footer>`) is completely removed from `app.component.html`
- [x] AC-13: A floating action overlay with Cart and Accessibility icons appears fixed at bottom-right on all wizard pages
- [x] AC-21: Clicking the Accessibility FAB opens a panel with language selector, font size options, high contrast toggle, and reduced motion toggle (reuses `AccessibilityMenuComponent`)
- [x] AC-22: Accessibility settings (font size, high contrast, reduced motion) are persisted via `AccessibilityStore` and applied to the document immediately
- [x] AC-23: Cart panel and Accessibility panel are mutually exclusive — opening one closes the other
- [x] AC-14: Location cards display `formattedAddress` derived from API `description` field
- [x] AC-15: When API `description` is empty, null, or undefined, the fallback address "Musterstraße 1, 00000 Musterhausen" is shown
- [x] AC-16: Existing card designs (location-cards, brand-cards) remain visually unchanged — only the data source changes
- [x] AC-17: Wizard breadcrumbs remain visually and functionally unchanged
- [x] AC-18: When URL contains query parameters `groupId` and `branchId` (e.g. `/#/home/brand?groupId=vw&branchId=vw-essen`), the location selection is skipped, the matching branch is stored in `BookingStore.selectedLocation`, and the user lands directly on the brand selection page
- [x] AC-19: Deep-linked location data (via URL params) is treated identically to user-selected location data — brands are loaded from API for that branch, all downstream wizard steps work normally
- [x] AC-20: When deep-link is active, the "Standort" breadcrumb step is hidden or disabled — user can navigate back to "Marke" at most, NOT to location selection
- [x] AC-24: Service selection (Step 3) displays static service cards from `AVAILABLE_SERVICES`, user can select/deselect multiple services
- [x] AC-25: Appointment selection (Step 5) loads available time slots from `POST /api/v1/snaps` using real `branchId`, `serviceIds`, and `brandName` from Store
- [x] AC-26: Appointment cards display date, time, and branch name from SoftNet API response
- [x] AC-27: Notes/extras form (Step 4) accepts optional notes and extras for the booking
- [x] AC-28: Car information form (Step 7) captures customer and vehicle data via reactive forms
- [x] AC-29: Booking overview (Step 8) displays a summary of all selected options (location, brand, services, appointment, car info)
- [x] AC-30: Backend `POST /api/v1/appointments` creates an appointment via SoftNet API and returns `AppointmentResponseDto`
- [x] AC-31: Backend `GET /api/v1/appointments/:id` verifies the appointment exists and returns its status, date, time, and branch name

---

## 3. Preconditions

### 3.1 System
- Angular Frontend is running (`npm start`)
- NestJS Backend is running on `http://localhost:3000` (REQ-014)
- `environment.apiUrl` is configured (`http://localhost:3000/api/v1` for dev, `/api/v1` for prod)
- `SessionTokenService` can acquire a token via `GET /api/v1/session/token`
- `sessionTokenInterceptor` is registered in `provideHttpClient(withInterceptors(...))`
- `CircuitBreakerService` is available (`providedIn: 'root'`)
- `TraceService` is available (`providedIn: 'root'`)

### 3.2 User
- No authentication required (session token is auto-acquired)
- User navigates to the booking wizard

### 3.3 Data
- Backend `GET /api/v1/branches` returns `BranchResponseDto[]` with `id`, `name`, `description`, `brands[]`
- `src/assets/branch-config.json` exists for development/seeding purposes only — NOT used at runtime

### 3.4 Handoff (Input)
| Field | Source | Required |
|-------|--------|----------|
| — | — | **No precondition** (entry page of wizard) |

---

## 4. Main Flow

**Step 0:** App layout initialisation (one-time on app load)
- **System:** `app.component.html` renders WITHOUT `<app-header>` and WITHOUT `<footer>` (both removed)
- **System:** `<app-floating-overlay>` is rendered inside the app layout (fixed position, always visible)
- **System:** Floating overlay shows Cart icon (with badge count from store) and Accessibility icon
- **Expected:** No header, no footer; floating overlay visible bottom-right on every page

**Step 1:** App navigates to Location Selection (`/home/location`)
- **System:** `locationsResolver` fires
- **System:** Calls `BookingStore.loadLocations()`
- **System:** Shows loading spinner (`isLoading === true`)
- **System:** `BookingApiService.getLocations()` calls `GET /api/v1/branches`
- **System:** `sessionTokenInterceptor` attaches `X-Session-Token`, `X-Correlation-Id`, `X-Session-Id` headers
- **System:** API returns `{ data: BranchResponseDto[] }`
- **System:** Maps `BranchResponseDto[]` → `LocationDisplay[]` (id, name, formattedAddress from description with fallback)
- **System:** `CircuitBreakerService.recordSuccess()` is called
- **System:** `patchState({ locations, isLoading: false })`
- **Expected:** User sees location cards (existing `location-cards.component` — template/styling unchanged, only data source is now API)

**Step 2:** User selects a location
- **User:** Clicks on a location card
- **System:** Calls `BookingStore.setLocation(location)`
- **System:** Stores `selectedLocation` in BookingStore
- **System:** Navigates to `/home/brand`

**Step 3:** Brand Selection page loads
- **System:** `brandsResolver` fires
- **System:** Calls `BookingStore.loadBrands(locationId)`
- **System:** Shows loading spinner
- **System:** `BookingApiService.getBrandsByLocation(locationId)` calls `GET /api/v1/branches`, filters by location ID, extracts unique brand names
- **System:** Maps brand names → `BrandDisplay[]` (id = lowercase slug, name = display name)
- **System:** `patchState({ brands, isLoading: false })`
- **Expected:** User sees brand cards (existing `brand-cards.component` — template/styling unchanged, only data source is now API)

**Step 4:** User selects a brand
- **User:** Clicks on a brand card
- **System:** Calls `BookingStore.setBrand(brand)`
- **System:** Navigates to `/home/services` (REQ-004)

> **Note:** The wizard breadcrumbs (`wizard-breadcrumb.component`) continue to function exactly as before throughout all steps — no changes.

---

## 5. Alternative Flows

### 5.1 Alt Flow A: API Unreachable — Error State

**Trigger:** `GET /api/v1/branches` fails (network error, timeout, 5xx)

**Flow:**
1. `CircuitBreakerService.recordFailure()` is called
2. `patchState({ error: 'booking.api.errorServerError', isLoading: false })`
3. UI shows error message with retry button
4. Console logs: `[BookingApiService] API call failed`

### 5.2 Alt Flow B: Circuit Breaker OPEN — Request Blocked (DoS Protection)

**Trigger:** Circuit is OPEN (5+ consecutive failures) and user triggers a new request

**Flow:**
1. `CircuitBreakerService.isOpen()` returns `true`
2. API call is blocked — no request is sent to the backend
3. `patchState({ error: 'booking.api.errorServerError', isLoading: false })`
4. UI shows error message: "Server temporarily unavailable, try again later"
5. After `RESET_TIMEOUT_MS` (30s), circuit transitions to `HALF_OPEN`
6. Next request is allowed as a probe; on success → circuit CLOSED; on failure → circuit re-opens

### 5.3 Alt Flow C: Deep-Link via URL-Parameter (groupId + branchId)

**Trigger:** App-URL enthält Query-Parameter `groupId` und `branchId`, z.B.:
`https://example.com/#/home/brand?groupId=vw&branchId=vw-essen`

**Flow:**
1. Ein neuer `deepLinkGuard` (Functional Guard) prüft beim App-Start oder bei Navigation auf `/home/location` ob `groupId` und `branchId` als Query-Params vorhanden sind
2. Guard ruft `GET /api/v1/branches` auf und sucht den Branch mit passender `branchId`
3. Gefundener Branch wird als `LocationDisplay` in `BookingStore.selectedLocation` geschrieben — exakt wie bei User-Selektion
4. Guard navigiert direkt zu `/home/brand` (Location-Schritt wird übersprungen)
5. `brandsResolver` lädt Brands für den vorselektierten Branch von der API
6. Breadcrumbs: "Standort"-Schritt wird NICHT angezeigt (oder als disabled/locked) — der erste navigierbare Schritt ist "Marke"
7. User kann maximal bis "Marke" zurücknavigieren, NICHT zu Location
8. `BookingStore.isDeepLinked` Flag wird auf `true` gesetzt — steuert Breadcrumb-Sichtbarkeit und Guard-Verhalten

**Fehlerfall:**
- `branchId` nicht in API gefunden → Guard ignoriert Parameter, normaler Location-Flow (inkl. Location-Breadcrumb)
- API nicht erreichbar → Guard ignoriert Parameter, normaler Location-Flow

---

## 6. Exception Flows

### 6.1 Exception E1: HTTP 403 — Session Token Missing or Invalid

**Trigger:** `SessionTokenService.ensureToken()` fails or token is expired/invalid

**Flow:**
1. `sessionTokenInterceptor` catches 403 from API
2. `TraceService.error()` logs: `Token acquisition failed`
3. System retries token fetch once via `SessionTokenService.refreshToken()`
4. If retry fails: `patchState({ error: 'booking.api.errorTokenFailed', isLoading: false })`
5. UI shows error message with retry button
6. User clicks retry → `loadLocations()` is re-triggered

### 6.2 Exception E2: HTTP 500 — Server Error

**Trigger:** Backend returns 500 Internal Server Error

**Flow:**
1. `CircuitBreakerService.recordFailure()` is called
2. `TraceService.error()` logs: `API call failed with status 500`
3. If failure count < 5 (circuit still CLOSED): `patchState({ error: 'booking.api.errorServerError', isLoading: false })`
4. UI shows error message with retry button
5. If failure count >= 5: Circuit opens → next call is blocked (DoS protection, Alt Flow B)

### 6.3 Exception E3: Request Timeout

**Trigger:** API request exceeds 10 seconds without response

**Flow:**
1. HttpClient timeout (configured via interceptor or RxJS `timeout` operator)
2. `CircuitBreakerService.recordFailure()` is called
3. `TraceService.error()` logs: `API request timeout`
4. `patchState({ error: 'booking.api.errorTimeout', isLoading: false })`
5. UI shows timeout error message with retry button

### 6.4 Exception E4: Invalid API Response

**Trigger:** API returns data that does not match expected `{ data: BranchResponseDto[] }` schema

**Flow:**
1. `BookingApiService` validates response structure
2. If `response.data` is not an array: shows error message to user
3. `TraceService.error()` logs: `Invalid API response structure`
4. `CircuitBreakerService.recordFailure()` is called
5. `patchState({ error: 'booking.api.errorUnknown', isLoading: false })`

---

## 7. Postconditions

### 7.1 Success
| Field | Type | Value | Source |
|-------|------|-------|--------|
| `BookingStore.locations` | `LocationDisplay[]` | Array of locations | API |
| `BookingStore.selectedLocation` | `LocationDisplay \| null` | User's choice | User selection |
| `BookingStore.brands` | `BrandDisplay[]` | Brands for selected location | API |
| `BookingStore.selectedBrand` | `Brand \| null` | User's choice | User selection |
| `BookingStore.isLoading` | `boolean` | `false` | After load completes |
| `BookingStore.error` | `string \| null` | `null` | On success |

### 7.2 Failure
- `BookingStore.error` contains i18n key for error message
- `BookingStore.isLoading` is `false`
- `BookingStore.locations` remains empty (`[]`)
- UI shows error message with retry button

---

## 8. Business Rules

- **BR-1:** The API is the ONLY data source at runtime. Static data (`branch-config.json`) is NOT used at runtime.
- **BR-2:** `branch-config.json` is NOT deleted — it is development/seeding data only, NOT used at runtime.
- **BR-3:** Services (Dienstleistungen) remain static via `AVAILABLE_SERVICES` — NOT loaded from API.
- **BR-4:** The wizard flow order is: Location (Step 1) → Brand (Step 2) → Services (Step 3). This is unchanged from REQ-013.
- **BR-5:** When a location is selected, brands are filtered by that location. A location change resets brand selection and all subsequent steps.
- **BR-6:** The `CircuitBreakerService` protects the backend from DoS attacks by rate-limiting client requests. Since there is no user authentication, a stolen session cookie could be used for a denial-of-service attack. The circuit opens after 5 consecutive API failures (`FAILURE_THRESHOLD = 5`).
- **BR-7:** The circuit resets to HALF_OPEN after 30 seconds (`RESET_TIMEOUT_MS = 30_000`), allowing a probe request. This is part of the DoS protection recovery mechanism.
- **BR-8:** Every API request must include `X-Session-Token`, `X-Correlation-Id`, and `X-Session-Id` headers (handled by `sessionTokenInterceptor`).
- **BR-9:** When `BranchResponseDto.description` is empty (`""`), `null`, or `undefined` → display the fallback address `"Musterstraße 1, 00000 Musterhausen"` as `formattedAddress` on the location card.
- **BR-10:** The floating action overlay (`FloatingOverlayComponent`) must always be visible on all wizard pages — it is part of the app layout, not a per-page component.
- **BR-11:** The Cart icon in the floating overlay shows a badge count of selected items (same data as the removed header cart icon used to display).
- **BR-16:** The Accessibility FAB opens a panel with the original `AccessibilityMenuComponent` (language, font size, high contrast, reduced motion). Cart and Accessibility panels are mutually exclusive — opening one closes the other. Both FABs show a `close` icon when their respective panel is open.
- **BR-12:** Brand logos are resolved via `BRAND_LOGO_MAP` from `src/assets/brands/`. If no matching SVG exists for a brand name, use `src/assets/brands/default.svg` as fallback. Available logos: `audi.svg`, `bentley.svg`, `bmw.svg`, `bugatti.svg`, `cupra.svg`, `default.svg`, `hyundai.svg`, `mercedes.svg`, `opel.svg`, `porsche.svg`, `rimac.svg`, `seat.svg`, `skoda.svg`, `volkswagen.svg`, `vw_nutzfahrzeuge.svg`.
- **BR-13:** Deep-Link URL-Parameter (`groupId`, `branchId`) ermöglichen das Überspringen der Standortwahl. Die vorselektierte Location wird im Store genauso behandelt wie eine manuell gewählte. Ungültige/unbekannte Parameter werden stillschweigend ignoriert (normaler Flow).
- **BR-14:** Deep-Link-Parameter werden nach Verarbeitung aus der URL entfernt (clean URL), damit bei Browser-Refresh kein erneutes Überspringen passiert.
- **BR-15:** Bei aktivem Deep-Link (`isDeepLinked === true`) wird der "Standort"-Schritt im Breadcrumb ausgeblendet oder als nicht-klickbar dargestellt. Maximale Rücknavigation: bis "Marke". Route `/home/location` wird durch `deepLinkGuard` blockiert und redirected zu `/home/brand`. **Im normalen Flow (`isDeepLinked === false`) bleibt die Rücknavigation zu Location uneingeschränkt möglich** — Breadcrumb "Standort" ist klickbar, Zurück-Button auf der Brand-Seite navigiert zu Location.

---

## 9. Non-Functional Requirements

### Performance
- API response time < 2 seconds (including token acquisition)
- Loading spinner must appear within 100ms of navigation
- Retry attempts: max 1 automatic retry before showing error UI

### Security
- All API calls go through `sessionTokenInterceptor` (X-Session-Token)
- Correlation tracking via `TraceService` (X-Correlation-Id, X-Session-Id)
- No sensitive data stored in localStorage/sessionStorage
- API calls use HTTPS in production (`environment.prod.ts: apiUrl = '/api/v1'`)

### Reliability
- Circuit Breaker pattern for DoS protection: 5 failures → OPEN → 30s → HALF_OPEN → probe
- Protects the backend from excessive requests when no user authentication is present
- When circuit is OPEN: error message shown, no API requests sent

### Usability
- Loading spinner visible during API call
- Error message with retry button on transient failures
- Mobile responsive (unchanged from REQ-003/REQ-002)
- WCAG 2.1 AA compliant

---

## 10. Data Model

### API Response (Backend → Frontend)

```typescript
/** Wrapped API response from NestJS backend */
interface ApiResponse<T> {
  data: T;
  meta?: {
    timestamp: string;
    requestId: string;
  };
}

/** DTO returned by GET /api/v1/branches */
interface BranchDto {
  id: string;
  name: string;
  description: string;
  brands: string[];
}
```

### Frontend Display Models (existing, unchanged)

```typescript
/** Location display model — existing in location.model.ts */
interface LocationDisplay {
  id: string;
  name: string;
  city: string;
}

/** Brand type union — existing in brand.model.ts */
type Brand = 'audi' | 'bmw' | 'mercedes' | 'mini' | 'volkswagen';

/** Brand display model — existing in brand.model.ts */
interface BrandDisplay {
  id: Brand;
  name: string;
}
```

### Address Fallback Mapping

```typescript
/** Maps API description to formattedAddress with fallback */
const FALLBACK_ADDRESS = 'Musterstraße 1, 00000 Musterhausen';

function mapDescription(description: string | null | undefined): string {
  return description?.trim() || FALLBACK_ADDRESS;
}
```

### Mapping Logic

| Source (API) | Target (Frontend) | Transformation |
|---|---|---|
| `BranchDto.id` | `LocationDisplay.id` | Direct copy |
| `BranchDto.name` | `LocationDisplay.name` | Direct copy |
| `BranchDto.description` | `LocationDisplay.formattedAddress` | `description?.trim() \|\| 'Musterstraße 1, 00000 Musterhausen'` |
| `BranchDto.brands[n]` | `BrandDisplay.id` | `name.toLowerCase().replace(/\s+/g, '-')` |
| `BranchDto.brands[n]` | `BrandDisplay.name` | Direct copy |

---

## 11. UI/UX

### Loading State
- Material `<mat-spinner>` centered on page
- Shown when `BookingStore.isLoading()` is `true`
- Text below spinner: `{{ 'booking.api.loading' | translate }}` ("Daten werden geladen..." / "Loading data...")

### Error State
- Material `<mat-card>` with error icon
- Error title: `{{ 'booking.api.errorTitle' | translate }}` ("Fehler beim Laden" / "Loading Error")
- Error message: `{{ store.error() | translate }}`
- Retry button: `<button mat-flat-button>{{ 'booking.api.retryButton' | translate }}</button>` ("Erneut versuchen" / "Retry")
- Retry button calls `store.loadLocations()` or `store.loadBrands()` respectively

### Location Cards (UNCHANGED)
- `location-cards.component` template and SCSS remain **identical** — no visual changes
- Only the data source changes: `formattedAddress` now comes from API `description` field (with fallback)
- Card layout: radio group grid with check icon, name, address (with `location_on` icon), brand logos

### Brand Cards (UNCHANGED)
- `brand-cards.component` template and SCSS remain **identical** — no visual changes
- Only the data source changes: brands are now filtered from API response
- Card layout: radio group grid with check icon, logo image, brand name

### Wizard Breadcrumbs (UNCHANGED)
- `wizard-breadcrumb.component` remains **visually and functionally unchanged**
- Step icons, labels, separators, and active/done/future states are all preserved

### Layout Changes
- Loading spinner and error state replace the card grid while active (unchanged behaviour)

### Footer Removal
- The static `<footer>` with "Autohaus GmbH" in `app.component.html` must be **removed entirely**
- No footer is displayed in the application
- The footer was a placeholder and is no longer needed

### Header Removal
- The `<app-header>` component in `app.component.html` must be **removed entirely**
- The header was a placeholder and is no longer needed
- The Cart icon and Accessibility icon that were in the header are moved to the floating overlay (below)

### Floating Action Overlay (`FloatingOverlayComponent`)
New shared component added to `app.component.html` in place of header/footer.

| Property | Value |
|----------|-------|
| Position | `fixed`, `bottom: 1.5em`, `right: 1.5em` |
| Layout | Two round buttons stacked vertically, `0.5em` gap |
| Button size | `3em` diameter (`width: 3em; height: 3em; border-radius: 50%`) |
| Top button | Cart icon (`shopping_cart`) with badge showing selected-item count |
| Bottom button | Accessibility icon (`accessibility_new`) — opens accessibility settings panel |
| Button style | `var(--color-primary)` background, white (`#fff`) icon colour, `var(--shadow-medium)` box-shadow |
| z-index | `1000` |
| Touch target | Min `2.75em` (WCAG 2.1 AA) |
| Responsive | Same position on all viewports |
| Visibility | Always visible on every wizard page (app-layout level) |
| Panel behaviour | Cart and Accessibility panels are mutually exclusive — opening one closes the other |

#### Accessibility Panel
Clicking the Accessibility FAB opens a panel (same visual style as the Cart panel) containing the original `AccessibilityMenuComponent`:
- **Language selector** — switches app language (DE, EN, UK, FR, AR)
- **Font size** — radio group (small, normal, large, x-large)
- **High contrast** — toggle switch
- **Reduced motion** — toggle switch

Settings are managed via `AccessibilityStore` (providedIn: root) and persisted to localStorage.

---

## 12. API Specification

### GET /api/v1/branches

**Request:**
```http
GET /api/v1/branches HTTP/1.1
Host: localhost:3000
X-Session-Token: <token>
X-Correlation-Id: <sessionId-0001>
X-Session-Id: <sessionId>
```

**Success Response (200):**
```json
{
  "data": [
    {
      "id": "vw-essen",
      "name": "Volkswagen Zentrum Essen",
      "description": "Essen",
      "brands": ["VW", "VW Nutzfahrzeuge", "SEAT", "CUPRA"]
    },
    {
      "id": "audi-essen",
      "name": "Audi Zentrum Essen",
      "description": "Essen",
      "brands": ["Audi"]
    },
    {
      "id": "porsche-essen",
      "name": "Porsche Zentrum Essen",
      "description": "Essen",
      "brands": ["Porsche"]
    }
  ],
  "meta": {
    "timestamp": "2026-03-09T10:00:00.000Z",
    "requestId": "req-abc-123"
  }
}
```

**Error Response (403 — Token Missing):**
```json
{
  "statusCode": 403,
  "message": "Session token required",
  "error": "Forbidden"
}
```

**Error Response (500 — Server Error):**
```json
{
  "statusCode": 500,
  "message": "Internal server error",
  "error": "Internal Server Error"
}
```

### GET /api/v1/branches/:branchId

**Request:**
```http
GET /api/v1/branches/vw-essen HTTP/1.1
Host: localhost:3000
X-Session-Token: <token>
X-Correlation-Id: <sessionId-0002>
X-Session-Id: <sessionId>
```

**Success Response (200):**
```json
{
  "data": {
    "id": "vw-essen",
    "name": "Volkswagen Zentrum Essen",
    "description": "Essen",
    "brands": ["VW", "VW Nutzfahrzeuge", "SEAT", "CUPRA"]
  },
  "meta": {
    "timestamp": "2026-03-09T10:00:00.000Z",
    "requestId": "req-def-456"
  }
}
```

---

## 13. Test Cases

### TC-1: Happy Path — Load Locations from API
- **Given:** Backend is running, API returns 3 branches
- **When:** User navigates to `/home/location`
- **Then:** `BookingStore.locations` contains 3 `LocationDisplay` items, `isLoading === false`, `error === null`

### TC-2: Happy Path — Load Brands by Location from API
- **Given:** Backend is running, user selected location `vw-essen`
- **When:** User navigates to `/home/brand`
- **Then:** `BookingStore.brands` contains brands `["VW", "VW Nutzfahrzeuge", "SEAT", "CUPRA"]` mapped to `BrandDisplay[]`

### TC-3: Error State — API Unreachable
- **Given:** Backend is not running or returns errors
- **When:** User navigates to `/home/location`
- **Then:** `BookingStore.error` is set to i18n key, `isLoading === false`, UI shows error message + retry button

### TC-6: Retry — User Clicks Retry After Error
- **Given:** Error state is visible, backend has recovered
- **When:** User clicks retry button
- **Then:** Loading spinner shown, API call succeeds, locations loaded, error cleared

### TC-7: Loading State — Spinner Shown During Fetch
- **Given:** API is slow (simulated delay)
- **When:** User navigates to `/home/location`
- **Then:** Loading spinner is visible while `isLoading === true`

### TC-8: Circuit Breaker — Blocks Requests After Threshold (DoS Protection)
- **Given:** API fails 5 consecutive times
- **When:** 6th request is made
- **Then:** `CircuitBreakerService.isOpen() === true`, request is blocked, error message shown to user (no API call sent)

### TC-9: Circuit Breaker — Recovers via Half-Open
- **Given:** Circuit is OPEN, 30 seconds have elapsed
- **When:** Next location load is triggered
- **Then:** Probe request sent to API, on success circuit closes and API data is used

### TC-10: Session Token — Interceptor Attaches Headers
- **Given:** Valid session token is available
- **When:** `GET /api/v1/branches` is called
- **Then:** Request includes `X-Session-Token`, `X-Correlation-Id`, `X-Session-Id` headers

### TC-11: Session Token — 403 Triggers Token Refresh
- **Given:** Session token is expired
- **When:** API returns 403
- **Then:** `SessionTokenService` refreshes token, request is retried with new token

### TC-12: Data Mapping — BranchDto to LocationDisplay
- **Given:** API returns `{ id: "vw-essen", name: "VW Zentrum Essen", description: "Essen", brands: ["VW"] }`
- **When:** Mapping is applied
- **Then:** Result is `{ id: "vw-essen", name: "VW Zentrum Essen", city: "Essen" }`

### TC-12b: Data Mapping — Empty Description Fallback
- **Given:** API returns `{ id: "test-env", name: "Test environment", description: "", brands: ["BMW"] }`
- **When:** Mapping is applied
- **Then:** Result is `{ id: "test-env", name: "Test environment", city: "Musterstraße 1, 00000 Musterhausen" }`

### TC-13: Data Mapping — BranchDto Brands to BrandDisplay
- **Given:** API returns branch with `brands: ["VW", "SEAT", "CUPRA"]`
- **When:** Mapping is applied
- **Then:** Result is `[{ id: "vw", name: "VW" }, { id: "seat", name: "SEAT" }, { id: "cupra", name: "CUPRA" }]`

### TC-14: Header Removed
- **Given:** App is loaded
- **When:** Any wizard page is displayed
- **Then:** No `<app-header>` element exists in the DOM, no logo or company name visible at top

### TC-15: Footer Removed
- **Given:** App is loaded
- **When:** Any wizard page is displayed
- **Then:** No `<footer>` element exists in the DOM, no "Autohaus GmbH" text at bottom

### TC-16: Floating Overlay Visible
- **Given:** App is loaded
- **When:** Any wizard page is displayed
- **Then:** Floating overlay with Cart icon and Accessibility icon is visible at bottom-right, both clickable

### TC-20: Accessibility Panel Opens
- **Given:** Floating overlay is visible
- **When:** User clicks the Accessibility FAB
- **Then:** Accessibility panel opens with language selector, font size radio buttons, high contrast toggle, and reduced motion toggle

### TC-21: Accessibility Panel and Cart Panel Mutual Exclusion
- **Given:** Cart panel is open
- **When:** User clicks the Accessibility FAB
- **Then:** Cart panel closes and Accessibility panel opens

### TC-22: Accessibility Settings Applied
- **Given:** Accessibility panel is open
- **When:** User toggles high contrast
- **Then:** Document body receives `high-contrast` class, setting is persisted

### TC-17: Deep-Link — groupId + branchId überspringt Location
- **Given:** Backend läuft, Branch `vw-essen` existiert in API
- **When:** User öffnet `/#/home/brand?groupId=vw&branchId=vw-essen`
- **Then:** Location-Schritt wird übersprungen, `BookingStore.selectedLocation` enthält den Branch `vw-essen`, User sieht Markenauswahl mit Brands dieses Standorts

### TC-18: Deep-Link — ungültige branchId → normaler Flow
- **Given:** Backend läuft, Branch `xyz-invalid` existiert NICHT
- **When:** User öffnet `/#/home/brand?groupId=vw&branchId=xyz-invalid`
- **Then:** Parameter werden ignoriert, User landet auf Location-Auswahl (normaler Flow)

### TC-19: Deep-Link — Breadcrumb zeigt keinen Location-Schritt
- **Given:** User kam via Deep-Link (`branchId=vw-essen`)
- **When:** Markenauswahl wird angezeigt
- **Then:** Breadcrumb zeigt "Marke" als ersten Schritt (active), "Standort" ist NICHT sichtbar/klickbar. Navigation zu `/home/location` wird zu `/home/brand` redirected.

### TC-23: Service Selection — Display and Select Services
- **Given:** User has selected location + brand
- **When:** User navigates to `/home/services`
- **Then:** Service cards are displayed with title and icon; user can select/deselect services; summary bar shows count

### TC-24: Service Selection — Continue to Next Step
- **Given:** User has selected at least one service
- **When:** User clicks continue/next button
- **Then:** User navigates to notes or appointment step

### TC-25: Appointment Selection — Load Snaps from API
- **Given:** User has selected location + brand + services, backend is running
- **When:** User navigates to `/home/appointment`
- **Then:** Appointment cards are loaded from `POST /api/v1/snaps` (real API), at least one time slot is displayed

### TC-26: Appointment Selection — Select Time Slot
- **Given:** Appointment cards are visible
- **When:** User clicks on an appointment card
- **Then:** The selected appointment is highlighted, Store holds the selected snap

### TC-27: Notes Form — Fill Optional Notes
- **Given:** User navigated to `/home/notes`
- **When:** User enters text in the notes field
- **Then:** Notes are stored in BookingStore

### TC-28: Car Information — Fill Customer and Vehicle Forms
- **Given:** User navigated to `/home/carinformation`
- **When:** User fills out customer name, email, phone + vehicle license plate, brand, model
- **Then:** Forms are valid, data is stored in BookingStore

### TC-29: Booking Overview — Display Summary
- **Given:** User navigated to `/home/booking-overview`
- **When:** Page loads
- **Then:** Summary tiles show selected location, brand, services, appointment date/time, and car information

### TC-30: Full Wizard Flow — Location to Booking Overview
- **Given:** Backend is running, data is available
- **When:** User completes all wizard steps (location → brand → services → notes → appointment → carinformation → overview)
- **Then:** Each step loads correctly, data persists across steps, booking overview shows complete summary

### TC-31: Backend — POST /api/v1/appointments Creates Appointment
- **Given:** Valid `branchId`, `snapId`, `customerId`, `carId`, `jobTypeIds`, `operationIds`
- **When:** `POST /api/v1/appointments` is called
- **Then:** SoftNet appointment is created, response contains `id`, `status: "confirmed"`, `date`, `time`

### TC-32: Backend — GET /api/v1/appointments/:id Verifies Appointment
- **Given:** An appointment was created via POST
- **When:** `GET /api/v1/appointments/:id` is called with the returned appointment ID
- **Then:** Response contains the same `id`, `status`, `date`, `time`, `branchName` — confirming the appointment exists

### E2E-Teststrategie

**E2E-Tests laufen IMMER gegen das echte NestJS-Backend.**

- Das NestJS-Backend MUSS vor den Playwright-Tests gestartet werden
- Backend starten: `cd backend && npm run start:dev`
- Frontend starten: `npm start` (oder via `playwright.config.ts` webServer)
- Testdaten kommen aus der echten Backend-API (geseedete Branches/Brands)
- Kein Mocking, kein Intercepting, keine statischen Fallback-Daten
- `branch-config.json` wird zur Laufzeit NICHT verwendet

**Playwright-Konfiguration:**
- `playwright.config.ts` → Frontend auf Port 4200
- `playwright-backend.config.ts` → Backend auf Port 3000
- Beide müssen laufen für E2E-Tests

---

## 14. Implementation

### Files to Modify
- [x] `src/app/features/booking/services/booking-api.service.ts` — Extend `getLocations()` and `getBrandsByLocation()` to call real API; map `description` → `formattedAddress` with empty-description fallback address
- [x] `src/app/features/booking/stores/booking.store.ts` — Integrate `CircuitBreakerService` in `loadLocations` and `loadBrands` rxMethods
- [x] `src/app/features/booking/resolvers/locations.resolver.ts` — Already calls `store.loadLocations()`, no changes needed
- [x] `src/app/features/booking/resolvers/brands.resolver.ts` — Already calls `store.loadBrands()`, may need to pass locationId
- [x] `src/app/app.component.html` — Remove `<app-header>`, remove `<footer>`, add `<app-floating-overlay>`
- [x] `src/app/app.component.ts` — Import `FloatingOverlayComponent`
- [x] `src/app/features/booking/components/location-cards/location-cards.component.ts` — Only data-mapping changes (if needed for `formattedAddress`); **no template or SCSS changes**
- [x] `src/app/features/booking/components/brand-cards/brand-cards.component.ts` — Only data-mapping changes (if needed); **no template or SCSS changes**

### Files Unchanged (Reference Only)
- `src/app/core/http/session-token.service.ts` — Already implemented
- `src/app/core/http/session-token.interceptor.ts` — Already implemented
- `src/app/core/http/trace.service.ts` — Already implemented
- `src/app/core/http/circuit-breaker.service.ts` — Already implemented
- `backend/src/branches/branches.controller.ts` — Already implemented (REQ-014)
- `backend/src/branches/branches.service.ts` — Already implemented (REQ-014)
- `src/assets/branch-config.json` — Kept for development/seeding only, NOT used at runtime
- `src/app/features/booking/components/location-cards/location-cards.component.html` — Template unchanged
- `src/app/features/booking/components/location-cards/location-cards.component.scss` — Styling unchanged
- `src/app/features/booking/components/brand-cards/brand-cards.component.html` — Template unchanged
- `src/app/features/booking/components/brand-cards/brand-cards.component.scss` — Styling unchanged
- `src/app/shared/components/wizard-breadcrumb/` — Entirely unchanged (template, SCSS, TS)

### New Files
- [x] `src/app/shared/components/floating-overlay/floating-overlay.component.ts` — Standalone component
- [x] `src/app/shared/components/floating-overlay/floating-overlay.component.html` — Cart + Accessibility buttons
- [x] `src/app/shared/components/floating-overlay/floating-overlay.component.scss` — Fixed position, round buttons, shadow
- [x] `src/app/features/booking/guards/deep-link.guard.ts` — Functional Guard: prüft `groupId`/`branchId` Query-Params, schreibt in Store, navigiert zu `/home/brand`

### Effort
- Development: 4-6 hours
- Testing: 2-3 hours

---

## 15. Dependencies

**Requires:**
- REQ-014: NestJS Backend (provides `GET /api/v1/branches` endpoint, `SessionTokenGuard`, `BranchesService`)
- REQ-003: Standortwahl (Location Selection UI — consumes `BookingStore.locations`)
- REQ-002: Markenauswahl (Brand Selection UI — consumes `BookingStore.brands`)
- REQ-013: Branch-Marke-Tausch (Location→Brand wizard flow order)

**Blocks:**
- Future API integration requirements (e.g., Services from API, Appointments from API)

---

## 16. Naming Glossary

### BookingApiService Methods
| Method | Description |
|--------|-------------|
| `getLocations(brand?)` | Load locations from API, optionally filtered by brand. Shows error if API is unreachable. |
| `getBrands()` | Load all unique brands from API. Shows error if API is unreachable. |
| `getBrandsByLocation(locationId)` | Load brands for a specific location from API. Shows error if API is unreachable. |
| `getBrandsByBranch(brandNames)` | Map brand name strings to `BrandDisplay[]`. Unchanged. |
| `getServices()` | Load static services. Unchanged — NOT part of this REQ. |

### BookingStore rxMethods
| Method | Description |
|--------|-------------|
| `loadLocations()` | Trigger location loading via `BookingApiService.getLocations()`. Sets `isLoading`, handles errors. |
| `loadBrands(locationId?)` | Trigger brand loading via `BookingApiService`. Filters by location if provided. Sets `isLoading`, handles errors. |

### BookingStore State Fields (existing)
| Field | Type | Description |
|-------|------|-------------|
| `locations` | `LocationDisplay[]` | All available locations (from API) |
| `selectedLocation` | `LocationDisplay \| null` | User's selected location |
| `brands` | `BrandDisplay[]` | Brands for selected location (from API) |
| `selectedBrand` | `Brand \| null` | User's selected brand |
| `isLoading` | `boolean` | Loading state for API calls |
| `error` | `string \| null` | i18n key for error message |

### Computed Signals (existing, unchanged)
| Signal | Description |
|--------|-------------|
| `locationCount` | Number of available locations |
| `hasLocationSelected` | Whether a location is selected |
| `brandCount` | Number of available brands |
| `hasBrandSelected` | Whether a brand is selected |
| `filteredLocations` | Filtered location list |

---

## 17. i18n Keys

```typescript
// DE
booking: {
  api: {
    loading: 'Daten werden geladen...',
    errorTitle: 'Fehler beim Laden',
    errorServerError: 'Der Server ist momentan nicht erreichbar. Bitte versuchen Sie es später erneut.',
    errorTimeout: 'Die Anfrage hat zu lange gedauert. Bitte versuchen Sie es erneut.',
    errorTokenFailed: 'Die Sitzung konnte nicht hergestellt werden. Bitte laden Sie die Seite neu.',
    errorUnknown: 'Ein unbekannter Fehler ist aufgetreten. Bitte versuchen Sie es erneut.',
    retryButton: 'Erneut versuchen',
    fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
  },
  overlay: {
    cartLabel: 'Warenkorb',
    accessibilityLabel: 'Barrierefreiheit',
    cartBadgeLabel: '{{count}} Artikel im Warenkorb'
  }
}

// EN
booking: {
  api: {
    loading: 'Loading data...',
    errorTitle: 'Loading Error',
    errorServerError: 'The server is currently unavailable. Please try again later.',
    errorTimeout: 'The request took too long. Please try again.',
    errorTokenFailed: 'The session could not be established. Please reload the page.',
    errorUnknown: 'An unknown error occurred. Please try again.',
    retryButton: 'Retry',
    fallbackAddress: 'Musterstraße 1, 00000 Musterhausen'
  },
  overlay: {
    cartLabel: 'Shopping cart',
    accessibilityLabel: 'Accessibility',
    cartBadgeLabel: '{{count}} items in cart'
  }
}
```

---

## 18. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |

---

## 19. Implementation Notes

**Code language is ENGLISH.** All method names, variable names, comments, and interfaces are written in English.

**i18n keys are bilingual (DE + EN).** UI texts are translated via `ngx-translate` using type-safe keys from `translations.ts`.

**Existing infrastructure is reused:**
- `SessionTokenService` + `sessionTokenInterceptor` → automatic token management
- `TraceService` → correlation ID tracking for all API calls
- `CircuitBreakerService` → DoS protection via rate limiting (no user authentication)

**No new Store is created.** All state changes happen within the existing `BookingStore` (`providedIn: 'root'`).

### Bug-Fix: "Mandatory field model not set" (2026-04-13)

The SoftNet API requires a `model` field when creating appointments. The backend was setting `model: dto.vehicle.model || ''` which resulted in an empty string since the frontend vehicle form only collects license plate and mileage (by design). SoftNet rejected the empty string.

**Fix:** The backend now uses a fallback chain: `dto.vehicle.model || snap.wishlist.car?.model || 'Unknown'`. The `model` value is sourced from the cached snap object (which comes from SoftNet's own snaps response) or falls back to `'Unknown'`. The frontend vehicle form intentionally only collects license plate and mileage — no model field is shown to the user.

**Additional:** Added 14 new backend tests (199 total), improved `softnet-api.service.ts` function coverage from 40.9% to 90.9%.

### Bug: Stale Snaps bei Navigation zurueck (2026-04-17)

**Problem:** Gecachte Snaps werden bei Wizard-Ruecknavigation nicht invalidiert → `400 "snap SB allocation already used"` (SoftNet Error Code 779).

**→ Verschoben nach REQ-017 (Cleanup-Requirement), Section 13.** Dort vollstaendig dokumentiert mit Szenarien und Acceptance Criteria.

