# REQ-014: NestJS Backend

**Status:** Draft
**Priority:** Critical
**Type:** Functional + Non-Functional
**Created:** 2026-04-02
**Author:** Claude Code

---

## 1. Overview

### 1.1 Purpose
Das NestJS Backend dient als Middleware/Proxy zwischen der Angular-Frontend-App (Autohaus GmbH Fahrzeugauswahl) und der externen SoftNet API (`https://api.soft-nrg.com`). Es kapselt die SoftNet-API-Kommunikation, stellt eine eigene REST-API bereit, rendert die Angular-App per SSR (Server-Side Rendering) mit CSP-Nonce-Injection und implementiert ein mehrstufiges Sicherheitskonzept zum Schutz der oeffentlich zugaenglichen API ohne Authentifizierungssystem.

### 1.2 Scope
**Included:**
- NestJS Backend-Applikation mit modularer Architektur
- Proxy-Endpunkte fuer alle relevanten SoftNet-API-Aufrufe (Branches, Brands, Services/JobTypes, Snaps/Zeitslots, Appointments, Cars, Users/Customers)
- Branch-ID-basierte Konfiguration (initial eine Filiale via Environment-Config, spaeter mehrere)
- Server-Side Rendering (SSR) der Angular-App mit CSP-Nonce-Injection pro Request
- Nonce-basierte Content Security Policy (keine unsafe-inline) — Angular Material Styles werden per Nonce autorisiert
- Mehrstufiges Security-Konzept: Rate Limiting, Helmet.js (gehäertet), CORS, Input-Validation (DTOs mit @Matches), HMAC-basiertes Session-Token, Session-Token-Interceptor im Frontend
- TLS-Vorbereitung (initial lokal ohne Zertifikat, Konfiguration fuer spaetere Einbindung)
- Health-Check-Endpunkt
- Strukturierte Fehlerbehandlung mit i18n-Fehlermeldungen (DE + EN)
- Optimierung fuer viele gleichzeitige Anfragen
- iFrame-Einbettung: App wird auf Kunden-Websites per iFrame eingebunden (Overlay/Modal auf der Eltern-Seite)
- Branch-ID per URL-Parameter: Kunden-Website uebergibt Standort-ID via Query-Parameter (`?branchId=XXX`), Wizard startet direkt bei Markenauswahl (Standortauswahl wird uebersprungen)

**Excluded:**
- OAuth/Login/Authentifizierung fuer Endbenutzer
- Datenbank/Persistenzschicht (Backend ist stateless, SoftNet API ist die Datenquelle)
- Admin-Dashboard oder Backend-UI
- WebSocket/Real-Time-Kommunikation (Phase 1)
- GitHub Pages Deployment (ersetzt durch SSR-Deployment via NestJS)

### 1.3 Related Requirements
- REQ-002: Markenauswahl (Frontend nutzt `GET /api/brands` vom Backend)
- REQ-003: Standortwahl (Frontend nutzt `GET /api/branches/:branchId` Daten)
- REQ-004: Serviceauswahl (Frontend nutzt `GET /api/jobtypes` vom Backend)
- REQ-006: Terminauswahl (Frontend nutzt `POST /api/snaps` vom Backend)
- REQ-008: Werkstattkalender (Frontend nutzt `POST /api/snaps` mit Datumsbereich)
- REQ-009: carinformation (Frontend nutzt `POST /api/cars` und `POST /api/vaultitems`)
- REQ-010: Buchungsuebersicht (Frontend nutzt `POST /api/appointments`)

---

## 2. User Story

**Als** Betreiber der Autohaus-Terminbuchungsplattform
**moechte ich** ein sicheres, performantes NestJS Backend, das als Middleware zwischen der Angular-App und der SoftNet API fungiert
**damit** die Endbenutzer Termine buchen koennen, ohne dass die SoftNet-API-Credentials exponiert werden, und die Plattform gegen Missbrauch geschuetzt ist.

**Acceptance Criteria:**
- [ ] AC-1: Backend startet mit `npm run start` und ist unter `http://localhost:3000` erreichbar
- [ ] AC-2: Angular-App wird per SSR (Server-Side Rendering) unter Root-URL (`/`) gerendert mit CSP-Nonce-Injection
- [ ] AC-3: Alle REST-API-Endpunkte unter `/api/v1/` sind erreichbar und liefern korrekte Daten von SoftNet
- [ ] AC-4: Rate Limiting begrenzt Requests auf 100 pro 15 Minuten pro IP
- [ ] AC-5: Helmet.js setzt sichere HTTP-Headers (X-Frame-Options, X-Content-Type-Options, CSP, etc.)
- [ ] AC-6: CORS erlaubt nur konfigurierte Origins
- [ ] AC-7: Input-Validation prueft alle eingehenden Request-Bodies via class-validator DTOs
- [ ] AC-8: HMAC-basiertes Session-Token wird bei Page-Load generiert (20 Min TTL) und bei API-Calls validiert
- [ ] AC-9: Alle Secrets (SoftNet Fingerprint-Pfad, HMAC-Secret, Group-ID, Branch-ID, Auth-URLs, API-URLs) werden ausschliesslich ueber `.env`-Datei konfiguriert — keine Secrets im Code oder in Config-Dateien im Repo
- [ ] AC-19: Zwei separate SoftNet-API-Konfigurationen in `.env`: READ-Stage (fuer GET-Requests) und WRITE-Stage (fuer POST/PUT-Requests) — auf Dev: READ=Production, WRITE=Integration; auf Live: beide=Production
- [ ] AC-10: `.env.example` liegt im Repo mit Platzhaltern; `.env` ist in `.gitignore` und wird NIE committed
- [ ] AC-11: Backend liest beim Start die Fingerprint-JSON-Datei (Pfad aus `.env`) und generiert JWT-Tokens automatisch fuer die SoftNet-Auth
- [ ] AC-12: Group-ID und Branch-ID sind in `.env` konfigurierbar (spaeter mehrere Groups/Branches moeglich)
- [ ] AC-13: Fehler-Responses enthalten strukturierte JSON-Objekte mit i18n-faehigen Fehlermeldungen
- [ ] AC-14: Health-Check unter `GET /api/v1/health` liefert Backend-Status und SoftNet-Erreichbarkeit
- [ ] AC-15: TLS-Konfiguration ist vorbereitet (Zertifikat-Pfade in `.env`), initial deaktiviert
- [ ] AC-16: Backend verarbeitet mindestens 200 gleichzeitige Requests ohne Performance-Einbruch
- [ ] AC-17: iFrame-Einbettung ist moeglich — `X-Frame-Options` wird durch `Content-Security-Policy: frame-ancestors` mit konfigurierbaren Domains ersetzt (via `IFRAME_ALLOWED_ORIGINS` in `.env`)
- [ ] AC-18: Branch-ID kann per URL-Query-Parameter (`?branchId=XXX`) uebergeben werden — Angular-App ueberspringt Standortauswahl und startet direkt bei Markenauswahl fuer den jeweiligen Standort

---

## 3. Preconditions

### 3.1 System
- Node.js >= 20 LTS installiert
- NestJS CLI global installiert (`npm i -g @nestjs/cli`)
- Angular-App ist gebaut (`ng build` Output in `dist/` Verzeichnis)
- Netzwerkzugang zur SoftNet API (`https://api.soft-nrg.com`)
- SoftNet API access_token vorhanden (serverseitige Konfiguration)

### 3.2 User
- Keine Authentifizierung erforderlich (oeffentliche API)
- Endbenutzer greift ueber Browser auf die Angular-App zu

### 3.3 Data
- Branch-ID ist in der Environment-Konfiguration hinterlegt
- SoftNet API access_token ist in der Environment-Konfiguration hinterlegt
- Zwei separate SoftNet-API-Konfigurationen: READ-Stage (GET-Requests) und WRITE-Stage (POST/PUT-Requests)
  - **Dev-Umgebung:** READ=Production API (`https://api.soft-nrg.com`), WRITE=Integration API (`https://int-api.soft-nrg.com`) — damit auf Dev mit echten Stammdaten gelesen, aber nur auf der Test-Umgebung geschrieben wird
  - **Live-Umgebung:** READ=Production, WRITE=Production (beide identisch)

---

## 4. Main Flow

**Step 1:** Backend-Setup und Konfiguration
- **System:** NestJS-App wird initialisiert mit `ConfigModule`, `ThrottlerModule`
- **System:** Environment-Variablen werden geladen (Branch-ID, SoftNet API URL, access_token, CORS Origins)
- **System:** Helmet.js wird mit gehaerteter Konfiguration geladen (hidePoweredBy, Permissions-Policy, Referrer-Policy, ETag deaktiviert)
- **Expected:** Backend laeuft auf konfiguriertem Port (Standard: 3000)

**Step 2:** Angular-App wird per SSR serviert
- **User:** Oeffnet `http://localhost:3000` im Browser
- **System:** NestJS generiert eine einzigartige CSP-Nonce pro Request via `crypto.randomBytes(16)`
- **System:** Express-Middleware liefert statische Assets (JS, CSS, Bilder) aus dem `dist/angular-ai-test/browser/` Verzeichnis
- **System:** Fuer HTML-Requests wird `index.html` gelesen, `<app-root>` durch `<app-root ngCspNonce="${nonce}">` ersetzt, und die CSP-Header mit der Nonce gesetzt
- **System:** Alle nicht-API-Routen werden zur nonce-injizierten `index.html` weitergeleitet (SPA-Fallback fuer Path-Based Routing)
- **Expected:** Angular-App laedt mit nonce-basierter CSP (keine `unsafe-inline`), Angular Material Styles werden per Nonce autorisiert

**Step 3:** API-Request mit Session-Token
- **User:** Angular-App fordert Session-Token an (`GET /api/v1/session/token`)
- **System:** Generiert HMAC-basiertes Token mit Timestamp, 20 Min TTL
- **System:** Alle folgenden API-Requests muessen den Token im `X-Session-Token` Header mitsenden
- **Expected:** Token wird im Angular-App-State gespeichert

**Step 4:** Datenfluss — Brands abrufen
- **User:** Angular-App ruft `GET /api/v1/branches/:branchId/brands` auf
- **System:** Backend leitet an SoftNet `GET /api/planning/brands` weiter (mit access_token)
- **System:** Response wird transformiert und an Angular-App zurueckgegeben
- **Expected:** Frontend zeigt Markenauswahl (REQ-002)

**Step 5:** Datenfluss — Services/JobTypes abrufen
- **User:** Angular-App ruft `GET /api/v1/branches/:branchId/jobtypes` auf
- **System:** Backend leitet an SoftNet `GET /api/planning/jobtypes` weiter
- **Expected:** Frontend zeigt verfuegbare Services (REQ-004)

