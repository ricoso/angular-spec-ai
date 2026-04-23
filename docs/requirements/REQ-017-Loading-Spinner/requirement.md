# REQ-017: Cleanup — Loading-Spinner + Wizard-Bugfixes

**Status:** Draft
**Priority:** High
**Type:** Functional (Cross-Cutting, Cleanup)
**Created:** 2026-04-14
**Updated:** 2026-04-17
**Author:** Claude Code
**Scope:** Alle Wizard-Seiten mit API-Calls + bekannte Bugs

---

## 1. Overview

### 1.1 Purpose
Sammel-Requirement fuer Cleanup und Bugfixes im Buchungs-Wizard:
1. **Loading-Spinner** bei ALLEN Server-Abfragen nachrüsten
2. **Stale-Snaps-Bug** — Snaps muessen bei jedem Eintritt in die Terminauswahl neu geladen werden
3. Weitere bekannte Probleme, die noch nicht sauber laufen

### 1.2 Scope
**Included:**
- (a) Loading-Spinner bei Terminauswahl (REQ-006) nachrüsten
- (b) Loading-Spinner bei Service-Auswahl (REQ-004) prüfen/nachrüsten
- (c) Loading-Spinner bei Werkstattkalender (REQ-008) prüfen/nachrüsten
- (d) Loading-Spinner bei Buchungs-Submit (REQ-010) prüfen/nachrüsten
- (e) Einheitliches Pattern: Loading → Error (mit Retry) → Content
- **(f) Stale-Snaps-Bug: Snaps immer neu laden bei Eintritt in Step 5 (Terminauswahl)**
- **(g) Mobilitaets-Optionen (Mietwagen-Kategorien): Dropdown mit Rentcategories (Kleinwagen, Mittelklasse, etc.) anzeigen + bei Buchung an SoftNet API senden**
- **(h) iframe-Embedding + Schliessen-Button: App laeuft im iframe-Overlay, X-Button leitet zurueck zur Autohaus-Seite**
- **(i) Fingerprint-Konfiguration: Env-Variable enthaelt den JSON-Inhalt direkt (statt eines Datei-Pfades)**

> **Hinweis:** Multi-Branch-Setup (27 Filialen mit je 2 Group-IDs fuer Standard/Radwechsel) + Radwechsel-Saison-Feature-Toggle sind in **REQ-021-Multi-Branch-Setup** ausgelagert.

**Mobilitaets-Optionen Detail (g):**
- Neuer API-Endpunkt: `GET /api/v1/rentcategories` → SoftNet `GET /api/planning/rentcategories`
- Response: `{ id, groupid, name, description, brand[], branches[] }`
- UI: Dropdown im Buchungs-Step (z.B. Notes/Extras) mit verfuegbaren Mietwagen-Kategorien
- Bei Buchung: Ausgewaehlte Kategorie an `POST /api/v1/appointments` mitsenden
- SoftNet-Felder im Snap/Wishlist: `isFleet: true`, `fleetType: "rentObject"`, `fleetCategoryid: "<id>"`
- Abhaengig von `replacementPreference: true` im Snap-Request

**iframe-Embedding Detail (h):**
- **Aufruf:** Autohaus-Webseite oeffnet Overlay mit iframe → `src="https://buchung.example.com/home/brand?branchId=X&groupId=Y"`
- **Return-URL:** Fix `https://www.gottfried-schultz.de/werkstatt-termin/`
- **X-Button:** Sichtbar oben rechts im App-Header, leitet per `window.top.location.href` zur Return-URL zurueck
- **Nach Buchung:** Automatischer Redirect zur Return-URL (oder Bestaetigungs-Seite mit Link)
- **Backend-Aenderungen:**
  - `SecurityConfig.java`: `X-Frame-Options: DENY` → `SAMEORIGIN` oder entfernen
  - CSP Header: `frame-ancestors 'self' https://www.gottfried-schultz.de` hinzufuegen
- **Frontend-Aenderungen:**
  - Header-Component: X-Button hinzufuegen (nur sichtbar wenn im iframe: `window.self !== window.top`)
  - X-Button Click: `window.top.location.href = 'https://www.gottfried-schultz.de/werkstatt-termin/'`
  - Booking-Success-Page: Automatischer Redirect nach X Sekunden oder Button "Zurueck zur Webseite"
