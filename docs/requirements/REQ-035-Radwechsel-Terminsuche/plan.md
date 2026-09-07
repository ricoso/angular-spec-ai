# REQ-035 — Implementation Plan

## Root-Cause-Trace

- Schnellauswahl OK: `booking.store.ts` (`loadAppointments`) übergibt `store.selectedVariant()` an `AppointmentApiService.getAppointments`, das `variant` in den POST-Body schreibt.
- Kalender defekt: `booking.store.ts` (`loadWorkshopCalendarDays`) → `WorkshopCalendarApiService.getWorkshopCalendarDays` sendete **kein** `variant` → Backend `SnapsService.getSnaps` → `resolveGroupId(branchId, null)` → `"default"` → Default-Group + tireChange-Operation → SoftNet 400.
- Mapper: `OperationIdMapper.FRONTEND_TO_KEYWORDS` kannte nur das Default-Group-Naming („Radwechsel ohne/mit Einlagerung").

## Änderungen

### C1 — `variant` durch den Kalender-Pfad reichen (P1)

- `src/app/features/booking/services/workshop-calendar-api.service.ts`: Parameter `variant?: 'default' | 'tireChange'`, POST-Body `variant: variant ?? 'default'`.
- `src/app/features/booking/stores/booking.store.ts` (`loadWorkshopCalendarDays`): `store.selectedVariant()` übergeben.

### C2 — Mapper-Keywords: drei getrennte Services (P2)

`server/src/main/java/com/autohaus/backend/snaps/OperationIdMapper.java`, `FRONTEND_TO_KEYWORDS` (erste Übereinstimmung gewinnt; generisches Default-Group-Keyword als Fallback):

| Key | Keywords |
|---|---|
| `bring-own-tires` | `["bringe meine räder mit", "radwechsel ohne einlagerung"]` |
| `stored-tires` | `["räder sind eingelagert", "radwechsel mit einlagerung"]` |
| `store-tires` | `["sollen bitte eingelagert", "angelieferten räder"]` |
| `tire-change` | `["radwechsel ohne einlagerung", "bringe meine räder mit"]` |

### C3 — Schnellauswahl: Samstags-Bevorzugung nur bei tireChange (P3)

`src/app/features/booking/services/appointment-api.service.ts` (`getAppointments`): Nach dem bestehenden Slot-Picking, **nur wenn** `variant === 'tireChange'`:

1. Tage mit `dayAbbreviation === 'Sa'` → die ersten 3 (chronologisch).
2. Auffüllen mit den frühesten Nicht-Samstags-Tagen bis 4 Einträge.
3. Keine Samstage → bisherige Logik (früheste 4 Tage).

`variant === 'default'`/undefined: unveränderter Pfad. 28-Tage-Suchfenster bleibt.

### C4 — Backend-Guard gegen leere Operations (AC-5)

`server/src/main/java/com/autohaus/backend/snaps/SnapsService.java`: Wenn `mappedOps.isEmpty()` und `jobTypeIds` nicht leer → `ResponseStatusException(400, "No SoftNet operations resolved …")` statt kaputtem SoftNet-Call.

## Risiken / Hinweise

- Keyword-Matching = case-insensitives Substring-Matching auf deutsche Namen — Umlaute exakt („räder").
- `dayAbbreviation` kommt lokalisiert vom Backend („Sa").
- `store-tires` hat bewusst keinen „Radwechsel mit Einlagerung"-Fallback mehr (sonst identisch zu `stored-tires` in Default-Groups).

## Verifikation (End-to-End)

1. `npm run test:local:serve` → Radwechsel-Flow VW Zentrum Essen: Schnellauswahl zeigt Samstage zuerst; Kalender-Datumswahl liefert Slots.
2. Prod-Smoke nach Deploy (read-only, curl): Snaps mit `variant:'tireChange'` über beide Pfade; 10.10.2026 zeigt 5 Sa-Slots im Radwechsel-Flow, keine im Default-Flow.
3. Log-Check (Log Analytics): keine `SOFTNET_ERROR … missing operations` mehr für tireChange-Sessions.