**Step 6:** Datenfluss — Snaps (Zeitslots) abrufen
- **User:** Angular-App ruft `POST /api/v1/snaps` auf (mit Branch-ID, JobType-IDs, Datumsbereich)
- **System:** Backend leitet an SoftNet `POST /api/planning/snaps` weiter
- **System:** Zwei Snap-Varianten werden unterstuetzt: naechster verfuegbarer Slot, ab bestimmtem Datum + 3 Tage
- **Expected:** Frontend zeigt Terminvorschlaege (REQ-006) oder Werkstattkalender (REQ-008)

**Step 7:** Datenfluss — Kunde anlegen oder finden
- **User:** Angular-App ruft `POST /api/v1/vaultitems` auf (Kundendaten) oder `GET /api/v1/vaultitems?licensePlate=XX` (Suche)
- **System:** Backend leitet an SoftNet `POST /api/planning/vaultitems` oder `GET /api/planning/vaultitems` weiter
- **Expected:** Kunde wird im SoftNet-System angelegt oder gefunden

**Step 8:** Datenfluss — Fahrzeug anlegen
- **User:** Angular-App ruft `POST /api/v1/cars` auf (Fahrzeugdaten)
- **System:** Backend leitet an SoftNet `POST /api/planning/cars` weiter
- **Expected:** Fahrzeug wird im SoftNet-System angelegt

**Step 9:** Datenfluss — Termin buchen
- **User:** Angular-App ruft `POST /api/v1/appointments` auf (kompletter Buchungsdatensatz)
- **System:** Backend leitet an SoftNet `POST /api/planning/appointments` weiter
- **System:** Booking-ID wird aus SoftNet-Response extrahiert und zurueckgegeben
- **Expected:** Termin ist gebucht, Booking-ID wird im Frontend angezeigt

**Step 10:** Datenfluss — Termin-Status pruefen
- **User:** Angular-App ruft `GET /api/v1/appointments/:id` auf
- **System:** Backend leitet an SoftNet `GET /api/planning/appointments/{id}` weiter
- **Expected:** Termin-Status und Details werden angezeigt

---

## 5. Alternative Flows

### 5.1 SoftNet API nicht erreichbar

**Trigger:** SoftNet API antwortet nicht oder mit Timeout (> 10s)

**Flow:**
1. Backend erkennt Verbindungsfehler oder Timeout
2. Backend gibt strukturierte Fehler-Response zurueck (HTTP 503 Service Unavailable)
3. Angular-App zeigt Fehlermeldung: "Der Service ist voruebergehend nicht erreichbar. Bitte versuchen Sie es spaeter erneut."
4. Backend loggt den Fehler mit Details (Timestamp, Endpoint, Error-Code)
5. Health-Check-Endpunkt zeigt SoftNet-Status als `unhealthy`

### 5.2 Mehrere Filialen (Branch-IDs)

**Trigger:** Konfiguration wird auf mehrere Filialen erweitert

**Flow:**
1. Environment-Config enthaelt Array von Branch-IDs statt einzelner ID
2. Angular-App zeigt Filialauswahl als ersten Wizard-Schritt (vor Markenauswahl)
3. Ausgewaehlte Branch-ID wird in allen folgenden API-Calls als Path-Parameter mitgesendet
4. Backend validiert Branch-ID gegen konfigurierte Liste

### 5.3 iFrame-Einbettung mit Branch-ID per URL

**Trigger:** Kunden-Website bindet die Angular-App per iFrame ein und uebergibt die Standort-ID als Query-Parameter

**Flow:**
1. Kunden-Website oeffnet iFrame (z.B. als Overlay/Modal) mit URL: `https://booking-app.de/?branchId=69bbe9d5d053d96948dea49e#/home/brand`
2. Backend liefert `Content-Security-Policy: frame-ancestors <konfigurierte-domains>` Header (statt `X-Frame-Options: DENY`), damit iFrame-Einbettung erlaubt ist
3. Angular-App liest `branchId` aus dem Query-Parameter der URL
4. Branch-ID wird im Store gesetzt (entspricht einer automatischen Standortauswahl)
5. Wizard startet direkt bei Markenauswahl (`/home/brand`) — Standortauswahl wird uebersprungen
6. Backend validiert die uebergebene Branch-ID gegen die konfigurierte Liste erlaubter Filialen
7. Alle folgenden API-Calls verwenden die uebergebene Branch-ID als Path-Parameter (`/api/v1/branches/:branchId/...`)

**Darstellung:** Die Overlay/Modal-Darstellung wird vollstaendig von der Kunden-Website (Eltern-Seite) gesteuert. Die Angular-App im iFrame ist davon unabhaengig und rendert sich regulaer.

**Konfiguration:**
- `IFRAME_ALLOWED_ORIGINS` in `.env` — Komma-separierte Liste erlaubter Eltern-Domains (z.B. `https://www.kundenwebsite.de,https://kundenwebsite.de`)
- Wenn leer oder nicht gesetzt: iFrame-Einbettung ist deaktiviert (`frame-ancestors 'self'`)

### 5.4 SoftNet API liefert leere Daten

**Trigger:** SoftNet API antwortet mit 200 OK, aber leeren Arrays

**Flow:**
1. Backend transformiert Response und gibt leeres Array zurueck
2. Angular-App zeigt entsprechende "Keine Daten verfuegbar"-Meldung
3. Kein Fehler-Logging (valide Antwort)

---

## 6. Exception Flows

### 6.1 Exception E1: Rate Limit Exceeded

**Trigger:** Client ueberschreitet 100 Requests pro 15 Minuten

**Flow:**
1. ThrottlerGuard blockt den Request
2. Backend gibt HTTP 429 (Too Many Requests) zurueck
3. Response enthaelt `Retry-After` Header mit verbleibenden Sekunden
4. Fehler-Body: `{ "statusCode": 429, "messageKey": "error.rateLimitExceeded", "retryAfter": 120 }`

### 6.2 Exception E2: Invalid Session Token

**Trigger:** Client sendet keinen, abgelaufenen oder manipulierten Session-Token

**Flow:**
1. SessionTokenGuard prueft den `X-Session-Token` Header
2. HMAC-Verifikation schlaegt fehl oder Timestamp ist aelter als 20 Minuten
3. Backend gibt HTTP 403 (Forbidden) zurueck
4. Fehler-Body: `{ "statusCode": 403, "messageKey": "error.invalidSessionToken" }`
5. Client muss neuen Token anfordern (`GET /api/v1/session/token`)

### 6.3 Exception E3: SoftNet API Timeout

**Trigger:** SoftNet API antwortet nicht innerhalb von 10 Sekunden

**Flow:**
1. HttpService-Timeout greift (10.000ms)
2. Backend gibt HTTP 504 (Gateway Timeout) zurueck
3. Fehler-Body: `{ "statusCode": 504, "messageKey": "error.upstreamTimeout" }`
4. Backend loggt Timeout-Event mit Endpoint und Dauer

### 6.4 Exception E4: SoftNet API Fehler (4xx/5xx)

**Trigger:** SoftNet API antwortet mit Fehler-Status

**Flow:**
1. Backend erhaelt Fehler-Response von SoftNet
2. Backend mappt SoftNet-Fehler auf eigene Fehler-Codes
3. Bei 401/403 von SoftNet: HTTP 503 an Client (API-Konfigurationsfehler, kein User-Fehler)
4. Bei 400 von SoftNet: HTTP 400 an Client mit gemappter Fehlermeldung
5. Bei 500 von SoftNet: HTTP 502 (Bad Gateway) an Client
6. Alle Fehler werden geloggt

### 6.5 Exception E5: Validation Error

**Trigger:** Request-Body entspricht nicht dem erwarteten DTO-Schema

**Flow:**
1. ValidationPipe (class-validator) prueft eingehenden Body
2. Bei Validierungsfehler: HTTP 400 (Bad Request)
3. Fehler-Body: `{ "statusCode": 400, "messageKey": "error.validationFailed", "details": [{ "field": "email", "messageKey": "error.validation.invalidEmail" }] }`

---

## 7. Postconditions

### 7.1 Success
- Backend laeuft stabil und serviert die Angular-App
- Alle API-Endpunkte sind erreichbar und liefern transformierte SoftNet-Daten
- Rate Limiting und Session-Token-Validierung schuetzen die API
- Health-Check zeigt Backend- und SoftNet-Status als `healthy`
- Logs werden strukturiert in der Konsole ausgegeben (JSON-Format)

### 7.2 Failure
- Backend gibt strukturierte Fehler-Responses zurueck (kein Stack-Trace in Production)
- Fehler werden geloggt mit Timestamp, Request-ID, Error-Code und Stack-Trace (serverseitig)
- Angular-App zeigt benutzerfreundliche Fehlermeldung basierend auf `messageKey`

---

## 8. Business Rules

- **BR-1:** Branch-ID ist Pflicht fuer alle geschaeftlichen API-Calls (Brands, JobTypes, Snaps, Appointments)
- **BR-2:** Rate Limiting: maximal 100 Requests pro 15 Minuten pro IP-Adresse
- **BR-3:** Session-Token: maximal 20 Minuten gueltig, HMAC-signiert mit Server-Secret, stateless Verifikation
- **BR-4:** SoftNet API access_token wird ausschliesslich serverseitig gehalten und nie an den Client exponiert
- **BR-5:** Alle API-Responses werden vom Backend transformiert (SoftNet-interne Felder wie `cryptid` werden entfernt)
- **BR-6:** SoftNet API Timeout: 10 Sekunden, danach Abbruch und Fehler-Response
- **BR-7:** Angular-App wird als statische Dateien serviert — keine Template-Verarbeitung serverseitig
- **BR-8:** CORS: nur konfigurierte Origins erlaubt (lokal: `http://localhost:4200`, Production: eigene Domain)
- **BR-9:** Snap-Abfrage unterstuetzt zwei Modi: (a) naechster verfuegbarer Slot, (b) ab Datum + 3 Tage Bereich
- **BR-10:** Kundendaten koennen per Kennzeichen, Name oder E-Mail-Kombination gesucht werden

---

## 9. Non-Functional Requirements

### Performance
- API-Response-Time (Backend-intern, ohne SoftNet-Latenz): < 50ms
- End-to-End-Response-Time (inkl. SoftNet): < 2000ms (95. Perzentil)
- Concurrent Users: mindestens 200 gleichzeitige Verbindungen
- Static File Serving: < 100ms fuer Angular-Assets (mit Caching-Headers)
- Memory Usage: < 256 MB im Normalbetrieb