- **Responsive im iframe:** Funktioniert — CSS Breakpoints basieren auf Container-Breite (em), nicht Viewport

**Fingerprint-Konfiguration Detail (i):**
- **Aktuell:** `SOFTNET_FINGERPRINT_PATH` enthaelt einen Datei-Pfad (z.B. `./config/softnet-fingerprint.json`). Das Backend liest per `Files.readString(path)` den JSON-Inhalt aus dem Dateisystem.
- **Neu:** `SOFTNET_FINGERPRINT_PATH` enthaelt direkt den eigentlichen JSON-Wert als String (den vollstaendigen Inhalt der Fingerprint-Datei).
- **Grund:** Im Deployment-Container (OpenShift/Docker) steht keine gemountete Datei zur Verfuegung — nur die Env-Variable mit dem reinen JSON-Wert.
- **Backend-Aenderungen:**
  - `SoftnetAuthService.loadFingerprint()`: Kein `Files.readString()` mehr — Property-Wert direkt an `objectMapper.readTree(value)` uebergeben.
  - `SoftnetProperties.fingerprintPath` bleibt als Feldname bestehen (enthaelt jetzt JSON-Inhalt, nicht mehr einen Pfad).
  - Logging anpassen: `"Fingerprint loaded from env variable"` statt Pfad-Ausgabe.
- **Konfig-Schluessel:** Alles bleibt einheitlich auf `fingerprint-path` / `SOFTNET_FINGERPRINT_PATH` — kein Rename im App-Code, nur die Semantik des Wertes aendert sich.
- **Lokale Entwicklung:** Entwickler exportieren den JSON-Inhalt direkt als Env-Variable (z.B. `export SOFTNET_FINGERPRINT_PATH="$(cat cognigy.net_fingerprint-*.json)"`).
- **Tests:** `SoftnetAuthServiceTest` muss angepasst werden — kein temporaeres File mehr schreiben, JSON-String direkt als Property setzen.

**Excluded:**
- Aenderungen an bestehenden Spinner-Implementierungen (REQ-002, REQ-003)
- User-Lookup-Spinner (siehe REQ-016)

### 1.3 Related Requirements
- REQ-002: Markenauswahl (hat bereits Spinner-Pattern — Referenz)
- REQ-003: Standortwahl (hat bereits Spinner-Pattern — Referenz)
- REQ-004: Serviceauswahl (Spinner nachrüsten)
- REQ-006: Terminauswahl (Spinner nachrüsten)
- REQ-008: Werkstattkalender (Spinner nachrüsten)
- REQ-010: Buchungsübersicht (Spinner bei Submit nachrüsten)

---

## 2. User Story

**Als** Benutzer des Buchungs-Wizards
**möchte ich** bei allen Ladezeiten visuelles Feedback erhalten
**damit** ich weiss, dass die Anwendung arbeitet und nicht eingefroren ist.

**Acceptance Criteria:**
- [ ] AC-1: Loading-Spinner bei Terminauswahl (REQ-006) — 1:1 identisch zum Location-Selection Pattern (Template + TS + SCSS)
- [ ] AC-2: Loading-Spinner bei Service-Auswahl (REQ-004) vorhanden (prüfen, ggf. nachrüsten)
- [ ] AC-3: Loading-Spinner bei Werkstattkalender (REQ-008) vorhanden (prüfen, ggf. nachrüsten)
- [ ] AC-4: Loading-Spinner bei Buchungs-Submit (REQ-010) vorhanden (prüfen, ggf. nachrüsten)
- [ ] AC-5: Alle Spinner nutzen dasselbe Pattern wie Location-Selection (mat-spinner + Text + Error + Retry)
- [ ] AC-6: Referenz-Implementierung: `location-selection-container.component.html:7-32` + `.scss:24-70` + `.ts:33-34,42-44`
- [ ] AC-7: Spinner zeigt `role="status"` + `aria-live="polite"` (WCAG 2.1 AA)
- [ ] AC-8: Error-State zeigt `role="alert"` + `aria-live="assertive"`
- [ ] AC-9: `SOFTNET_FINGERPRINT_JSON` Env-Variable enthaelt JSON-Inhalt direkt, nicht mehr einen Datei-Pfad
- [ ] AC-10: `SoftnetAuthService` liest Fingerprint aus Property-Wert (ohne `Files.readString()`)
- [ ] AC-11: `SoftnetAuthServiceTest` deckt die neue Direkt-JSON-Variante ab (kein temporaeres File mehr)

