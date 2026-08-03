# Feature Documentation: Phone Number Normalization (SMS Format)

**Created:** 2026-08-03
**Requirement:** REQ-033-Telefonnummer-Normalisierung
**Language:** EN
**Status:** Implemented

---

## Overview

Customers enter their mobile phone number in the booking wizard (step 6, `/home/carinformation`) either with a leading `0` (e.g. `017012345678`) or already with a `+49` prefix (e.g. `+491701234567`) — the form accepts both formats. Until now, this raw value was passed through unchanged all the way to SoftNet. SoftNet — the external provider that, among other things, handles SMS delivery for appointment confirmations — does not reliably process numbers with a leading `0` for SMS dispatch. The result was a **silent failure**: no error, no exception, but the SMS never reached the customer.

This feature introduces a purely internal, backend-side normalization: before the number is sent to SoftNet, a leading `0` is replaced with `+49`. If the number already starts with `+`, it is left unchanged (no double-prefixing). The customer still sees exactly what they typed in the form — there is no visible UI change, no new error message, and no change to the existing frontend validation.

---

## User Flow

n/a — pure backend change with no visible UI change. The mockup (`mockup.html`/`mockup.png`) shows, for context, the existing hint text on the mobile phone field explaining that both formats are accepted — this requirement does not change that display.

---

## How It Works (System Flow)

### Step 1: Customer enters mobile phone number

**System:** The form validates as before against `^(\+|0)[0-9\s]+$` — unchanged.

### Step 2: Booking is submitted

**System:** `BookingStore.submitBooking()` passes `mobile`/`phone` from `customerInfo` into the backend request unchanged — no normalization happens in the frontend.

### Step 3 (NEW): Backend normalizes before the SoftNet call

**System:** `AppointmentsService.patchSnapWithBookingData()` calls `PhoneNumberNormalizer.toSoftnetFormat(...)` for both `mobile` and `phone` **before** writing the values into `wishlist.client.mobile`/`.phone`.

**Result:** SoftNet always receives the number in `+49…` format, regardless of the format the customer typed.

### Step 4: SoftNet processes the number reliably

**Result:** SMS delivery works as expected — the customer never notices anything changed.

### Normalization Rule (exact)

| Input (example) | Output sent to SoftNet | Rule |
|---------------------|---------------------|-------|
| `017012345678` | `+4917012345678` | Leading `0` → `+49`, rest unchanged |
| `0170 123 45678` | `+49170 123 45678` | Only the leading `0` is replaced — internal whitespace is preserved |
| `+491701234567` | `+491701234567` | Already has a `+` prefix → unchanged (no double-prefixing) |
| `null` / blank / no `0`/`+` prefix | returned unchanged | Defensive — no crash, no exception |

---

## Responsive Views

n/a — backend feature, no UI change.

---

## Accessibility

n/a — backend feature, no UI change.

---

## Technical Details

| Property | Value |
|-------------|------|
| Route | n/a — no frontend change |
| Container Component | n/a — no frontend change |
| Store | n/a — no frontend change |
| API Service | n/a — no frontend service affected |

**New/changed backend classes** (`server/src/main/java/com/autohaus/backend/`):

| Class/File | Change |
|--------------|----------|
| `common/util/PhoneNumberNormalizer.java` | **New** — static method `toSoftnetFormat(String)`: leading `0` → `+49`, `+` prefix unchanged, otherwise returned unchanged defensively |
| `appointments/AppointmentsService.java` | `patchSnapWithBookingData()`: `client.mobile`/`client.phone` are normalized before being written |
| `customers/CustomersService.java` | `createCustomer()`: `data.mobilePhone` is normalized before being written (consistency — the `vaultitems` endpoint is currently not used in the active booking flow) |

**Related documents:**
- Requirement: [`requirement.md`](./requirement.md)
- Quality report: [`qualitaets.md`](./qualitaets.md)