### Security
- Helmet.js: Sichere HTTP-Headers (X-Frame-Options: DENY, X-Content-Type-Options: nosniff, Content-Security-Policy, Referrer-Policy, Strict-Transport-Security bei TLS)
- CORS: Whitelist-basiert, nur konfigurierte Origins
- Rate Limiting: IP-basiert via @nestjs/throttler (100 Req/15min)
- Input-Validation: class-validator + class-transformer auf allen POST/PUT-Bodies
- Session-Token: HMAC-SHA256-signiert, Timestamp-basiert, 20 Min TTL, stateless Verifikation
- SoftNet-Credentials: ausschliesslich in Environment-Variablen, nie in Responses
- TLS: Konfiguration vorbereitet, initial lokal ohne Zertifikat
- No eval(), no dynamic code execution
- Request-Body-Limit: max 1 MB

### Scalability
- Stateless Design: kein Server-State, horizontal skalierbar
- Environment-basierte Konfiguration fuer Multi-Instance-Deployment

### Logging
- Structured JSON Logging (Timestamp, Level, Request-ID, Message)
- Log-Levels: error, warn, info, debug
- SoftNet API Call Logging: URL, Method, Duration, Status
- Keine sensiblen Daten in Logs (access_token, Kundendaten werden maskiert)

---

## 10. Data Model

### Environment Configuration

```typescript
/**
 * Backend environment configuration
 * DE: Backend-Umgebungskonfiguration / EN: Backend environment config
 */
interface EnvironmentConfig {
  port: number;                    // Server port (default: 3000)
  softnet: {
    baseUrl: string;               // SoftNet API base URL
    accessToken: string;           // SoftNet API access token
    timeout: number;               // Request timeout in ms (default: 10000)
  };
  branchId: string;                // Primary branch ID (Filiale)
  cors: {
    allowedOrigins: string[];      // Allowed CORS origins
  };
  session: {
    secret: string;                // HMAC signing secret
    ttlMinutes: number;            // Token TTL (default: 20)
  };
  tls: {
    enabled: boolean;              // Enable TLS (default: false)
    certPath: string;              // Path to TLS certificate
    keyPath: string;               // Path to TLS private key
  };
}
```

### Request/Response DTOs

```typescript
/**
 * Session token response
 */
interface SessionTokenResponseDto {
  token: string;
  expiresAt: string;               // ISO 8601 timestamp
}

/**
 * Branch response (transformed from SoftNet Group)
 */
interface BranchResponseDto {
  id: string;
  name: string;
  description: string;
  brands: string[];
}

/**
 * Brand response (transformed from SoftNet)
 */
interface BrandResponseDto {
  id: string;
  name: string;
}

/**
 * JobType response (mapped to Angular ServiceDisplay)
 */
interface JobTypeResponseDto {
  id: string;
  name: string;
  description: string;
  operations: OperationResponseDto[];
}

/**
 * Operation within a JobType
 */
interface OperationResponseDto {
  id: string;
  name: string;
}

/**
 * Snap request — query available time slots
 */
interface SnapRequestDto {
  branchId: string;                // Branch ID (Filiale)
  jobTypeIds: string[];            // Selected job types / services
  fromDate: string;                // ISO date string (YYYY-MM-DD)
  toDate: string;                  // ISO date string (YYYY-MM-DD)
  mode: 'next-available' | 'date-range';
}

/**
 * Snap response — available time slots
 */
interface SnapResponseDto {
  date: string;                    // ISO date (YYYY-MM-DD)
  displayDate: string;             // DD.MM.YYYY
  dayAbbreviation: string;         // Mo, Di, Mi, Do, Fr, Sa
  slots: TimeSlotDto[];
}

interface TimeSlotDto {
  id: string;
  time: string;                    // HH:MM
  displayTime: string;             // HH:MM Uhr
  available: boolean;
}

/**
 * Create customer (vault item) request
 * Maps to Angular CustomerInfo + VehicleInfo
 */
interface CreateCustomerRequestDto {
  email: string;
  salutation: 'mr' | 'ms';
  firstName: string;
  lastName: string;
  street: string;
  postalCode: string;
  city: string;
  mobilePhone: string;
}

/**
 * Search customer request
 */
interface SearchCustomerRequestDto {
  licensePlate?: string;
  lastName?: string;
  email?: string;
}

/**
 * Create car request
 * Maps to Angular VehicleInfo
 */
interface CreateCarRequestDto {
  licensePlate: string;
  mileage: number;
  vin: string;
  brandId?: string;
}

/**
 * Create appointment request
 */
interface CreateAppointmentRequestDto {
  branchId: string;
  snapId: string;                  // Selected time slot from Snap response
  customerId: string;              // Customer vault item ID
  carId: string;                   // Car ID
  jobTypeIds: string[];            // Selected services
  operationIds: string[];          // Selected service options
  note?: string;                   // Booking note (REQ-005)
  mobilityOption?: string;         // Mobility alternative
  appointmentPreference?: string;  // Anytime, morning, afternoon
  callbackOption?: string;         // Callback request
}

/**
 * Appointment response
 */
interface AppointmentResponseDto {
  id: string;                      // Booking ID
  status: string;
  date: string;
  time: string;
  branchName: string;
  services: string[];
  customerName: string;
  licensePlate: string;
}

/**
 * Standard error response
 */
interface ErrorResponseDto {
  statusCode: number;
  messageKey: string;              // i18n key for frontend translation
  message: string;                 // Fallback message (English)
  details?: ValidationErrorDto[];
  retryAfter?: number;             // For 429 responses
}

interface ValidationErrorDto {
  field: string;
  messageKey: string;
  message: string;
}

/**
 * Health check response
 */
interface HealthCheckResponseDto {
  status: 'healthy' | 'degraded' | 'unhealthy';
  uptime: number;                  // Seconds
  timestamp: string;               // ISO 8601
  dependencies: {
    softnet: {
      status: 'healthy' | 'unhealthy';
      responseTime: number;        // ms
    };
  };
}
```

### Relation to Existing Angular Models

Die bestehenden Angular-Frontend-Models werden vom Backend-Response auf die Frontend-Interfaces gemappt:

| Backend DTO | Angular Model | Datei |
|------------|---------------|-------|
| `BrandResponseDto` | `BrandDisplay` | `src/app/features/booking/models/brand.model.ts` |
| `JobTypeResponseDto` | `ServiceDisplay` | `src/app/features/booking/models/service.model.ts` |
| `SnapResponseDto` | `AppointmentSlot` / `WorkshopCalendarDay` | `src/app/features/booking/models/appointment.model.ts` / `workshop-calendar.model.ts` |
| `CreateCustomerRequestDto` | `CustomerInfo` | `src/app/features/booking/models/customer.model.ts` |
| `CreateCarRequestDto` | `VehicleInfo` | `src/app/features/booking/models/customer.model.ts` |
| `AppointmentResponseDto` | (neu: `BookingConfirmation`) | Zu erstellen |

---

## 11. REST-API-Response-Dokumentation

> UI/UX entfaellt (Backend-Requirement). Stattdessen: REST-API-Response-Formate.

### Standard-Response-Envelope

Alle API-Responses folgen einem einheitlichen Format:

**Success Response:**
```json
{
  "data": { },
  "meta": {
    "timestamp": "2026-04-02T10:30:00.000Z",
    "requestId": "uuid-v4"
  }
}
```

**Error Response:**
```json
{
  "statusCode": 400,
  "messageKey": "error.validationFailed",
  "message": "Validation failed",
  "details": [
    {
      "field": "email",
      "messageKey": "error.validation.invalidEmail",
      "message": "Invalid email format"
    }
  ],
  "meta": {
    "timestamp": "2026-04-02T10:30:00.000Z",
    "requestId": "uuid-v4"
  }
}
```

### Response-Codes

| Code | Bedeutung | Anwendungsfall |
|------|-----------|---------------|
| 200 | OK | Erfolgreiche GET-Anfrage |
| 201 | Created | Erfolgreiches POST (Appointment, Car, Customer) |
| 400 | Bad Request | Validation-Fehler im Request-Body |
| 403 | Forbidden | Ungueltiges oder fehlendes Session-Token |
| 404 | Not Found | Ressource nicht gefunden (z.B. Appointment-ID) |
| 429 | Too Many Requests | Rate Limit ueberschritten |
| 502 | Bad Gateway | SoftNet API liefert 5xx-Fehler |
| 503 | Service Unavailable | SoftNet API nicht erreichbar |
| 504 | Gateway Timeout | SoftNet API Timeout (> 10s) |

---

## 12. API Specification

### 12.1 SoftNet API Endpunkte (Extern — alle extrahiert)

Base-URL: `https://api.soft-nrg.com` (Production) / `https://int-api.soft-nrg.com` (Integration)
Auth: `access_token` (Bearer Token im Header)