---

## 3. Preconditions

### 3.1 System
- BookingStore verfügbar mit `isLoading` und `error` Signals
- Bestehende Spinner-Referenz in Location-Selection und Brand-Selection

### 3.2 Data
- Alle API-Calls nutzen das bestehende `isLoading`-Signal aus dem BookingStore

---

## 4. Main Flow

**Step 1:** Benutzer navigiert zu einer Wizard-Seite mit API-Call
- **System:** Resolver löst Daten-Loading aus
- **System:** `isLoading()` wird `true` im BookingStore

**Step 2:** Loading-State
- **System:** Spinner mit `mat-spinner` + Loading-Text wird angezeigt
- **System:** Seiteninhalt ist ausgeblendet

**Step 3a:** Erfolg — Daten geladen
- **System:** `isLoading()` wird `false`, `error()` bleibt `null`
- **System:** Spinner verschwindet, Seiteninhalt erscheint

**Step 3b:** Fehler
- **System:** `isLoading()` wird `false`, `error()` enthält Fehlermeldung
- **System:** Error-State mit Icon + Titel + Message + Retry-Button erscheint

**Step 4:** Retry
- **User:** Klickt "Erneut versuchen"
- **System:** Daten werden erneut geladen, zurück zu Step 2

---

## 5. Exception Flows

### 5.1 Timeout
**Trigger:** API-Antwort dauert > 10 Sekunden
**Flow:** Request wird abgebrochen, Error-State erscheint mit Retry-Button

---

## 6. Non-Functional Requirements

### Performance
- Spinner erscheint sofort (< 100ms nach Navigation)
- Keine Layout-Shifts beim Wechsel Loading → Content

### Usability
- WCAG 2.1 AA: `role="status"` + `aria-live="polite"` auf Spinner
- WCAG 2.1 AA: `role="alert"` + `aria-live="assertive"` auf Error-State
- Kontrast: 4.5:1
- Focus-Styles: `:focus-visible` auf Retry-Button

---

## 7. Aktueller Stand

| Seite | Spinner vorhanden | Referenz |
|-------|-------------------|----------|
| Location-Selection (REQ-003) | ✅ Ja (Referenz!) | `location-selection-container.component.html:7-32` |
| Brand-Selection (REQ-002) | ✅ Ja (identisch) | `brand-selection-container.component.html:18-46` |
| Appointment-Selection (REQ-006) | ❌ **FEHLT** | `appointment-selection-container.component.html` |
| Service-Selection (REQ-004) | ❌ Prüfen | |
| Workshop-Calendar (REQ-008) | ❌ Prüfen | |
| Booking-Overview (REQ-010) | ❌ Prüfen | |

---

## 8. Spinner-Pattern — Referenz-Implementierung (1:1 von Location-Selection)

**Referenz-Datei:** `src/app/features/booking/components/location-selection/location-selection-container.component.html`

Das Spinner-Pattern aus der Location-Selection ist die **verbindliche Vorlage** für alle Wizard-Seiten.
Es besteht aus 3 States: Loading → Error (mit Retry) → Content.

### PFLICHT-Nachrüstung für Appointment-Selection:

1. **Template** (`appointment-selection-container.component.html`):
   - `@if (isLoading())` Block mit `mat-spinner` + Loading-Text VOR dem Grid einfügen
   - `@else if (error())` Block mit Error-Icon + Titel + Message + Retry-Button
   - `@else` Block um den bestehenden Content (Grid + Calendar-Link + Nav)
   - 1:1 identisch zum Location-Selection Pattern

2. **TypeScript** (`appointment-selection-container.component.ts`):
   - `MatProgressSpinnerModule` zu imports hinzufügen
   - `protected readonly isLoading = this.store.isLoading;` hinzufügen
   - `protected readonly error = this.store.error;` hinzufügen
   - `protected onRetry(): void { this.store.loadAppointments(); }` hinzufügen

