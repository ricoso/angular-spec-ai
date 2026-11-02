# Feature-Dokumentation: Benachrichtigungs-Kanal-Toggle

**Erstellt:** 2026-06-01
**Requirement:** REQ-027-Notification-Channel-Toggle
**Sprache:** DE
**Status:** Implementiert

---

## Übersicht

Auf der Hinweisseite (Wizard-Schritt 4 — `/home/notes`) wählt der Kunde
unter "Benachrichtigung", ob er Buchungsbestätigungen und Terminerinnerungen
per **E-Mail** oder per **SMS** erhalten möchte. Die Auswahl wird beim
Absenden der Buchung an die SoftNet-API durchgereicht (`notifications: { SMS, SMTP }`).

Default-Auswahl ist **E-Mail**.

---

## Benutzerführung

### Schritt 1: Hinweisseite öffnen

Nach Auswahl von Services klickt der Kunde auf "Weiter" und landet auf der Hinweisseite.
Dort sind drei bestehende Switch-Cards sichtbar (Mietwagen, Terminpräferenz, Rückruf)
sowie die neue Card **"Benachrichtigung"**.

**Beschreibung:** Die Pill-Group zeigt zwei Optionen:
- **E-Mail** (vorausgewählt) — Icon `bi-envelope-fill`
- **SMS** — Icon `bi-chat-dots-fill`

### Schritt 2: Kanal wechseln

Der Kunde tippt/klickt auf eine der beiden Pills.

**Beschreibung:** Die aktive Pill wird primärfarben hervorgehoben.
Die Auswahl wird sofort als Output `extrasChanged` an den Container emittiert
und im `BookingStore` als Teil von `notesExtras.notificationChannel` gespeichert.

### Schritt 3: Buchung absenden

Beim Klick auf "Weiter" / "Termin buchen" wird der gewählte Kanal in den
SoftNet-Request gemappt:

| Auswahl | Backend-Request |
|---------|-----------------|
| E-Mail  | `notifications: { SMS: false, SMTP: true }` |
| SMS     | `notifications: { SMS: true,  SMTP: false }` |

Das Spring-Boot-Backend reicht das Feld unverändert an die SoftNet-Endpoints
`api.planning.snaps.query` und `api.planning.appointments.add` weiter.

---

## Responsive Ansichten

### Desktop (1280×720)
Pill-Group horizontal nebeneinander unterhalb des Card-Headers.

### Tablet (≥ 48em)
Layout wie Desktop, volle Card-Breite.

### Mobile (< 48em)
Pill-Group bleibt horizontal, einzelne Pills bei Bedarf umbrechen.

Mockup-Screenshot: `./mockup.png`

---

## Barrierefreiheit

- **Tastaturnavigation:** Pfeil-Tasten zwischen Radios; Leertaste zur Auswahl.
- **Screen Reader:** Card als `role="region"` + `aria-label`; Pill-Group als
  `role="radiogroup"` + `aria-labelledby="notification-title"`. Icons
  sind `aria-hidden="true"`, Pill-Label ist sichtbarer Text.
- **Farbkontrast:** Aktive Pill nutzt `--color-primary` mit Text `--color-text-on-primary` (WCAG 2.1 AA).
- **Focus-Styles:** `:focus-visible` mit `--color-focus-ring` als Outline.
- **Touch-Targets:** mindestens `2.75em` Höhe (`--touch-target-min`).

---

## Technische Details

| Eigenschaft | Wert |
|-------------|------|
| Route | `/#/home/notes` |
| Container Component | `NotesContainerComponent` |
| Presentational Component | `NotesExtrasFormComponent` |
| Store | `BookingStore` (Computed `selectedNotificationChannel`) |
| API Service | `AppointmentApiService` (`notifications?: { SMS, SMTP }`) |
| Backend DTO | `SnapRequestDto.NotificationsDto`, `CreateAppointmentRequest.NotificationsDto` |
| Default | `email` (in `DEFAULT_NOTES_EXTRAS`) |
| Mapping | `mapNotificationChannelToSoftnet()` in `notes-extras.model.ts` |
| i18n Keys | `booking.notes.notification.{title, label, email, sms}` |
| Sprachen | DE, EN, UK, FR, AR |