| Nr | Method | Endpunkt | Beschreibung |
|----|--------|----------|-------------|
| 1 | GET | `/api/planning/groups` | Alle Gruppen (Autohausgruppen) abrufen |
| 2 | GET | `/api/planning/groups/{groupid}` | Einzelne Gruppe nach ID |
| 3 | GET | `/api/planning/branches` | Alle Filialen abrufen |
| 4 | GET | `/api/planning/branches/{branchid}` | Einzelne Filiale nach ID |
| 5 | GET | `/api/planning/branches/{branchid}/group/{groupid}` | Filiale innerhalb einer Gruppe |
| 6 | GET | `/api/planning/brands` | Fahrzeugmarken abrufen |
| 7 | GET | `/api/planning/jobtypes` | Service-Typen (Auftragsarten) abrufen |
| 8 | GET | `/api/planning/operations` | Alle Operationen (Service-Optionen) abrufen |
| 9 | GET | `/api/planning/operations/{id}` | Einzelne Operation nach ID |
| 10 | GET | `/api/planning/tags` | Tags (Kategorien/Labels) abrufen |
| 11 | GET | `/api/planning/tags/{id}` | Einzelnen Tag nach ID |
| 12 | GET | `/api/planning/rentcategories` | Mietwagen-Kategorien abrufen |
| 13 | GET | `/api/planning/mobilityalternatives` | Mobilitaetsalternativen abrufen |
| 14 | GET | `/api/planning/fields` | Konfigurierbare Felder (Custom Fields) abrufen |
| 15 | GET | `/api/planning/users` | Benutzer abrufen |
| 16 | GET | `/api/planning/users/myprofile` | Eigenes Benutzerprofil abrufen |
| 17 | GET | `/api/planning/users/canmakeappointment` | Pruefen ob Benutzer Termine erstellen darf |
| 18 | POST | `/api/planning/snaps` | Zeitslots (Snaps) abfragen |
| 19 | POST | `/api/planning/appointments` | Termin erstellen (Buchung) |
| 20 | GET | `/api/planning/appointments` | Termine abrufen (Liste) |
| 21 | GET | `/api/planning/appointments/{id}` | Einzelnen Termin nach ID |
| 22 | GET | `/api/planning/appointments/{id}/category` | Termin-Kategorie abrufen |
| 23 | PUT | `/api/planning/appointments/{id}/cancel` | Termin stornieren |
| 24 | POST | `/api/planning/images` | Bild hochladen |
| 25 | GET | `/api/planning/images/{id}` | Bild abrufen |
| 26 | DELETE | `/api/planning/images/{id}` | Bild loeschen |
| 27 | GET | `/api/planning/thumbnails/{id}` | Thumbnail abrufen |
| 28 | POST | `/api/planning/cars` | Fahrzeug anlegen |
| 29 | GET | `/api/planning/cars` | Fahrzeuge abrufen |
| 30 | PUT | `/api/planning/cars/{id}` | Fahrzeug aktualisieren |
| 31 | DELETE | `/api/planning/cars/{id}` | Fahrzeug loeschen |
| 32 | POST | `/api/planning/vaultitems` | Kundendatensatz anlegen |
| 33 | GET | `/api/planning/vaultitems` | Kundendatensaetze abrufen/suchen |
| 34 | PUT | `/api/planning/vaultitems/{id}` | Kundendatensatz aktualisieren |
| 35 | DELETE | `/api/planning/vaultitems/{id}` | Kundendatensatz loeschen |
| 36 | GET | `/api/planning/fleetcategory` | Fuhrpark-Kategorien abrufen |
| 37 | GET | `/api/planning/fleetcategory/{id}` | Einzelne Fuhrpark-Kategorie nach ID |

### 12.2 Backend-eigene API-Endpunkte (fuer Angular-Frontend)

Base-Path: `/api/v1`

#### Session Management

```http
GET /api/v1/session/token
```
**Response (200):**
```json
{
  "data": {
    "token": "hmac-sha256-signed-token-string",
    "expiresAt": "2026-04-02T11:00:00.000Z"
  }
}
```
> Generiert HMAC-basiertes Session-Token (20 Min TTL). Kein Request-Body noetig.

#### Health Check

```http
GET /api/v1/health
```
**Response (200):**
```json
{
  "data": {
    "status": "healthy",
    "uptime": 3600,
    "timestamp": "2026-04-02T10:30:00.000Z",
    "dependencies": {
      "softnet": {
        "status": "healthy",
        "responseTime": 120
      }
    }
  }
}
```

#### Branches (Filialen)

```http
GET /api/v1/branches
```
**Response (200):**
```json
{
  "data": [
    {
      "id": "branch-uuid",
      "name": "Autohaus Mitte",
      "description": "Filiale in der Innenstadt",
      "brands": ["audi", "volkswagen"]
    }
  ]
}
```

```http
GET /api/v1/branches/:branchId
```
**Response (200):** Einzelne Branch mit Details.

#### Brands (Marken)

```http
GET /api/v1/branches/:branchId/brands
```
**Response (200):**
```json
{
  "data": [
    { "id": "brand-uuid", "name": "Audi" },
    { "id": "brand-uuid", "name": "BMW" }
  ]
}
```
> Proxy zu SoftNet `GET /api/planning/brands`, gefiltert nach Branch.

#### JobTypes (Services)

```http
GET /api/v1/branches/:branchId/jobtypes
```
**Response (200):**
```json
{
  "data": [
    {
      "id": "jobtype-uuid",
      "name": "Inspektion",
      "description": "Regulaere Fahrzeuginspektion",
      "operations": [
        { "id": "op-uuid", "name": "Dialogannahme" },
        { "id": "op-uuid", "name": "Oelwechsel" }
      ]
    }
  ]
}
```
> Proxy zu SoftNet `GET /api/planning/jobtypes` + `GET /api/planning/operations`.

#### Snaps (Zeitslots)

```http
POST /api/v1/snaps
Content-Type: application/json
X-Session-Token: hmac-token

{
  "branchId": "branch-uuid",
  "jobTypeIds": ["jobtype-1", "jobtype-2"],
  "fromDate": "2026-04-03",
  "toDate": "2026-04-06",
  "mode": "next-available"
}
```
**Response (200):**
```json
{
  "data": [
    {
      "date": "2026-04-03",
      "displayDate": "03.04.2026",
      "dayAbbreviation": "Fr",
      "slots": [
        {
          "id": "snap-uuid",
          "time": "09:00",
          "displayTime": "09:00 Uhr",
          "available": true
        }
      ]
    }
  ]
}
```
> Proxy zu SoftNet `POST /api/planning/snaps`. Modus `next-available` holt naechsten Slot, `date-range` holt alle Slots in +3-Tage-Bereich.

#### Mobility Alternatives

```http
GET /api/v1/branches/:branchId/mobility-alternatives
```
**Response (200):**
```json
{
  "data": [
    { "id": "alt-uuid", "name": "Leihwagen Kompakt" }
  ]
}
```
> Proxy zu SoftNet `GET /api/planning/mobilityalternatives`.

#### Customers (Vault Items)

```http
POST /api/v1/customers
Content-Type: application/json
X-Session-Token: hmac-token

{
  "email": "max@example.com",
  "salutation": "mr",
  "firstName": "Max",
  "lastName": "Mustermann",
  "street": "Hauptstrasse 1",
  "postalCode": "40213",
  "city": "Duesseldorf",
  "mobilePhone": "01721234567"
}
```
**Response (201):**
```json
{
  "data": {
    "id": "customer-uuid",
    "email": "max@example.com",
    "firstName": "Max",
    "lastName": "Mustermann"
  }
}
```
> Proxy zu SoftNet `POST /api/planning/vaultitems`.

```http
GET /api/v1/customers/search?licensePlate=DU-AB1234&lastName=Mustermann&email=max@example.com
X-Session-Token: hmac-token
```
**Response (200):**
```json
{
  "data": [
    {
      "id": "customer-uuid",
      "firstName": "Max",
      "lastName": "Mustermann",
      "email": "max@example.com"
    }
  ]
}
```
> Proxy zu SoftNet `GET /api/planning/vaultitems` mit Query-Parametern.

#### Cars (Fahrzeuge)

```http
POST /api/v1/cars
Content-Type: application/json
X-Session-Token: hmac-token

{
  "licensePlate": "DU-AB1234",
  "mileage": 45000,
  "vin": "WVWZZZ3CZWE123456",
  "brandId": "brand-uuid"
}
```
**Response (201):**
```json
{
  "data": {
    "id": "car-uuid",
    "licensePlate": "DU-AB1234"
  }
}
```
> Proxy zu SoftNet `POST /api/planning/cars`.

#### Appointments (Termine)

```http
POST /api/v1/appointments
Content-Type: application/json
X-Session-Token: hmac-token

{
  "branchId": "branch-uuid",
  "snapId": "snap-uuid",
  "customerId": "customer-uuid",
  "carId": "car-uuid",
  "jobTypeIds": ["jobtype-1"],
  "operationIds": ["op-1", "op-2"],
  "note": "Bitte Klimaanlage pruefen",
  "mobilityOption": "compact-car",
  "appointmentPreference": "morning",
  "callbackOption": "yes"
}
```
**Response (201):**
```json
{
  "data": {
    "id": "appointment-uuid",
    "status": "confirmed",
    "date": "2026-04-03",
    "time": "09:00",
    "branchName": "Autohaus Mitte",
    "services": ["Inspektion"],
    "customerName": "Max Mustermann",
    "licensePlate": "DU-AB1234"
  }
}
```
> Proxy zu SoftNet `POST /api/planning/appointments`.

```http
GET /api/v1/appointments/:id
X-Session-Token: hmac-token
```
**Response (200):** Termin-Details (Status, Datum, Services, Kundendaten).

```http
PUT /api/v1/appointments/:id/cancel
X-Session-Token: hmac-token
```
**Response (200):** Termin wird storniert.

---

## 13. Test Cases

### Unit Tests (Jest)

#### TC-1: SoftNetApiService — Brands abrufen
- **Given:** SoftNet API ist erreichbar, access_token ist gueltig
- **When:** `getBrands(branchId)` wird aufgerufen
- **Then:** Transformierte Brand-Liste wird zurueckgegeben

#### TC-2: SoftNetApiService — Timeout-Handling
- **Given:** SoftNet API antwortet nicht innerhalb von 10s
- **When:** Beliebiger API-Call wird ausgefuehrt
- **Then:** GatewayTimeoutException wird geworfen

#### TC-3: SessionTokenService — Token generieren
- **Given:** Server-Secret ist konfiguriert
- **When:** `generateToken()` wird aufgerufen
- **Then:** HMAC-signierter Token mit korrektem Timestamp wird generiert

#### TC-4: SessionTokenService — Token validieren (gueltig)
- **Given:** Token wurde vor 5 Minuten generiert
- **When:** `validateToken(token)` wird aufgerufen
- **Then:** Validation gibt `true` zurueck

#### TC-5: SessionTokenService — Token validieren (abgelaufen)
- **Given:** Token wurde vor 25 Minuten generiert
- **When:** `validateToken(token)` wird aufgerufen
- **Then:** Validation gibt `false` zurueck

#### TC-6: SessionTokenService — Token validieren (manipuliert)
- **Given:** Token-Payload wurde veraendert
- **When:** `validateToken(token)` wird aufgerufen
- **Then:** HMAC-Verifikation schlaegt fehl, gibt `false` zurueck

#### TC-7: SnapsController — Valider Request
- **Given:** Gueltiger SnapRequestDto
- **When:** `POST /api/v1/snaps` mit gueltigem Body
- **Then:** 200 OK mit Snap-Daten

#### TC-8: SnapsController — Invalider Request (fehlende Felder)
- **Given:** SnapRequestDto ohne `branchId`
- **When:** `POST /api/v1/snaps` mit unvollstaendigem Body
- **Then:** 400 Bad Request mit Validation-Details

#### TC-9: AppointmentsController — Termin erstellen
- **Given:** Gueltiger CreateAppointmentRequestDto und alle referenzierten IDs existieren
- **When:** `POST /api/v1/appointments` mit vollstaendigem Body
- **Then:** 201 Created mit Appointment-ID

#### TC-10: ThrottlerGuard — Rate Limit
- **Given:** 100 Requests wurden innerhalb von 15 Minuten gesendet
- **When:** 101. Request wird gesendet
- **Then:** 429 Too Many Requests mit Retry-After Header