3. **SCSS** (`appointment-selection-container.component.scss`):
   - `&__loading`, `&__loading-text`, `&__error`, `&__error-icon`, `&__error-title`, `&__error-message`, `&__retry-button` Styles hinzufügen
   - 1:1 identisch zu `location-selection-container.component.scss:24-70`

### HTML-Pattern (verbindlich):
```html
@if (isLoading()) {
  <div class="[component]__loading" role="status" aria-live="polite">
    <mat-spinner diameter="48" />
    <p class="[component]__loading-text">{{ booking.api.loading | translate }}</p>
  </div>
} @else if (error()) {
  <div class="[component]__error" role="alert" aria-live="assertive">
    <mat-icon class="[component]__error-icon" aria-hidden="true">error_outline</mat-icon>
    <h2 class="[component]__error-title">{{ booking.api.errorTitle | translate }}</h2>
    <p class="[component]__error-message">{{ booking.api.errorUnknown | translate }}</p>
    <button mat-flat-button color="primary" class="[component]__retry-button" (click)="onRetry()">
      <mat-icon aria-hidden="true">refresh</mat-icon>
      {{ booking.api.retryButton | translate }}
    </button>
  </div>
} @else {
  <!-- Seiteninhalt -->
}
```

### SCSS-Pattern (verbindlich, 1:1 aus Location-Selection):
```scss
&__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-2xl) 0;
}

&__loading-text {
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
  margin: 0;
}

&__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-2xl) var(--spacing-md);
}

&__error-icon {
  font-size: 3em;
  width: 3em;
  height: 3em;
  color: var(--color-error);
}

&__error-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}

&__error-message {
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
  margin: 0 0 var(--spacing-md);
  max-width: 30em;
}

&__retry-button {
  mat-icon {
    margin-right: var(--spacing-xs);
  }
}
```

---

## 9. Implementation

### Erweiterte Dateien
```
src/app/features/booking/
└── components/
    ├── service-selection/
    │   └── service-selection-container.component.html  # ERWEITERN: Spinner-Pattern
    ├── appointment-selection/
    │   ├── appointment-selection-container.component.html  # ERWEITERN: Spinner-Pattern (1:1 Location)
    │   ├── appointment-selection-container.component.ts    # ERWEITERN: isLoading, error, onRetry, MatProgressSpinnerModule
    │   └── appointment-selection-container.component.scss  # ERWEITERN: Loading/Error Styles (1:1 Location)
    ├── workshop-calendar/
    │   └── workshop-calendar-container.component.html  # ERWEITERN: Spinner-Pattern
    └── booking-overview/
        └── booking-overview-container.component.html  # ERWEITERN: Spinner bei Submit
```

### Route
Keine neuen Routes — Spinner sind Teil der bestehenden Container-Components.

### Effort
- Development: 3 Stunden
- Testing: 2 Stunden

---

## 10. Test Cases

### TC-1: Loading-Spinner bei Service-Auswahl
- **Given:** Benutzer navigiert zur Service-Auswahl
- **When:** Services werden geladen
- **Then:** Spinner mit "Daten werden geladen..." erscheint, danach Service-Liste

### TC-2: Loading-Spinner bei Terminauswahl
- **Given:** Benutzer navigiert zur Terminauswahl
- **When:** Termine werden geladen
- **Then:** Spinner erscheint, danach Termin-Karten

### TC-3: Loading-Spinner bei Werkstattkalender
- **Given:** Benutzer wechselt Datum im Kalender
- **When:** Kalender-Daten werden geladen
- **Then:** Spinner erscheint, danach Zeitslots

### TC-4: Loading-Spinner bei Booking-Submit
- **Given:** Benutzer klickt "Jetzt anfragen" auf Buchungsübersicht
- **When:** Buchung wird gesendet
- **Then:** Spinner/Loading-State erscheint während Übertragung

### TC-5: Error-State mit Retry
- **Given:** API-Call schlägt fehl
- **When:** Error-State angezeigt
- **Then:** Error-Icon + Titel + Message + Retry-Button sichtbar, Retry löst neuen Load aus

