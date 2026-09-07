# REQ-035 — Tasks

## Phase A — Frontend variant-Fix (P1)

- [x] **T1:** `workshop-calendar-api.service.ts`: `variant`-Parameter + Body-Feld ergänzen.
- [x] **T2:** `booking.store.ts` `loadWorkshopCalendarDays`: `store.selectedVariant()` durchreichen.
- [x] **T3:** Jest: `workshop-calendar-api.service.spec.ts` — Body enthält `variant` (tireChange + Default-Fallback).

## Phase B — Mapper (P2)

- [x] **T4:** `OperationIdMapper.java`: Keyword-Tabelle gemäß plan.md C2 (drei getrennte Services + Fallbacks).
- [x] **T5:** JUnit `OperationIdMapperTest`: reale tireChange-Namen (VW Essen) UND Default-Naming; drei Keys → drei **verschiedene** IDs.

## Phase C — Schnellauswahl Samstags-Bevorzugung (P3)

- [x] **T6:** `appointment-api.service.ts` `getAppointments`: Auswahl-Logik gemäß C3 (nur tireChange; default unverändert).
- [x] **T7:** Jest: ≥3 Samstage (3 Sa + 1 Werktag), 1 Samstag (1 Sa + 3 Werktage), 0 Samstage (wie bisher), `variant='default'` (unverändert).

## Phase D — Backend-Guard (AC-5)

- [x] **T8:** `SnapsService.java`: Empty-Operations-Guard → 400 mit Message.
- [x] **T9:** JUnit: Guard-Test (Frontend-Key ohne Match → 400 lokal, kein SoftNet-Call).

## Phase E — Qualität & Abschluss (Workflow CLAUDE.md)

- [x] **T10:** Spec-Kit-Dateien nach `docs/requirements/REQ-035-Radwechsel-Terminsuche/` (spec.md, plan.md, tasks.md).
- [x] **T11:** Tech-Check: `npm run lint:fix`, `npm run type-check`, `npm run test:coverage`, `cd server && ./gradlew test`.
- [x] **T12:** `npm run test:local:smoke` + E2E (`npm run test:local`), 3 Viewports.
- [x] **T13:** Fertigmeldung — Änderungen bleiben lokal, kein Commit/Push (User prüft selbst).
