# REQ-024: Error Fallback Screen (App-Wide Top-Level)

**Status:** Draft
**Priority:** High
**Type:** Functional (Cross-Cutting, Resilience)
**Created:** 2026-04-27
**Author:** Claude Code
**Scope:** App-Shell, HTTP-Layer, neuer Error-Outlet ausserhalb von `/home`

---

## 1. Overview

### 1.1 Purpose
App-weiter Top-Level Error-Fallback-Screen, der gerendert wird, wenn die Anwendung keine Daten anzeigen kann (kein verwertbarer Wizard-State erreichbar). Statt eines weissen Bildschirms, einer halb-gerenderten Wizard-Seite oder einer Endlosschleife im Token-Refresh sieht der Benutzer einen klaren, zugaenglichen Error-Screen mit Retry und Telefon-Notausstieg.

### 1.2 Scope

**Included:**
- Globaler `AppErrorStore` (Signal Store, `providedIn: 'root'`) mit `criticalError` Signal
- HTTP-Interceptor `error-fallback.interceptor.ts` der kritische Fehler erkennt und in den Store schreibt
- Erkennung von:
  - 5xx ohne Recovery (nach Circuit-Breaker-Probing)
  - Auth-Loop (3+ aufeinanderfolgende 401 inkl. Token-Refresh-Versuche)
  - Network-/DNS-/Timeout-Fehler (`HttpErrorResponse.status === 0`)
  - Initial-Load-Failures der App-Bootstrapping-Endpunkte (z.B. `GET /api/v1/branches`)
- Neue Lazy-Loaded Route `/error` mit `ErrorFallbackContainerComponent`
- Auto-Navigation per `effect()` im App-Shell, wenn `criticalError` gesetzt wird
- UI-Aktionen: Retry, Telefon-Link
- Kollabierbare Detail-Box mit `code`, `requestId`, `timestamp` und ggf. `httpStatus`
- i18n in 5 Sprachen (DE, EN, UK, FR, AR) unter `app.error.*`
- WCAG 2.1 AA: `role="alert"`, `aria-live="assertive"`, Focus-Management auf den Retry-Button

**Excluded:**
- Component-interne Error-States innerhalb des Wizards (das ist REQ-017 — bleibt bestehen)
- Backend Telemetry-Endpunkt `POST /api/v1/error-report` (zukuenftige Erweiterung, hier nur als Hook erwaehnt)
- User-Lookup-Fehler (REQ-016, hat eigene Inline-Behandlung)
- Validation-Fehler in Formularen (Reactive Forms zeigen diese inline)
- Zentrale Sentry-/APM-Anbindung (separate Iteration)

### 1.3 Related Requirements

- **REQ-017:** Loading-Spinner + Component-spezifische Error-States innerhalb der Wizard-Steps. REQ-024 ist die Eskalation, wenn der component-spezifische Retry nicht greift oder der Error den ganzen App-State unbenutzbar macht.
- **REQ-001:** Header — der Error-Screen rendert ohne Standard-Header (Ausnahme: Logo bleibt sichtbar fuer Wiedererkennung).
- **REQ-002 / REQ-003:** Brand-/Location-Selection — typische Initial-Load-Pfade, deren Fehlschlag REQ-024 ausloest, wenn der Resolver komplett scheitert.
- **REQ-021:** Multi-Branch-Setup — Branch-Konfiguration ist Teil des kritischen Initial-Loads.

---

## 2. User Story

**As a** Benutzer des Buchungs-Wizards
**I want** einen klaren Hinweis und sinnvolle Auswege, wenn die Anwendung keine Daten laden kann
**So that** ich nicht vor einem weissen Bildschirm sitze, sondern direkt anrufen oder einen Retry ausloesen kann.

**Acceptance Criteria:**
- [ ] AC-1: Globaler `AppErrorStore` (Signal Store, `providedIn: 'root'`) existiert mit Signal `criticalError: Signal<CriticalError | null>`, Methoden `setCriticalError(err)` und `clearCriticalError()`
- [ ] AC-2: HTTP-Interceptor `error-fallback.interceptor.ts` faengt 5xx-Antworten ab und setzt `criticalError` mit `code: 'SERVER_5XX'`, sobald der Circuit-Breaker keine Recovery mehr erlaubt
- [ ] AC-3: 3+ aufeinanderfolgende 401-Antworten (inkl. Session-Token-Refresh) setzen `criticalError` mit `code: 'AUTH_LOOP'` (Schutz gegen Endlosschleife im `session-token.interceptor`)
- [ ] AC-4: Network-Error / DNS-Fail / Timeout (`status === 0`) setzt `criticalError` mit `code: 'NETWORK'`
- [ ] AC-5: Bei `criticalError != null` navigiert die App automatisch zu `/error` (per `effect()` im App-Shell, ausserhalb von `/home`); der laufende Wizard-State wird nicht gerendert
- [ ] AC-6: Error-Screen zeigt: Icon (`error_outline`) + Titel + Subtext + Retry-Button (primary, filled) + Telefon-Link
- [ ] AC-7: Kollabierbare Detail-Box (`<details>` oder Mat-Expansion) mit `code`, `requestId`, `timestamp`, optional `httpStatus`
- [ ] AC-8: WCAG 2.1 AA: Container hat `role="alert"` + `aria-live="assertive"`, Retry-Button erhaelt initialen Focus, Kontrast >= 4.5:1, `:focus-visible` Styles, Touch-Targets >= 2.75em
- [ ] AC-9: Retry-Button: setzt `criticalError = null`, navigiert zu `/home/location`, Resolver triggert Reload der kritischen Daten
- [ ] AC-10: Telefon-Link: `<a href="tel:+49...">` mit Nummer aus Branch-Config (`selectedBranch.phone`) — Fallback auf zentralen i18n-Default, wenn keine Branch-Auswahl existiert
- [ ] AC-11: i18n in 5 Sprachen (DE, EN, UK, FR, AR) unter `app.error.*` Key-Block — neue Keys werden in `translations.ts` ergaenzt, nicht in `booking.api.*` dupliziert
- [ ] AC-12: Component-interne Errors aus REQ-017 bleiben funktional — keine Regression. REQ-017-Errors haben Vorrang innerhalb eines Steps; REQ-024 greift erst, wenn der Step nicht erreicht wird oder der Retry erneut scheitert
- [ ] AC-13: Bei Retry-Fehlschlag (zweiter Fehler innerhalb 10 s) bleibt `criticalError` gesetzt, der Screen aktualisiert nur `requestId` und `timestamp` (keine zweite Navigation)
- [ ] AC-14: Wenn Browser-`navigator.onLine === false` ist, wird der Subtext um den Hinweis "Pruefen Sie Ihre Internetverbindung" ergaenzt (Code bleibt `NETWORK`)

---

## 3. Preconditions

### 3.1 System
- Angular App laeuft, `provideHttpClient(withInterceptors([...]))` ist konfiguriert
- `CircuitBreakerService` und `SessionTokenInterceptor` sind aktiv (REQ-017 Vorarbeit)
- `BookingStore` hat bereits `error: Signal<string | null>` (Component-Level, bleibt bestehen)

### 3.2 User
- Keine Authentifizierung erforderlich — Error-Screen ist oeffentlich erreichbar

### 3.3 Data
- Keine Daten-Voraussetzungen — der Screen rendert auch ohne `branches`, `brands`, `selectedBranch`

---

## 4. Main Flow

**Step 1:** Benutzer oeffnet `/home/location`
- **System:** Resolver triggert `GET /api/v1/branches`
- **System:** Backend antwortet mit `503` (z.B. SoftNet down)

**Step 2:** Circuit-Breaker oeffnet nach 5 fehlgeschlagenen Requests
- **System:** `error-fallback.interceptor` sieht weiteren `503` (geblockt vom Breaker), setzt `criticalError = { code: 'SERVER_5XX', httpStatus: 503, requestId, timestamp }`

**Step 3:** App-Shell `effect()` reagiert auf `criticalError`
- **System:** Router navigiert zu `/error`
- **System:** `ErrorFallbackContainerComponent` rendert mit `role="alert"`, Focus geht auf Retry-Button

**Step 4:** Benutzer klickt "Erneut versuchen"
- **User:** Klickt Retry
- **System:** `clearCriticalError()`, `router.navigateByUrl('/home/location')`, Resolver laedt erneut
- **Expected:** Bei Erfolg wird der Wizard normal angezeigt; bei erneutem Fehler bleibt der Error-Screen sichtbar, Detail-Box aktualisiert sich

**Step 5 (Alternative):** Benutzer klickt Telefon-Link
- **User:** Klickt `<a href="tel:+49...">`
- **System:** Browser/OS oeffnet Telefon-App
- **Expected:** Benutzer kann das Autohaus direkt anrufen

---

## 5. Alternative Flows

### 5.1 Alt Flow A: Auth-Loop

**Trigger:** Session-Token-Service liefert dauerhaft `401`, Token-Refresh-Versuche fuehren erneut zu `401`.

**Flow:**
1. `session-token.interceptor` zaehlt aufeinanderfolgende 401-Antworten in einem `signal<number>` (Counter im `SessionTokenService`)
2. Bei Counter >= 3 wird der Refresh-Versuch abgebrochen, `error-fallback.interceptor` (oder direkt der Service) setzt `criticalError = { code: 'AUTH_LOOP', httpStatus: 401, ... }`
3. App navigiert zu `/error`
4. Retry setzt Counter zurueck und ruft `/home/location` erneut auf

### 5.2 Alt Flow B: Network/DNS-Fehler

**Trigger:** Browser kann Backend nicht erreichen (`HttpErrorResponse.status === 0`).

**Flow:**
1. `error-fallback.interceptor` faengt `status === 0` ab
2. Setzt `criticalError = { code: 'NETWORK', requestId, timestamp }`
3. Bei `navigator.onLine === false` ergaenzt Container den Subtext um den Offline-Hinweis
4. Retry triggert erneuten Request (Browser kann inzwischen wieder online sein)

---

## 6. Exception Flows

### 6.1 Exception E1: Wiederholter Fehler im Retry

**Trigger:** Benutzer klickt Retry, naechster API-Call schlaegt erneut fehl.

**Flow:**
1. Interceptor setzt `criticalError` neu (`requestId` + `timestamp` aktualisiert)
2. Da der Router schon auf `/error` ist, erfolgt KEINE zweite Navigation
3. Container rendert mit aktualisierten Detail-Daten
4. Counter "Wiederholte Fehler" wird im `AppErrorStore` mitgezaehlt — nach 3 fehlgeschlagenen Retries wird der Retry-Button deaktiviert und ein "Bitte spaeter erneut versuchen" Hinweis eingeblendet

### 6.2 Exception E2: Branch-Config nicht verfuegbar

**Trigger:** `selectedBranch` ist `null`, daher keine Filial-Telefonnummer verfuegbar.

**Flow:**
1. Container nutzt Fallback-Telefonnummer aus i18n-Key `app.error.fallbackPhone`
2. UI-Label zeigt "Allgemeine Service-Hotline" statt Filial-Name
3. Restliches Verhalten bleibt unveraendert

---

## 7. Postconditions

### 7.1 Success (Retry erfolgreich)
- `criticalError` ist `null`
- App ist auf `/home/location` mit normal geladenen Daten
- Standard-Wizard-Flow ist wieder aktiv
- BookingStore hat keine Stale-Daten (Resolver hat neu geladen)

### 7.2 Failure (Retry erneut fehlgeschlagen)
- `criticalError` bleibt gesetzt (mit aktualisierten Detail-Feldern)
- Router bleibt auf `/error`
- Nach 3 fehlgeschlagenen Retries: Retry-Button disabled, Telefon-Link bleibt einzige Aktion
- Keine Daten in BookingStore wurden korrumpiert

---

## 8. Business Rules

- **BR-1:** REQ-024 (App-Level) und REQ-017 (Component-Level) sind komplementaer. Component-Level wird zuerst versucht; nur wenn der Wizard-Step nicht erreichbar ist oder mehrere kritische Loads gleichzeitig scheitern, eskaliert das System zu REQ-024.
- **BR-2:** Auth-Loop-Schwellwert: 3 aufeinanderfolgende 401-Antworten innerhalb eines Token-Refresh-Zyklus.
- **BR-3:** Retry-Schwellwert: max. 3 erfolglose Retries vom Error-Screen aus, dann wird der Retry-Button deaktiviert.
- **BR-4:** Detail-Box ist standardmaessig kollabiert (User muss aktiv aufklappen). `requestId` ist die `X-Request-ID` aus dem Response-Header (siehe REQ-017 Trace-Service); `timestamp` ist ISO-8601.
- **BR-5:** Telefonnummer-Quelle: 1. `selectedBranch.phone` aus `BookingStore`; 2. Fallback i18n-Key `app.error.fallbackPhone`; 3. Hardcoded Fallback im Build (Service-Hotline).
- **BR-6:** Der Error-Screen darf KEINE Wizard-Navigation rendern (Header-Stepper, Footer-Buttons), nur Logo + Error-Inhalt.
- **BR-7:** Auto-Navigation zu `/error` erfolgt NUR bei `criticalError != null`. Beim Setzen muss eine Re-Entrancy-Guard verhindern, dass `effect()` mehrfach navigiert.
- **BR-8:** Kein Auto-Refresh / Self-Healing — Retry ist immer User-initiiert (verhindert versteckte Endlosschleifen).
- **BR-9:** Code-Sprache fuer alle Identifier (Klassen, Methoden, Felder, Logs) ist Englisch. UI-Texte sind in 5 Sprachen lokalisiert.

---

## 9. Non-Functional Requirements

### Performance
- Error-Screen rendert in < 100 ms ab `criticalError`-Set
- Lazy-Loaded `error.routes.ts` haelt Bundle der Hauptseite klein
- Kein Layout-Shift beim Rendern (Logo + Error-Block stabil)

### Security
- KEINE Stack-Traces, KEINE technischen Backend-Details (DB-Strings, Pfad-Infos) im UI — nur `code`, `requestId`, `timestamp`, ggf. HTTP-Status
- `requestId` ist eine Korrelations-ID (UUID), nicht reverse-engineerable
- Telefon-Link nutzt `tel:` URI, kein `javascript:` o.ae.

### Usability
- WCAG 2.1 AA, Mobile-First, Touch-Targets >= 2.75 em
- Initial-Focus auf Retry-Button (`@ViewChild` + `afterNextRender`)
- Screen-Reader liest Titel + Subtext via `aria-live="assertive"` direkt vor
- Buttons IMMER `mat-flat-button` (filled), nicht stroked
- Detail-Box (`<details>`) ist Tastatur-bedienbar (Enter/Space togglen)

### Resilience
- App muss auch ohne `BookingStore`-Initial-Load rendern koennen
- Error-Screen darf keinen weiteren API-Call beim Mount machen (sonst Risiko fuer Endlosschleife)

---

## 10. Data Model

```typescript
// src/app/core/error-handling/models/critical-error.model.ts

/**
 * Error code categorising the failure reason.
 * `NETWORK`     — Browser konnte Backend nicht erreichen (status 0, DNS fail, timeout)
 * `SERVER_5XX`  — Backend antwortet mit 5xx und Circuit-Breaker erlaubt keine Recovery
 * `AUTH_LOOP`   — 3+ aufeinanderfolgende 401, Token-Refresh fuehrt erneut zu 401
 * `UNKNOWN`     — Catch-All fuer unerwartete Faelle
 */
export type CriticalErrorCode = 'NETWORK' | 'SERVER_5XX' | 'AUTH_LOOP' | 'UNKNOWN';

export interface CriticalError {
  code: CriticalErrorCode;
  /** Englische Default-Nachricht, primaer fuer Logs. UI nutzt i18n-Key abhaengig von `code`. */
  message: string;
  /** X-Request-ID Korrelations-ID aus Response-Header, oder null wenn Request nie ankam. */
  requestId: string | null;
  /** ISO-8601 Zeitpunkt des Auftretens. */
  timestamp: string;
  /** Optional: HTTP-Status, wenn Response kam. */
  httpStatus?: number;
}

// src/app/core/error-handling/app-error.store.ts

interface AppErrorState {
  criticalError: CriticalError | null;
  /** Counter fuer Retry-Versuche vom Error-Screen aus (Reset bei Erfolg). */
  retryAttempts: number;
}

const INITIAL_STATE: AppErrorState = {
  criticalError: null,
  retryAttempts: 0,
};

// Computed:
//   hasCriticalError: boolean
//   canRetry: boolean (retryAttempts < 3)
//
// Methods:
//   setCriticalError(err: CriticalError): void
//   clearCriticalError(): void
//   incrementRetry(): void
//   resetRetry(): void
```

---

## 11. UI/UX

### Layout (Mockup, Desktop)

```
┌──────────────────────────────────────────────────────────────────────┐
│ [Logo]                                                          [X]  │  ← nur Logo + Close, kein Wizard-Header
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│                          [error_outline 4em]                         │
│                                                                      │
│                  Es ist ein Fehler aufgetreten                       │
│                                                                      │
│       Wir koennen momentan keine Daten laden. Bitte versuchen        │
│       Sie es erneut oder rufen Sie uns direkt an.                    │
│                                                                      │
│                  ┌─────────────────────────────┐                     │
│                  │  [refresh] Erneut versuchen │                     │
│                  └─────────────────────────────┘                     │
│                                                                      │
│              [phone] Direkt anrufen: +49 211 123456                  │
│                                                                      │
│                  ▸ Technische Details                                │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘

Detail-Box aufgeklappt:
                  ▾ Technische Details
                    Fehler-Code: SERVER_5XX
                    Anfrage-ID:  a1b2c3d4-e5f6-7890-abcd-ef0123456789
                    Zeitstempel: 2026-04-27T10:23:45.123Z
                    HTTP-Status: 503
```

### UI Elements

| Element | Typ | Material Component | ARIA |
|---------|-----|-------------------|------|
| Container | Section | `<section>` | `role="alert"`, `aria-live="assertive"` |
| Logo | Bild | `<img>` | `alt` aus i18n |
| Close-Button (X) | Icon Button | `mat-icon-button` | `aria-label="Schliessen"` (nur iframe) |
| Error-Icon | Material Icon | `mat-icon` | `aria-hidden="true"` |
| Titel | H1 | `<h1>` | - |
| Subtext | Paragraph | `<p>` | - |
| Retry-Button | Filled Button | `mat-flat-button color="primary"` | `aria-label` aus i18n |
| Phone-Link | Anchor | `<a href="tel:...">` | `aria-label` aus i18n |
| Details Toggle | `<details>` | nativ | Tastatur-bedienbar |
| Code-Label | DL/DT/DD | `<dl>` | - |

### Responsive Behavior

| Viewport | Layout |
|----------|--------|
| Mobile (< 48em) | Buttons full-width gestapelt, Icon 3em, Padding `var(--spacing-lg)` |
| Tablet (>= 48em) | Buttons inline, max-width Container 30em, zentriert |
| Desktop (>= 64em) | Wie Tablet, Icon 4em, Padding `var(--spacing-2xl)` |

### Styling-Hinweise

- Hintergrund: `var(--color-background-surface)` (weiss)
- Error-Icon: `var(--color-error)`
- Buttons: `mat-flat-button` (filled), nicht stroked
- Spacing: ausschliesslich CSS-Variablen aus `_variables.scss`
- Schriftgroessen: em/rem
- Initial-Focus: Retry-Button erhaelt Focus beim Mount (`afterNextRender`)
- Focus-Styles: `:focus-visible` mit deutlich sichtbarem Outline

---

## 12. API Specification

### Frontend-Erkennung (kein neuer Backend-Endpunkt in dieser Iteration)

```typescript
// error-fallback.interceptor.ts
// Pseudo-Logik:
return next(req).pipe(
  catchError((error: HttpErrorResponse) => {
    if (error.status === 0) {
      appErrorStore.setCriticalError({
        code: 'NETWORK',
        message: 'Network unreachable',
        requestId: error.headers?.get('X-Request-ID') ?? null,
        timestamp: new Date().toISOString(),
      });
    } else if (error.status >= 500 && circuitBreaker.isOpen()) {
      appErrorStore.setCriticalError({
        code: 'SERVER_5XX',
        message: error.message,
        requestId: error.headers?.get('X-Request-ID') ?? null,
        timestamp: new Date().toISOString(),
        httpStatus: error.status,
      });
    } else if (error.status === 401 && sessionTokenService.consecutive401() >= 3) {
      appErrorStore.setCriticalError({
        code: 'AUTH_LOOP',
        message: 'Authentication failed repeatedly',
        requestId: error.headers?.get('X-Request-ID') ?? null,
        timestamp: new Date().toISOString(),
        httpStatus: 401,
      });
    }
    return throwError(() => error);
  }),
);
```

### Optionale zukuenftige Erweiterung (out of scope dieser Iteration)

```http
POST /api/v1/error-report
Content-Type: application/json

{
  "code": "SERVER_5XX",
  "requestId": "a1b2c3d4-...",
  "timestamp": "2026-04-27T10:23:45.123Z",
  "httpStatus": 503,
  "userAgent": "Mozilla/5.0 ..."
}
```

**Success (202):** Empty body — Server-Side-Logs koennen mit Client-Side-Errors korreliert werden.

---

## 13. Test Cases

### TC-1: 5xx triggers Error-Screen
- **Given:** `GET /api/v1/branches` antwortet mit 503, Circuit-Breaker oeffnet
- **When:** Benutzer oeffnet `/home/location`
- **Then:** Auto-Navigation zu `/error`, Code `SERVER_5XX` sichtbar in Details

### TC-2: Network-Error
- **Given:** `navigator.onLine === false`
- **When:** API-Call laeuft in Timeout (`status === 0`)
- **Then:** Error-Screen mit Code `NETWORK`, Subtext enthaelt Offline-Hinweis

### TC-3: Auth-Loop
- **Given:** Session-Token wird vom Backend wiederholt mit 401 abgelehnt
- **When:** 3 aufeinanderfolgende 401 zaehlen sich auf
- **Then:** Error-Screen mit Code `AUTH_LOOP`, Token-Refresh wird gestoppt

### TC-4: Retry erfolgreich
- **Given:** User ist auf `/error`, Backend ist wieder online
- **When:** User klickt Retry
- **Then:** `criticalError = null`, Navigation zu `/home/location`, Wizard rendert normal

### TC-5: Retry erneut fehlgeschlagen
- **Given:** User ist auf `/error`, Backend bleibt down
- **When:** User klickt Retry
- **Then:** `criticalError` wird neu gesetzt (`requestId` + `timestamp` aktualisiert), keine zweite Navigation, `retryAttempts++`

### TC-6: Retry-Limit
- **Given:** `retryAttempts === 3`
- **When:** User sieht den Screen
- **Then:** Retry-Button ist disabled, Telefon-Link bleibt aktiv

### TC-7: Telefon-Link mit Branch-Phone
- **Given:** `selectedBranch.phone === '+49 211 123456'`
- **When:** Error-Screen rendert
- **Then:** `<a href="tel:+492111234566">` ist sichtbar, Label nennt Filial-Namen

### TC-8: Telefon-Link Fallback
- **Given:** `selectedBranch === null`
- **When:** Error-Screen rendert
- **Then:** Telefon-Link nutzt i18n-Fallback, Label "Allgemeine Service-Hotline"

### TC-9: Detail-Box Default kollabiert
- **Given:** Error-Screen rendert
- **When:** User sieht den Screen
- **Then:** `<details>` ist nicht aufgeklappt, Code/RequestId/Timestamp sind nicht sichtbar bis User klickt

### TC-10: WCAG Accessibility
- **Given:** Screen-Reader aktiv
- **When:** Error-Screen rendert
- **Then:** Titel + Subtext werden via `aria-live="assertive"` angesagt; Initial-Focus auf Retry-Button; Tab-Order: Retry → Phone → Details-Toggle

### TC-11: REQ-017 Regression
- **Given:** Wizard ist auf `/home/service`, einzelner API-Call schlaegt fehl, Circuit-Breaker bleibt geschlossen
- **When:** User sieht den Step
- **Then:** REQ-017-Inline-Error mit Retry rendert (NICHT REQ-024-Fullscreen) — beide Mechanismen koexistieren

### TC-12: i18n in 5 Sprachen
- **Given:** App-Sprache ist `ar`
- **When:** Error-Screen rendert
- **Then:** Alle Texte sind auf Arabisch, RTL-Layout aktiv, Buttons korrekt platziert

---

## 14. Implementation

### Neue Dateien

```
src/app/core/error-handling/
├── app-error.store.ts                             # Signal Store, providedIn: 'root'
├── app-error.store.spec.ts                        # Unit-Tests
├── error-fallback.interceptor.ts                  # HTTP-Interceptor, faengt 5xx/0/AUTH_LOOP
├── error-fallback.interceptor.spec.ts             # Unit-Tests
├── index.ts                                       # Re-Exports
└── models/
    └── critical-error.model.ts                    # Interface CriticalError + Type CriticalErrorCode

src/app/features/error-fallback/
├── error-fallback-container.component.ts          # Container (OnPush, inject Store + Router)
├── error-fallback-container.component.html        # Template (3-States: nur ein Block — Error)
├── error-fallback-container.component.scss        # Styles aus _variables.scss
├── error-fallback-container.component.spec.ts     # Unit-Tests (Retry, Phone)
└── error-fallback.routes.ts                       # Route-Config fuer Lazy-Loading
```

### Geanderte Dateien

```
src/app/app.routes.ts                              # Neue '/error' Lazy-Route
src/app/app.config.ts                              # error-fallback.interceptor in withInterceptors([])
src/app/app.component.ts                           # effect() — bei criticalError != null navigate zu /error
src/app/core/i18n/translations.ts                  # Neuer app.error.* Key-Block in DE/EN/UK/FR/AR
src/app/core/http/session-token.service.ts         # consecutive401() Counter + Schwellwert-Check
src/app/core/http/index.ts                         # Re-Exports unveraendert
```

### Components

- [ ] `ErrorFallbackContainerComponent` (Container, OnPush, inject `AppErrorStore` + `Router` + `BookingStore` fuer Branch-Phone)
- [ ] `AppErrorStore` (Signal Store, `providedIn: 'root'`)
- [ ] `errorFallbackInterceptor` (HttpInterceptorFn)

### Effort

- Development: 5-7 Stunden
- Testing: 3-4 Stunden
- Insgesamt: ca. 1 Manntag

---

## 15. Dependencies

**Requires:**
- REQ-017 (Loading-Spinner + Component-Errors): liefert das Inline-Error-Pattern, an dem REQ-024 vorbei eskaliert
- `CircuitBreakerService` (existiert in `core/http`)
- `SessionTokenService` (existiert in `core/http`) — muss um `consecutive401()` Counter erweitert werden
- Angular Material (`mat-icon`, `mat-flat-button`, `mat-icon-button`)
- ngx-translate / i18n-Service

**Blocks:**
- Keine

---

## 16. Naming Glossary

### Models

- `CriticalError` — Interface fuer kritischen Fehler
- `CriticalErrorCode` — Type-Union: `'NETWORK' | 'SERVER_5XX' | 'AUTH_LOOP' | 'UNKNOWN'`

### Store: `AppErrorStore`

- **State:** `criticalError`, `retryAttempts`
- **Computed:** `hasCriticalError`, `canRetry`
- **Methods:**
  - `setCriticalError(err: CriticalError): void`
  - `clearCriticalError(): void`
  - `incrementRetry(): void`
  - `resetRetry(): void`

### Interceptor

- `errorFallbackInterceptor: HttpInterceptorFn` — registriert in `app.config.ts`

### Container Methods

- `onRetry(): void` — Retry-Button Handler: `clearCriticalError()` + `incrementRetry()` + Navigation `/home/location`
- `onCallPhone(): void` — Telemetry-Hook fuer Telefon-Klick (optional, zukuenftige Erweiterung)

### Computed Signals (Container)

- `criticalError` (proxied vom Store)
- `errorTitleKey` — i18n-Key abhaengig von `code`
- `errorSubtextKey` — i18n-Key abhaengig von `code` + `navigator.onLine`
- `phoneNumber` — abhaengig von `selectedBranch.phone` oder Fallback
- `phoneLabel` — abhaengig von `selectedBranch.name` oder Fallback
- `canRetry` — proxied vom Store

### Route

