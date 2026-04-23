# REQ-017: Cleanup — Loading-Spinner + Wizard-Bugfixes

**Version:** 1.0
**Status:** Implemented
**Date:** 2026-04-23

---

## Overview

This release addresses six parallel cleanup topics in the booking wizard:

1. **Loading spinners** added to all server calls (unified pattern borrowed from location-selection)
2. **Stale-snaps bug fixed** — appointment slots are freshly fetched on every entry into step 5 (appointment selection)
3. **Rental car categories** — new API endpoint `/api/v1/rentcategories` + dynamic dropdown on the notes page
4. **iframe embedding** — the wizard can now be embedded as an iframe on the dealership website; a close button in the header navigates back
5. **Fingerprint configuration** — the SoftNet auth fingerprint is now read directly from an env variable (no more file IO)
6. **Booking submit** — retry mechanism on failed bookings

---

## 1. Loading-Spinner Pattern

### Reference Pattern (adopted from location-selection)

Every wizard page with an API call uses three states:

```
Loading (mat-spinner + text) → Error (icon + retry button) → Content
```

**Affected components:**
- `appointment-selection-container` (appointment selection, step 5)
- `service-selection-container` (service selection, step 3)
- `workshop-calendar-container` (workshop calendar)
- `booking-overview-container` (booking overview — submit spinner)

### Accessibility (WCAG 2.1 AA)

- Loading block: `role="status"` + `aria-live="polite"`
- Error block: `role="alert"` + `aria-live="assertive"`
- Retry button: `mat-flat-button` with focus styles

### i18n Keys (DE/EN/UK/FR/AR)

| Key | EN |
|-----|----|
| `booking.api.loading` | Loading data... |
| `booking.api.submitting` | Sending your request... |
| `booking.api.errorTitle` | Loading Error |
| `booking.api.errorUnknown` | An unknown error occurred. Please try again. |
| `booking.api.errorSubmit` | The booking could not be sent. Please try again. |
| `booking.api.retryButton` | Retry |

---

## 2. Stale-Snaps Bug

**Problem:** When navigating back in the wizard (e.g. picking a different service), previously seen appointments were not invalidated. On submit this caused `400 Bad Request — snap SB allocation already used`.

**Fix:** `BookingStore.loadAppointments` now clears `appointments` and `selectedAppointment` in state first, before fetching fresh data. The resolver calls the method on every route entry — no more cache optimisation; every step-5 navigation triggers a fresh backend call.

**Scenarios covered (all solved):**
- Pick appointment → back → different service → step 5
- Pick appointment → back → different location → step 5
- Pick appointment → back → different brand → step 5
- User dwells on step 5 → books
- First navigation to step 5

---

## 3. Rental Car Categories

### Backend

New module `server/src/main/java/com/autohaus/backend/rentcategories/`:

| Component | File |
|-----------|------|
| Controller | `RentCategoriesController.java` → `GET /api/v1/rentcategories` |
| Service | `RentCategoriesService.java` → maps SoftNet `/api/planning/rentcategories` |
| DTO | `RentCategoryResponseDto.java` (`id`, `groupId`, `name`, `description`, `brands[]`, `branches[]`) |

### Frontend

| Component | File |
|-----------|------|
| Model | `rent-category.model.ts` |
| API service | `rent-category-api.service.ts` |
| Resolver | `rent-categories.resolver.ts` (for `/notes`) |
| Store | `BookingStore.loadRentCategories` + `setSelectedRentCategoryId` |
| UI | Dropdown in `notes-extras-form.component` (visible only when categories are present) |

### Submit integration

On booking, `rentCategoryId` is sent to `POST /api/v1/appointments`. The backend sets in the SoftNet snap/wishlist:
```json
{
  "isFleet": true,
  "fleetType": "rentObject",
  "fleetCategoryid": "<id>"
}
```

---

## 4. iframe Embedding

### Backend (SecurityConfig)

- `X-Frame-Options` changed from `DENY` to `SAMEORIGIN`
- New CSP header: `frame-ancestors 'self' https://www.gottfried-schultz.de`

### Frontend (header)

- New close button in the header, visible only when `window.self !== window.top` (= app runs inside an iframe)
- Click redirects via `window.top.location.href` to the fixed return URL:
  `https://www.gottfried-schultz.de/werkstatt-termin/`
- A11y: `aria-label` with i18n key `header.close.buttonLabel`

---

## 5. Fingerprint Configuration

### Before

`SOFTNET_FINGERPRINT_PATH` contained a file path → backend read the JSON via `Files.readString()`.

### After

`SOFTNET_FINGERPRINT_PATH` contains the JSON directly as a string → `ObjectMapper.readTree()` parses it without any file IO.

### Local development

```bash
export SOFTNET_FINGERPRINT_PATH="$(cat cognigy.net_fingerprint-*.json)"
```

### Container deployment (OpenShift / Docker)

The env variable is injected by the deployment system (GitLab secrets store → K8s env vars). No mounted file needed anymore.

---

## 6. Booking Submit with Retry

**Problem (before):** On a failed booking, the button remained disabled (`bookingSubmitted: true` was set even on errors).

**Fix:**
- `bookingSubmitted` is now only set to `true` on success
- New store method `resetBookingSubmission()` clears `bookingError: null` + `bookingSubmitted: false`
- Container uses a `submitError` computed signal + `onRetry()` that re-submits

---

## Test Coverage

- **Frontend:** 528/528 tests PASS
- **Backend:** 201 tests PASS
- **New test files:** 3 (2 frontend, 1 backend)
- **Extended tests:** 11 files

---

## Migration / Breaking Changes

### Backend
- `CreateAppointmentRequest`: new `rentCategoryId` field (nullable, backwards compatible)
- `SOFTNET_FINGERPRINT_PATH` env variable now holds JSON content (previously a file path)

### Frontend
- `BookingStore`: new state `rentCategories`, `selectedRentCategoryId`
- New route resolver: `/notes` requires `rentCategoriesResolver`

---

## Deployment Notes

1. Before deploy: change the `SOFTNET_FINGERPRINT_PATH` env variable to hold the JSON content (not the file path)
2. Dealership website: set up iframe embedding at `https://buchung.example.com/home/brand?branchId=X&groupId=Y`
3. Review the dealership website CSP (`child-src` must allow the iframe host)