#### TC-11: SessionTokenGuard — Fehlender Token
- **Given:** Request ohne `X-Session-Token` Header
- **When:** Geschuetzter Endpunkt wird aufgerufen
- **Then:** 403 Forbidden

#### TC-12: HealthController — SoftNet erreichbar
- **Given:** SoftNet API antwortet normal
- **When:** `GET /api/v1/health`
- **Then:** Status `healthy` mit SoftNet-Response-Time

#### TC-13: HealthController — SoftNet nicht erreichbar
- **Given:** SoftNet API antwortet nicht
- **When:** `GET /api/v1/health`
- **Then:** Status `degraded`, SoftNet-Status `unhealthy`

### E2E Tests (Supertest)

#### TC-E2E-1: Kompletter Buchungsflow
- **Given:** Backend laeuft, SoftNet-Mock aktiv
- **When:** Session-Token holen, Brands laden, JobTypes laden, Snaps abfragen, Customer anlegen, Car anlegen, Appointment erstellen
- **Then:** Jeder Step gibt korrekten HTTP-Status und Daten zurueck, Appointment-ID wird zurueckgegeben

#### TC-E2E-2: Static File Serving
- **Given:** Angular-Build existiert in `dist/`
- **When:** `GET /` aufgerufen
- **Then:** `index.html` wird ausgeliefert mit korrektem Content-Type

#### TC-E2E-3: SPA-Fallback
- **Given:** Angular-Build existiert in `dist/`
- **When:** `GET /home/brand` aufgerufen (Angular-Route, kein File)
- **Then:** `index.html` wird ausgeliefert (SPA-Fallback)

#### TC-E2E-4: Security Headers
- **Given:** Backend laeuft
- **When:** Beliebiger Request
- **Then:** Helmet.js-Headers sind gesetzt (X-Content-Type-Options, CSP mit frame-ancestors)

#### TC-E2E-5: iFrame-Einbettung mit Branch-ID
- **Given:** Backend laeuft, `IFRAME_ALLOWED_ORIGINS` ist konfiguriert
- **When:** Request mit `Origin`-Header einer erlaubten Domain
- **Then:** Response enthaelt `Content-Security-Policy: frame-ancestors` mit der konfigurierten Domain

#### TC-E2E-6: Branch-ID per Query-Parameter
- **Given:** Backend laeuft, Angular-App geladen mit `?branchId=validId`
- **When:** App startet
- **Then:** Standortauswahl wird uebersprungen, Wizard beginnt bei Markenauswahl, API-Calls verwenden uebergebene Branch-ID

#### TC-E2E-7: Origin Guard blockiert externe Requests
- **Given:** Backend laeuft
- **When:** API-Request ohne Origin/Referer Header von externer IP (curl, Postman)
- **Then:** Response `403 Forbidden` mit Fehlermeldung "Request origin is not authorized"

#### TC-E2E-8: Origin Guard erlaubt Frontend-Requests
- **Given:** Backend laeuft, Angular-App im Browser geladen
- **When:** API-Request mit Origin `http://localhost:3000`
- **Then:** Request wird normal verarbeitet

#### TC-E2E-9: Circuit Breaker im Frontend
- **Given:** Backend nicht erreichbar (oder 5x hintereinander 5xx-Fehler)
- **When:** Frontend sendet weitere API-Requests
- **Then:** Requests werden sofort mit Status 503 abgelehnt (ohne Backend-Call), nach 30s wird erneut probiert

#### TC-E2E-10: Playwright E2E mit Backend
- **Given:** Angular-App gebaut, NestJS Backend laeuft auf Port 3000
- **When:** `npm run e2e:backend` ausgefuehrt
- **Then:** Alle bestehenden Playwright-Tests laufen erfolgreich gegen das Backend (statt Angular Dev-Server)

---

## 14. Implementation

### NestJS Module

- [ ] `AppModule` — Root-Modul mit ConfigModule, ThrottlerModule, ServeStaticModule
- [ ] `SoftnetModule` — SoftNet API Client (HttpModule, SoftnetApiService)
- [ ] `BranchesModule` — Branch-Endpunkte (Controller, Service)
- [ ] `BrandsModule` — Brand-Endpunkte (Controller, Service)
- [ ] `JobTypesModule` — JobType/Service-Endpunkte (Controller, Service)
- [ ] `SnapsModule` — Snap/Zeitslot-Endpunkte (Controller, Service)
- [ ] `AppointmentsModule` — Termin-Endpunkte (Controller, Service)
- [ ] `CustomersModule` — Kunden-Endpunkte (Controller, Service)
- [ ] `CarsModule` — Fahrzeug-Endpunkte (Controller, Service)
- [ ] `SessionModule` — Session-Token (Controller, Service, Guard)
- [ ] `HealthModule` — Health-Check (Controller, Service)

### Controllers

- [ ] `BranchesController` — `GET /api/v1/branches`, `GET /api/v1/branches/:id`
- [ ] `BrandsController` — `GET /api/v1/branches/:branchId/brands`
- [ ] `JobTypesController` — `GET /api/v1/branches/:branchId/jobtypes`
- [ ] `SnapsController` — `POST /api/v1/snaps`
- [ ] `AppointmentsController` — `POST /api/v1/appointments`, `GET /api/v1/appointments/:id`, `PUT /api/v1/appointments/:id/cancel`
- [ ] `CustomersController` — `POST /api/v1/customers`, `GET /api/v1/customers/search`
- [ ] `CarsController` — `POST /api/v1/cars`
- [ ] `SessionController` — `GET /api/v1/session/token`
- [ ] `HealthController` — `GET /api/v1/health`
- [ ] `MobilityController` — `GET /api/v1/branches/:branchId/mobility-alternatives`

### Services

- [ ] `SoftnetApiService` — Zentrale SoftNet HTTP-Client-Klasse (alle 37 Endpunkte)
- [ ] `BranchesService` — Branch-Logik + SoftNet-Mapping
- [ ] `BrandsService` — Brand-Logik + SoftNet-Mapping
- [ ] `JobTypesService` — JobType-Logik + SoftNet-Mapping (inkl. Operations)
- [ ] `SnapsService` — Snap-Logik (Modi: next-available, date-range)
- [ ] `AppointmentsService` — Termin-Logik (Create, Get, Cancel)
- [ ] `CustomersService` — Kunden-Logik (Create, Search)
- [ ] `CarsService` — Fahrzeug-Logik (Create)
- [ ] `SessionTokenService` — HMAC-Token-Generierung und -Validierung
- [ ] `HealthService` — Health-Check-Logik (SoftNet-Ping)
- [ ] `MobilityService` — Mobilitaetsalternativen-Logik

### Guards

- [ ] `SessionTokenGuard` — Validiert `X-Session-Token` Header auf allen geschuetzten Endpunkten
- [ ] `ThrottlerGuard` (von @nestjs/throttler) — Rate Limiting

### Middleware

- [ ] `RequestIdMiddleware` — Generiert UUID-v4 Request-ID fuer Logging und Response-Headers
- [ ] `LoggingMiddleware` — Loggt eingehende Requests (Method, URL, Duration, Status)

### Interceptors

- [ ] `TransformInterceptor` — Wrapping aller Responses in Standard-Envelope (`{ data, meta }`)
- [ ] `TimeoutInterceptor` — Globaler Request-Timeout (30s Backend-intern)

### Filters

- [ ] `AllExceptionsFilter` — Globaler Exception-Handler, transformiert alle Fehler in `ErrorResponseDto`
- [ ] `SoftnetExceptionFilter` — Spezifischer Handler fuer SoftNet-API-Fehler (Mapping auf Backend-Fehler-Codes)

### Pipes

- [ ] `ValidationPipe` (global) — class-validator + class-transformer fuer alle eingehenden DTOs

### Folder Structure

```
backend/
├── src/
│   ├── main.ts                           # Bootstrap (Helmet, CORS, ValidationPipe, TLS)
│   ├── app.module.ts                     # Root module
│   ├── config/
│   │   ├── environment.config.ts         # ConfigModule registration
│   │   └── environment.validation.ts     # Joi/class-validator schema for env vars
│   ├── common/
│   │   ├── dto/
│   │   │   ├── error-response.dto.ts
│   │   │   └── paginated-response.dto.ts
│   │   ├── filters/
│   │   │   ├── all-exceptions.filter.ts
│   │   │   └── softnet-exception.filter.ts
│   │   ├── guards/
│   │   │   └── session-token.guard.ts
│   │   ├── interceptors/
│   │   │   ├── transform.interceptor.ts
│   │   │   └── timeout.interceptor.ts
│   │   ├── middleware/
│   │   │   ├── request-id.middleware.ts
│   │   │   └── logging.middleware.ts
│   │   └── pipes/
│   │       └── validation.pipe.ts
│   ├── softnet/
│   │   ├── softnet.module.ts
│   │   ├── softnet-api.service.ts        # Central SoftNet HTTP client
│   │   └── softnet-api.types.ts          # SoftNet raw response types
│   ├── branches/
│   │   ├── branches.module.ts
│   │   ├── branches.controller.ts
│   │   ├── branches.service.ts
│   │   └── dto/
│   │       └── branch-response.dto.ts
│   ├── brands/
│   │   ├── brands.module.ts
│   │   ├── brands.controller.ts
│   │   ├── brands.service.ts
│   │   └── dto/
│   │       └── brand-response.dto.ts
│   ├── jobtypes/
│   │   ├── jobtypes.module.ts
│   │   ├── jobtypes.controller.ts
│   │   ├── jobtypes.service.ts
│   │   └── dto/
│   │       ├── jobtype-response.dto.ts
│   │       └── operation-response.dto.ts
│   ├── snaps/
│   │   ├── snaps.module.ts
│   │   ├── snaps.controller.ts
│   │   ├── snaps.service.ts
│   │   └── dto/
│   │       ├── snap-request.dto.ts
│   │       └── snap-response.dto.ts
│   ├── appointments/
│   │   ├── appointments.module.ts
│   │   ├── appointments.controller.ts
│   │   ├── appointments.service.ts
│   │   └── dto/
│   │       ├── create-appointment-request.dto.ts
│   │       └── appointment-response.dto.ts
│   ├── customers/
│   │   ├── customers.module.ts
│   │   ├── customers.controller.ts
│   │   ├── customers.service.ts
│   │   └── dto/
│   │       ├── create-customer-request.dto.ts
│   │       ├── search-customer-request.dto.ts
│   │       └── customer-response.dto.ts
│   ├── cars/
│   │   ├── cars.module.ts
│   │   ├── cars.controller.ts
│   │   ├── cars.service.ts
│   │   └── dto/
│   │       ├── create-car-request.dto.ts
│   │       └── car-response.dto.ts
│   ├── session/
│   │   ├── session.module.ts
│   │   ├── session.controller.ts
│   │   └── session-token.service.ts
│   ├── mobility/
│   │   ├── mobility.module.ts
│   │   ├── mobility.controller.ts
│   │   ├── mobility.service.ts
│   │   └── dto/
│   │       └── mobility-response.dto.ts
│   └── health/
│       ├── health.module.ts
│       ├── health.controller.ts
│       └── health.service.ts
├── test/
│   ├── app.e2e-spec.ts                  # E2E tests (Supertest)
│   ├── softnet-api.service.spec.ts      # SoftNet API Service unit tests
│   ├── session-token.service.spec.ts    # Session token unit tests
│   └── mocks/
│       └── softnet-mock.service.ts      # SoftNet API mock for testing
├── .env                                  # Environment variables (gitignored)
├── .env.example                          # Environment template
├── nest-cli.json
├── package.json
└── tsconfig.json
```

### Effort
- Development: 40 hours
- Testing: 16 hours
- Documentation: 4 hours

---

## 15. Dependencies

**Requires:**
- Node.js >= 20 LTS
- SoftNet API access_token (serverseitig konfiguriert)
- Angular-App Build Output (`dist/` Verzeichnis)
- REQ-001 bis REQ-010: Bestehender Angular-Frontend-Code als Client

**NPM Dependencies:**
- `@nestjs/core`, `@nestjs/common`, `@nestjs/platform-express` — NestJS Core
- `@nestjs/config` — Environment-Konfiguration
- `@nestjs/throttler` — Rate Limiting
- `@nestjs/serve-static` — Statisches File Serving
- `@nestjs/axios` / `axios` — HTTP Client fuer SoftNet API
- `helmet` — HTTP Security Headers
- `class-validator`, `class-transformer` — Input Validation / DTO Transformation
- `uuid` — Request-ID Generierung
- `@nestjs/testing`, `supertest` — Testing

**Blocks:**
- Alle zukuenftigen Backend-abhaengigen Features (echte API-Calls statt Click-Dummy)
- Angular-Frontend-Migration von statischen Daten zu echtem Backend

---

## 16. Naming Glossary

### Controllers
| Name | Route-Prefix | Beschreibung |
|------|-------------|-------------|
| `BranchesController` | `/api/v1/branches` | Filialen abrufen |
| `BrandsController` | `/api/v1/branches/:branchId/brands` | Marken pro Filiale |
| `JobTypesController` | `/api/v1/branches/:branchId/jobtypes` | Services pro Filiale |
| `SnapsController` | `/api/v1/snaps` | Zeitslots abfragen |
| `AppointmentsController` | `/api/v1/appointments` | Termine (CRUD) |
| `CustomersController` | `/api/v1/customers` | Kunden (Create, Search) |
| `CarsController` | `/api/v1/cars` | Fahrzeuge (Create) |
| `SessionController` | `/api/v1/session` | Session-Token |
| `HealthController` | `/api/v1/health` | Health-Check |
| `MobilityController` | `/api/v1/branches/:branchId/mobility-alternatives` | Mobilitaetsalternativen |

### Services
| Name | Beschreibung |
|------|-------------|
| `SoftnetApiService` | Zentraler HTTP-Client fuer SoftNet API (alle 37 Endpunkte) |
| `BranchesService` | Branch-Daten abrufen und transformieren |
| `BrandsService` | Brand-Daten abrufen und transformieren |
| `JobTypesService` | JobType + Operations abrufen und zusammenfuehren |
| `SnapsService` | Snap-Abfragen (next-available, date-range Modus) |
| `AppointmentsService` | Termine erstellen, abrufen, stornieren |
| `CustomersService` | Kunden anlegen und per Kennzeichen/Name/E-Mail suchen |
| `CarsService` | Fahrzeuge anlegen |
| `SessionTokenService` | HMAC-SHA256-Token generieren und validieren |
| `HealthService` | Health-Check (SoftNet-Ping, Uptime) |
| `MobilityService` | Mobilitaetsalternativen abrufen |

### Guards
| Name | Beschreibung |
|------|-------------|
| `SessionTokenGuard` | Validiert X-Session-Token Header (HMAC + TTL) |

### Middleware
| Name | Beschreibung |
|------|-------------|
| `RequestIdMiddleware` | Generiert UUID-v4 Request-ID |
| `LoggingMiddleware` | Loggt Request Method, URL, Duration, Status |

### Interceptors
| Name | Beschreibung |
|------|-------------|
| `TransformInterceptor` | Wrapping in Standard-Envelope `{ data, meta }` |
| `TimeoutInterceptor` | Globaler 30s Timeout |

### Filters
| Name | Beschreibung |
|------|-------------|
| `AllExceptionsFilter` | Globaler Exception-Handler, strukturierte Fehler-Response |
| `SoftnetExceptionFilter` | SoftNet-spezifisches Error-Mapping (4xx/5xx) |

### DTOs
| Name | Richtung | Beschreibung |
|------|----------|-------------|
| `SnapRequestDto` | Request | Zeitslot-Abfrage |
| `CreateAppointmentRequestDto` | Request | Termin erstellen |
| `CreateCustomerRequestDto` | Request | Kunde anlegen |
| `CreateCarRequestDto` | Request | Fahrzeug anlegen |
| `SearchCustomerRequestDto` | Request | Kunden-Suche |
| `BranchResponseDto` | Response | Filial-Daten |
| `BrandResponseDto` | Response | Marken-Daten |
| `JobTypeResponseDto` | Response | Service-Daten |
| `SnapResponseDto` | Response | Zeitslot-Daten |
| `AppointmentResponseDto` | Response | Termin-Daten |
| `SessionTokenResponseDto` | Response | Session-Token |
| `HealthCheckResponseDto` | Response | Health-Status |
| `ErrorResponseDto` | Response | Fehler-Daten |

---

## 17. i18n Keys

```typescript
// DE — Backend-Fehlermeldungen (fuer API-Responses)
backend: {
  error: {
    rateLimitExceeded: 'Zu viele Anfragen. Bitte versuchen Sie es in {{retryAfter}} Sekunden erneut.',
    invalidSessionToken: 'Ihre Sitzung ist abgelaufen. Bitte laden Sie die Seite neu.',
    upstreamTimeout: 'Der Service ist voruebergehend nicht erreichbar. Bitte versuchen Sie es spaeter erneut.',
    upstreamError: 'Ein Serverfehler ist aufgetreten. Bitte versuchen Sie es spaeter erneut.',
    serviceUnavailable: 'Der Service ist voruebergehend nicht verfuegbar. Bitte versuchen Sie es spaeter erneut.',
    validationFailed: 'Die eingegebenen Daten sind ungueltig. Bitte pruefen Sie Ihre Eingaben.',
    notFound: 'Die angeforderte Ressource wurde nicht gefunden.',
    internalError: 'Ein unerwarteter Fehler ist aufgetreten. Bitte versuchen Sie es spaeter erneut.',
    validation: {
      required: 'Dieses Feld ist erforderlich.',
      invalidEmail: 'Bitte geben Sie eine gueltige E-Mail-Adresse ein.',
      invalidDate: 'Bitte geben Sie ein gueltiges Datum ein (YYYY-MM-DD).',
      invalidLicensePlate: 'Bitte geben Sie ein gueltiges Kfz-Kennzeichen ein.',
      invalidVin: 'Die FIN muss genau 17 alphanumerische Zeichen enthalten.',
      invalidMileage: 'Der Kilometerstand muss eine positive Zahl sein.',
      invalidPostalCode: 'Bitte geben Sie eine gueltige Postleitzahl ein.',
      invalidPhone: 'Bitte geben Sie eine gueltige Mobilfunknummer ein.',
      invalidBranchId: 'Die angegebene Filiale ist ungueltig.',
      invalidSnapMode: 'Der Abfragemodus muss "next-available" oder "date-range" sein.'
    }
  },
  health: {
    healthy: 'Alle Systeme betriebsbereit.',
    degraded: 'System eingeschraenkt verfuegbar.',
    unhealthy: 'System nicht verfuegbar.'
  }
}

// EN — Backend error messages (for API responses)
backend: {
  error: {
    rateLimitExceeded: 'Too many requests. Please try again in {{retryAfter}} seconds.',
    invalidSessionToken: 'Your session has expired. Please reload the page.',
    upstreamTimeout: 'The service is temporarily unavailable. Please try again later.',
    upstreamError: 'A server error occurred. Please try again later.',
    serviceUnavailable: 'The service is temporarily unavailable. Please try again later.',
    validationFailed: 'The submitted data is invalid. Please check your input.',
    notFound: 'The requested resource was not found.',
    internalError: 'An unexpected error occurred. Please try again later.',
    validation: {
      required: 'This field is required.',
      invalidEmail: 'Please enter a valid email address.',
      invalidDate: 'Please enter a valid date (YYYY-MM-DD).',
      invalidLicensePlate: 'Please enter a valid license plate number.',
      invalidVin: 'The VIN must contain exactly 17 alphanumeric characters.',
      invalidMileage: 'Mileage must be a positive number.',
      invalidPostalCode: 'Please enter a valid postal code.',
      invalidPhone: 'Please enter a valid mobile phone number.',
      invalidBranchId: 'The specified branch is invalid.',
      invalidSnapMode: 'The query mode must be "next-available" or "date-range".'
    }
  },
  health: {
    healthy: 'All systems operational.',
    degraded: 'System partially available.',
    unhealthy: 'System unavailable.'
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

## 19. Security-Konzept (Detailliert)

### Warum dieses Konzept?

Die API ist oeffentlich zugaenglich (keine User-Authentifizierung), aber sie muss gegen Missbrauch (DDoS, Scraping, Automated Abuse) geschuetzt werden. Das folgende mehrstufige Konzept kombiniert bewaehrte Methoden fuer genau diesen Anwendungsfall:

### Schicht 1: Rate Limiting (@nestjs/throttler)

**Was:** IP-basierte Request-Begrenzung auf 100 Requests pro 15 Minuten.

**Warum:** Einfachste und effektivste Verteidigungslinie gegen Brute-Force und einfache DDoS-Angriffe. IP-basiert, weil ohne User-Auth keine bessere Identifizierung moeglich ist. 100/15min ist grosszuegig genug fuer normales Benutzerverhalten (ein Wizard-Durchlauf benoetigt ca. 8-12 API-Calls), aber restriktiv genug gegen Automated Abuse.

**Konfiguration:**
```typescript
ThrottlerModule.forRoot([{
  ttl: 900000,  // 15 Minuten in ms
  limit: 100    // Max 100 Requests
}])
```

### Schicht 2: Helmet.js (HTTP Security Headers)

**Was:** Automatische Setzung sicherheitsrelevanter HTTP-Headers.

**Warum:** Schutz gegen gaengige Web-Angriffe (XSS, Clickjacking, MIME-Sniffing) auf der Transport-Ebene. Kostenlos, kein Performance-Impact, Industry Standard.

**Headers:**
- `X-Frame-Options` — NICHT gesetzt (wird durch CSP `frame-ancestors` ersetzt, da iFrame-Einbettung unterstuetzt wird)
- `Content-Security-Policy: frame-ancestors <IFRAME_ALLOWED_ORIGINS>` — Erlaubt iFrame-Einbettung nur von konfigurierten Domains. Wenn `IFRAME_ALLOWED_ORIGINS` leer: `frame-ancestors 'self'` (keine Fremd-Einbettung)
- `X-Content-Type-Options: nosniff` — Verhindert MIME-Sniffing
- `Content-Security-Policy` — Beschraenkt zusaetzlich Ressourcen-Quellen (script-src, style-src, etc.)
- `Referrer-Policy: no-referrer` — Keine Referrer-Information an Dritte
- `Strict-Transport-Security` — Erzwingt HTTPS (wenn TLS aktiv)

### Schicht 3: CORS (Cross-Origin Resource Sharing)

**Was:** Nur konfigurierte Origins duerfen API-Requests senden.

**Warum:** Verhindert, dass fremde Websites API-Calls von ihrem Frontend aus machen. Wichtig bei oeffentlicher API, weil sonst jede Website die API nutzen koennte.

**Konfiguration:**
```typescript
app.enableCors({
  origin: configService.get('CORS_ALLOWED_ORIGINS').split(','),
  methods: ['GET', 'POST', 'PUT'],
  allowedHeaders: ['Content-Type', 'X-Session-Token'],
  credentials: false
});
```

### Schicht 4: Input Validation (class-validator)

**Was:** Strenge Validierung aller eingehenden Request-Bodies gegen TypeScript DTOs mit Decorators.

**Warum:** Verhindert Injection-Angriffe, Malformed Requests und unerwartete Datentypen. Jeder Endpunkt, der Daten empfaengt, wird validiert, bevor die Daten an SoftNet weitergeleitet werden.

**Beispiel:**
```typescript
class CreateCustomerRequestDto {
  @IsEmail()
  email: string;

