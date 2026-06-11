# Feature Documentation: Notification Channel Toggle

**Created:** 2026-06-01
**Requirement:** REQ-027-Notification-Channel-Toggle
**Language:** EN
**Status:** Implemented

---

## Overview

On the notes page (wizard step 4 — `/home/notes`), the customer selects under
"Notification" whether they want to receive booking confirmations and appointment
reminders via **Email** or **SMS**. The choice is forwarded to the SoftNet API
when submitting the booking (`notifications: { SMS, SMTP }`).

Default selection is **Email**.

---

## User Flow

### Step 1: Open Notes Page

After selecting services and clicking "Continue", the customer lands on the
notes page. Three existing switch cards are visible (Rental Car, Appointment
Preference, Callback) plus the new **"Notification"** card.

**Description:** The pill group offers two options:
- **Email** (pre-selected) — icon `bi-envelope-fill`
- **SMS** — icon `bi-chat-dots-fill`

### Step 2: Switch Channel

The customer taps/clicks one of the pills.

**Description:** The active pill is highlighted in primary color. The selection
is immediately emitted via the `extrasChanged` output and stored in `BookingStore`
under `notesExtras.notificationChannel`.

### Step 3: Submit Booking

When the customer continues to the next step / submits the booking, the chosen
channel is mapped into the SoftNet request body:

| Selection | Backend Request |
|-----------|-----------------|
| Email     | `notifications: { SMS: false, SMTP: true }` |
| SMS       | `notifications: { SMS: true,  SMTP: false }` |

The Spring Boot backend forwards the field unchanged to the SoftNet endpoints
`api.planning.snaps.query` and `api.planning.appointments.add`.

---

## Responsive Views

### Desktop (1280×720)
Pill group is laid out horizontally below the card header.

### Tablet (≥ 48em)
Same as desktop, full card width.

### Mobile (< 48em)
Pill group remains horizontal; individual pills wrap if needed.

Mockup screenshot: `./mockup.png`

---

## Accessibility

- **Keyboard navigation:** arrow keys cycle between radios; space toggles selection.
- **Screen readers:** card uses `role="region"` + `aria-label`; pill group uses
  `role="radiogroup"` + `aria-labelledby="notification-title"`. Icons are
  `aria-hidden="true"`, the pill label is the visible text.
- **Color contrast:** active pill uses `--color-primary` with text `--color-text-on-primary` (WCAG 2.1 AA).
- **Focus styles:** `:focus-visible` outlines with `--color-focus-ring`.
- **Touch targets:** at least `2.75em` (`--touch-target-min`).

---

## Technical Details

| Property | Value |
|----------|-------|
| Route | `/#/home/notes` |
| Container Component | `NotesContainerComponent` |
| Presentational Component | `NotesExtrasFormComponent` |
| Store | `BookingStore` (computed `selectedNotificationChannel`) |
| API Service | `AppointmentApiService` (`notifications?: { SMS, SMTP }`) |
| Backend DTO | `SnapRequestDto.NotificationsDto`, `CreateAppointmentRequest.NotificationsDto` |
| Default | `email` (in `DEFAULT_NOTES_EXTRAS`) |
| Mapping | `mapNotificationChannelToSoftnet()` in `notes-extras.model.ts` |
| i18n Keys | `booking.notes.notification.{title, label, email, sms}` |
| Languages | DE, EN, UK, FR, AR |
