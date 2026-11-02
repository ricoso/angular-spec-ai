# REQ-036 — Tasks

## Phase A — Zielfeld korrigieren (AC-1 bis AC-5)

- [x] **T1:** `AppointmentsService.patchSnapWithBookingData`: `fleetCategoryid` → `rentCategoryid`, `isFleet`/`fleetType` unverändert; Kommentar mit REQ-Verweis und Begründung (rentCategory- vs. fleetCategory-Collection + Scope).
- [x] **T2:** 24-Zeichen-Guard mit Konstante `RENT_CATEGORY_ID_LENGTH = 24`; bei abweichender Länge keines der drei Felder setzen + `log.warn` (Trace-ID kommt über MDC).

## Phase B — Erfolgs-Log erweitern (AC-6, AC-7)

- [x] **T3:** `BOOKING_CREATED` um `| mobility={...}` erweitert (`isFleet`, `fleetType`, `rentCategoryid` — ohne Redaction, da keine PII), Helper `mobilityLogFields`; leeres `mobility={}` ohne Mobilität.

## Phase C — Tests

- [x] **T4:** Bestehender Test umbenannt zu `createAppointment_withRentCategory_sendsRentCategoryidNotFleetCategoryid` → prüft `rentCategoryid` **und** das Fehlen von `fleetCategoryid`; nutzt die echte 24-stellige ID `682ad3dc6969db66dd27f6c3` („Kleinwagen", Velbert).
- [x] **T5:** `createAppointment_rentCategoryWithWrongLength_isNotSentAtAll` — `"cat-abc"` → keines der vier Felder im Snap.
- [x] **T6:** `createAppointment_withoutRentCategory_setsNoMobilityFields` (AC-4).
- [x] **T7:** `createAppointment_logsMobilityFields_soASuccessfulBookingIsTraceable` + `createAppointment_withoutMobility_logsEmptyMobilityObject` (nutzt den bestehenden `ListAppender`-Aufbau, Helper `bookingLogLine` extrahiert).

## Phase D — Qualität & Abschluss (Workflow CLAUDE.md)

- [x] **T8:** Tech-Check grün: `lint:fix` (0 Errors, 255 vorbestehende Warnings), `type-check` sauber, `test:coverage` 736/736, `./gradlew test` BUILD SUCCESSFUL.
- [x] **T9:** Step 6.5 grün: `test:local:smoke` PASS, `test:local` 113 passed / 0 failed (3 Viewports, exit 0).
- [x] **T10 (teilweise):** Im E2E-Backend-Log erscheint der neue Abschnitt korrekt als `mobility={}` — die eine E2E-Buchung lief ohne Mietwagen, der gefüllte Fall ist daher nur durch T7 (Unit) abgedeckt. **Offen für den User:** eine echte Buchung mit „Kleinwagen" durchspielen und die `rentCategoryid` im Log gegenprüfen (bewusst nicht automatisiert ausgeführt — das legt einen echten Termin in einer Filiale an).
- [x] **T11:** Fertigmeldung — Änderungen bleiben lokal, kein Commit/Push.

## Phase E — Extern (blockiert die Umsetzung nicht)

- [ ] **T12:** Rückfragen 1–3 aus spec.md an den Dienstleister stellen (inkl. Debug-Paket `debug-mobilitaet-6a9aca66.md`).
- [ ] **T13:** Nach Deploy: Prod-Smoke gemäß plan.md Punkt 4 + fachliche Bestätigung durch den Betreiber einholen, dass die Kategorie im Zielsystem ankommt.