### TC-6: WCAG Accessibility
- **Given:** Spinner wird angezeigt
- **When:** Screen-Reader aktiv
- **Then:** `role="status"` wird vorgelesen, Error-State hat `role="alert"`

### TC-7: Fingerprint aus Env-Variable (JSON direkt)
- **Given:** `SOFTNET_FINGERPRINT_PATH` enthaelt den vollstaendigen JSON-Inhalt (nicht einen Pfad)
- **When:** Backend startet
- **Then:** `SoftnetAuthService` parst den JSON-Wert erfolgreich, `clientEmail`/`clientId`/`privateKeyId`/`privateKey` werden gesetzt, kein File-IO

### TC-8: Fingerprint fehlerhaft/leer
- **Given:** `SOFTNET_FINGERPRINT_PATH` ist leer oder kein gueltiges JSON
- **When:** Backend startet
- **Then:** Warnung wird geloggt, Backend startet trotzdem, SoftNet-Auth schlaegt beim ersten Request fehl

---

## 11. Dependencies

**Requires:**
- REQ-002: Markenauswahl (Spinner-Pattern als Referenz)
- REQ-003: Standortwahl (Spinner-Pattern als Referenz)

**Blocks:**
- Keine

---

## 12. Implementation Notes

**Spinner-Nachrüstung:**
```typescript
// Alle Container, die API-Calls auslösen, prüfen isLoading() aus BookingStore
// Pattern: @if (isLoading()) { spinner } @else if (error()) { error } @else { content }
// Referenz: brand-selection-container.component.html / location-selection-container.component.html
```

**Store:** Keine Store-Änderungen nötig — `isLoading` und `error` existieren bereits im BookingStore.

---

## 13. Bug: Stale Snaps bei Wizard-Ruecknavigation (2026-04-17)

### Problem

Wenn der Benutzer nach der Terminauswahl (Step 5) im Wizard zurueck navigiert und einen anderen Service (Step 3), Standort (Step 1) oder Marke (Step 2) waehlt, werden die Snaps **NICHT** neu geladen. Der Benutzer sieht veraltete Terminslots. Beim Buchen kommt `400 Bad Request` mit `"snap SB allocation already used"` (SoftNet Error Code 779).

### Ursache

Die Snap-Daten im Store werden nur einmal geladen und bei Aenderungen an vorherigen Wizard-Steps nicht invalidiert.

### Anforderung (MUSS)

Bei **jedem Eintritt in Step 5 (Terminauswahl)** muessen die Snaps **immer neu** vom Backend abgerufen werden (`POST /api/v1/snaps`). Es darf KEINE gecachten Snaps aus einem vorherigen Durchlauf verwendet werden.

### Betroffene Szenarien

| Szenario | Erwartetes Verhalten |
|----------|---------------------|
| Benutzer waehlt Termin → zurueck → anderer Service → Step 5 | Snaps neu laden |
| Benutzer waehlt Termin → zurueck → anderer Standort → Step 5 | Snaps neu laden |
| Benutzer waehlt Termin → zurueck → andere Marke → Step 5 | Snaps neu laden |
| Benutzer verweilt lange auf Step 5 (> 30 Min) → bucht | Snaps neu laden (Backend-Cache abgelaufen) |
| Benutzer navigiert zum ersten Mal zu Step 5 | Snaps laden (normal) |

### Loesung

Im Route Resolver oder beim Eintritt in Step 5 **immer** `store.loadAppointments()` aufrufen — unabhaengig davon, ob bereits Snaps im Store vorhanden sind. Kein `if (snaps.length > 0) return` Check.

### Acceptance Criteria

- [ ] AC-F1: Bei jedem Eintritt in Step 5 werden Snaps neu vom Backend geladen
- [ ] AC-F2: Nach Service-Wechsel (Step 3) + Navigation zu Step 5 zeigt aktuelle Snaps
- [ ] AC-F3: Nach Standort-Wechsel (Step 1) + Navigation zu Step 5 zeigt aktuelle Snaps
- [ ] AC-F4: Kein `"snap SB allocation already used"` Fehler bei normaler Wizard-Navigation
- [ ] AC-F5: Loading-Spinner waehrend Snap-Neuladen sichtbar (abhaengig von AC-1)

---

## 14. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |
