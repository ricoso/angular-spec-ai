# REQ-017: Cleanup — Loading-Spinner + Wizard-Bugfixes

**Version:** 1.0
**Status:** Implementiert
**Datum:** 2026-04-23

---

## Überblick

Dieses Release adressiert sechs parallele Cleanup-Themen im Buchungs-Wizard:

1. **Loading-Spinner** bei allen Server-Abfragen nachgerüstet (einheitliches Pattern aus Location-Selection)
2. **Stale-Snaps-Bug behoben** — Termine werden bei jedem Eintritt in Step 5 (Terminauswahl) neu vom Backend geholt
3. **Mietwagen-Kategorien** — neuer API-Endpunkt `/api/v1/rentcategories` + dynamisches Dropdown auf der Notes-Seite
4. **iframe-Embedding** — der Wizard kann nun als iframe auf der Autohaus-Webseite eingebettet werden; ein X-Button im Header führt zurück
5. **Fingerprint-Konfiguration** — der SoftNet-Auth-Fingerprint wird nun direkt aus einer Env-Variable gelesen (kein File-IO mehr)
6. **Buchungs-Submit** — Retry-Mechanismus bei fehlgeschlagener Buchung

---

## 1. Loading-Spinner Pattern

### Referenz-Pattern (aus Location-Selection übernommen)

Jede Wizard-Seite mit API-Call verwendet drei States:

```
Loading (mat-spinner + Text) → Error (Icon + Retry-Button) → Content
```

**Betroffene Komponenten:**
- `appointment-selection-container` (Terminauswahl, Step 5)
- `service-selection-container` (Service-Auswahl, Step 3)
- `workshop-calendar-container` (Werkstattkalender)
- `booking-overview-container` (Buchungs-Übersicht — Submit-Spinner)

### Accessibility (WCAG 2.1 AA)

- Loading-Block: `role="status"` + `aria-live="polite"`
- Error-Block: `role="alert"` + `aria-live="assertive"`
- Retry-Button: `mat-flat-button` mit Focus-Styles

### i18n-Keys (DE/EN/UK/FR/AR)

| Key | DE |
|-----|----|
| `booking.api.loading` | Daten werden geladen… |
| `booking.api.submitting` | Ihre Anfrage wird gesendet… |
| `booking.api.errorTitle` | Fehler beim Laden |
| `booking.api.errorUnknown` | Ein unbekannter Fehler ist aufgetreten. Bitte versuchen Sie es erneut. |
| `booking.api.errorSubmit` | Die Buchung konnte nicht gesendet werden. Bitte versuchen Sie es erneut. |
| `booking.api.retryButton` | Erneut versuchen |

---

## 2. Stale-Snaps-Bug

**Problem:** Bei Rück-Navigation im Wizard (z.B. anderer Service gewählt) wurden die vorher gesehenen Termine nicht invalidiert. Beim Buchen führte das zu `400 Bad Request — snap SB allocation already used`.

**Lösung:** `BookingStore.loadAppointments` löscht bei jedem Aufruf zuerst `appointments` und `selectedAppointment` im State, bevor neue Daten geholt werden. Der Resolver ruft die Methode bei jedem Routen-Eintritt auf — es gibt keine Cache-Optimierung mehr, jede Step-5-Navigation triggert einen frischen Backend-Call.

**Betroffene Szenarien (alle gelöst):**
- Termin wählen → zurück → anderer Service → Step 5
- Termin wählen → zurück → anderer Standort → Step 5
- Termin wählen → zurück → andere Marke → Step 5
- Benutzer verweilt lange auf Step 5 → bucht
- Erste Navigation zu Step 5

---

## 3. Mietwagen-Kategorien (Rentcategories)

### Backend

Neues Modul `server/src/main/java/com/autohaus/backend/rentcategories/`:

| Komponente | Datei |
|------------|-------|
| Controller | `RentCategoriesController.java` → `GET /api/v1/rentcategories` |
| Service | `RentCategoriesService.java` → mappt SoftNet `/api/planning/rentcategories` |
| DTO | `RentCategoryResponseDto.java` (`id`, `groupId`, `name`, `description`, `brands[]`, `branches[]`) |

