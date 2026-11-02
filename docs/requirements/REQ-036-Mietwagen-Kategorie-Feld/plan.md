# REQ-036 — Implementation Plan

## Ausgangslage (Ist-Code)

`server/src/main/java/com/autohaus/backend/appointments/AppointmentsService.java:135-139`:

```java
if (dto.rentCategoryId() != null && !dto.rentCategoryId().isBlank()) {
    wishlist.put("isFleet", true);
    wishlist.put("fleetType", "rentObject");
    wishlist.put("fleetCategoryid", dto.rentCategoryId());   // <-- falsches Feld
}
```

Die ID stammt aus `GET /api/planning/rentcategories` (`SoftnetApiClient.getRentCategories:86-87`),
`fleetCategoryid` erwartet laut Spec aber eine ID aus der `fleetcategory`-Collection.

Der Erfolgs-Log (`AppointmentsService:68-71`) schreibt nur `wishlist.client` und `wishlist.car` —
die Mobilitäts-Felder sind darin nicht enthalten.

## Änderungen

### C1 — Zielfeld korrigieren + 24-Zeichen-Guard (AC-1 bis AC-5)

In `patchSnapWithBookingData` (Z. 135-139):

- `fleetCategoryid` entfällt, stattdessen `rentCategoryid`.
- `isFleet` / `fleetType` bleiben unverändert (`"rentObject"` ist im Spec-Enum enthalten).
- Vorgeschaltete Längenprüfung: nur eine ID mit **exakt 24 Zeichen** wird gesendet (Spec:
  `minLength`/`maxLength` = 24). Andernfalls **keines** der drei Felder setzen und `log.warn` mit
  Trace-ID (kommt über MDC automatisch ins Log-Präfix) — so verhält sich der Request wie „keine
  Mobilitäts-Option" statt SoftNet einen ungültigen Wert zu schicken.
- Kommentar mit REQ-Verweis und dem Grund (Collection-Unterschied), damit der nächste Leser nicht
  wieder auf `fleetCategoryid` zurückwechselt. Bestehende Kommentar-Dichte im Umfeld beachten.

Konstante für die ID-Länge (`RENT_CATEGORY_ID_LENGTH = 24`) statt Magic Number.

### C2 — Erfolgs-Log um Mobilitäts-Felder erweitern (AC-6, AC-7)

`BOOKING_CREATED` (Z. 68-71) um einen Abschnitt `| mobility={...}` ergänzen, gespeist aus dem
gepatchten `snap.wishlist`: `isFleet`, `fleetType`, `rentCategoryid`. Diese Werte sind IDs und
Booleans — **keine PII**, daher ohne `LogSanitizer`-Redaction, konsistent zur bereits im Klartext
geloggten `car.brand` / `currentMileage`. Ist keine Mobilität gewählt, bleibt der Abschnitt leer
(`mobility={}`), damit die Zeile ein stabiles, greppbares Format behält.

## Risiken / Hinweise

- **Restrisiko (in spec.md dokumentiert):** Falls SoftNet `fleetCategoryid` hart erzwingt, schlägt die
  Buchung mit Validierungsfehler fehl. Wird in Step 6.5 sichtbar; Rückfallebene ist Variante 2
  (Mapping gegen `/api/planning/fleetcategory`).
- `wishlist` ist ein `ObjectNode` auf einer **Kopie** des Snaps (`rawSnap.deepCopy()`, Z. 51) — der
  pristine Snap für das Fehler-Logging bleibt unberührt. Keine Änderung an dieser Mechanik.
- `SnapRequestDto` trägt keine Mobilitäts-Felder; die Snap-Suche bleibt unangetastet (Mobilität
  beeinflusst die Slot-Suche nicht). Kein Eingriff in `SnapsService`.
- Frontend bleibt unverändert: `rentCategoryId` wird schon korrekt bis zum Backend durchgereicht
  (`booking.store.ts:686`). Der Fehler sitzt ausschließlich im Backend-Mapping.

## Verifikation (End-to-End)

1. **Unit (Gradle):** `cd server && ./gradlew test` — neue/angepasste Tests aus tasks.md.
2. **Lokal (Step 6.5):** `npm run test:local:smoke`, dann `npm run test:local` (3 Viewports).
3. **Manuell lokal:** `npm run test:local:serve` → Buchung mit Mietwagen „Kleinwagen" durchspielen,
   im Backend-Log prüfen: `BOOKING_CREATED … | mobility={"isFleet":true,"fleetType":"rentObject","rentCategoryid":"<24-char-id>"}`.
4. **Nach Deploy (Prod-Smoke, read-only):** Buchung mit Mobilität auslösen, dann in Log Analytics
   die `BOOKING_CREATED`-Zeile ziehen und die `rentCategoryid` gegen
   `GET /api/v1/branches/{branchId}/rentcategories` abgleichen. Zusätzlich prüfen, dass keine
   `SOFTNET_ERROR`-Zeile mit `ValidationError` zum Thema `fleetCategoryid` auftritt.
5. **Fachliche Endkontrolle:** Bestätigung durch den Betreiber, dass die Kategorie im nachfolgenden
   System jetzt ankommt — das ist das eigentliche Abnahmekriterium und nur dort prüfbar.