  @IsEnum(['mr', 'ms'])
  salutation: string;

  @IsString()
  @MinLength(2)
  @MaxLength(50)
  firstName: string;
}
```

### Schicht 5: HMAC-basiertes Session-Token (Stateless)

**Was:** Bei Page-Load generiert das Frontend ein Session-Token via `GET /api/v1/session/token`. Dieses Token muss bei allen folgenden API-Calls im `X-Session-Token` Header mitgesendet werden. Das Token ist HMAC-SHA256-signiert mit einem Server-Secret und enthaelt einen Timestamp. Es laeuft nach 20 Minuten ab.

**Warum:** Das ist der Kompromiss zwischen der User-Anforderung (Session-Token mit begrenzten Requests/Zeit) und State-of-the-Art (stateless). Im Gegensatz zu serverseitig gespeicherten Sessions benoetigt HMAC keinen Server-State und ist horizontal skalierbar. Der Token verhindert, dass API-Calls direkt (ohne vorheriges Page-Load) gesendet werden, was Simple Bots und Curl-basierte Angriffe erschwert.

**Token-Aufbau:**
```
base64url(timestamp) + "." + base64url(hmac-sha256(timestamp, server-secret))
```

**Validierung (stateless):**
1. Token in Timestamp und HMAC aufteilen
2. HMAC mit Server-Secret und Timestamp neu berechnen und vergleichen
3. Pruefen ob Timestamp innerhalb von 20 Minuten liegt
4. Kein Server-State noetig (kein Redis, keine Datenbank)

**Bewusste Entscheidung gegen Alternativen:**
- **Server-Side Sessions (Redis):** Unnoetige Komplexitaet und Infrastruktur fuer einen Click-Dummy-Kontext.
- **JWT:** Overkill, da keine User-Identitaet transportiert werden muss.
- **Proof-of-Work:** Schlechte User-Experience auf schwachen Geraeten (Mobile).
- **CAPTCHA:** Unterbricht den Wizard-Flow und verschlechtert die UX.

### Schicht 6 (Zukunft): API Abuse Detection

**Was:** IP-Fingerprinting + Anomalie-Erkennung fuer fortgeschrittene Angriffe.

**Warum:** Fuer Phase 1 nicht noetig (Click-Dummy), aber als Erweiterung vorbereitet.

---

## 20. TLS-Konfiguration

### Phase 1: Lokale Entwicklung (ohne TLS)

```typescript
// main.ts
async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);
  // ... Helmet, CORS, etc.
  await app.listen(3000);
}
```

### Phase 2: Production (mit TLS)

```typescript
// main.ts
async function bootstrap(): Promise<void> {
  const configService = app.get(ConfigService);
  const tlsEnabled = configService.get<boolean>('TLS_ENABLED');

  if (tlsEnabled) {
    const httpsOptions = {
      cert: readFileSync(configService.get<string>('TLS_CERT_PATH')),
      key: readFileSync(configService.get<string>('TLS_KEY_PATH'))
    };
    const app = await NestFactory.create(AppModule, { httpsOptions });
    await app.listen(443);
  } else {
    const app = await NestFactory.create(AppModule);
    await app.listen(3000);
  }
}
```

### Environment-Variablen (.env.example)

```env
# Server
PORT=3000

# SoftNet API — READ Stage (GET-Requests: Brands, Branches, JobTypes, etc.)
# Auf Dev: Production API fuer echte Stammdaten; auf Live: Production API
SOFTNET_READ_BASE_URL=https://api.soft-nrg.com
SOFTNET_READ_AUTH_URL=https://auth.soft-nrg.com/oauth/token
SOFTNET_READ_FINGERPRINT_PATH=./config/fingerprint-production.json

# SoftNet API — WRITE Stage (POST/PUT-Requests: Appointments, Customers, Cars, etc.)
# Auf Dev: Integration API (Test-Umgebung); auf Live: Production API
SOFTNET_WRITE_BASE_URL=https://int-api.soft-nrg.com
SOFTNET_WRITE_AUTH_URL=https://int-auth.soft-nrg.com/oauth/token
SOFTNET_WRITE_FINGERPRINT_PATH=./config/fingerprint-integration.json

# SoftNet Allgemein
SOFTNET_TIMEOUT=10000

# Group & Branch (aus SoftNet)
GROUP_ID=698b34c24dd8eea4d6d42179
BRANCH_ID=58341c5cbe856f6dc47e9f05

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:4200,http://localhost:3000

# iFrame Einbettung (komma-separierte erlaubte Eltern-Domains, leer = deaktiviert)
IFRAME_ALLOWED_ORIGINS=

# Session Token
SESSION_SECRET=your-hmac-secret-minimum-32-chars
SESSION_TTL_MINUTES=20