### Frontend

| Komponente | Datei |
|------------|-------|
| Model | `rent-category.model.ts` |
| API-Service | `rent-category-api.service.ts` |
| Resolver | `rent-categories.resolver.ts` (für `/notes`) |
| Store | `BookingStore.loadRentCategories` + `setSelectedRentCategoryId` |
| UI | Dropdown in `notes-extras-form.component` (nur sichtbar, wenn Kategorien vorhanden) |

### Submit-Integration

Bei der Buchung wird `rentCategoryId` an `POST /api/v1/appointments` mitgesendet. Das Backend setzt im SoftNet-Snap/Wishlist:
```json
{
  "isFleet": true,
  "fleetType": "rentObject",
  "fleetCategoryid": "<id>"
}
```

---

## 4. iframe-Embedding

### Backend (SecurityConfig)

- `X-Frame-Options` von `DENY` auf `SAMEORIGIN` geändert
- Neuer CSP-Header: `frame-ancestors 'self' https://www.gottfried-schultz.de`

### Frontend (Header)

- Neuer X-Button im Header, sichtbar nur wenn `window.self !== window.top` (= App läuft im iframe)
- Klick leitet per `window.top.location.href` zu fixer Return-URL:
  `https://www.gottfried-schultz.de/werkstatt-termin/`
- A11y: `aria-label` mit i18n-Key `header.close.buttonLabel`

---

## 5. Fingerprint-Konfiguration

### Vorher

`SOFTNET_FINGERPRINT_PATH` enthielt einen Datei-Pfad → Backend las per `Files.readString()` das JSON.

### Nachher

`SOFTNET_FINGERPRINT_PATH` enthält das JSON direkt als String → `ObjectMapper.readTree()` parst direkt ohne File-IO.

### Lokale Entwicklung

```bash
export SOFTNET_FINGERPRINT_PATH="$(cat cognigy.net_fingerprint-*.json)"
```

### Container-Deployment (OpenShift/Docker)

Die Env-Variable wird vom Deployment-System gesetzt (GitLab Secrets Store → K8s Env Vars). Keine gemountete Datei mehr nötig.

---

## 6. Buchungs-Submit mit Retry

**Problem (vorher):** Bei fehlgeschlagener Buchung blieb der Button disabled (`bookingSubmitted: true` wurde auch im Error-Case gesetzt).

**Lösung:**
- `bookingSubmitted` wird nur bei Erfolg auf `true` gesetzt
- Neue Store-Methode `resetBookingSubmission()` setzt `bookingError: null` + `bookingSubmitted: false`
- Container nutzt `submitError` computed Signal + `onRetry()` das Submit wiederholt

---

## Test-Abdeckung

- **Frontend:** 528/528 Tests PASS
- **Backend:** 201 Tests PASS
- **Neue Test-Dateien:** 3 (2 Frontend, 1 Backend)
- **Erweiterte Tests:** 11 Dateien

---

## Migration / Breaking Changes

### Backend
- `CreateAppointmentRequest`: neues Feld `rentCategoryId` (nullable, rückwärtskompatibel)
- `SOFTNET_FINGERPRINT_PATH` Env-Variable muss nun JSON-Inhalt enthalten (vorher Datei-Pfad)

### Frontend
- `BookingStore`: neuer State `rentCategories`, `selectedRentCategoryId`
- Neue Route-Resolver-Regel: `/notes` benötigt `rentCategoriesResolver`

---

## Deployment-Hinweise

1. Vor dem Deployment: `SOFTNET_FINGERPRINT_PATH` Env-Variable auf JSON-Inhalt umstellen (statt Pfad)
2. Autohaus-Webseite: iframe-Einbettung auf `https://buchung.example.com/home/brand?branchId=X&groupId=Y` einrichten
3. CSP der Autohaus-Webseite prüfen (`child-src` muss den iframe-Host erlauben)