- Path: `error`, lazy-loaded via `loadChildren: () => import('./features/error-fallback/error-fallback.routes').then(m => m.errorFallbackRoutes)`

---

## 17. i18n Keys

**Code-Sprache: Englisch.** Key-Block: `app.error.*` (auf App-Ebene, nicht unter `booking.*`, da App-weit).

### DE (Deutsch)

```typescript
app: {
  error: {
    title: 'Es ist ein Fehler aufgetreten',
    subtext: 'Wir koennen momentan keine Daten laden. Bitte versuchen Sie es erneut oder rufen Sie uns direkt an.',
    subtextOffline: 'Pruefen Sie Ihre Internetverbindung. Wir koennen das Backend nicht erreichen.',
    subtextAuthLoop: 'Die Anmeldung mit dem Backend ist mehrfach fehlgeschlagen. Bitte versuchen Sie es spaeter erneut.',
    subtextServer: 'Unser Server antwortet derzeit nicht. Bitte versuchen Sie es in wenigen Minuten erneut.',
    retryButton: 'Erneut versuchen',
    retryButtonAriaLabel: 'Daten erneut laden',
    retryDisabled: 'Bitte spaeter erneut versuchen',
    phoneLink: 'Direkt anrufen: {phone}',
    phoneLinkAriaLabel: 'Autohaus anrufen unter {phone}',
    phoneLabelBranch: 'Filiale {branch}',
    phoneLabelFallback: 'Allgemeine Service-Hotline',
    fallbackPhone: '+49 211 123456',
    detailsToggle: 'Technische Details',
    codeLabel: 'Fehler-Code',
    requestIdLabel: 'Anfrage-ID',
    timestampLabel: 'Zeitstempel',
    httpStatusLabel: 'HTTP-Status'
  }
}
```

### EN (English)

```typescript
app: {
  error: {
    title: 'An error occurred',
    subtext: 'We cannot load any data right now. Please retry or call us directly.',
    subtextOffline: 'Please check your internet connection. We cannot reach the backend.',
    subtextAuthLoop: 'Authentication with the backend failed repeatedly. Please try again later.',
    subtextServer: 'Our server is not responding right now. Please try again in a few minutes.',
    retryButton: 'Retry',
    retryButtonAriaLabel: 'Retry loading data',
    retryDisabled: 'Please try again later',
    phoneLink: 'Call directly: {phone}',
    phoneLinkAriaLabel: 'Call dealership at {phone}',
    phoneLabelBranch: 'Branch {branch}',
    phoneLabelFallback: 'General service hotline',
    fallbackPhone: '+49 211 123456',
    detailsToggle: 'Technical details',
    codeLabel: 'Error code',
    requestIdLabel: 'Request ID',
    timestampLabel: 'Timestamp',
    httpStatusLabel: 'HTTP status'
  }
}
```

### UK (Ukrainian — Maschinen-Uebersetzung passend zum bestehenden Schema)

```typescript
app: {
  error: {
    title: 'Сталася помилка',
    subtext: 'Наразі не вдається завантажити дані. Будь ласка, спробуйте знову або зателефонуйте нам напряму.',
    subtextOffline: 'Перевірте ваше інтернет-з\'єднання. Ми не можемо зв\'язатися з сервером.',
    subtextAuthLoop: 'Автентифікація з сервером неодноразово не вдалася. Будь ласка, спробуйте пізніше.',
    subtextServer: 'Наш сервер наразі не відповідає. Будь ласка, спробуйте через кілька хвилин.',
    retryButton: 'Повторити',
    retryButtonAriaLabel: 'Повторити завантаження даних',
    retryDisabled: 'Будь ласка, спробуйте пізніше',
    phoneLink: 'Зателефонувати напряму: {phone}',
    phoneLinkAriaLabel: 'Зателефонувати автосалону за номером {phone}',
    phoneLabelBranch: 'Філія {branch}',
    phoneLabelFallback: 'Загальна сервісна гаряча лінія',
    fallbackPhone: '+49 211 123456',
    detailsToggle: 'Технічні деталі',
    codeLabel: 'Код помилки',
    requestIdLabel: 'ID запиту',
    timestampLabel: 'Часова мітка',
    httpStatusLabel: 'HTTP-статус'
  }
}
```

### FR (Francais — Maschinen-Uebersetzung passend zum bestehenden Schema)