# TLS (optional)
TLS_ENABLED=false
TLS_CERT_PATH=./certs/cert.pem
TLS_KEY_PATH=./certs/key.pem
```

---

## 21. Implementation Notes

**Code-Sprache:** ENGLISCH — Alle Klassen, Methoden, Variablen, Kommentare und DTOs in Englisch.

**Error Messages:** i18n-faehig via `messageKey` in allen Fehler-Responses. Frontend uebersetzt anhand des Keys (DE + EN).

**SoftNet Dual-Stage Routing:** Die `SoftnetApiService`-Klasse verwaltet zwei separate HTTP-Clients — einen fuer die READ-Stage (GET-Requests) und einen fuer die WRITE-Stage (POST/PUT-Requests). Jeder Client hat eigene Base-URL, Auth-URL und Fingerprint-Konfiguration. Die Zuordnung erfolgt automatisch anhand der HTTP-Methode. Auf Dev liest man so echte Stammdaten von Production, schreibt aber nur auf die Integration-Umgebung. Auf Live zeigen beide auf Production.

**SoftNet API Mapping:** Die `SoftnetApiService`-Klasse abstrahiert alle 37 SoftNet-Endpunkte. Jeder Controller-Service nutzt nur die relevanten Methoden. Response-Transformation (Entfernung interner Felder wie `cryptid`, `externalRefNr`) findet in den jeweiligen Service-Klassen statt.

**Angular-Frontend-Anpassung:** Bestehende Click-Dummy-Services (`BookingApiService`, `AppointmentApiService`, `WorkshopCalendarApiService`) muessen angepasst werden, um echte HTTP-Calls an das Backend zu senden statt statische Daten zurueckzugeben. Der `BookingStore` bleibt unveraendert (er ruft weiterhin die API-Services auf).

**Mock-Daten-Migration:** Alle bisherigen Mock-/Testdaten wurden aus dem Angular-Frontend entfernt. Das Backend ruft die echte SoftNet API auf (Integration oder Production, je nach `.env`-Konfiguration). Es gibt KEINEN Mock-Modus mehr — weder im Frontend noch im Backend.

**E2E-Tests (Playwright):** E2E-Tests laufen IMMER mit dem NestJS-Backend — es gibt keinen Click-Dummy-Modus mehr. Hierzu existiert eine separate Playwright-Konfiguration (`playwright-backend.config.ts`), die das Backend auf Port 3000 startet und die Angular-App von dort serviert.

- **Lokal / GitHub Actions (Dev):** `npm run e2e:backend` — Backend verbindet sich mit SoftNet Integration-API (`int-api.soft-nrg.com`)
- **Kubernetes Pod (Prod):** Gleicher Command — Backend verbindet sich mit SoftNet Production-API (`api.soft-nrg.com`)

**PFLICHT:** Bei jeder Aenderung am Backend oder an der Frontend-Backend-Integration MUESSEN die E2E-Tests erfolgreich laufen. Die E2E-Tests pruefen:
- Static File Serving (Angular-App wird vom Backend ausgeliefert)
- API-Endpunkte funktionieren korrekt
- Origin Guard blockiert externe Requests
- Circuit Breaker im Frontend reagiert korrekt auf Backend-Fehler
- Security Headers (Helmet, CORS) sind korrekt gesetzt

Tests muessen ggf. an geaenderte Datenstrukturen (echte SoftNet-API-Daten statt fruehere Mock-Daten) angepasst werden.

### Deployment-Strategie

| Umgebung | SoftNet-Instanz | Env-Quelle | E2E |
|----------|----------------|-----------|-----|
| **Lokal** | Integration (`int-api.soft-nrg.com`) | `.env`-Datei | `npm run e2e:backend` |
| **GitHub Actions** | Integration (`int-api.soft-nrg.com`) | GitHub Secrets → `.env` | `npm run e2e:backend` |
| **Kubernetes (Prod)** | Production (`api.soft-nrg.com`) | **GitLab Secrets Store** → K8s Environment Variables | Im Pod |

**Prod-Secrets-Management:**
- Die `.env`-Datei wird NICHT in den Kubernetes Pod deployed
- Stattdessen werden alle Secrets im **GitLab CI/CD Secrets Store** hinterlegt (`Settings > CI/CD > Variables`)
- GitLab CI/CD injiziert die Secrets als **Environment Variables** in den Kubernetes Pod
- NestJS `ConfigModule` (mit `@nestjs/config`) liest sowohl `.env`-Dateien als auch `process.env` — daher funktioniert der gleiche Code in beiden Modi
- **Secrets die in GitLab konfiguriert werden muessen:**
  - `PORT`, `SOFTNET_READ_BASE_URL`, `SOFTNET_READ_AUTH_URL`, `SOFTNET_READ_FINGERPRINT_PATH`
  - `SOFTNET_WRITE_BASE_URL`, `SOFTNET_WRITE_AUTH_URL`, `SOFTNET_WRITE_FINGERPRINT_PATH`
  - `SOFTNET_TIMEOUT`, `GROUP_ID`, `BRANCH_ID`
  - `CORS_ALLOWED_ORIGINS`, `IFRAME_ALLOWED_ORIGINS`
  - `SESSION_SECRET`, `SESSION_TTL_MINUTES`
  - `TLS_ENABLED`, `TLS_CERT_PATH`, `TLS_KEY_PATH`
- Die Fingerprint-JSON-Dateien muessen ebenfalls im Pod verfuegbar sein (z.B. als Kubernetes Secret Volume Mount)

---

## 22. SoftNet API — Verifizierte Test-Erkenntnisse (02.04.2026)

> Die folgenden Erkenntnisse stammen aus echten API-Tests gegen die SoftNet Integration-Umgebung.

### 22.1 Authentifizierung (verifiziert, funktioniert)

**Flow:** JWT-Bearer Grant (OAuth 2.0)

1. **JWT erstellen** mit Payload:
   ```json
   {
     "id": "<private_key_id>",
     "scope": "scope.api.planning.service",
     "sub": "<client_email>",
     "iss": "<client_id>",
     "aud": "https://int-auth.soft-nrg.com/oauth/token",
     "iat": <unix_timestamp>,
     "exp": <iat + 300>
   }
   ```
   Header: `{ "alg": "RS256", "typ": "JWT" }`

2. **JWT signieren** mit RSA Private Key aus der Fingerprint-JSON-Datei

3. **Token anfordern:**
   ```http
   POST https://int-auth.soft-nrg.com/oauth/token
   Content-Type: application/x-www-form-urlencoded

   grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=<signed_jwt>
   ```

4. **Response:**
   ```json
   { "access_token": "NjhkODdi...", "token_type": "Bearer", "expires_in": 3600 }
   ```

**WICHTIG:** Token laeuft nach **1 Stunde** ab. Backend muss Token cachen und rechtzeitig erneuern.

### 22.2 Pflicht-Header

| Header | Wert | Wann noetig |
|--------|------|-------------|
| `Authorization` | `Bearer <access_token>` | ALLE Requests |
| `x-nrg-group` | `<group_id>` (24-Zeichen Mongo-ID, NICHT cryptid!) | Alle Endpunkte ausser `/groups` und `/branches` |

**ACHTUNG:** Der `x-nrg-group` Header erwartet die kurze `id` (z.B. `698b34c24dd8eea4d6d42179`), NICHT die lange `cryptid`!

### 22.3 Verifizierte Endpunkte

| Endpunkt | Method | x-nrg-group | Status | Response-Details |
|----------|--------|-------------|--------|-----------------|
| `/api/planning/groups` | GET | NEIN | **200** | Array von Gruppen. Jede Gruppe hat: `id`, `cryptid`, `name`, `description`, `brand[]` (konfigurierte Marken), `allowOtherBrands`, `branches[]`, `acceptPassingCustomer`, `apptMaxDaysInFuture: 80`, `apptMaxCount: 50` |
| `/api/planning/branches` | GET | NEIN | **200** | Array von Branches. Jede Branch hat: `id`, `name`, `label` (Stadtname), `company`, `zip` |
| `/api/planning/brands` | GET | JA | **200** | ~150 System-Marken (NICHT gefiltert nach Gruppe!). Fuer Gruppen-spezifische Marken die `brand[]` aus der Groups-Response verwenden |
| `/api/planning/jobtypes` | GET | JA | **200** | Array von JobTypes: `{ id, name }`. Getestet: "normal" (`54c8aec06b41ffe254ac0101`), "client waiting" (`54c8aec06b41ffe254ac0102`) |
| `/api/planning/operations` | GET | JA | **200** | Array von Operations (Services): `{ id, name }`. Getestet: 10 Ops (Service Fahrzeug-Check, Raederwechsel, HU/AU, Service Bremse, Service Motoroel, Wischerblaetter, etc.) |
| `/api/planning/snaps` | POST | JA | **200** | Verfuegbare Terminslots. Siehe Details unten |
| `/api/planning/users/myprofile` | GET | — | **401** | Nicht verfuegbar fuer Service Accounts |

### 22.4 Snaps-Request (Terminslots abfragen — verifiziert)

**Request:**
```json
{
  "groupid": "<24-char-group-id>",
  "brand": "BMW",
  "car": {
    "brand": "BMW"
  },
  "operations": ["<operation-id>"],
  "preferredBranch": "<branch-id>",
  "checkInDatePreference": "asSoonAsPossible"
}
```

**Pflichtfelder:**
- `groupid` — Gruppe (24-Zeichen ID)
- `brand` — Markenname als String (z.B. "BMW", "Mercedes-Benz", "Volkswagen")
- `car.brand` — MUSS gesetzt sein, sonst Fehler "missing car brand"
- `operations` — Mindestens 1 Operation-ID

**Optionale Felder:**
- `preferredBranch` — Branch-ID fuer bevorzugten Standort
- `checkInDatePreference` — `"asSoonAsPossible"` oder `"date"` (mit `checkInDate` Feld)

**Response (Auszug):**
```json
{
  "snaps": [
    {
      "ranking": 0,
      "score": 1,
      "checkInTime": "2026-04-03T07:00:00.000Z",
      "checkOutTime": "2026-04-03T08:00:00.000Z",
      "daySpan": 1,
      "totalDurationTime": 4800,
      "checkInResource": "Bronze, Marten",
      "internal": "<uuid-fuer-buchung>",
      "branch": { "id": "<branch-id>", "name": "..." }
    }
  ],
  "wishlist": { ... }
}
```

**Erkenntnisse:**
- `totalDurationTime` ist in **Sekunden** (4800s = 80 Min)
- `internal` UUID wird fuer die Terminbuchung (`POST /appointments`) benoetigt
- `checkInResource` ist der Name des Serviceberaters
- Slots sind nach `ranking`/`score` sortiert (niedrig = besser)
- Es werden mehrere Berater parallel angeboten (z.B. "Bronze, Marten" und "Silber, Markus")

### 22.5 Wichtige Erkenntnisse fuer die Backend-Implementierung

1. **Marken-Filter:** `/api/planning/brands` liefert ALLE System-Marken (~150). Die tatsaechlich am Standort verfuegbaren Marken stehen im `brand[]`-Array der Groups-Response. Das Backend muss filtern!

2. **Group vs. Branch:** Eine Gruppe kann mehrere Branches haben. Die Group-ID ist der primaere Identifier fuer die meisten API-Calls, Branch-ID nur fuer Standort-Praeferenz.

3. **Token-Caching:** Access Token ist 1h gueltig. Backend sollte Token cachen und bei Ablauf (oder 401-Response) automatisch erneuern.

4. **Service Account Limitierung:** `/users/myprofile` funktioniert NICHT mit Service Accounts (401). Nur fuer User-Tokens.

5. **Fehlerformat:** Bei falscher Group-ID kommt `403: AccessContextError: "The method is not accessible from this context. Expected correct group id"`.

6. **car.brand ist Pflicht:** Snaps-Request schlaegt ohne `car.brand` fehl, auch wenn `brand` auf Top-Level gesetzt ist.

### 22.6 Test-Umgebung — Zugangs-IDs

**Funktioniert (Service Account freigeschaltet):**

| Standort | Group-ID | Branch-ID |
|----------|----------|-----------|
| Muenchen (Test) | `698b34c24dd8eea4d6d42179` | `58341c5cbe856f6dc47e9f05` |

**Freischaltung bei SoftNRG noetig:**

| Standort | Group-ID | Branch-ID | Status |
|----------|----------|-----------|--------|
| Wuppertal | `69bbe9d5d053d96948dea49e` | `68f6053f8d2bb4389ece92b5` | 403 — nicht freigeschaltet |
| An der Wickenburg (Essen) | `69ca5ebb6e3c53884e231f0b` | `68f60df88d2bb4389eec1d39` | 403 — nicht freigeschaltet |

**Auth-Endpunkte:**
- Integration Auth: `https://int-auth.soft-nrg.com/oauth/token`
- Integration API: `https://int-api.soft-nrg.com`
- Production Auth: `https://auth.soft-nrg.com/oauth/token`
- Production API: `https://api.soft-nrg.com`

### 22.7 Anpassungen am Data Model basierend auf Tests

Das `EnvironmentConfig`-Interface (Section 10) muss angepasst werden:

```typescript
interface EnvironmentConfig {
  // ... bestehende Felder ...
  softnet: {
    baseUrl: string;               // z.B. "https://int-api.soft-nrg.com"
    authUrl: string;               // z.B. "https://int-auth.soft-nrg.com/oauth/token" (NEU!)
    fingerprintPath: string;       // Pfad zur Fingerprint-JSON-Datei (NEU! statt accessToken)
    timeout: number;
  };
  groupId: string;                 // NEU: Group-ID (Pflicht fuer x-nrg-group Header)
  branchId: string;                // Branch-ID (fuer preferredBranch in Snaps)
}
```

**Aenderung:** Statt eines statischen `accessToken` wird die Fingerprint-Datei referenziert. Das Backend generiert JWTs und holt sich Tokens selbst. Zusaetzlich wird `groupId` und `authUrl` benoetigt.