```typescript
app: {
  error: {
    title: 'Une erreur est survenue',
    subtext: 'Nous ne pouvons charger aucune donnee pour le moment. Veuillez reessayer ou nous appeler directement.',
    subtextOffline: 'Verifiez votre connexion Internet. Nous ne pouvons pas joindre le serveur.',
    subtextAuthLoop: 'L\'authentification avec le serveur a echoue plusieurs fois. Veuillez reessayer plus tard.',
    subtextServer: 'Notre serveur ne repond pas pour le moment. Veuillez reessayer dans quelques minutes.',
    retryButton: 'Reessayer',
    retryButtonAriaLabel: 'Recharger les donnees',
    retryDisabled: 'Veuillez reessayer plus tard',
    phoneLink: 'Appeler directement : {phone}',
    phoneLinkAriaLabel: 'Appeler le concessionnaire au {phone}',
    phoneLabelBranch: 'Agence {branch}',
    phoneLabelFallback: 'Hotline service general',
    fallbackPhone: '+49 211 123456',
    detailsToggle: 'Details techniques',
    codeLabel: 'Code d\'erreur',
    requestIdLabel: 'ID de requete',
    timestampLabel: 'Horodatage',
    httpStatusLabel: 'Statut HTTP'
  }
}
```

### AR (Arabic — Maschinen-Uebersetzung, RTL)

```typescript
app: {
  error: {
    title: 'حدث خطأ',
    subtext: 'لا يمكننا تحميل أي بيانات حاليًا. يرجى المحاولة مرة أخرى أو الاتصال بنا مباشرة.',
    subtextOffline: 'يرجى التحقق من اتصالك بالإنترنت. لا يمكننا الوصول إلى الخادم.',
    subtextAuthLoop: 'فشلت المصادقة مع الخادم عدة مرات. يرجى المحاولة لاحقًا.',
    subtextServer: 'الخادم لا يستجيب حاليًا. يرجى المحاولة مرة أخرى بعد بضع دقائق.',
    retryButton: 'إعادة المحاولة',
    retryButtonAriaLabel: 'إعادة تحميل البيانات',
    retryDisabled: 'يرجى المحاولة لاحقًا',
    phoneLink: 'اتصل مباشرة: {phone}',
    phoneLinkAriaLabel: 'الاتصال بالوكالة على الرقم {phone}',
    phoneLabelBranch: 'الفرع {branch}',
    phoneLabelFallback: 'الخط الساخن العام للخدمة',
    fallbackPhone: '+49 211 123456',
    detailsToggle: 'التفاصيل التقنية',
    codeLabel: 'رمز الخطأ',
    requestIdLabel: 'معرف الطلب',
    timestampLabel: 'الطابع الزمني',
    httpStatusLabel: 'حالة HTTP'
  }
}
```

---

## 18. Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | | |
| Tech Lead | | |

---

## 19. Implementation Notes

**Code-Sprache: Englisch (FIXIERT).** Klassen, Methoden, Felder, Logs, Kommentare in TypeScript-Code sind Englisch. UI-Texte sind in 5 Sprachen lokalisiert (siehe Section 17).

### Wichtige Konventionen

- **Container/Presentational:** `ErrorFallbackContainerComponent` ist der einzige Component fuer diese Route — kein Presentational-Split noetig (sehr kleine Surface). OnPush, `inject(AppErrorStore)`.
- **Store-Pattern:** `AppErrorStore` folgt dem Signal-Store-Pattern (`withState`, `withComputed`, `withMethods`). KEIN `onInit` mit Daten-Loading. KEIN `rxMethod` (rein synchron).
- **HTTP-Interceptor-Reihenfolge:** `[sessionTokenInterceptor, circuitBreakerInterceptor, errorFallbackInterceptor]` — `errorFallbackInterceptor` zuletzt, damit er die finalen Fehler nach Circuit-Breaker und Auth-Refresh sieht.
- **Re-Entrancy Guard:** Im App-Shell-`effect()` muss verhindert werden, dass die Navigation zu `/error` mehrfach ausgeloest wird. Pattern: `if (router.url === '/error') return;`.
- **Telemetry-Hook:** `onCallPhone()` ist ein leerer Hook fuer zukuenftige Telemetry — keine Implementierung in dieser Iteration.
- **Bilingual Code Comments:** JSDoc-Bloecke an oeffentlichen Methoden in DE + EN (siehe `.claude/skills/bilingual-code.md`).

### Test-Coverage-Ziel

- `AppErrorStore`: > 90 % (kleiner Surface, alle Methoden testbar)
- `errorFallbackInterceptor`: > 85 % (drei Fehler-Codes + Happy-Path)
- `ErrorFallbackContainerComponent`: > 80 % (Retry, Phone, Disabled-State)

### Styling-Hinweise

- Alle Farben/Spacings aus `_variables.scss`
- KEINE Inline-Styles, KEINE hardcoded Pixel
- Mobile-First, Breakpoints: < 48em / >= 48em / >= 64em
- Buttons IMMER `mat-flat-button` (filled), nicht stroked

### Definition of Done

- [ ] Alle 14 ACs erfuellt
- [ ] Tests >= 80 % Coverage (Jest)
- [ ] `/check-architecture`, `/check-i18n`, `/check-styling`, `/check-security` gruen
- [ ] WCAG 2.1 AA manuell verifiziert (Screen-Reader + Keyboard)
- [ ] Funktionstest bei offline (DevTools "Offline" + abgestelltem Backend)
